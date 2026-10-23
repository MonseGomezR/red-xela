package gt.edu.cunoc.redxela.modelo;

import java.util.Locale;
import java.util.Objects;

/** Entrada inmutable del catálogo; las existencias se administran por separado. */
public record Producto(String codigo, String nombre, TipoMercancia tipoMercancia) {
    public Producto {
        Objects.requireNonNull(codigo, "El código del producto es obligatorio.");
        Objects.requireNonNull(nombre, "El nombre del producto es obligatorio.");
        Objects.requireNonNull(tipoMercancia, "El tipo de mercancía es obligatorio.");
        codigo = codigo.strip().toUpperCase(Locale.ROOT);
        nombre = nombre.strip();
        if (codigo.isEmpty() || nombre.isEmpty()) {
            throw new IllegalArgumentException("El código y el nombre no pueden estar vacíos.");
        }
    }

    @Override
    public String toString() {
        return codigo + " - " + nombre;
    }
}
