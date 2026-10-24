package gt.edu.cunoc.redxela.servicio;

import gt.edu.cunoc.redxela.catalogo.CatalogoProductos;
import gt.edu.cunoc.redxela.modelo.*;
import java.util.*;
import java.util.concurrent.*;

/** Estado compartido por las tres pantallas y ciclo de vida de los trabajadores. */
public final class ContextoCentro implements AutoCloseable {
    public static final int EXISTENCIAS_INICIALES_POR_PRODUCTO = 60;
    public final RegistroClientes clientes = new RegistroClientes();
    public final RegistroPedidos pedidos = new RegistroPedidos(clientes);
    public final Almacen almacen = new Almacen();
    public final GestorRecursos recursos = new GestorRecursos();
    public final MotorSimulacion motor = new MotorSimulacion(almacen, recursos);
    public final CanalRecepcion recepcion = new CanalRecepcion(almacen, recursos);
    public final MonitorConflictos conflictos = new MonitorConflictos(recursos);
    private final ScheduledExecutorService generador = Executors.newSingleThreadScheduledExecutor(MotorSimulacion.hilos("llegadas"));
    private final List<Cliente> automaticos = new ArrayList<>();
    private volatile boolean automatico = true, estres, cerrado;
    private double siguiente = 3;
    private int secuenciaManual = 1;
    private final Map<UUID, String> identificadores = new HashMap<>();

    public ContextoCentro() {
        for (NivelServicio nivel : NivelServicio.values()) automaticos.add(clientes.registrar(
                "Cliente de simulación N" + nivel.getPrioridad(), "nivel" + nivel.getPrioridad() + "@redxela.local", nivel));
        CatalogoProductos.getProductos().forEach(p -> almacen.recibir(p, EXISTENCIAS_INICIALES_POR_PRODUCTO));
        generador.scheduleWithFixedDelay(this::generar, 100, 100, TimeUnit.MILLISECONDS);
    }
    public synchronized Pedido registrar(UUID cliente, List<DetallePedido> detalles) {
        if (cerrado) throw new IllegalStateException("La sesión está cerrada.");
        Pedido pedido = pedidos.registrar(cliente, detalles);
        motor.ingresar(pedido);
        return pedido;
    }
    public void automatico(boolean valor) { automatico = valor; }
    public synchronized String siguienteIdManual() {
        while (identificadores.containsValue("MAN-" + secuenciaManual)) secuenciaManual++;
        return "MAN-" + secuenciaManual;
    }
    public synchronized String identificador(Pedido pedido) {
        return identificadores.getOrDefault(pedido.id(), "P-" + pedido.id().toString().substring(0, 8).toUpperCase(Locale.ROOT));
    }
    public synchronized Pedido registrarManual(String id, UUID clienteId, List<DetallePedido> detalles) {
        if(cerrado) throw new IllegalStateException("La sesión está cerrada.");
        if(id == null) throw new IllegalArgumentException("Ingresa el ID del pedido.");
        String codigo = Objects.requireNonNull(id).strip().replaceFirst("^#", "").toUpperCase(Locale.ROOT);
        if(!codigo.matches("[A-Z0-9][A-Z0-9_-]{1,31}")) throw new IllegalArgumentException("Ingresa un ID de 2 a 32 letras, números o guiones.");
        if(identificadores.containsValue(codigo)) throw new IllegalArgumentException("Ese ID de pedido ya está registrado.");
        Pedido pedido = pedidos.registrar(clienteId, detalles);
        motor.ingresar(pedido); identificadores.put(pedido.id(), codigo); secuenciaManual++;
        return pedido;
    }
    public boolean automatico() { return automatico; }
    public void estres(boolean valor) { estres = valor; }
    public boolean estres() { return estres; }
    private void generar() {
        if (cerrado || !motor.activa()) return;
        double ahora = motor.estadisticas().minutos();
        if (!automatico) { siguiente = ahora; return; }
        if (ahora < siguiente) return;
        siguiente = ahora + (estres ? 0.25 : 3);
        var azar = ThreadLocalRandom.current();
        Cliente cliente = automaticos.get(azar.nextInt(automaticos.size()));
        Producto producto = CatalogoProductos.getProductos().get(azar.nextInt(CatalogoProductos.getProductos().size()));
        synchronized (this) {
            if (!cerrado) registrar(cliente.id(), List.of(new DetallePedido(producto, azar.nextInt(1, estres ? 21 : 9))));
        }
    }
    @Override public synchronized void close() {
        cerrado = true;
        generador.shutdownNow(); recepcion.close(); conflictos.close(); motor.close();
    }
}
