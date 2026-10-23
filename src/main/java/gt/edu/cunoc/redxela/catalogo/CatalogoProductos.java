package gt.edu.cunoc.redxela.catalogo;

import gt.edu.cunoc.redxela.modelo.Producto;
import gt.edu.cunoc.redxela.modelo.TipoMercancia;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/** Catálogo común para la recepción de mercancía y el registro de pedidos. */
public final class CatalogoProductos {
    private static final List<Producto> PRODUCTOS = List.of(
            new Producto("GEN-001", "Cuaderno", TipoMercancia.GENERAL),
            new Producto("FRA-001", "Vaso de vidrio", TipoMercancia.FRAGIL),
            new Producto("PES-001", "Saco de cemento", TipoMercancia.PESADA),
            new Producto("VOL-001", "Estantería", TipoMercancia.VOLUMINOSA),
            new Producto("ELE-001", "Computadora portátil", TipoMercancia.ELECTRONICA));

    private CatalogoProductos() {
    }

    public static List<Producto> getProductos() {
        return PRODUCTOS;
    }

    public static Optional<Producto> buscarPorCodigo(String codigo) {
        if (codigo == null || codigo.isBlank()) {
            return Optional.empty();
        }
        String buscado = codigo.strip().toUpperCase(Locale.ROOT);
        return PRODUCTOS.stream().filter(producto -> producto.codigo().equals(buscado)).findFirst();
    }
}
