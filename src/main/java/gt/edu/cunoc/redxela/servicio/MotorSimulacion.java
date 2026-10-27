package gt.edu.cunoc.redxela.servicio;

import gt.edu.cunoc.redxela.modelo.*;
import java.util.*;
import java.util.concurrent.*;

/**
 * Ordena los pedidos y los procesa con trabajadores. Un segundo real equivale a un minuto simulado a 1x.
 * synchronized protege el reloj y los estados cuando los consultan la pantalla y los trabajadores.
 */
public final class MotorSimulacion implements AutoCloseable {

    private static final class Trabajo {

        private final Pedido pedido;
        private final double llegada;
        private final long secuencia;

        private EstadoPedido estado = EstadoPedido.REGISTRADO;
        private double inicio;
        private double fin;
        private double progreso;
        private double inicioFase;
        // Se guarda cada tramo de espera para sumar también los reintentos.
        private double desdeEspera;
        private double esperaAcumulada;
        private double procesoAnterior;
        private boolean preparado;
        private boolean despachando;
        private int numeroConflicto;
        private String fase = "Pendiente";
        private String error = "";
        private boolean preferente;

        private Trabajo(Pedido pedido, double llegada, long secuencia) {
            this.pedido = pedido;
            this.llegada = llegada;
            this.desdeEspera = llegada;
            this.secuencia = secuencia;
        }
    }

    /** Copia de los datos que necesita la pantalla; no permite cambiar el trabajo. */
    public record VistaPedido(
            Pedido pedido,
            EstadoPedido estado,
            double progreso,
            double restante,
            double espera,
            String fase,
            String error,
            boolean preferente
    ) {
    }

    /** Totales y promedios calculados con el reloj de la simulación. */
    public record Estadisticas(
            int completados,
            double esperaMedia,
            double procesoMedio,
            int recursosEnUso,
            int recursosTotales,
            double ocupacionMedia,
            double minutos
    ) {
    }

    private static final int MAXIMO_TRABAJADORES = 8;
    private static final long INTERVALO_RELOJ_MILISEGUNDOS = 60;
    private static final long INTERVALO_ESPERA_MILISEGUNDOS = 40;
    private final Almacen almacen;
    private final GestorRecursos recursos;
    private final MonitorConflictos conflictos;
    private final List<Trabajo> trabajos = new ArrayList<>();
    // Un hilo mueve el reloj y hasta ocho trabajadores procesan pedidos al mismo tiempo.
    private final ScheduledExecutorService reloj = Executors.newSingleThreadScheduledExecutor(new FabricaHilos("reloj"));
    private final ExecutorService trabajadores = Executors.newFixedThreadPool(MAXIMO_TRABAJADORES, new FabricaHilos("pedido"));
    private boolean activa;
    private boolean cerrada;
    private double minutos;
    private double velocidad = 1;
    private double tiempoOcupado;
    private double tiempoMedido;
    private long ultimoInstante = System.nanoTime();
    private long secuencia;
    private PoliticaAtencion politica = PoliticaAtencion.SERVICIO;

    /** Crea un motor independiente, usado al comparar políticas. */
    public MotorSimulacion(Almacen almacen, GestorRecursos recursos) {
        this(almacen, recursos, null);
    }

    /** Conecta el inventario, los recursos y el monitor de conflictos con los trabajadores. */
    public MotorSimulacion(Almacen almacen, GestorRecursos recursos, MonitorConflictos conflictos) {
        this.almacen = almacen;
        this.recursos = recursos;
        this.conflictos = conflictos;
        reloj.scheduleWithFixedDelay(this::actualizarReloj, 0, INTERVALO_RELOJ_MILISEGUNDOS, TimeUnit.MILLISECONDS);
    }

