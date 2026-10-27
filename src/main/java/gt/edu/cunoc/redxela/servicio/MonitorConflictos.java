package gt.edu.cunoc.redxela.servicio;

import gt.edu.cunoc.redxela.modelo.TipoRecurso;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;

/** Coordina la espera circular entre dos pedidos que llegan al despacho. */
public final class MonitorConflictos implements AutoCloseable {

    /** Datos de lectura que indican qué acciones puede habilitar la pantalla. */
    public record Vista(boolean activo, boolean recuperando, boolean listo, boolean preparando,
            UUID pedidoA, UUID pedidoB, String mensaje) {
    }

    private static final class Escenario {

        private final int numero;
        private final UUID pedidoA;
        private final UUID pedidoB;
        private final String titularA;
        private final String titularB;
        // Cada contador llega a cero cuando ambos pedidos alcanzan la misma etapa.
        private final CountDownLatch preparados = new CountDownLatch(2);
        private final CountDownLatch reservados = new CountDownLatch(1);
        private final CountDownLatch listos = new CountDownLatch(2);
        // Se conservan los trabajadores para poder interrumpir solo al pedido que debe volver a la cola.
        private Thread hiloA;
        private Thread hiloB;
        private boolean continuaA;
        private boolean resolviendo;
        private boolean ganadorFinalizado;
        private boolean completoA;
        private boolean completoB;
        private boolean terminado;
        private boolean cancelado;

        private Escenario(int numero, UUID pedidoA, UUID pedidoB, String titularA, String titularB) {
            this.numero = numero;
            this.pedidoA = pedidoA;
            this.pedidoB = pedidoB;
            this.titularA = titularA;
            this.titularB = titularB;
        }
    }

    private final GestorRecursos recursos;
    private Escenario escenario;
    private int secuencia;
    private boolean cerrado;
    private String mensaje = "No hay esperas circulares detectadas.";

    public MonitorConflictos(GestorRecursos recursos) {
        this.recursos = recursos;
    }

    /** Crea un escenario nuevo y le asigna un número para distinguirlo de intentos anteriores. */
    public synchronized int configurar(UUID pedidoA, UUID pedidoB, String titularA, String titularB) {
        if (cerrado) {
            throw new IllegalArgumentException("El monitor de conflictos está cerrado.");
        }
        if (pedidoA == null || pedidoB == null || pedidoA.equals(pedidoB)
                || titularA == null || titularB == null || titularA.equals(titularB)) {
            throw new IllegalArgumentException("Selecciona dos pedidos pendientes distintos.");
        }
        if (escenario != null && (!escenario.terminado || escenario.hiloA != null || escenario.hiloB != null)) {
            throw new IllegalArgumentException("Finaliza el escenario actual antes de iniciar otro.");
        }
        escenario = new Escenario(++secuencia, pedidoA, pedidoB, titularA, titularB);
        mensaje = "Los dos pedidos se prepararán antes de provocar la espera circular en despacho.";
        return escenario.numero;
    }

    /** Comprueba que este pedido todavía deba entrar al conflicto preparado. */
    public synchronized boolean participa(UUID pedido, int numero) {
        return escenario != null && escenario.numero == numero && !escenario.terminado
                && !escenario.resolviendo && pertenece(escenario, pedido);
    }

    /** El pedido reencolado espera hasta que termine el elegido para continuar. */
    public synchronized boolean permiteDespacho(UUID pedido, int numero) {
        if (escenario == null || escenario.numero != numero || escenario.terminado || !escenario.resolviendo) {
            return true;
        }
        UUID ganador = escenario.continuaA ? escenario.pedidoA : escenario.pedidoB;
        return pedido.equals(ganador) || escenario.ganadorFinalizado;
    }

    /** Ambos pedidos liberan la preparación antes de retener los recursos de despacho. */
    public void prepararDespacho(UUID pedido, int numero) throws InterruptedException {
        Escenario actual;
        boolean esA;
        synchronized (this) {
            actual = comprobarEscenario(numero);
            if (!pertenece(actual, pedido)) {
                throw new InterruptedException("El pedido no pertenece a este escenario.");
            }
            esA = pedido.equals(actual.pedidoA);
            if (esA) {
                actual.hiloA = Thread.currentThread();
            } else {
                actual.hiloB = Thread.currentThread();
            }
        }
        actual.preparados.countDown();
        // La espera queda fuera de synchronized para que el otro trabajador pueda entrar.
        actual.preparados.await();
        if (esA) {
            boolean reservado = false;
            while (!reservado) {
                synchronized (this) {
                    comprobarEscenario(numero);
                    reservado = recursos.prepararConflicto(actual.titularA, actual.titularB);
                }
                if (!reservado) {
                    Thread.sleep(40);
                }
            }
            actual.reservados.countDown();
        }
        actual.reservados.await();
        synchronized (this) {
            comprobarEscenario(numero);
        }
    }

    /** El segundo recurso se solicita con una espera real sobre su semáforo. */
    public void esperarDespacho(UUID pedido, int numero) throws InterruptedException {
        Escenario actual;
        synchronized (this) {
            actual = comprobarEscenario(numero);
            if (!pertenece(actual, pedido)) {
                throw new InterruptedException("El pedido no pertenece a este escenario.");
            }
        }
        actual.listos.countDown();
        actual.listos.await();
        if (pedido.equals(actual.pedidoA)) {
            recursos.esperarUnidad(actual.titularA, TipoRecurso.ESCANER, 1);
        } else {
            recursos.esperarUnidad(actual.titularB, TipoRecurso.AREA_CARGA, 1);
        }
        synchronized (this) {
            comprobarEscenario(numero);
        }
    }

