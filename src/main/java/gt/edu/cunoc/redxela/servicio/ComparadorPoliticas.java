package gt.edu.cunoc.redxela.servicio;

import gt.edu.cunoc.redxela.modelo.DetallePedido;
import gt.edu.cunoc.redxela.modelo.EstadoPedido;
import gt.edu.cunoc.redxela.modelo.Pedido;
import gt.edu.cunoc.redxela.modelo.Producto;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Ejecuta las políticas con copias de una misma carga de trabajo e inventario. */
public final class ComparadorPoliticas implements AutoCloseable {

    public static final double MINUTOS_POR_POLITICA = 30;
    private static final double VELOCIDAD_COMPARACION = 8;
    private final ExecutorService trabajador = Executors.newSingleThreadExecutor(new FabricaHilos("comparacion"));
    private final List<ResultadoComparacion> resultados = new ArrayList<>();
    private boolean ejecutando;
    private boolean cerrado;
    private int ejecucion;
    private String estado = "Pausa la simulación para comparar sus pedidos pendientes.";

    public ComparadorPoliticas() {
    }

    /** Copia los pendientes y el inventario mientras la sesión principal está pausada. */
    public synchronized void iniciar(MotorSimulacion principal, Almacen almacen) {
        if (cerrado) {
            throw new IllegalStateException("La comparación está cerrada.");
        }
        if (ejecutando) {
            throw new IllegalArgumentException("Espera a que termine la comparación actual.");
        }
        List<Pedido> carga = new ArrayList<>();
        Map<Producto, Integer> inventario;
        // El reloj y los trabajadores no pueden cambiar estados durante la copia.
        synchronized (principal) {
            if (principal.activa()) {
                throw new IllegalArgumentException("Pausa la simulación antes de comparar políticas.");
            }
            inventario = almacen.existencias();
            for (MotorSimulacion.VistaPedido vista : principal.pedidos()) {
                if (vista.estado() == EstadoPedido.EN_PROCESO || vista.estado() == EstadoPedido.EN_CONFLICTO) {
                    descontarPedidoActivo(inventario, vista.pedido());
                } else if (vista.estado() != EstadoPedido.COMPLETADO) {
                    carga.add(vista.pedido());
                }
            }
        }
        if (carga.isEmpty()) {
            throw new IllegalArgumentException("No hay pedidos pendientes para comparar.");
        }
        ejecucion++;
        int numero = ejecucion;
        ejecutando = true;
        estado = "Comparación " + numero + ": " + carga.size() + " pedidos capturados.";
        trabajador.submit(() -> comparar(numero, carga, inventario));
    }

    /** Aparta de la copia las unidades que pertenecen a pedidos activos de la sesión principal. */
    private void descontarPedidoActivo(Map<Producto, Integer> inventario, Pedido pedido) {
        // Estas unidades pertenecen a pedidos que continúan en la sesión principal.
        for (DetallePedido detalle : pedido.detalles()) {
            Producto producto = detalle.producto();
            int disponible = inventario.getOrDefault(producto, 0) - detalle.cantidad();
            inventario.put(producto, Math.max(0, disponible));
        }
    }

    /** Ejecuta las cuatro políticas una por una y guarda sus resultados por separado. */
    private void comparar(int numero, List<Pedido> carga, Map<Producto, Integer> inventario) {
        try {
            for (PoliticaAtencion politica : PoliticaAtencion.values()) {
                if (Thread.currentThread().isInterrupted()) {
                    throw new InterruptedException();
                }
                synchronized (this) {
                    estado = "Comparación " + numero + ": ejecutando " + politica + ".";
                }
                ResultadoComparacion resultado = ejecutarPolitica(numero, politica, carga, inventario);
                synchronized (this) {
                    resultados.add(resultado);
                }
            }
            synchronized (this) {
                estado = "Comparación " + numero + " terminada. Cada política recibió " + carga.size() + " pedidos y el mismo inventario.";
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            synchronized (this) {
                estado = "Comparación interrumpida al cerrar.";
            }
        } catch (RuntimeException exception) {
            synchronized (this) {
                estado = "No se pudo completar la comparación: " + exception.getMessage();
            }
        } finally {
            synchronized (this) {
                ejecutando = false;
            }
        }
    }

    /** Crea un motor y recursos propios; ejecuta 30 minutos simulados y luego los cierra. */
    private ResultadoComparacion ejecutarPolitica(int numero, PoliticaAtencion politica,
            List<Pedido> carga, Map<Producto, Integer> inventario) throws InterruptedException {
        Almacen copia = new Almacen();
        for (Map.Entry<Producto, Integer> entrada : inventario.entrySet()) {
            if (entrada.getValue() > 0) {
                copia.recibir(entrada.getKey(), entrada.getValue());
            }
        }
        GestorRecursos recursos = new GestorRecursos();
        // try cierra el motor aunque la comparación falle o sea interrumpida.
        try (MotorSimulacion motor = new MotorSimulacion(copia, recursos)) {
            motor.politica(politica);
            motor.velocidad(VELOCIDAD_COMPARACION);
            for (Pedido pedido : carga) {
                motor.ingresar(pedido);
            }
            motor.activar(true);
            while (motor.estadisticas().minutos() < MINUTOS_POR_POLITICA) {
                Thread.sleep(20);
            }
            motor.activar(false);
            // Permite que los trabajadores terminen de observar el último avance del reloj.
            Thread.sleep(80);
            return new ResultadoComparacion(numero, politica, carga.size(), motor.estadisticas());
        }
    }

    /** Entrega una copia de los resultados acumulados durante la sesión. */
    public synchronized List<ResultadoComparacion> getResultados() {
        return new ArrayList<>(resultados);
    }

    public synchronized boolean isEjecutando() {
        return ejecutando;
    }

    public synchronized String getEstado() {
        return estado;
    }

    /** Interrumpe la comparación y espera el cierre del motor que estuviera usando. */
    @Override
    public void close() {
        synchronized (this) {
            cerrado = true;
            if (ejecutando) {
                estado = "Comparación interrumpida al cerrar.";
            }
        }
        FabricaHilos.detener(trabajador);
        synchronized (this) {
            ejecutando = false;
        }
    }
}
