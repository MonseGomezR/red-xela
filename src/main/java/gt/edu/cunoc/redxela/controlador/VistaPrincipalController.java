package gt.edu.cunoc.redxela.controlador;

import gt.edu.cunoc.redxela.catalogo.CatalogoProductos;
import gt.edu.cunoc.redxela.modelo.*;
import gt.edu.cunoc.redxela.servicio.RegistroClientes;
import gt.edu.cunoc.redxela.servicio.RegistroPedidos;
import java.util.List;
import java.util.function.Function;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;

/** Conecta los formularios con los registros de la sesión, sin procesar pedidos. */
public final class VistaPrincipalController {
    private final RegistroClientes clientes = new RegistroClientes();
    private final RegistroPedidos pedidos = new RegistroPedidos(clientes);
    private final ObservableList<DetallePedido> borrador = FXCollections.observableArrayList();

    @FXML private TabPane pestañas;
    @FXML private Tab pestañaClientes;
    @FXML private TextField nombreCliente;
    @FXML private TextField correoCliente;
    @FXML private ComboBox<NivelServicio> nivelCliente;
    @FXML private TableView<Cliente> tablaClientes;
    @FXML private Label mensajeClientes;
    @FXML private ComboBox<Cliente> clientePedido;
    @FXML private Label servicioPedido;
    @FXML private ComboBox<Producto> productoPedido;
    @FXML private TextField cantidadPedido;
    @FXML private TableView<DetallePedido> tablaDetalles;
    @FXML private Label totalArticulos;
    @FXML private Label mensajePedidos;
    @FXML private TableView<Pedido> tablaPedidos;
    @FXML private Button quitarProducto;
    @FXML private Button verDetalle;

    @FXML
    private void initialize() {
        nivelCliente.getItems().setAll(NivelServicio.values());
        nivelCliente.setValue(NivelServicio.ESTANDAR);
        productoPedido.getItems().setAll(CatalogoProductos.getProductos());
        productoPedido.getSelectionModel().selectFirst();
        clientePedido.valueProperty().addListener((observable, anterior, cliente) -> {
            servicioPedido.setText(cliente == null ? "Sin cliente seleccionado" : cliente.nivelServicio().toString());
            mensajePedidos.setText("Selecciona un cliente y agrega los productos que necesita.");
            mensajePedidos.getStyleClass().setAll("mensaje");
        });
        tablaClientes.getColumns().add(columna("Nombre", Cliente::nombre, 230));
        tablaClientes.getColumns().add(columna("Correo", Cliente::correo, 310));
        tablaClientes.getColumns().add(columna("Servicio", cliente -> cliente.nivelServicio().toString(), 160));
        tablaDetalles.getColumns().add(columna("Producto", detalle -> detalle.producto().toString(), 410));
        tablaDetalles.getColumns().add(columna("Mercancía", detalle -> detalle.producto().tipoMercancia().toString(), 190));
        tablaDetalles.getColumns().add(columna("Unidades", detalle -> Integer.toString(detalle.cantidad()), 100));
        tablaDetalles.setItems(borrador);
        borrador.addListener((ListChangeListener<DetallePedido>) cambio -> actualizarTotal());
        quitarProducto.disableProperty().bind(tablaDetalles.getSelectionModel().selectedItemProperty().isNull());
        tablaPedidos.getColumns().add(columna("ID del pedido", pedido -> pedido.id().toString(), 285));
        tablaPedidos.getColumns().add(columna("Cliente", pedido -> clientes.buscarPorId(pedido.clienteId())
                .map(Cliente::nombre).orElse("Cliente desconocido"), 190));
        tablaPedidos.getColumns().add(columna("Servicio", pedido -> pedido.nivelServicio().toString(), 120));
        tablaPedidos.getColumns().add(columna("Unidades", pedido -> Integer.toString(pedido.getTotalArticulos()), 85));
        tablaPedidos.getColumns().add(columna("Estado", pedido -> pedido.estado().toString(), 180));
        verDetalle.disableProperty().bind(tablaPedidos.getSelectionModel().selectedItemProperty().isNull());
        // La tabla conserva el orden de llegada hasta incorporar la planificación.
        tablaClientes.setPlaceholder(new Label("Todavía no hay clientes registrados."));
        tablaDetalles.setPlaceholder(new Label("Agrega los productos del pedido."));
        tablaPedidos.setPlaceholder(new Label("Todavía no hay pedidos registrados."));
    }

    private <T> TableColumn<T, String> columna(String titulo, Function<T, String> valor, double ancho) {
        TableColumn<T, String> columna = new TableColumn<>(titulo);
        columna.setCellValueFactory(celda -> new ReadOnlyStringWrapper(valor.apply(celda.getValue())));
        columna.setPrefWidth(ancho);
        columna.setSortable(false);
        return columna;
    }

