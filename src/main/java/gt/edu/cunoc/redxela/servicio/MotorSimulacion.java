package gt.edu.cunoc.redxela.servicio;

import gt.edu.cunoc.redxela.modelo.*;
import java.util.*;
import java.util.concurrent.*;

/** Planificación y trabajadores reales. Un segundo real equivale a un minuto simulado a 1x. */
public final class MotorSimulacion implements AutoCloseable {
    private static final class Trabajo {
        final Pedido pedido;
        final double llegada;
        final long secuencia;
        EstadoPedido estado = EstadoPedido.REGISTRADO;
        double inicio, fin, progreso;
        String fase = "Pendiente", error = "";
        boolean preferente;
        Trabajo(Pedido pedido, double llegada, long secuencia) {
            this.pedido = pedido; this.llegada = llegada; this.secuencia = secuencia;
        }
    }
    public record VistaPedido(Pedido pedido, EstadoPedido estado, double progreso, double restante,
                               double espera, String fase, String error, boolean preferente) { }
    public record Estadisticas(int completados, double esperaMedia, double procesoMedio, int recursosEnUso,
                                int recursosTotales, double ocupacionMedia, double minutos) { }
    private final Almacen almacen;
    private final GestorRecursos recursos;
    private final List<Trabajo> trabajos = new ArrayList<>();
    private final ScheduledExecutorService reloj = Executors.newSingleThreadScheduledExecutor(hilos("reloj"));
    private final ExecutorService trabajadores = Executors.newFixedThreadPool(8, hilos("pedido"));
    private boolean activa, cerrada;
    private double minutos, velocidad = 1, tiempoOcupado, tiempoMedido;
    private long anterior = System.nanoTime(), secuencia;
    private PoliticaAtencion politica = PoliticaAtencion.SERVICIO;

    public MotorSimulacion(Almacen almacen, GestorRecursos recursos) {
        this.almacen = almacen; this.recursos = recursos;
        reloj.scheduleWithFixedDelay(this::tick, 0, 60, TimeUnit.MILLISECONDS);
    }

    static ThreadFactory hilos(String nombre) {
        return tarea -> { Thread hilo = new Thread(tarea, "redxela-" + nombre); hilo.setDaemon(true); return hilo; };
    }

    public synchronized void ingresar(Pedido pedido) {
        if (cerrada) throw new IllegalStateException("La simulación está cerrada.");
        trabajos.add(new Trabajo(pedido, minutos, ++secuencia));
    }
    public synchronized void activar(boolean valor) { activa = valor; anterior = System.nanoTime(); }
    public synchronized boolean activa() { return activa; }
    public synchronized void velocidad(double valor) {
        if (!Double.isFinite(valor) || valor < 0.5 || valor > 8) throw new IllegalArgumentException("Velocidad no válida.");
        velocidad = valor;
    }
    public synchronized void politica(PoliticaAtencion valor) { politica = Objects.requireNonNull(valor); }
    public synchronized PoliticaAtencion politica() { return politica; }
    public synchronized void priorizar(UUID id) {
        Trabajo t = trabajos.stream().filter(p -> p.pedido.id().equals(id)).findFirst().orElseThrow();
        if (t.estado == EstadoPedido.COMPLETADO || t.estado == EstadoPedido.EN_PROCESO)
            throw new IllegalArgumentException("Selecciona un pedido que siga en espera.");
        t.preferente = true;
    }

    private synchronized void tick() {
        long ahora = System.nanoTime();
        double delta = (ahora - anterior) / 1_000_000_000.0 * velocidad;
        anterior = ahora;
        if (!activa || cerrada) return;
        minutos += delta;
        var estados = recursos.estados();
        tiempoOcupado += delta * estados.stream().filter(e -> !e.disponible()).count();
        tiempoMedido += delta * estados.size();
        asignar();
    }

    public synchronized void paso() {
        if (activa) throw new IllegalArgumentException("Pausa la simulación antes de avanzar un minuto.");
        if (cerrada) return;
        minutos += 1;
        asignar();
    }

    private void asignar() {
        int activos = (int) trabajos.stream().filter(t -> t.estado == EstadoPedido.EN_PROCESO).count();
        List<Trabajo> candidatos = pendientes();
        // Una reserva pendiente no debe anular un cambio de política ni la llegada de mayor prioridad.
        candidatos.forEach(t -> almacen.liberar(t.pedido.id()));
        for (Trabajo t : candidatos) {
            if (activos >= 8) break;
            if (!almacen.reservar(t.pedido.id(), t.pedido.detalles())) {
                t.estado = EstadoPedido.ESPERANDO_INVENTARIO; t.fase = "Sin existencias"; continue;
            }
            if (!recursos.intentar(titular(t), necesidades(t.pedido))) {
                t.estado = EstadoPedido.ESPERANDO_RECURSOS; t.fase = "Recursos ocupados"; continue;
            }
            t.estado = EstadoPedido.EN_PROCESO; t.inicio = minutos; t.fase = "Preparación";
            activos++;
            trabajadores.submit(() -> procesar(t));
        }
    }