    /** Agrega una sola vez el pedido y registra su orden de llegada. */
    public synchronized void ingresar(Pedido pedido) {
        if (cerrada) {
            throw new IllegalStateException("La simulación está cerrada.");
        }
        if (pedido == null) {
            throw new IllegalArgumentException("El pedido es obligatorio.");
        }
        for (Trabajo trabajo : trabajos) {
            if (trabajo.pedido.id().equals(pedido.id())) {
                throw new IllegalArgumentException("Ese pedido ya está en la simulación.");
            }
        }
        trabajos.add(new Trabajo(pedido, minutos, ++secuencia));
    }

    /** Inicia o pausa el reloj; una pausa no elimina pedidos ni reservas. */
    public synchronized void activar(boolean valor) {
        if (cerrada) {
            throw new IllegalStateException("La simulación está cerrada.");
        }
        activa = valor;
        ultimoInstante = System.nanoTime();
    }

    public synchronized boolean activa() {
        return activa;
    }

    /** Cambia cuántos minutos simulados pasan por cada segundo real. */
    public synchronized void velocidad(double valor) {
        if (!Double.isFinite(valor) || valor < 0.5 || valor > 8) {
            throw new IllegalArgumentException("Velocidad no válida.");
        }
        velocidad = valor;
    }

    /** Cambia el criterio para la siguiente asignación, sin interrumpir pedidos en curso. */
    public synchronized void politica(PoliticaAtencion valor) {
        politica = Objects.requireNonNull(valor);
    }

    public synchronized PoliticaAtencion politica() {
        return politica;
    }

    /** Coloca un pedido pendiente delante de los demás sin quitarles recursos a los activos. */
    public synchronized void priorizar(UUID id) {
        Trabajo trabajo = null;
        for (Trabajo candidato : trabajos) {
            if (candidato.pedido.id().equals(id)) {
                trabajo = candidato;
                break;
            }
        }
        if (trabajo == null) {
            throw new IllegalArgumentException("El pedido no pertenece a la simulación.");
        }
        if (trabajo.estado == EstadoPedido.COMPLETADO || trabajo.estado == EstadoPedido.EN_PROCESO
                || trabajo.estado == EstadoPedido.EN_CONFLICTO) {
            throw new IllegalArgumentException("Selecciona un pedido que siga en espera.");
        }
        trabajo.preferente = true;
    }

    /** Mide el tiempo real transcurrido y lo convierte a minutos simulados. */
    private synchronized void actualizarReloj() {
        long ahora = System.nanoTime();
        double minutosTranscurridos = (ahora - ultimoInstante) / 1_000_000_000.0 * velocidad;
        ultimoInstante = ahora;
        if (!activa || cerrada) {
            return;
        }
        minutos += minutosTranscurridos;
        registrarOcupacion(minutosTranscurridos);
        asignar();
    }

    /** Avanza un minuto estando en pausa y vuelve a revisar los pedidos pendientes. */
    public synchronized void paso() {
        if (activa) {
            throw new IllegalArgumentException("Pausa la simulación antes de avanzar un minuto.");
        }
        if (cerrada) {
            return;
        }
        minutos += 1;
        registrarOcupacion(1);
        asignar();
    }

    /** Acumula el tiempo de uso de las 26 unidades para calcular la ocupación media. */
    private void registrarOcupacion(double duracion) {
        List<GestorRecursos.Estado> estados = recursos.estados();
        int ocupados = 0;
        for (GestorRecursos.Estado estado : estados) {
            if (!estado.disponible()) {
                ocupados++;
            }
        }
        tiempoOcupado += duracion * ocupados;
        tiempoMedido += duracion * estados.size();
    }

