package gt.edu.cunoc.redxela.servicio;

import gt.edu.cunoc.redxela.modelo.*;
import java.util.*;
import java.util.concurrent.*;

/**
 * Recepción envía mensajes; un consumidor de inventario actualiza el almacén.
 */
public final class CanalRecepcion implements AutoCloseable {

    /** Mensaje que recepción entrega a inventario con el producto y la cantidad recibida. */
    public record Entrada(UUID id, Producto producto, int cantidad, int muelle) {
    }

    public record Resultado(Entrada entrada, String estado) {
    }

    // Hasta 64 mensajes esperan su turno. La cola coordina el envío y la lectura entre hilos.
    private final BlockingQueue<Entrada> mensajes = new ArrayBlockingQueue<>(64);
    private final Map<UUID, Resultado> historial = new LinkedHashMap<>();
    private final ExecutorService consumidor = Executors.newSingleThreadExecutor(new FabricaHilos("inventario"));
    private final Almacen almacen;
    private final GestorRecursos recursos;
    private boolean cerrado;

    /** Inicia un trabajador de inventario que consume los mensajes en orden. */
    public CanalRecepcion(Almacen almacen, GestorRecursos recursos) {
        this.almacen = almacen;
        this.recursos = recursos;
        consumidor.submit(this::consumir);
    }

    /** Valida la entrada y la encola; si la cola está llena, informa el error sin bloquear la pantalla. */
    public synchronized UUID enviar(Producto producto, int cantidad, int muelle) {
        if (cerrado) {
            throw new IllegalStateException("La recepción está cerrada.");
        }
        Objects.requireNonNull(producto);
        if (cantidad <= 0 || cantidad > almacen.resumen().capacidad()) {
            throw new IllegalArgumentException("La cantidad debe estar entre 1 y la capacidad del almacén.");
        }
        if (muelle < 1 || muelle > 2) {
            throw new IllegalArgumentException("Selecciona un muelle de carga válido.");
        }
        Entrada entrada = new Entrada(UUID.randomUUID(), producto, cantidad, muelle);
        if (!mensajes.offer(entrada)) {
            throw new IllegalArgumentException("Recepción está llena. Intenta de nuevo cuando avance inventario.");
        }
        historial.put(entrada.id, new Resultado(entrada, "En cola"));
        limitarHistorial();
        return entrada.id;
    }

    /** Toma un mensaje, obtiene muelle y escáner, y registra el lote en el almacén. */
    private void consumir() {
        while (!Thread.currentThread().isInterrupted()) {
            Entrada entrada = null;
            String titular = null;
            try {
                // take espera cuando la cola está vacía, sin consultar una y otra vez.
                entrada = mensajes.take();
                titular = "Recepción " + entrada.id.toString().substring(0, 6);
                // Se respeta el muelle elegido y se comparte el escáner con el despacho.
                while (!recursos.intentarRecepcion(titular, entrada.muelle)) {
                    Thread.sleep(50);
                }
                estado(entrada, "Procesando");
                Thread.sleep(180);
                UUID lote = almacen.recibir(entrada.producto, entrada.cantidad);
                estado(entrada, "Recibido, lote " + lote.toString().substring(0, 8));
            } catch (InterruptedException exception) {
                if (entrada != null) {
                    estado(entrada, "Cancelado al cerrar");
                }
                Thread.currentThread().interrupt();
            } catch (RuntimeException exception) {
                if (entrada != null) {
                    estado(entrada, "Rechazado: " + exception.getMessage());
                }
            } finally {
                if (titular != null) {
                    recursos.liberar(titular);
                }
            }
        }
    }

    /** Actualiza el resultado que puede consultar la pantalla de recepción. */
    private synchronized void estado(Entrada entrada, String valor) {
        historial.put(entrada.id, new Resultado(entrada, valor));
    }

    /** Devuelve una copia para que la pantalla no cambie el historial mientras se procesa. */
    public synchronized List<Resultado> historial() {
        return List.copyOf(historial.values());
    }

    /** Cuenta los mensajes que aún no ha tomado el trabajador de inventario. */
    public int pendientes() {
        return mensajes.size();
    }

    /** Cancela mensajes pendientes y espera que el trabajador libere muelle y escáner. */
    @Override
    public void close() {
        synchronized (this) {
            cerrado = true;
            Entrada entrada;
            while ((entrada = mensajes.poll()) != null) {
                historial.put(entrada.id, new Resultado(entrada, "Cancelado al cerrar"));
            }
        }
        FabricaHilos.detener(consumidor);
    }

    /** Retira el resultado terminado más antiguo al superar 200 registros. */
    private void limitarHistorial() {
        // El historial es visual; los mensajes pendientes se conservan en la cola.
        if (historial.size() > 200) {
            UUID eliminar = historial.entrySet()
                    .stream()
                    .filter(e -> !e.getValue().estado.equals("En cola") && !e.getValue().estado.equals("Procesando"))
                    .map(Map.Entry::getKey)
                    .findFirst().orElse(null);
            if (eliminar != null) {
                historial.remove(eliminar);
            }
        }
    }
}
