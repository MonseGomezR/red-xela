package gt.edu.cunoc.redxela.modelo;

/**
 * Estados que mostrarán las áreas de inventario, procesamiento y despacho.
 */
public enum EstadoPedido {

    REGISTRADO("Registrado"),
    ESPERANDO_INVENTARIO("Esperando inventario"),
    ESPERANDO_RECURSOS("Esperando recursos"),
    EN_PROCESO("En proceso"),
    EN_CONFLICTO("En conflicto de recursos"),
    COMPLETADO("Completado");

    private final String nombre;

    private EstadoPedido(String nombre) {
        this.nombre = nombre;
    }

    @Override
    public String toString() {
        return nombre;
    }
}