    private void procesar(Trabajo t) {
        boolean terminado = false;
        try {
            avanzar(t, Math.max(1, t.pedido.getTotalArticulos() * 0.25), 0, 0.85);
            recursos.liberar(titular(t));
            synchronized (this) { t.fase = "Esperando despacho"; }
            while (true) {
                synchronized (this) {
                    if (cerrada) throw new InterruptedException();
                    if (recursos.intentar(titular(t), Map.of(TipoRecurso.ESCANER, 1, TipoRecurso.AREA_CARGA, 1))) break;
                }
                Thread.sleep(40);
            }
            synchronized (this) { t.fase = "Despacho"; }
            avanzar(t, 0.6, 0.85, 1);
            synchronized (this) {
                almacen.despachar(t.pedido.id());
                t.progreso = 1; t.fin = minutos; t.estado = EstadoPedido.COMPLETADO; t.fase = "Entregado";
                terminado = true;
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        } catch (RuntimeException exception) {
            synchronized (this) { t.error = exception.getMessage(); }
        } finally {
            synchronized (this) {
                recursos.liberar(titular(t));
                if (!terminado) {
                    almacen.liberar(t.pedido.id());
                    t.estado = EstadoPedido.REGISTRADO; t.progreso = 0; t.fase = "Reencolado";
                }
            }
        }
    }

    private void avanzar(Trabajo t, double duracion, double desde, double hasta) throws InterruptedException {
        double inicio;
        synchronized (this) { inicio = minutos; }
        while (true) {
            synchronized (this) {
                if (cerrada) throw new InterruptedException();
                double fraccion = Math.min(1, (minutos - inicio) / duracion);
                t.progreso = desde + (hasta - desde) * fraccion;
                if (fraccion >= 1) return;
            }
            Thread.sleep(40);
        }
    }

    private List<Trabajo> pendientes() {
        Comparator<Trabajo> orden = switch (politica) {
            case FIFO -> Comparator.comparingLong(t -> t.secuencia);
            case SJF -> Comparator.comparingInt(t -> t.pedido.getTotalArticulos());
            case SERVICIO -> Comparator.comparingInt(t -> t.pedido.nivelServicio().getPrioridad());
            case ENVEJECIMIENTO -> Comparator.comparingInt(t -> Math.max(1,
                    t.pedido.nivelServicio().getPrioridad() - (int) ((minutos - t.llegada) / 10)));
        };
        return trabajos.stream().filter(t -> t.estado != EstadoPedido.COMPLETADO && t.estado != EstadoPedido.EN_PROCESO)
                .sorted(Comparator.<Trabajo>comparingInt(t -> t.preferente ? 0 : 1).thenComparing(orden)
                        .thenComparingLong(t -> t.secuencia)).toList();
    }
    public synchronized List<VistaPedido> espera() { return pendientes().stream().map(this::vista).toList(); }
    public synchronized List<VistaPedido> pedidos() { return trabajos.stream().map(this::vista).toList(); }
    private VistaPedido vista(Trabajo t) {
        double trabajo = Math.max(1, t.pedido.getTotalArticulos() * 0.25) + 0.6;
        double espera = (t.estado == EstadoPedido.EN_PROCESO || t.estado == EstadoPedido.COMPLETADO ? t.inicio : minutos) - t.llegada;
        return new VistaPedido(t.pedido, t.estado, t.progreso, trabajo * (1 - t.progreso),
                Math.max(0, espera), t.fase, t.error, t.preferente);
    }
    public synchronized Estadisticas estadisticas() {
        var completados = trabajos.stream().filter(t -> t.estado == EstadoPedido.COMPLETADO).toList();
        var unidades = recursos.estados();
        return new Estadisticas(completados.size(), completados.stream().mapToDouble(t -> t.inicio - t.llegada).average().orElse(0),
                completados.stream().mapToDouble(t -> t.fin - t.inicio).average().orElse(0),
                (int) unidades.stream().filter(e -> !e.disponible()).count(), unidades.size(),
                tiempoMedido == 0 ? 0 : tiempoOcupado / tiempoMedido, minutos);
    }
    private String titular(Trabajo t) { return "P-" + t.pedido.id().toString().substring(0, 8); }
    private Map<TipoRecurso, Integer> necesidades(Pedido pedido) {
        Map<TipoRecurso, Integer> resultado = new EnumMap<>(TipoRecurso.class);
        // Los tipos se preparan usando la mayor cantidad necesaria de cada recurso, sin multiplicar por unidades.
        pedido.detalles().forEach(d -> d.producto().tipoMercancia().getRecursosRequeridos()
                .forEach((tipo, cantidad) -> resultado.merge(tipo, cantidad, Math::max)));
        return resultado;
    }
    @Override public void close() {
        synchronized (this) { cerrada = true; activa = false; }
        reloj.shutdownNow(); trabajadores.shutdownNow();
        synchronized (this) {
            for (Trabajo t : trabajos) {
                almacen.liberar(t.pedido.id()); recursos.liberar(titular(t));
            }
        }
    }
}
