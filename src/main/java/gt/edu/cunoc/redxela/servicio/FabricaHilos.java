package gt.edu.cunoc.redxela.servicio;

import java.util.concurrent.ThreadFactory;

/** Crea los hilos de cada servicio con un nombre reconocible. */
public final class FabricaHilos implements ThreadFactory {

    private final String nombreServicio;

    public FabricaHilos(String nombreServicio) {
        this.nombreServicio = nombreServicio;
    }

    @Override
    public Thread newThread(Runnable tarea) {
        Thread hilo = new Thread(tarea, "redxela-" + nombreServicio);
        hilo.setDaemon(true);
        return hilo;
    }
}
