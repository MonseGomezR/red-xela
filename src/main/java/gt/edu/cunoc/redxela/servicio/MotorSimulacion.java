package gt.edu.cunoc.redxela.servicio;

import gt.edu.cunoc.redxela.modelo.*;
import java.util.*;
import java.util.concurrent.*;

/**
 * Planificación y trabajadores reales. Un segundo real equivale a un minuto simulado a 1x.
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
        private String fase = "Pendiente";
        private String error = "";
        private boolean preferente;

        private Trabajo(Pedido pedido, double llegada, long secuencia) {
            this.pedido = pedido;
            this.llegada = llegada;
            this.secuencia = secuencia;
        }
    }

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
    private final List<Trabajo> trabajos = new ArrayList<>();
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

    public MotorSimulacion(Almacen almacen, GestorRecursos recursos) {
        this.almacen = almacen;
        this.recursos = recursos;
        reloj.scheduleWithFixedDelay(this::actualizarReloj, 0, INTERVALO_RELOJ_MILISEGUNDOS, TimeUnit.MILLISECONDS);
    }

    public synchronized void ingresar(Pedido pedido) {
        if (cerrada) {
            throw new IllegalStateException("La simulación está cerrada.");
        }
        trabajos.add(new Trabajo(pedido, minutos, ++secuencia));
    }

    public synchronized void activar(boolean valor) {
        activa = valor;
        ultimoInstante = System.nanoTime();
    }

    public synchronized boolean activa() {
        return activa;
    }

    public synchronized void velocidad(double valor) {
        if (!Double.isFinite(valor) || valor < 0.5 || valor > 8) {
            throw new IllegalArgumentException("Velocidad no válida.");
        }
        velocidad = valor;
    }

    public synchronized void politica(PoliticaAtencion valor) {
        politica = Objects.requireNonNull(valor);
    }

    public synchronized PoliticaAtencion politica() {
        return politica;
    }

    public synchronized void priorizar(UUID id) {
        Trabajo trabajo = trabajos
                .stream()
                .filter(candidato -> candidato.pedido.id().equals(id))
                .findFirst()
                .orElseThrow();
        if (trabajo.estado == EstadoPedido.COMPLETADO || trabajo.estado == EstadoPedido.EN_PROCESO) {
            throw new IllegalArgumentException("Selecciona un pedido que siga en espera.");
        }
        trabajo.preferente = true;
    }

    private synchronized void actualizarReloj() {
        long ahora = System.nanoTime();
        double minutosTranscurridos = (ahora - ultimoInstante) / 1_000_000_000.0 * velocidad;
        ultimoInstante = ahora;
        if (!activa || cerrada) {
            return;
        }
        minutos += minutosTranscurridos;
        List<GestorRecursos.Estado> estados = recursos.estados();
        tiempoOcupado += minutosTranscurridos * estados
                .stream()
                .filter(estadoRecurso -> !estadoRecurso.disponible()).count();
        tiempoMedido += minutosTranscurridos * estados.size();
        asignar();
    }

    public synchronized void paso() {
        if (activa) {
            throw new IllegalArgumentException("Pausa la simulación antes de avanzar un minuto.");
        }
        if (cerrada) {
            return;
        }
        minutos += 1;
        asignar();
    }

    private void asignar() {
        int activos = (int) trabajos.stream().filter(trabajo -> trabajo.estado == EstadoPedido.EN_PROCESO).count();
        List<Trabajo> candidatos = pendientes();
        // Una reserva pendiente no debe anular un cambio de política ni la llegada de mayor prioridad.
        candidatos.forEach(trabajo -> almacen.liberar(trabajo.pedido.id()));
        for (Trabajo trabajo : candidatos) {
            if (activos >= MAXIMO_TRABAJADORES) {
                break;
            }
            if (!almacen.reservar(trabajo.pedido.id(), trabajo.pedido.detalles())) {
                trabajo.estado = EstadoPedido.ESPERANDO_INVENTARIO;
                trabajo.fase = "Sin existencias";
                continue;
            }
            if (!recursos.intentar(titular(trabajo), necesidades(trabajo.pedido))) {
                trabajo.estado = EstadoPedido.ESPERANDO_RECURSOS;
                trabajo.fase = "Recursos ocupados";
                continue;
            }
            trabajo.estado = EstadoPedido.EN_PROCESO;
            trabajo.inicio = minutos;
            trabajo.fase = "Preparación";
            activos++;
            trabajadores.submit(() -> procesar(trabajo));
        }
    }

    private void procesar(Trabajo trabajo) {
        boolean terminado = false;
        try {
            avanzar(trabajo, Math.max(1, trabajo.pedido.getTotalArticulos() * 0.25), 0, 0.85);
            recursos.liberar(titular(trabajo));
            synchronized (this) {
                trabajo.fase = "Esperando despacho";
            }
            esperarRecursosDespacho(trabajo);
            synchronized (this) {
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
                    trabajo.estado = EstadoPedido.REGISTRADO;
                    trabajo.progreso = 0;
                    trabajo.fase = "Reencolado";
                }
            }
        }
    }

    private void avanzar(Trabajo trabajo, double duracion, double desde, double hasta) throws InterruptedException {
        double inicio;
        synchronized (this) {
            inicio = minutos;
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

    private List<Trabajo> pendientes() {
        Comparator<Trabajo> orden = crearOrdenAtencion();
        return trabajos
                .stream()
                .filter(trabajo -> trabajo.estado != EstadoPedido.COMPLETADO && trabajo.estado != EstadoPedido.EN_PROCESO)
                .sorted(Comparator.<Trabajo>comparingInt(trabajo -> trabajo.preferente ? 0 : 1)
                .thenComparing(orden)
                .thenComparingLong(trabajo -> trabajo.secuencia))
                .toList();
    }

    public synchronized List<VistaPedido> espera() {
        return pendientes().stream().map(this::vista).toList();
    }

    public synchronized List<VistaPedido> pedidos() {
        return trabajos.stream().map(this::vista).toList();
    }

    private VistaPedido vista(Trabajo trabajo) {
        double duracionTotal = Math.max(1, trabajo.pedido.getTotalArticulos() * 0.25) + 0.6;
        double espera = (trabajo.estado == EstadoPedido.EN_PROCESO || trabajo.estado == EstadoPedido.COMPLETADO ? trabajo.inicio : minutos) - trabajo.llegada;
        return new VistaPedido(
                trabajo.pedido,
                trabajo.estado,
                trabajo.progreso,
                duracionTotal * (1 - trabajo.progreso),
                Math.max(0, espera),
                trabajo.fase,
                trabajo.error,
                trabajo.preferente
        );
    }

    public synchronized Estadisticas estadisticas() {
        List<Trabajo> completados = trabajos
                .stream()
                .filter(trabajo -> trabajo.estado == EstadoPedido.COMPLETADO)
                .toList();
        List<GestorRecursos.Estado> unidades = recursos.estados();
        double esperaMedia = completados.stream()
                .mapToDouble(trabajo -> trabajo.inicio - trabajo.llegada)
                .average()
                .orElse(0);
        double procesoMedio = completados.stream()
                .mapToDouble(trabajo -> trabajo.fin - trabajo.inicio)
                .average()
                .orElse(0);
        int recursosEnUso = (int) unidades.stream()
                .filter(estadoRecurso -> !estadoRecurso.disponible())
                .count();
        double ocupacionMedia = tiempoMedido == 0 ? 0 : tiempoOcupado / tiempoMedido;

        return new Estadisticas(completados.size(), esperaMedia, procesoMedio,
                recursosEnUso, unidades.size(), ocupacionMedia, minutos);
    }

    private String titular(Trabajo trabajo) {
        return "P-" + trabajo.pedido.id().toString().substring(0, 8);
    }

    private Map<TipoRecurso, Integer> necesidades(Pedido pedido) {
        Map<TipoRecurso, Integer> resultado = new EnumMap<>(TipoRecurso.class);
        // Los tipos se preparan usando la mayor cantidad necesaria de cada recurso, sin multiplicar por unidades.
        for (DetallePedido detalle : pedido.detalles()) {
            Map<TipoRecurso, Integer> requeridos = detalle.producto().tipoMercancia().getRecursosRequeridos();
            for (Map.Entry<TipoRecurso, Integer> recurso : requeridos.entrySet()) {
                resultado.merge(recurso.getKey(), recurso.getValue(), Math::max);
            }
        }
        return resultado;
    }

    @Override
    public void close() {
        synchronized (this) {
            cerrada = true;
            activa = false;
        }
        reloj.shutdownNow();
        trabajadores.shutdownNow();
        synchronized (this) {
            for (Trabajo trabajo : trabajos) {
                almacen.liberar(trabajo.pedido.id());
                recursos.liberar(titular(trabajo));
            }
        }
    }

    /** Espera sin retener el monitor entre intentos para permitir pausas y cierre. */
    private void esperarRecursosDespacho(Trabajo trabajo) throws InterruptedException {
        while (true) {
            synchronized (this) {
                if (cerrada) {
                    throw new InterruptedException();
                }
                if (recursos.intentar(titular(trabajo), Map.of(TipoRecurso.ESCANER, 1, TipoRecurso.AREA_CARGA, 1))) {
                    break;
                }
            }
            Thread.sleep(INTERVALO_ESPERA_MILISEGUNDOS);
        }
    }

    private Comparator<Trabajo> crearOrdenAtencion() {
        return switch (politica) {
            case FIFO -> Comparator.comparingLong(trabajo -> trabajo.secuencia);
            case SJF -> Comparator.comparingInt(trabajo -> trabajo.pedido.getTotalArticulos());
            case SERVICIO -> Comparator.comparingInt(trabajo -> trabajo.pedido.nivelServicio().getPrioridad());
            case ENVEJECIMIENTO -> Comparator.comparingInt(trabajo -> Math.max(
                    1,
                    trabajo.pedido.nivelServicio().getPrioridad() - (int) ((minutos - trabajo.llegada) / 10)
            ));
        };
    }
}
