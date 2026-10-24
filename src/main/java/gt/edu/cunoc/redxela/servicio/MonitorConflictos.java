package gt.edu.cunoc.redxela.servicio;

import gt.edu.cunoc.redxela.modelo.TipoRecurso;
import java.util.Map;
import java.util.concurrent.*;

/** Demostración real: dos operaciones retienen recursos distintos y esperan el recurso de la otra. */
public final class MonitorConflictos implements AutoCloseable {
    public record Vista(boolean activo, boolean recuperando, boolean listo, String mensaje) { }
    private final GestorRecursos recursos;
    private final ExecutorService hilos = Executors.newFixedThreadPool(3, MotorSimulacion.hilos("conflicto"));
    private Future<?> operacionA, operacionB;
    private CountDownLatch listos;
    private boolean activo, recuperando;
    private String mensaje = "No hay esperas circulares detectadas.";

    public MonitorConflictos(GestorRecursos recursos) { this.recursos = recursos; }

    public synchronized void provocar() {
        if (activo || recuperando) throw new IllegalArgumentException("Finaliza el escenario actual antes de iniciar otro.");
        if (!recursos.prepararConflicto("DL-A", "DL-B"))
            throw new IllegalArgumentException("Montacargas 1 o el escáner están ocupados. Reintenta cuando se liberen.");
        activo = true;
        listos = new CountDownLatch(2);
        mensaje = "A retiene Montacargas 1 y espera Escáner. B retiene Escáner y espera Montacargas 1.";
        operacionA = hilos.submit(() -> esperar("DL-A", TipoRecurso.ESCANER));
        operacionB = hilos.submit(() -> esperar("DL-B", TipoRecurso.MONTACARGAS));
    }

    private void esperar(String titular, TipoRecurso segundo) {
        try {
            listos.countDown();
            recursos.esperarUnidad(titular, segundo, 1);
            Thread.sleep(600);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        } finally { recursos.liberar(titular); }
    }

    /** Interrumpe al perdedor; el ganador obtiene el segundo recurso y el otro vuelve a espera. */
    public synchronized void resolver(boolean continuaA) {
        if (!activo || listos.getCount() != 0) throw new IllegalArgumentException("El escenario aún no está listo para resolver.");
        String ganador = continuaA ? "A" : "B", perdedor = continuaA ? "B" : "A";
        (continuaA ? operacionB : operacionA).cancel(true);
        activo = false; recuperando = true;
        mensaje = "Continúa " + ganador + ". Se interrumpió " + perdedor + " y su operación vuelve a la lista de espera.";
        hilos.submit(() -> {
            String titular = "DL-" + perdedor + " (reencolado)";
            try {
                Thread.sleep(750);
                while (!recursos.intentar(titular, Map.of(TipoRecurso.MONTACARGAS, 1, TipoRecurso.ESCANER, 1)))
                    Thread.sleep(60);
                Thread.sleep(300);
                synchronized (this) { mensaje = "Conflicto resuelto. Ambas operaciones terminaron; " + perdedor + " fue atendida después."; }
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            } finally {
                recursos.liberar(titular);
                synchronized (this) { recuperando = false; }
            }
        });
    }

    public synchronized Vista vista() { return new Vista(activo, recuperando, activo && listos.getCount() == 0, mensaje); }
    @Override public void close() { hilos.shutdownNow(); }
}