    /** Revisa la cola en orden, reserva inventario y asigna todos los recursos necesarios. */
    private void asignar() {
        int activos = 0;
        for (Trabajo trabajo : trabajos) {
            if (trabajo.estado == EstadoPedido.EN_PROCESO || trabajo.estado == EstadoPedido.EN_CONFLICTO) {
                activos++;
            }
        }
        List<Trabajo> candidatos = pendientes();
        // Una reserva pendiente no debe anular un cambio de política ni la llegada de mayor prioridad.
        for (Trabajo trabajo : candidatos) {
            almacen.liberar(trabajo.pedido.id());
        }
        for (Trabajo trabajo : candidatos) {
            if (activos >= MAXIMO_TRABAJADORES) {
                break;
            }
            if (conflictos != null && !conflictos.permiteDespacho(trabajo.pedido.id(), trabajo.numeroConflicto)) {
                trabajo.estado = EstadoPedido.ESPERANDO_RECURSOS;
                trabajo.fase = "Reencolado; esperando el despacho del pedido seleccionado";
                continue;
            }
            if (!almacen.reservar(trabajo.pedido.id(), trabajo.pedido.detalles())) {
                trabajo.estado = EstadoPedido.ESPERANDO_INVENTARIO;
                trabajo.fase = "Sin existencias";
                continue;
            }
            Map<TipoRecurso, Integer> requeridos = trabajo.preparado ? recursosDespacho() : necesidades(trabajo.pedido);
            if (!recursos.intentar(titular(trabajo), requeridos)) {
                trabajo.estado = EstadoPedido.ESPERANDO_RECURSOS;
                trabajo.fase = trabajo.preparado ? "Esperando recursos de despacho" : "Recursos ocupados";
                continue;
            }
            trabajo.estado = EstadoPedido.EN_PROCESO;
            trabajo.esperaAcumulada += minutos - trabajo.desdeEspera;
            trabajo.inicio = minutos;
            trabajo.inicioFase = minutos;
            trabajo.fase = trabajo.preparado ? "Despacho" : "Preparación";
            trabajo.error = "";
            activos++;
            trabajadores.submit(() -> procesar(trabajo));
        }
    }

