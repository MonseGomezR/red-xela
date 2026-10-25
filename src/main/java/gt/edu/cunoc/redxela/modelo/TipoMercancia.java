package gt.edu.cunoc.redxela.modelo;

import java.util.Map;
import static gt.edu.cunoc.redxela.modelo.TipoRecurso.*;

/**
 * Necesidades de manipulación de una mercancía, independientes del servicio.
 */
public enum TipoMercancia {

    GENERAL(
            "General",
            "Artículos pequeños de manipulación ordinaria.",
            Map.of(ESTACION_EMPAQUE, 1, ENCARGADO_BODEGA, 1)
    ),
    FRAGIL(
            "Frágil",
            "Requiere protección y revisión antes del despacho.",
            Map.of(ESTACION_EMPAQUE, 1, ENCARGADO_BODEGA, 1, CONTROL_CALIDAD, 1)
    ),
    PESADA(
            "Pesada",
            "Necesita traslado mecánico y apoyo durante la carga.",
            Map.of(ENCARGADO_BODEGA, 2, MONTACARGAS, 1, AREA_CARGA, 1)
    ),
    VOLUMINOSA(
            "Voluminosa",
            "Ocupa dos estaciones para preparar su embalaje.",
            Map.of(ESTACION_EMPAQUE, 2, ENCARGADO_BODEGA, 2, MONTACARGAS, 1)
    ),
    ELECTRONICA(
            "Electrónica",
            "Requiere verificar integridad e identificación individual.",
            Map.of(ESTACION_EMPAQUE, 1, ENCARGADO_BODEGA, 1, CONTROL_CALIDAD, 1, ESCANER, 1)
    );

    private final String nombre;
    private final String descripcion;
    private final Map<TipoRecurso, Integer> recursosRequeridos;

    private TipoMercancia(String nombre, String descripcion, Map<TipoRecurso, Integer> recursosRequeridos) {
        this.nombre = nombre;
        this.descripcion = descripcion;
        this.recursosRequeridos = Map.copyOf(recursosRequeridos);
    }

    public String getDescripcion() {
        return descripcion;
    }

    /**
     * Cantidades por operación de manipulación, no por unidad del producto.
     */
    public Map<TipoRecurso, Integer> getRecursosRequeridos() {
        return recursosRequeridos;
    }

    @Override
    public String toString() {
        return nombre;
    }
}