    @FXML
    private void registrarCliente() {
        try {
            if (nivelCliente.getValue() == null) {
                throw new IllegalArgumentException("Selecciona un nivel de servicio.");
            }
            Cliente cliente = clientes.registrar(nombreCliente.getText(), correoCliente.getText(), nivelCliente.getValue());
            tablaClientes.getItems().setAll(clientes.getClientes());
            clientePedido.getItems().setAll(clientes.getClientes());
            clientePedido.setValue(cliente);
            nombreCliente.clear();
            correoCliente.clear();
            mostrarMensaje(mensajeClientes, "Cuenta registrada para " + cliente.nombre() + ". Ya puedes crear sus pedidos.", false);
        } catch (IllegalArgumentException exception) {
            mostrarMensaje(mensajeClientes, exception.getMessage(), true);
        }
    }

    @FXML
    private void irAClientes() {
        pestañas.getSelectionModel().select(pestañaClientes);
        nombreCliente.requestFocus();
    }

    @FXML
    private void agregarProducto() {
        try {
            Producto producto = productoPedido.getValue();
            if (producto == null) {
                throw new IllegalArgumentException("Selecciona un producto del catálogo.");
            }
            int cantidad;
            try {
                cantidad = Integer.parseInt(cantidadPedido.getText().strip());
            } catch (NumberFormatException exception) {
                throw new IllegalArgumentException("La cantidad debe ser un número entero entre 1 y " + Integer.MAX_VALUE + ".");
            }
            DetallePedido nuevo = new DetallePedido(producto, cantidad);
            long total = borrador.stream().mapToLong(DetallePedido::cantidad).sum() + cantidad;
            if (total > Integer.MAX_VALUE) {
                throw new IllegalArgumentException("La cantidad total de artículos es demasiado grande.");
            }
            // Consolida productos repetidos antes de enviar el pedido al registro.
            int indice = -1;
            for (int i = 0; i < borrador.size(); i++) {
                if (borrador.get(i).producto().codigo().equals(producto.codigo())) {
                    indice = i;
                    break;
                }
            }
            if (indice >= 0) {
                nuevo = new DetallePedido(producto, borrador.get(indice).cantidad() + cantidad);
                borrador.set(indice, nuevo);
            } else {
                borrador.add(nuevo);
            }
            cantidadPedido.setText("1");
            mostrarMensaje(mensajePedidos, "Producto agregado al pedido.", false);
        } catch (IllegalArgumentException exception) {
            mostrarMensaje(mensajePedidos, exception.getMessage(), true);
        }
    }

    @FXML
    private void quitarProducto() {
        DetallePedido seleccionado = tablaDetalles.getSelectionModel().getSelectedItem();
        if (seleccionado != null) {
            borrador.remove(seleccionado);
            mostrarMensaje(mensajePedidos, "Producto quitado del pedido.", false);
        }
    }

    @FXML
    private void registrarPedido() {
        try {
            Cliente cliente = clientePedido.getValue();
            if (cliente == null) {
                throw new IllegalArgumentException("Selecciona un cliente o registra una cuenta en Clientes.");
            }
            Pedido pedido = pedidos.registrar(cliente.id(), List.copyOf(borrador));
            tablaPedidos.getItems().setAll(pedidos.getPedidos());
            tablaPedidos.getSelectionModel().select(pedido);
            borrador.clear();
            mostrarMensaje(mensajePedidos, "Pedido registrado: " + pedido.getTotalArticulos() + " artículos para " + cliente.nombre() + ".", false);
        } catch (IllegalArgumentException exception) {
            mostrarMensaje(mensajePedidos, exception.getMessage(), true);
        }
    }

    @FXML
    private void verDetalle() {
        Pedido pedido = tablaPedidos.getSelectionModel().getSelectedItem();
        if (pedido == null) {
            return;
        }
        StringBuilder contenido = new StringBuilder("ID: ").append(pedido.id())
                .append("\nCliente: ").append(clientes.buscarPorId(pedido.clienteId()).orElseThrow())
                .append("\nServicio: ").append(pedido.nivelServicio())
                .append("\nEstado: ").append(pedido.estado()).append("\n\n");
        for (DetallePedido detalle : pedido.detalles()) {
            contenido.append(detalle.producto()).append(" — ").append(detalle.cantidad())
                    .append(detalle.cantidad() == 1 ? " unidad\n" : " unidades\n");
        }
        contenido.append("\nTotal: ").append(pedido.getTotalArticulos()).append(" artículos");
        Alert detalle = new Alert(Alert.AlertType.INFORMATION);
        detalle.initOwner(tablaPedidos.getScene().getWindow());
        detalle.setTitle("Detalle del pedido");
        detalle.setHeaderText("Pedido registrado");
        TextArea texto = new TextArea(contenido.toString());
        texto.setEditable(false);
        texto.setWrapText(true);
        texto.setPrefSize(560, 300);
        detalle.getDialogPane().setContent(texto);
        detalle.showAndWait();
    }

    private void actualizarTotal() {
        totalArticulos.setText("Total: " + borrador.stream().mapToLong(DetallePedido::cantidad).sum() + " artículos");
    }

    private void mostrarMensaje(Label destino, String texto, boolean error) {
        destino.setText(texto);
        destino.getStyleClass().setAll("mensaje", error ? "error" : "exito");
    }
}