    /** Prepara y despacha en un trabajador. El bloque finally libera los recursos al salir. */
    private void procesar(Trabajo trabajo) {
        boolean terminado = false;
        boolean esperandoDespacho = false;
        int numeroConflicto = trabajo.numeroConflicto;
        try {
            if (!trabajo.preparado) {
                avanzar(trabajo, Math.max(1, trabajo.pedido.getTotalArticulos() * 0.25), 0, 0.85);
                recursos.liberar(titular(trabajo));
                synchronized (this) {
                    trabajo.preparado = true;
                    trabajo.fase = "Esperando despacho";
                }
                if (conflictos != null && conflictos.participa(trabajo.pedido.id(), numeroConflicto)) {
                    prepararConflictoDespacho(trabajo, numeroConflicto);
                } else {
                    synchronized (this) {
                        if (cerrada) {
                            throw new InterruptedException();
                        }
                        if (!recursos.intentar(titular(trabajo), recursosDespacho())) {
                            // Conserva la preparación y deja que el planificador decida el siguiente intento.
                            esperandoDespacho = true;
                            return;
                        }
                    }
                }
            } else if (conflictos != null && conflictos.participa(trabajo.pedido.id(), numeroConflicto)) {
                recursos.liberar(titular(trabajo));
                prepararConflictoDespacho(trabajo, numeroConflicto);
            }
            synchronized (this) {
                trabajo.estado = EstadoPedido.EN_PROCESO;
                trabajo.despachando = true;
                trabajo.inicioFase = minutos;
                trabajo.fase = "Despacho";
            }
            avanzar(trabajo, 0.6, 0.85, 1);
            synchronized (this) {
                almacen.despachar(trabajo.pedido.id());
                trabajo.progreso = 1;
                trabajo.fin = minutos;
                trabajo.estado = EstadoPedido.COMPLETADO;
                trabajo.fase = "Entregado";
                terminado = true;
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        } catch (RuntimeException exception) {
            synchronized (this) {
                trabajo.error = exception.getMessage();
            }
        } finally {
            synchronized (this) {
                recursos.liberar(titular(trabajo));
                if (!terminado) {
                    almacen.liberar(trabajo.pedido.id());
                    trabajo.procesoAnterior += minutos - trabajo.inicio;
                    trabajo.desdeEspera = minutos;
                    trabajo.estado = esperandoDespacho ? EstadoPedido.ESPERANDO_RECURSOS : EstadoPedido.REGISTRADO;
                    trabajo.progreso = trabajo.preparado ? 0.85 : 0;
                    trabajo.despachando = false;
                    if (esperandoDespacho) {
                        trabajo.fase = "Esperando recursos de despacho";
                    } else if (trabajo.preparado) {
                        trabajo.fase = "Reencolado para despacho";
                    } else {
                        trabajo.fase = "Reencolado";
                    }
                }
            }
            if (conflictos != null) {
                conflictos.finalizarIntento(trabajo.pedido.id(), numeroConflicto, terminado);
            }
        }
    }

    /** Espera al segundo pedido y después retiene un recurso mientras solicita el otro. */
    private void prepararConflictoDespacho(Trabajo trabajo, int numero) throws InterruptedException {
        synchronized (this) {
            trabajo.fase = "Esperando al otro pedido para preparar el conflicto";
        }
        conflictos.prepararDespacho(trabajo.pedido.id(), numero);
        synchronized (this) {
            trabajo.estado = EstadoPedido.EN_CONFLICTO;
            trabajo.fase = "Espera circular entre Área de carga 1 y Escáner";
        }
        conflictos.esperarDespacho(trabajo.pedido.id(), numero);
    }

    /** Todos los pedidos usan un área de carga y el escáner al despachar. */
    private Map<TipoRecurso, Integer> recursosDespacho() {
        return Map.of(TipoRecurso.ESCANER, 1, TipoRecurso.AREA_CARGA, 1);
    }

    /** Estima el trabajo que falta; no puede predecir cuánto tardará en liberarse un recurso. */
    private double tiempoRestante(Trabajo trabajo) {
        if (trabajo.estado == EstadoPedido.COMPLETADO) {
            return 0;
        }
        if (trabajo.preparado) {
            return trabajo.despachando ? Math.max(0, 0.6 - (minutos - trabajo.inicioFase)) : 0.6;
        }
        double preparacion = Math.max(1, trabajo.pedido.getTotalArticulos() * 0.25);
        if (trabajo.estado == EstadoPedido.EN_PROCESO) {
            preparacion = Math.max(0, preparacion - (minutos - trabajo.inicioFase));
        }
        return preparacion + 0.6;
    }

    /** Programa el conflicto sin crear pedidos ni omitir su preparación. */
    public synchronized void provocarConflicto(UUID pedidoA, UUID pedidoB) {
        if (cerrada || conflictos == null) {
            throw new IllegalArgumentException("El monitor de conflictos no está disponible.");
        }
        if (pedidoA == null || pedidoB == null || pedidoA.equals(pedidoB)) {
            throw new IllegalArgumentException("Selecciona dos pedidos pendientes distintos.");
        }
        Trabajo trabajoA = buscarPendiente(pedidoA);
        Trabajo trabajoB = buscarPendiente(pedidoB);
        Map<Producto, Integer> disponibles = almacen.existencias();
        for (Trabajo trabajo : trabajos) {
            if (trabajo.estado == EstadoPedido.EN_PROCESO || trabajo.estado == EstadoPedido.EN_CONFLICTO) {
                for (DetallePedido detalle : trabajo.pedido.detalles()) {
                    Producto producto = detalle.producto();
                    disponibles.put(producto, disponibles.getOrDefault(producto, 0) - detalle.cantidad());
                }
            }
        }
        for (Trabajo trabajo : new Trabajo[] { trabajoA, trabajoB }) {
            for (DetallePedido detalle : trabajo.pedido.detalles()) {
                Producto producto = detalle.producto();
                int cantidad = disponibles.getOrDefault(producto, 0) - detalle.cantidad();
                if (cantidad < 0) {
                    throw new IllegalArgumentException("No hay existencias suficientes para preparar ambos pedidos.");
                }
                disponibles.put(producto, cantidad);
            }
        }
        int numero = conflictos.configurar(pedidoA, pedidoB, titular(trabajoA), titular(trabajoB));
        trabajoA.numeroConflicto = numero;
        trabajoB.numeroConflicto = numero;
    }

    /** Busca solo entre pedidos que todavía pueden recibir una asignación. */
    private Trabajo buscarPendiente(UUID id) {
        for (Trabajo trabajo : pendientes()) {
            if (trabajo.pedido.id().equals(id)) {
                return trabajo;
            }
        }
        throw new IllegalArgumentException("El pedido ya no está pendiente.");
    }

    /** Actualiza el progreso usando minutos simulados; por eso también respeta la pausa. */
    private void avanzar(Trabajo trabajo, double duracion, double desde, double hasta) throws InterruptedException {
        double inicio;
        synchronized (this) {
            inicio = minutos;
            trabajo.inicioFase = inicio;
        }
        while (true) {
            synchronized (this) {
                if (cerrada) {
                    throw new InterruptedException();
                }
                double fraccion = Math.min(1, (minutos - inicio) / duracion);
                trabajo.progreso = desde + (hasta - desde) * fraccion;
                if (fraccion >= 1) {
                    return;
                }
            }
            Thread.sleep(INTERVALO_ESPERA_MILISEGUNDOS);
        }
    }

    /** Crea una lista ordenada sin cambiar la lista que conserva todos los pedidos. */
    private List<Trabajo> pendientes() {
        List<Trabajo> resultado = new ArrayList<>();
        for (Trabajo trabajo : trabajos) {
            if (trabajo.estado != EstadoPedido.COMPLETADO && trabajo.estado != EstadoPedido.EN_PROCESO
                    && trabajo.estado != EstadoPedido.EN_CONFLICTO) {
                resultado.add(trabajo);
            }
        }
        resultado.sort(this::compararTrabajos);
        return resultado;
    }

    /** Muestra los bloqueados y después los pendientes según la política actual. */
    public synchronized List<VistaPedido> espera() {
        List<VistaPedido> resultado = new ArrayList<>();
        for (Trabajo trabajo : trabajos) {
            if (trabajo.estado == EstadoPedido.EN_CONFLICTO) {
                resultado.add(vista(trabajo));
            }
        }
        for (Trabajo trabajo : pendientes()) {
            resultado.add(vista(trabajo));
        }
        return resultado;
    }

    /** Devuelve copias de lectura para que la interfaz no modifique los trabajos. */
    public synchronized List<VistaPedido> pedidos() {
        List<VistaPedido> resultado = new ArrayList<>();
        for (Trabajo trabajo : trabajos) {
            resultado.add(vista(trabajo));
        }
        return resultado;
    }

    /** Suma la espera actual a los intentos anteriores y prepara los datos de pantalla. */
    private VistaPedido vista(Trabajo trabajo) {
        double espera = trabajo.esperaAcumulada;
        if (trabajo.estado != EstadoPedido.EN_PROCESO && trabajo.estado != EstadoPedido.EN_CONFLICTO
                && trabajo.estado != EstadoPedido.COMPLETADO) {
            espera += minutos - trabajo.desdeEspera;
        }
        return new VistaPedido(
                trabajo.pedido,
                trabajo.estado,
                trabajo.progreso,
                tiempoRestante(trabajo),
                Math.max(0, espera),
                trabajo.fase,
                trabajo.error,
                trabajo.preferente
        );
    }

    /** Calcula los promedios de los completados y la ocupación de toda la sesión. */
    public synchronized Estadisticas estadisticas() {
        int completados = 0;
        double esperaTotal = 0;
        double procesoTotal = 0;
        for (Trabajo trabajo : trabajos) {
            if (trabajo.estado == EstadoPedido.COMPLETADO) {
                completados++;
                esperaTotal += trabajo.esperaAcumulada;
                procesoTotal += trabajo.procesoAnterior + trabajo.fin - trabajo.inicio;
            }
        }
        List<GestorRecursos.Estado> unidades = recursos.estados();
        double esperaMedia = completados == 0 ? 0 : esperaTotal / completados;
        double procesoMedio = completados == 0 ? 0 : procesoTotal / completados;
        int recursosEnUso = 0;
        for (GestorRecursos.Estado unidad : unidades) {
            if (!unidad.disponible()) {
                recursosEnUso++;
            }
        }
        double ocupacionMedia = tiempoMedido == 0 ? 0 : tiempoOcupado / tiempoMedido;

        return new Estadisticas(completados, esperaMedia, procesoMedio,
                recursosEnUso, unidades.size(), ocupacionMedia, minutos);
    }

    /** Usa el mismo nombre del pedido en todas sus reservas de recursos. */
    private String titular(Trabajo trabajo) {
        return "P-" + trabajo.pedido.id().toString().substring(0, 8);
    }

    /** Reúne los recursos de sus mercancías tomando la mayor cantidad de cada tipo. */
    private Map<TipoRecurso, Integer> necesidades(Pedido pedido) {
        Map<TipoRecurso, Integer> resultado = new EnumMap<>(TipoRecurso.class);
        // Los tipos se preparan usando la mayor cantidad necesaria de cada recurso, sin multiplicar por unidades.
        for (DetallePedido detalle : pedido.detalles()) {
            Map<TipoRecurso, Integer> requeridos = detalle.producto().tipoMercancia().getRecursosRequeridos();
            for (Map.Entry<TipoRecurso, Integer> recurso : requeridos.entrySet()) {
                int cantidadActual = resultado.getOrDefault(recurso.getKey(), 0);
                resultado.put(recurso.getKey(), Math.max(cantidadActual, recurso.getValue()));
            }
        }
        return resultado;
    }

    /** Detiene el reloj y los trabajadores antes de limpiar cualquier reserva pendiente. */
    @Override
    public void close() {
        synchronized (this) {
            cerrada = true;
            activa = false;
        }
        // Los trabajadores liberan sus reservas en finally antes de dar por terminado el cierre.
        FabricaHilos.detener(reloj);
        FabricaHilos.detener(trabajadores);
        synchronized (this) {
            for (Trabajo trabajo : trabajos) {
                almacen.liberar(trabajo.pedido.id());
                recursos.liberar(titular(trabajo));
            }
        }
    }

    /** Compara dos pedidos. Una igualdad siempre se resuelve por orden de llegada. */
    private int compararTrabajos(Trabajo primero, Trabajo segundo) {
        if (primero.preferente != segundo.preferente) {
            return primero.preferente ? -1 : 1;
        }
        int comparacion = 0;
        switch (politica) {
            case FIFO:
                break;
            case SJF:
                comparacion = Integer.compare(primero.pedido.getTotalArticulos(), segundo.pedido.getTotalArticulos());
                break;
            case SERVICIO:
                comparacion = Integer.compare(primero.pedido.nivelServicio().getPrioridad(), segundo.pedido.nivelServicio().getPrioridad());
                break;
            case ENVEJECIMIENTO:
                comparacion = Integer.compare(prioridadConEspera(primero), prioridadConEspera(segundo));
                break;
        }
        return comparacion == 0 ? Long.compare(primero.secuencia, segundo.secuencia) : comparacion;
    }

    /** Cada diez minutos de antigüedad mejora un nivel, hasta llegar a prioridad uno. */
    private int prioridadConEspera(Trabajo trabajo) {
        int nivelesGanados = (int) ((minutos - trabajo.llegada) / 10);
        return Math.max(1, trabajo.pedido.nivelServicio().getPrioridad() - nivelesGanados);
    }
}
