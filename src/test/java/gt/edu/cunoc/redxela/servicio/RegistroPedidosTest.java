package gt.edu.cunoc.redxela.servicio;

import gt.edu.cunoc.redxela.catalogo.CatalogoProductos;
import gt.edu.cunoc.redxela.modelo.DetallePedido;
import gt.edu.cunoc.redxela.modelo.EstadoPedido;
import gt.edu.cunoc.redxela.modelo.NivelServicio;
import gt.edu.cunoc.redxela.modelo.Producto;
import gt.edu.cunoc.redxela.modelo.TipoMercancia;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RegistroPedidosTest {
    private final RegistroClientes clientes = new RegistroClientes();
    private final RegistroPedidos pedidos = new RegistroPedidos(clientes);
    private final Producto cuaderno = CatalogoProductos.buscarPorCodigo("GEN-001").orElseThrow();

    @Test
    void elPedidoUsaElServicioContratadoYConservaElCliente() {
        var cliente = clientes.registrar("Ana", "ana@ejemplo.com", NivelServicio.PRIORITARIO);
        var pedido = pedidos.registrar(cliente.id(), List.of(new DetallePedido(cuaderno, 3)));
        assertEquals(cliente.id(), pedido.clienteId());
        assertEquals(NivelServicio.PRIORITARIO, pedido.nivelServicio());
        assertEquals(EstadoPedido.REGISTRADO, pedido.estado());
        assertEquals(List.of(pedido), pedidos.getPedidos());
    }

    @Test
    void rechazaClientesNoRegistradosYConservaLaListaVacia() {
        var detalles = List.of(new DetallePedido(cuaderno, 1));
        assertThrows(IllegalArgumentException.class, () -> pedidos.registrar(UUID.randomUUID(), detalles));
        assertThrows(IllegalArgumentException.class, () -> pedidos.registrar(null, detalles));
        assertTrue(pedidos.getPedidos().isEmpty());
    }

    @Test
    void rechazaProductosInexistentesYAlteracionesDeUnProductoDelCatalogo() {
        var cliente = clientes.registrar("Ana", "ana@ejemplo.com", NivelServicio.ESTANDAR);
        for (Producto producto : List.of(
                new Producto("OTRO-001", "Inventado", TipoMercancia.GENERAL),
                new Producto("GEN-001", "Cuaderno", TipoMercancia.PESADA))) {
            assertThrows(IllegalArgumentException.class, () ->
                    pedidos.registrar(cliente.id(), List.of(new DetallePedido(producto, 1))));
        }
        assertTrue(pedidos.getPedidos().isEmpty());
    }

    @Test
    void unRegistroFallidoNoModificaLosPedidosAnteriores() {
        var cliente = clientes.registrar("Ana", "ana@ejemplo.com", NivelServicio.EXPRES);
        var primero = pedidos.registrar(cliente.id(), List.of(new DetallePedido(cuaderno, 2)));
        var instantanea = pedidos.getPedidos();
        assertThrows(IllegalArgumentException.class, () -> pedidos.registrar(cliente.id(), List.of()));
        var segundo = pedidos.registrar(cliente.id(), List.of(new DetallePedido(cuaderno, 1)));
        assertEquals(List.of(primero), instantanea);
        assertEquals(List.of(primero, segundo), pedidos.getPedidos());
        assertNotEquals(primero.id(), segundo.id());
        assertThrows(UnsupportedOperationException.class, instantanea::clear);
    }
}
