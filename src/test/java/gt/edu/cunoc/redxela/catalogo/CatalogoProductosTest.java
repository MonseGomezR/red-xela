package gt.edu.cunoc.redxela.catalogo;

import gt.edu.cunoc.redxela.modelo.Producto;
import gt.edu.cunoc.redxela.modelo.TipoMercancia;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CatalogoProductosTest {
    @Test
    void permiteBuscarUnCodigoSinDependerDeMayusculasOEspacios() {
        Producto producto = CatalogoProductos.buscarPorCodigo(" ele-001 ").orElseThrow();
        assertEquals("ELE-001", producto.codigo());
        assertEquals(TipoMercancia.ELECTRONICA, producto.tipoMercancia());
        assertTrue(CatalogoProductos.buscarPorCodigo("INEXISTENTE").isEmpty());
        assertTrue(CatalogoProductos.buscarPorCodigo(" ").isEmpty());
        assertTrue(CatalogoProductos.buscarPorCodigo(null).isEmpty());
    }

    @Test
    void elCatalogoCubreTodasLasMercanciasConCodigosUnicos() {
        var productos = CatalogoProductos.getProductos();
        Set<TipoMercancia> tipos = productos.stream()
                .map(Producto::tipoMercancia).collect(Collectors.toSet());
        assertEquals(Set.copyOf(Arrays.asList(TipoMercancia.values())), tipos);
        assertEquals((long) productos.size(), productos.stream().map(Producto::codigo).distinct().count());
        assertThrows(UnsupportedOperationException.class, productos::clear);
    }

    @Test
    void normalizaElProductoYRechazaIdentificadoresVacios() {
        Producto producto = new Producto(" gen-002 ", "  Lápiz  ", TipoMercancia.GENERAL);
        assertEquals("GEN-002", producto.codigo());
        assertEquals("Lápiz", producto.nombre());
        assertThrows(IllegalArgumentException.class, () -> new Producto(" ", "Lápiz", TipoMercancia.GENERAL));
        assertThrows(IllegalArgumentException.class, () -> new Producto("GEN-002", " ", TipoMercancia.GENERAL));
        assertThrows(NullPointerException.class, () -> new Producto("GEN-002", "Lápiz", null));
    }
}
