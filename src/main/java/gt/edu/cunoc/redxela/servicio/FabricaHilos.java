package gt.edu.cunoc.redxela.servicio;

import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

/** Crea los hilos de cada servicio con un nombre reconocible. */
public final class FabricaHilos implements ThreadFactory {

    private final String nombreServicio;

    public FabricaHilos(String nombreServicio) {
        this.nombreServicio = nombreServicio;
    }

    @Override
    public Thread newThread(Runnable tarea) {
        Thread hilo = new Thread(tarea, "redxela-" + nombreServicio);
        // Un hilo auxiliar no mantiene abierta la aplicación si termina la ventana.
        // Los servicios igualmente esperan su cierre para liberar sus recursos.
        hilo.setDaemon(true);
        return hilo;
    }

    /** Espera la liberación de recursos sin mantener bloqueados los monitores de los servicios. */
    public static void detener(ExecutorService servicio) {
        servicio.shutdownNow();
        // Limpia temporalmente la interrupción para poder esperar y la restaura al terminar.
        boolean interrumpido = Thread.interrupted();
        long limite = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
        try {
            while (!servicio.isTerminated()) {
                long restante = limite - System.nanoTime();
                if (restante <= 0) {
                    break;
                }
                try {
                    servicio.awaitTermination(restante, TimeUnit.NANOSECONDS);
                } catch (InterruptedException exception) {
                    // Se completa el cierre y luego se restaura la interrupción del llamador.
                    interrumpido = true;
                }
            }
        } finally {
            if (interrumpido) {
                Thread.currentThread().interrupt();
            }
        }
    }
}
