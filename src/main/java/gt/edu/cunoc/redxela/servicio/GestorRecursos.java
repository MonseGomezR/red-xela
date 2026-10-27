package gt.edu.cunoc.redxela.servicio;

import gt.edu.cunoc.redxela.modelo.TipoRecurso;
import java.util.*;
import java.util.concurrent.Semaphore;

/**
 * Cada unidad física dispone de un semáforo binario y un titular visible.
 * synchronized protege la asignación del titular y el semáforo controla el permiso de uso.
 */
public final class GestorRecursos {

    private static final class Unidad {

        private final TipoRecurso tipo;
        private final int numero;
        // Un permiso permite un solo usuario; true respeta la cola de quienes esperan con acquire.
        private final Semaphore permiso = new Semaphore(1, true);
        private String titular;

        private Unidad(TipoRecurso tipo, int numero) {
            this.tipo = tipo;
            this.numero = numero;
        }
    }

    /** Copia del recurso y su usuario actual para las pantallas. */
    public record Estado(TipoRecurso tipo, int numero, String titular) {

        public boolean disponible() {
            return titular == null;
        }
    }

    private final List<Unidad> unidades = new ArrayList<>();

    /** Crea una unidad y un semáforo para cada recurso físico. */
    public GestorRecursos() {
        for (TipoRecurso tipo : TipoRecurso.values()) {
            for (int i = 1; i <= tipo.getCapacidad(); i++) {
                unidades.add(new Unidad(tipo, i));
            }
        }
    }

    /**
     * Adquisición atómica en orden global: no retiene una parte si otra no está disponible.
     */
    public synchronized boolean intentar(String titular, Map<TipoRecurso, Integer> cantidades) {
        Objects.requireNonNull(titular);
        List<Unidad> elegidas = new ArrayList<>();
        for (TipoRecurso tipo : TipoRecurso.values()) {
            int cantidad = cantidades.getOrDefault(tipo, 0);
            if (cantidad < 0 || cantidad > tipo.getCapacidad()) {
                throw new IllegalArgumentException("Cantidad de recursos inválida.");
            }
            List<Unidad> libres = new ArrayList<>();
            for (Unidad unidad : unidades) {
                if (libres.size() == cantidad) {
                    break;
                }
                if (unidad.tipo == tipo && unidad.titular == null && unidad.permiso.availablePermits() == 1) {
                    libres.add(unidad);
                }
            }
            if (libres.size() != cantidad) {
                return false;
            }
            elegidas.addAll(libres);
        }
        for (Unidad unidad : elegidas) {
            if (!unidad.permiso.tryAcquire()) {
                for (Unidad previa : elegidas) {
                    if (titular.equals(previa.titular)) {
                        previa.titular = null;
                        previa.permiso.release();
                    }
                }
                return false;
            }
            unidad.titular = titular;
        }
        return true;
    }

    /** Libera solo las unidades de ese usuario; una segunda llamada no duplica permisos. */
    public synchronized void liberar(String titular) {
        for (Unidad unidad : unidades) {
            if (titular.equals(unidad.titular)) {
                unidad.titular = null;
                unidad.permiso.release();
            }
        }
    }

    /** Devuelve datos de lectura sin entregar los semáforos a la interfaz. */
    public synchronized List<Estado> estados() {
        List<Estado> resultado = new ArrayList<>();
        for (Unidad unidad : unidades) {
            resultado.add(new Estado(unidad.tipo, unidad.numero, unidad.titular));
        }
        return resultado;
    }

    /** Toma juntos el muelle elegido y el escáner, o deja ambos disponibles. */
    public synchronized boolean intentarRecepcion(String titular, int muelle) {
        Unidad carga = unidad(TipoRecurso.AREA_CARGA, muelle);
        Unidad escaner = unidad(TipoRecurso.ESCANER, 1);
        if (carga.titular != null || escaner.titular != null || !carga.permiso.tryAcquire()) {
            return false;
        }
        if (!escaner.permiso.tryAcquire()) {
            carga.permiso.release();
            return false;
        }
        carga.titular = titular;
        escaner.titular = titular;
        return true;
    }

    /**
     * Reserva las primeras unidades para la demostración controlada de espera circular.
     */
    public synchronized boolean prepararConflicto(String titularA, String titularB) {
        Unidad carga = unidad(TipoRecurso.AREA_CARGA, 1);
        Unidad escaner = unidad(TipoRecurso.ESCANER, 1);
        if (carga.titular != null || escaner.titular != null || carga.permiso.availablePermits() == 0 || escaner.permiso.availablePermits() == 0) {
            return false;
        }
        if (!carga.permiso.tryAcquire()) {
            return false;
        }
        if (!escaner.permiso.tryAcquire()) {
            carga.permiso.release();
            return false;
        }
        carga.titular = titularA;
        escaner.titular = titularB;
        return true;
    }

    /**
     * Espera interruptible fuera del monitor, para que la recuperación pueda liberar recursos.
     */
    public void esperarUnidad(String titular, TipoRecurso tipo, int numero) throws InterruptedException {
        Unidad unidad;
        synchronized (this) {
            unidad = unidad(tipo, numero);
        }
        unidad.permiso.acquire();
        synchronized (this) {
            unidad.titular = titular;
        }
    }

    /** Busca una unidad física por su tipo y número. */
    private Unidad unidad(TipoRecurso tipo, int numero) {
        for (Unidad unidad : unidades) {
            if (unidad.tipo == tipo && unidad.numero == numero) {
                return unidad;
            }
        }
        throw new IllegalArgumentException("El recurso indicado no existe.");
    }
}
