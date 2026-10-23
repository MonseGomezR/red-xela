package gt.edu.cunoc.redxela.modelo;

import java.time.Duration;
import java.util.Optional;

/** Clasifica la atención contratada; un número menor indica mayor prioridad. */
public enum NivelServicio {
    EXPRES("Exprés", 1, null),
    PRIORITARIO("Prioritario", 2, Duration.ofMinutes(10)),
    ESTANDAR("Estándar", 3, Duration.ofMinutes(30)),
    PROGRAMADO("Programado", 4, Duration.ofMinutes(60)),
    ECONOMICO("Económico", 5, Duration.ofMinutes(120));

    private final String nombre;
    private final int prioridad;
    private final Duration esperaObjetivo;

    NivelServicio(String nombre, int prioridad, Duration esperaObjetivo) {
        this.nombre = nombre;
        this.prioridad = prioridad;
        this.esperaObjetivo = esperaObjetivo;
    }

    public int getPrioridad() {
        return prioridad;
    }

    /** Exprés requiere atención inmediata, sin asignarle un plazo arbitrario. */
    public Optional<Duration> getEsperaObjetivo() {
        return Optional.ofNullable(esperaObjetivo);
    }

    public boolean esInmediato() {
        return this == EXPRES;
    }

    @Override
    public String toString() {
        return nombre;
    }
}
