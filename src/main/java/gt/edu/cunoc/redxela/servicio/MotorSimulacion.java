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
        private double inicioFase;
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
    private final MonitorConflictos conflictos;
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
        this(almacen, recursos, null);
    }

    public MotorSimulacion(Almacen almacen, GestorRecursos recursos, MonitorConflictos conflictos) {
        this.almacen = almacen;
        this.recursos = recursos;
        this.conflictos = conflictos;
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
        if (trabajo.estado == EstadoPedido.COMPLETADO || trabajo.estado == EstadoPedido.EN_PROCESO
                || trabajo.estado == EstadoPedido.EN_CONFLICTO) {
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
        registrarOcupacion(minutosTranscurridos);
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
        registrarOcupacion(1);
        asignar();
    }

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

    private void asignar() {
        int activos = (int) trabajos.stream().filter(trabajo -> trabajo.estado == EstadoPedido.EN_PROCESO
                || trabajo.estado == EstadoPedido.EN_CONFLICTO).count();
        List<Trabajo> candidatos = pendientes();
        // Una reserva pendiente no debe anular un cambio de política ni la llegada de mayor prioridad.
        candidatos.forEach(trabajo -> almacen.liberar(trabajo.pedido.id()));
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
            activos++;
            trabajadores.submit(() -> procesar(trabajo));
        }
    }

    private void procesar(Trabajo trabajo) {
        boolean terminado = false;
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
                    esperarRecursosDespacho(trabajo);
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
                    trabajo.estado = EstadoPedido.REGISTRADO;
                    trabajo.progreso = trabajo.preparado ? 0.85 : 0;
                    trabajo.despachando = false;
                    trabajo.fase = trabajo.preparado ? "Reencolado para despacho" : "Reencolado";
                }
            }
            if (conflictos != null) {
                conflictos.finalizarIntento(trabajo.pedido.id(), numeroConflicto, terminado);
            }
        }
    }

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

    private Map<TipoRecurso, Integer> recursosDespacho() {
        return Map.of(TipoRecurso.ESCANER, 1, TipoRecurso.AREA_CARGA, 1);
    }

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

    private Trabajo buscarPendiente(UUID id) {
        for (Trabajo trabajo : pendientes()) {
            if (trabajo.pedido.id().equals(id)) {
                return trabajo;
            }
        }
        throw new IllegalArgumentException("El pedido ya no está pendiente.");
    }

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

    private List<Trabajo> pendientes() {
        Comparator<Trabajo> orden = crearOrdenAtencion();
        return trabajos
                .stream()
                .filter(trabajo -> trabajo.estado != EstadoPedido.COMPLETADO && trabajo.estado != EstadoPedido.EN_PROCESO
                        && trabajo.estado != EstadoPedido.EN_CONFLICTO)
                .sorted(Comparator.<Trabajo>comparingInt(trabajo -> trabajo.preferente ? 0 : 1)
                .thenComparing(orden)
                .thenComparingLong(trabajo -> trabajo.secuencia))
                .toList();
    }

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

    public synchronized List<VistaPedido> pedidos() {
        return trabajos.stream().map(this::vista).toList();
    }

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

    public synchronized Estadisticas estadisticas() {
        List<Trabajo> completados = trabajos
                .stream()
                .filter(trabajo -> trabajo.estado == EstadoPedido.COMPLETADO)
                .toList();
        List<GestorRecursos.Estado> unidades = recursos.estados();
        double esperaMedia = completados.stream()
                .mapToDouble(trabajo -> trabajo.esperaAcumulada)
                .average()
                .orElse(0);
        double procesoMedio = completados.stream()
                .mapToDouble(trabajo -> trabajo.procesoAnterior + trabajo.fin - trabajo.inicio)
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
                if (recursos.intentar(titular(trabajo), recursosDespacho())) {
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
