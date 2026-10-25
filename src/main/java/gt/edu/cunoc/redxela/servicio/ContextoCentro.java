package gt.edu.cunoc.redxela.servicio;

import gt.edu.cunoc.redxela.catalogo.CatalogoProductos;
import gt.edu.cunoc.redxela.modelo.*;
import java.util.*;
import java.util.concurrent.*;

/**
 * Reúne los servicios del centro y administra el ciclo de vida de sus trabajadores.
 */
public final class ContextoCentro implements AutoCloseable {

    public static final int EXISTENCIAS_INICIALES_POR_PRODUCTO = 60;
    private final RegistroClientes clientes = new RegistroClientes();
    private final RegistroPedidos pedidos = new RegistroPedidos(clientes);
    private final Almacen almacen = new Almacen();
    private final GestorRecursos recursos = new GestorRecursos();
    private final MotorSimulacion motor = new MotorSimulacion(almacen, recursos);
    private final CanalRecepcion recepcion = new CanalRecepcion(almacen, recursos);
    private final MonitorConflictos conflictos = new MonitorConflictos(recursos);
    private final ComparadorPoliticas comparador = new ComparadorPoliticas();
    private final ScheduledExecutorService generador = Executors.newSingleThreadScheduledExecutor(new FabricaHilos("llegadas"));
    private final List<Cliente> automaticos = new ArrayList<>();
    private volatile boolean automatico = true;
    private volatile boolean estres;
    private volatile boolean cerrado;
    private double siguiente = 3;
    private int secuenciaManual = 1;
    private final Map<UUID, String> identificadores = new HashMap<>();

    public ContextoCentro() {
        crearClientesSimulados();
        cargarExistenciasIniciales();
        generador.scheduleWithFixedDelay(this::generar, 100, 100, TimeUnit.MILLISECONDS);
    }

    public synchronized Pedido registrar(UUID cliente, List<DetallePedido> detalles) {
        if (cerrado) {
            throw new IllegalStateException("La sesión está cerrada.");
        }
        Pedido pedido = pedidos.registrar(cliente, detalles);
        motor.ingresar(pedido);
        return pedido;
    }

    public void automatico(boolean valor) {
        automatico = valor;
    }

    public synchronized String siguienteIdManual() {
        while (identificadores.containsValue("MAN-" + secuenciaManual)) {
            secuenciaManual++;
        }
        return "MAN-" + secuenciaManual;
    }

    public synchronized String identificador(Pedido pedido) {
        return identificadores.getOrDefault(
                pedido.id(),
                "P-" + pedido.id().toString().substring(0, 8).toUpperCase(Locale.ROOT)
        );
    }

    public synchronized Pedido registrarManual(String id, UUID clienteId, List<DetallePedido> detalles) {
        if (cerrado) {
            throw new IllegalStateException("La sesión está cerrada.");
        }
        if (id == null) {
            throw new IllegalArgumentException("Ingresa el ID del pedido.");
        }
        String codigo = Objects.requireNonNull(id).strip().replaceFirst("^#", "").toUpperCase(Locale.ROOT);
        if (!codigo.matches("[A-Z0-9][A-Z0-9_-]{1,31}")) {
            throw new IllegalArgumentException("Ingresa un ID de 2 a 32 letras, números o guiones.");
        }
        if (identificadores.containsValue(codigo)) {
            throw new IllegalArgumentException("Ese ID de pedido ya está registrado.");
        }
        Pedido pedido = pedidos.registrar(clienteId, detalles);
        motor.ingresar(pedido);
        identificadores.put(pedido.id(), codigo);
        secuenciaManual++;
        return pedido;
    }

    public boolean automatico() {
        return automatico;
    }

    public void estres(boolean valor) {
        estres = valor;
    }

    public boolean estres() {
        return estres;
    }

    private void generar() {
        if (cerrado || !motor.activa()) {
            return;
        }
        double ahora = motor.estadisticas().minutos();
        if (!automatico) {
            siguiente = ahora;
            return;
        }
        if (ahora < siguiente) {
            return;
        }
        siguiente = ahora + (estres ? 0.25 : 3);
        ThreadLocalRandom azar = ThreadLocalRandom.current();
        Cliente cliente = automaticos.get(azar.nextInt(automaticos.size()));
        Producto producto = CatalogoProductos.getProductos().get(azar.nextInt(CatalogoProductos.getProductos().size()));
        synchronized (this) {
            if (!cerrado) {
                registrar(cliente.id(), List.of(new DetallePedido(producto, azar.nextInt(1, estres ? 21 : 9))));
            }
        }
    }

    @Override
    public synchronized void close() {
        cerrado = true;
        generador.shutdownNow();
        comparador.close();
        recepcion.close();
        conflictos.close();
        motor.close();
    }

    private void crearClientesSimulados() {
        for (NivelServicio nivel : NivelServicio.values()) {
            automaticos.add(clientes.registrar(
                    "Cliente de simulación N" + nivel.getPrioridad(),
                    "nivel" + nivel.getPrioridad() + "@redxela.local",
                    nivel
            ));
        }
    }

    private void cargarExistenciasIniciales() {
        for (Producto producto : CatalogoProductos.getProductos()) {
            almacen.recibir(producto, EXISTENCIAS_INICIALES_POR_PRODUCTO);
        }
    }

    public RegistroClientes getClientes() {
        return clientes;
    }

    public RegistroPedidos getPedidos() {
        return pedidos;
    }

    public Almacen getAlmacen() {
        return almacen;
    }

    public GestorRecursos getRecursos() {
        return recursos;
    }

    public MotorSimulacion getMotor() {
        return motor;
    }

    public CanalRecepcion getRecepcion() {
        return recepcion;
    }

    public MonitorConflictos getConflictos() {
        return conflictos;
    }

    public ComparadorPoliticas getComparador() {
        return comparador;
    }
}
