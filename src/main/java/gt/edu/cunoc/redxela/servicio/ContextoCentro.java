package gt.edu.cunoc.redxela.servicio;

import gt.edu.cunoc.redxela.catalogo.CatalogoProductos;
import gt.edu.cunoc.redxela.modelo.*;
import java.util.*;
import java.util.concurrent.*;

/** Estado compartido por las tres pantallas y ciclo de vida de los trabajadores. */
public final class ContextoCentro implements AutoCloseable {
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

    public ContextoCentro() {
        for (NivelServicio nivel : NivelServicio.values()) automaticos.add(clientes.registrar(
                "Cliente de simulación N" + nivel.getPrioridad(), "nivel" + nivel.getPrioridad() + "@redxela.local", nivel));
        // Stock inicial real: tres ubicaciones por producto. Se consume durante la simulación.
        CatalogoProductos.getProductos().forEach(p -> almacen.recibir(p, 60));
        generador.scheduleWithFixedDelay(this::generar, 100, 100, TimeUnit.MILLISECONDS);
    }
    public synchronized Pedido registrar(UUID cliente, List<DetallePedido> detalles) {
        if (cerrado) throw new IllegalStateException("La sesión está cerrada.");
        Pedido pedido = pedidos.registrar(cliente, detalles);
        motor.ingresar(pedido);
        return pedido;
    }
    public void automatico(boolean valor) { automatico = valor; }
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
