package gt.edu.cunoc.redxela.modelo;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

class PedidoTest {
    private final Producto cuaderno = new Producto("GEN-001", "Cuaderno", TipoMercancia.GENERAL);
    private final Producto vaso = new Producto("FRA-001", "Vaso", TipoMercancia.FRAGIL);
    private final UUID clienteId = UUID.randomUUID();

    @Test
    void registraPedidoMixtoConIdentidadYTotalDeUnidades() {
        Pedido pedido = Pedido.crear(clienteId, NivelServicio.PRIORITARIO,
                List.of(new DetallePedido(cuaderno, 4), new DetallePedido(vaso, 3)));
        assertEquals(7, pedido.getTotalArticulos());
        assertEquals(clienteId, pedido.clienteId());
        assertEquals(NivelServicio.PRIORITARIO, pedido.nivelServicio());
        assertEquals(EstadoPedido.REGISTRADO, pedido.estado());
        assertNotNull(pedido.creadoEn());
        assertEquals(TipoMercancia.FRAGIL, pedido.detalles().get(1).producto().tipoMercancia());
    }

    @Test
    void pedidosConLosMismosDatosRecibenIdentificadoresDistintos() {
        var detalles = List.of(new DetallePedido(cuaderno, 2));
        Pedido primero = Pedido.crear(clienteId, NivelServicio.ESTANDAR, detalles);
        Pedido segundo = Pedido.crear(clienteId, NivelServicio.ESTANDAR, detalles);
        assertNotEquals(primero.id(), segundo.id());
    }

    @Test
    void cambiarLaListaOriginalNoAlteraUnPedidoRegistrado() {
        var detalles = new ArrayList<>(List.of(new DetallePedido(cuaderno, 2)));
        Pedido pedido = Pedido.crear(clienteId, NivelServicio.EXPRES, detalles);
        detalles.clear();
        assertEquals(2, pedido.getTotalArticulos());
        assertThrows(UnsupportedOperationException.class, () -> pedido.detalles().clear());
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1, Integer.MIN_VALUE})
    void rechazaCantidadesNoPositivas(int cantidad) {
        assertThrows(IllegalArgumentException.class, () -> new DetallePedido(cuaderno, cantidad));
    }

    @Test
    void rechazaPedidosSinProductosOConDatosObligatoriosAusentes() {
        var detalles = List.of(new DetallePedido(cuaderno, 1));
        assertThrows(IllegalArgumentException.class, () ->
                Pedido.crear(clienteId, NivelServicio.ESTANDAR, List.of()));
        assertThrows(NullPointerException.class, () ->
                Pedido.crear(null, NivelServicio.ESTANDAR, detalles));
        assertThrows(NullPointerException.class, () -> Pedido.crear(clienteId, null, detalles));
        assertThrows(NullPointerException.class, () ->
                Pedido.crear(clienteId, NivelServicio.ESTANDAR, null));
        assertThrows(NullPointerException.class, () -> new DetallePedido(null, 1));
    }

    @Test
    void rechazaElMismoCodigoEnDosDetallesAunqueCambieElNombre() {
        Producto duplicado = new Producto(" gen-001 ", "Otro nombre", TipoMercancia.GENERAL);
        assertThrows(IllegalArgumentException.class, () -> Pedido.crear(clienteId,
                NivelServicio.ECONOMICO,
                List.of(new DetallePedido(cuaderno, 2), new DetallePedido(duplicado, 1))));
    }

    @Test
    void rechazaTotalesQueDesbordanElEnteroSinConvertirlosEnNegativos() {
        assertThrows(IllegalArgumentException.class, () -> Pedido.crear(clienteId,
                NivelServicio.PROGRAMADO, List.of(
                        new DetallePedido(cuaderno, Integer.MAX_VALUE), new DetallePedido(vaso, 1))));
    }
}