    /** Interrumpe al otro pedido para que su finally libere el recurso que necesita el elegido. */
    public void resolver(boolean continuaA) {
        Thread interrumpido;
        synchronized (this) {
            if (escenario == null || escenario.terminado || escenario.resolviendo || escenario.listos.getCount() != 0) {
                throw new IllegalArgumentException("Los pedidos todavía no están listos para resolver el conflicto.");
            }
            escenario.continuaA = continuaA;
            escenario.resolviendo = true;
            interrumpido = continuaA ? escenario.hiloB : escenario.hiloA;
            mensaje = "El pedido seleccionado continúa el despacho. El otro vuelve a la lista de espera.";
        }
        interrumpido.interrupt();
    }

    /** Cancela el escenario antes de formar el ciclo y devuelve los pedidos al flujo normal. */
    public void cancelarPreparacion() {
        Escenario actual;
        synchronized (this) {
            if (escenario == null || escenario.terminado || escenario.resolviendo || escenario.listos.getCount() == 0) {
                throw new IllegalArgumentException("Solo se puede cancelar un escenario que todavía se está preparando.");
            }
            actual = escenario;
            actual.cancelado = true;
            actual.terminado = true;
            mensaje = "Preparación cancelada. Los pedidos continúan por la cola normal.";
        }
        interrumpir(actual);
    }

    /** Recibe el resultado del mismo trabajador que procesa el pedido. */
    public void finalizarIntento(UUID pedido, int numero, boolean completado) {
        Escenario escenarioCancelado = null;
        synchronized (this) {
            if (escenario == null || escenario.numero != numero || !pertenece(escenario, pedido)) {
                return;
            }
            if (pedido.equals(escenario.pedidoA) && escenario.hiloA == Thread.currentThread()) {
                escenario.hiloA = null;
            }
            if (pedido.equals(escenario.pedidoB) && escenario.hiloB == Thread.currentThread()) {
                escenario.hiloB = null;
            }
            if (escenario.terminado) {
                return;
            }
            if (completado) {
                if (pedido.equals(escenario.pedidoA)) {
                    escenario.completoA = true;
                } else {
                    escenario.completoB = true;
                }
                UUID ganador = escenario.continuaA ? escenario.pedidoA : escenario.pedidoB;
                if (escenario.resolviendo && pedido.equals(ganador)) {
                    escenario.ganadorFinalizado = true;
                    mensaje = "El pedido seleccionado terminó. El pedido reencolado ya puede continuar.";
                }
                if (escenario.completoA && escenario.completoB) {
                    escenario.terminado = true;
                    mensaje = "Conflicto resuelto. Ambos pedidos completaron el despacho.";
                }
            } else if (!escenario.resolviendo || pedido.equals(escenario.continuaA ? escenario.pedidoA : escenario.pedidoB)) {
                escenario.cancelado = true;
                escenario.terminado = true;
                mensaje = "El escenario se interrumpió. Los pedidos vuelven al procesamiento normal.";
                escenarioCancelado = escenario;
            }
        }
        if (escenarioCancelado != null) {
            interrumpir(escenarioCancelado);
        }
    }

    /** Informa si el escenario está en preparación, bloqueado o en recuperación. */
    public synchronized Vista vista() {
        boolean vigente = escenario != null && !escenario.terminado;
        boolean listo = vigente && !escenario.resolviendo && escenario.listos.getCount() == 0;
        boolean recuperando = vigente && escenario.resolviendo;
        boolean preparando = vigente && !listo && !recuperando;
        return new Vista(listo, recuperando, listo, preparando,
                escenario == null ? null : escenario.pedidoA,
                escenario == null ? null : escenario.pedidoB, mensaje);
    }

    /** Evita que un trabajador continúe con un escenario cancelado o reemplazado. */
    private Escenario comprobarEscenario(int numero) throws InterruptedException {
        if (cerrado || escenario == null || escenario.numero != numero || escenario.cancelado || escenario.terminado) {
            throw new InterruptedException("El escenario ya no está activo.");
        }
        return escenario;
    }

    private boolean pertenece(Escenario actual, UUID pedido) {
        return pedido.equals(actual.pedidoA) || pedido.equals(actual.pedidoB);
    }

    /** Avisa a los trabajadores del escenario para que salgan de sus esperas. */
    private synchronized void interrumpir(Escenario actual) {
        if (actual.hiloA != null && actual.hiloA != Thread.currentThread()) {
            actual.hiloA.interrupt();
        }
        if (actual.hiloB != null && actual.hiloB != Thread.currentThread()) {
            actual.hiloB.interrupt();
        }
    }

    /** Cancela el escenario y despierta a los trabajadores bloqueados antes del cierre del motor. */
    @Override
    public void close() {
        Escenario actual;
        synchronized (this) {
            cerrado = true;
            actual = escenario;
            if (actual != null) {
                actual.cancelado = true;
                actual.terminado = true;
            }
        }
        if (actual != null) {
            interrumpir(actual);
        }
    }
}
