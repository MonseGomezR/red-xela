package gt.edu.cunoc.redxela.servicio;

import gt.edu.cunoc.redxela.modelo.TipoRecurso;
import java.util.*;
import java.util.concurrent.Semaphore;

/** Cada unidad física dispone de un semáforo binario y un titular visible. */
public final class GestorRecursos {
    private static final class Unidad {
        final TipoRecurso tipo;
        final int numero;
        final Semaphore permiso = new Semaphore(1, true);
        String titular;
        Unidad(TipoRecurso tipo, int numero) { this.tipo = tipo; this.numero = numero; }
    }
    public record Estado(TipoRecurso tipo, int numero, String titular) {
        public boolean disponible() { return titular == null; }
    }
    private final List<Unidad> unidades = new ArrayList<>();

    public GestorRecursos() {
        for (TipoRecurso tipo : TipoRecurso.values())
            for (int i = 1; i <= tipo.getCapacidad(); i++) unidades.add(new Unidad(tipo, i));
    }

    /** Adquisición atómica en orden global: no retiene una parte si otra no está disponible. */
    public synchronized boolean intentar(String titular, Map<TipoRecurso, Integer> cantidades) {
        Objects.requireNonNull(titular);
        List<Unidad> elegidas = new ArrayList<>();
        for (TipoRecurso tipo : TipoRecurso.values()) {
            int cantidad = cantidades.getOrDefault(tipo, 0);
            if (cantidad < 0 || cantidad > tipo.getCapacidad()) throw new IllegalArgumentException("Cantidad de recursos inválida.");
            List<Unidad> libres = unidades.stream().filter(u -> u.tipo == tipo && u.titular == null
                    && u.permiso.availablePermits() == 1).limit(cantidad).toList();
            if (libres.size() != cantidad) return false;
            elegidas.addAll(libres);
        }
        for (Unidad u : elegidas) {
            if (!u.permiso.tryAcquire()) {
                for (Unidad previa : elegidas) {
                    if (titular.equals(previa.titular)) { previa.titular = null; previa.permiso.release(); }
                }
                return false;
            }
            u.titular = titular;
        }
        return true;
    }

    public synchronized void liberar(String titular) {
        for (Unidad u : unidades) if (titular.equals(u.titular)) {
            u.titular = null;
            u.permiso.release();
        }
    }

    public synchronized List<Estado> estados() {
        return unidades.stream().map(u -> new Estado(u.tipo, u.numero, u.titular)).toList();
    }

    public synchronized boolean intentarRecepcion(String titular, int muelle) {
        Unidad carga = unidad(TipoRecurso.AREA_CARGA, muelle);
        Unidad escaner = unidad(TipoRecurso.ESCANER, 1);
        if (carga.titular != null || escaner.titular != null || !carga.permiso.tryAcquire()) return false;
        if (!escaner.permiso.tryAcquire()) { carga.permiso.release(); return false; }
        carga.titular = titular; escaner.titular = titular;
        return true;
    }

    /** Reserva las primeras unidades para la demostración controlada de espera circular. */
    public synchronized boolean prepararConflicto(String a, String b) {
        Unidad montacargas = unidad(TipoRecurso.MONTACARGAS, 1);
        Unidad escaner = unidad(TipoRecurso.ESCANER, 1);
        if (montacargas.titular != null || escaner.titular != null
                || montacargas.permiso.availablePermits() == 0 || escaner.permiso.availablePermits() == 0) return false;
        if (!montacargas.permiso.tryAcquire()) return false;
        if (!escaner.permiso.tryAcquire()) { montacargas.permiso.release(); return false; }
        montacargas.titular = a; escaner.titular = b;
        return true;
    }

    /** Espera interruptible fuera del monitor, para que la recuperación pueda liberar recursos. */
    public void esperarUnidad(String titular, TipoRecurso tipo, int numero) throws InterruptedException {
        Unidad u;
        synchronized (this) { u = unidad(tipo, numero); }
        u.permiso.acquire();
        synchronized (this) { u.titular = titular; }
    }

    private Unidad unidad(TipoRecurso tipo, int numero) {
        return unidades.stream().filter(u -> u.tipo == tipo && u.numero == numero).findFirst().orElseThrow();
    }
}
