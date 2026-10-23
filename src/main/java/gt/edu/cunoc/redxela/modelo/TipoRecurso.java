package gt.edu.cunoc.redxela.modelo;

/** Capacidades físicas del centro; la asignación concurrente se gestiona aparte. */
public enum TipoRecurso {
    ESTACION_EMPAQUE("Estación de empaque", 6),
    AREA_CARGA("Área de carga", 2),
    ENCARGADO_BODEGA("Encargado de bodega", 10),
    MONTACARGAS("Montacargas", 3),
    CONTROL_CALIDAD("Estación de control de calidad", 4),
    ESCANER("Sistema de escaneo", 1);

    private final String nombre;
    private final int capacidad;

    TipoRecurso(String nombre, int capacidad) {
        this.nombre = nombre;
        this.capacidad = capacidad;
    }

    public int getCapacidad() {
        return capacidad;
    }

    @Override
    public String toString() {
        return nombre;
    }
}
