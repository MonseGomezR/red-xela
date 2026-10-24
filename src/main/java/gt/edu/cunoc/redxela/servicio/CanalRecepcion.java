package gt.edu.cunoc.redxela.servicio;

import gt.edu.cunoc.redxela.modelo.*;
import java.util.*;
import java.util.concurrent.*;

/** Recepción envía mensajes; un consumidor de inventario actualiza el almacén. */
public final class CanalRecepcion implements AutoCloseable {
    public record Entrada(UUID id, Producto producto, int cantidad, int muelle) { }
    public record Resultado(Entrada entrada, String estado) { }
    private final BlockingQueue<Entrada> mensajes = new ArrayBlockingQueue<>(64);
    private final Map<UUID, Resultado> historial = new LinkedHashMap<>();
    private final ExecutorService consumidor = Executors.newSingleThreadExecutor(MotorSimulacion.hilos("inventario"));
    private final Almacen almacen;
    private final GestorRecursos recursos;
    private boolean cerrado;

    public CanalRecepcion(Almacen almacen, GestorRecursos recursos) {
        this.almacen = almacen; this.recursos = recursos;
        consumidor.submit(this::consumir);
    }
    public synchronized UUID enviar(Producto producto, int cantidad, int muelle) {
        if (cerrado) throw new IllegalStateException("La recepción está cerrada.");
        Objects.requireNonNull(producto);
        if (cantidad <= 0 || cantidad > almacen.resumen().capacidad())
            throw new IllegalArgumentException("La cantidad debe estar entre 1 y la capacidad del almacén.");
        if (muelle < 1 || muelle > 2) throw new IllegalArgumentException("Selecciona un muelle de carga válido.");
        Entrada entrada = new Entrada(UUID.randomUUID(), producto, cantidad, muelle);
        if (!mensajes.offer(entrada)) throw new IllegalArgumentException("Recepción está llena. Intenta de nuevo cuando avance inventario.");
        historial.put(entrada.id, new Resultado(entrada, "En cola"));
        // El historial es visual; los mensajes pendientes se conservan en la cola.
        if (historial.size() > 200) {
            UUID eliminar = historial.entrySet().stream().filter(e -> !e.getValue().estado.equals("En cola")
                    && !e.getValue().estado.equals("Procesando")).map(Map.Entry::getKey).findFirst().orElse(null);
            if (eliminar != null) historial.remove(eliminar);
        }
        return entrada.id;
    }
    private void consumir() {
        while (!Thread.currentThread().isInterrupted()) {
            Entrada entrada = null;
            String titular = null;
            try {
                entrada = mensajes.take();
                titular = "Recepción " + entrada.id.toString().substring(0, 6);
                // Se respeta el muelle elegido y se comparte el escáner con el despacho.
                while (!recursos.intentarRecepcion(titular, entrada.muelle)) Thread.sleep(50);
                estado(entrada, "Procesando");
                Thread.sleep(180);
                UUID lote = almacen.recibir(entrada.producto, entrada.cantidad);
                estado(entrada, "Recibido, lote " + lote.toString().substring(0, 8));
            } catch (InterruptedException exception) {
                if (entrada != null) estado(entrada, "Cancelado al cerrar");
                Thread.currentThread().interrupt();
            } catch (RuntimeException exception) {
                if (entrada != null) estado(entrada, "Rechazado: " + exception.getMessage());
            } finally {
                if (titular != null) recursos.liberar(titular);
            }
        }
    }
    private synchronized void estado(Entrada entrada, String valor) {
        historial.put(entrada.id, new Resultado(entrada, valor));
    }
    public synchronized List<Resultado> historial() { return List.copyOf(historial.values()); }
    public int pendientes() { return mensajes.size(); }
    @Override public synchronized void close() {
        cerrado = true; consumidor.shutdownNow();
        Entrada entrada;
        while ((entrada = mensajes.poll()) != null) historial.put(entrada.id, new Resultado(entrada, "Cancelado al cerrar"));
    }
}
