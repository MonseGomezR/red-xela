package gt.edu.cunoc.redxela.modelo;

import java.util.Objects;

/**
 * Relaciona un producto con una cantidad estrictamente positiva.
 */
public record DetallePedido(Producto producto, int cantidad) {

    /** Impide guardar una línea sin producto o con una cantidad cero o negativa. */
    public DetallePedido {
        Objects.requireNonNull(producto, "El producto es obligatorio.");
        if (cantidad <= 0) {
            throw new IllegalArgumentException("La cantidad debe ser mayor que cero.");
        }
    }
}
