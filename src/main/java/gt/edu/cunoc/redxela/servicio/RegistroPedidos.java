package gt.edu.cunoc.redxela.servicio;

import gt.edu.cunoc.redxela.modelo.Cliente;
import gt.edu.cunoc.redxela.catalogo.CatalogoProductos;
import gt.edu.cunoc.redxela.modelo.DetallePedido;
import gt.edu.cunoc.redxela.modelo.Pedido;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Recibe pedidos válidos de clientes registrados y conserva su orden de llegada.
 */
public final class RegistroPedidos {

    private final RegistroClientes clientes;
    private final List<Pedido> pedidos = new ArrayList<>();

    public RegistroPedidos(RegistroClientes clientes) {
        this.clientes = Objects.requireNonNull(clientes);
    }

    public synchronized Pedido registrar(UUID clienteId, List<DetallePedido> detalles) {
        Cliente cliente = clientes.buscarPorId(clienteId).orElseThrow(() -> new IllegalArgumentException("Selecciona un cliente registrado."));
        Pedido pedido = Pedido.crear(cliente.id(), cliente.nivelServicio(), detalles);
        for (DetallePedido detalle : pedido.detalles()) {
            boolean productoValido = CatalogoProductos.buscarPorCodigo(detalle.producto().codigo()).filter(detalle.producto()::equals).isPresent();
            if (!productoValido) {
                throw new IllegalArgumentException("El producto no pertenece al catálogo: " + detalle.producto().codigo());
            }
        }
        pedidos.add(pedido);
        return pedido;
    }

    public synchronized List<Pedido> getPedidos() {
        return List.copyOf(pedidos);
    }
}
