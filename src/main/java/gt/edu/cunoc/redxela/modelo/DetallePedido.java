package gt.edu.cunoc.redxela.modelo;

import java.util.Objects;

/**
 * Relaciona un producto con una cantidad estrictamente positiva.
 */
public record DetallePedido(Producto producto, int cantidad) {

    public DetallePedido {
        Objects.requireNonNull(producto, "El producto es obligatorio.");
        if (cantidad <= 0) {
            throw new IllegalArgumentException("La cantidad debe ser mayor que cero.");
        }
    }
}
