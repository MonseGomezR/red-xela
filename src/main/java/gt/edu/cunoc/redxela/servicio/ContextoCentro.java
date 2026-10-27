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
    private final MonitorConflictos conflictos = new MonitorConflictos(recursos);
    private final MotorSimulacion motor = new MotorSimulacion(almacen, recursos, conflictos);
    private final CanalRecepcion recepcion = new CanalRecepcion(almacen, recursos);
    private final ComparadorPoliticas comparador = new ComparadorPoliticas();
    private final ScheduledExecutorService generador = Executors.newSingleThreadScheduledExecutor(new FabricaHilos("llegadas"));
    private final List<Cliente> automaticos = new ArrayList<>();
    // volatile permite que el generador vea los cambios hechos desde la pantalla.
    private volatile boolean automatico = true;
    private volatile boolean estres;
    private volatile boolean cerrado;
    private double siguiente = 3;
    private int secuenciaManual = 1;
    private final Map<UUID, String> identificadores = new HashMap<>();

    /** Carga los datos iniciales y programa la revisión periódica de nuevas llegadas. */
    public ContextoCentro() {
        crearClientesSimulados();
        cargarExistenciasIniciales();
        generador.scheduleWithFixedDelay(this::generar, 100, 100, TimeUnit.MILLISECONDS);
    }

    /** Guarda el pedido y lo entrega al motor dentro de la misma operación. */
    public synchronized Pedido registrar(UUID cliente, List<DetallePedido> detalles) {
        if (cerrado) {
            throw new IllegalStateException("La sesión está cerrada.");
        }
        Pedido pedido = pedidos.registrar(cliente, detalles);
        motor.ingresar(pedido);
        return pedido;
    }

    /** Permite activar o detener las llegadas automáticas sin detener los pedidos existentes. */
    public void automatico(boolean valor) {
        automatico = valor;
    }

    /** Propone un código manual que todavía no se ha usado en la sesión. */
    public synchronized String siguienteIdManual() {
        while (identificadores.containsValue("MAN-" + secuenciaManual)) {
            secuenciaManual++;
        }
        return "MAN-" + secuenciaManual;
    }

    public synchronized String identificador(Pedido pedido) {
        return identificador(pedido.id());
    }

    /** Busca el código manual o muestra una versión corta del identificador interno. */
    public synchronized String identificador(UUID id) {
        return identificadores.getOrDefault(
                id,
                "P-" + id.toString().substring(0, 8).toUpperCase(Locale.ROOT)
        );
    }

    /** Valida el código visible y registra el pedido con el servicio de su cliente. */
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

    /** Cambia la frecuencia y el tamaño de los pedidos que crea el generador. */
    public void estres(boolean valor) {
        estres = valor;
    }

    public boolean estres() {
        return estres;
    }

    /** Al llegar el siguiente instante simulado, crea un pedido con cliente y producto al azar. */
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

    /** Cierra primero las llegadas y después los servicios que pueden tener trabajo pendiente. */
    @Override
    public void close() {
        synchronized (this) {
            cerrado = true;
        }
        FabricaHilos.detener(generador);
        comparador.close();
        recepcion.close();
        conflictos.close();
        motor.close();
    }

    /** Prepara una cuenta de simulación por cada nivel de servicio. */
    private void crearClientesSimulados() {
        for (NivelServicio nivel : NivelServicio.values()) {
            automaticos.add(clientes.registrar(
                    "Cliente de simulación N" + nivel.getPrioridad(),
                    "nivel" + nivel.getPrioridad() + "@redxela.local",
                    nivel
            ));
        }
    }

    /** Guarda 60 unidades de cada producto usando las mismas reglas del almacén. */
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
