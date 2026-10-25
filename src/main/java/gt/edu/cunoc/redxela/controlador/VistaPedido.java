package gt.edu.cunoc.redxela.controlador;

import java.util.List;
import gt.edu.cunoc.redxela.catalogo.CatalogoProductos;
import gt.edu.cunoc.redxela.modelo.*;
import gt.edu.cunoc.redxela.servicio.ContextoCentro;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import org.kordamp.ikonli.feather.Feather;
import static gt.edu.cunoc.redxela.controlador.Componentes.*;

/**
 * Mantiene un borrador de varios productos sin alterar el inventario.
 */
public final class VistaPedido extends VBox {

    private final ContextoCentro centro;
    private final TextField campoId = new TextField();
    private final TextField campoCantidad = new TextField("1");
    private final ComboBox<Cliente> selectorCliente = new ComboBox<>();
    private final ComboBox<Producto> selectorProducto = new ComboBox<>();
    private final Label servicio = texto("Selecciona una cuenta", "dato-cian");
    private final Label total = texto("0 productos, 0 unidades", "dato-cian");
    private final Label existencias = texto("", "muted");
    private final Label aviso = parrafo("", "muted");
    private final TableView<DetallePedido> detalles = new TableView<>();

    public VistaPedido(ContextoCentro centro, Runnable registrarCliente) {
        this.centro = centro;
        getStyleClass().add("pagina");
        campoId.setText(centro.siguienteIdManual());
        configurarCliente();
        configurarProductos();
        configurarTabla();
        getChildren().add(crearFormulario(registrarCliente));
        actualizar();
    }

    private void configurarCliente() {
        selectorCliente.setPromptText("Selecciona una cuenta registrada");
        selectorCliente.setMaxWidth(Double.MAX_VALUE);
        selectorCliente.valueProperty().addListener((observable, anterior, actual) -> actualizarServicio(actual));
    }

    private void actualizarServicio(Cliente cuenta) {
        if (cuenta == null) {
            servicio.setText("Selecciona una cuenta");
            return;
        }
        servicio.setText("N" + cuenta.nivelServicio().getPrioridad() + " - " + cuenta.nivelServicio());
    }

    private void configurarProductos() {
        selectorProducto.getItems().setAll(CatalogoProductos.getProductos());
        selectorProducto.getSelectionModel().selectFirst();
        selectorProducto.setMaxWidth(Double.MAX_VALUE);
        selectorProducto.valueProperty().addListener((o, a, b) -> actualizarExistencias());
        campoCantidad.setPrefWidth(100);
        campoCantidad.setMaxWidth(120);
    }

    private void configurarTabla() {
        detalles.getColumns().add(columna("Producto", detalle -> detalle.producto().nombre(), 350));
        detalles.getColumns().add(columna(
                "Tipo de mercancía",
                detalle -> detalle.producto().tipoMercancia().toString(),
                210
        ));
        detalles.getColumns().add(columna("Unidades", detalle -> Integer.toString(detalle.cantidad()), 100));
        detalles.setPrefHeight(180);
        detalles.setPlaceholder(texto("Agrega los productos del pedido.", "muted"));
        detalles.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        detalles.getSelectionModel().selectedItemProperty().addListener((observable, anterior, actual) -> {
            if (actual != null) {
                selectorProducto.setValue(actual.producto());
                campoCantidad.setText(Integer.toString(actual.cantidad()));
            }
        });
    }

    private VBox crearFormulario(Runnable registrarCliente) {
        HBox cuenta = fila(selectorCliente, boton("Registrar cliente", "boton-secundario", registrarCliente));
        HBox.setHgrow(selectorCliente, Priority.ALWAYS);
        HBox seleccion = fila(
                selectorProducto,
                campoCantidad,
                boton("Guardar producto", "boton-secundario", this::guardarProducto)
        );
        HBox.setHgrow(selectorProducto, Priority.ALWAYS);
        Button quitar = boton("Quitar producto", "boton-secundario", this::quitarProducto);
        quitar.disableProperty().bind(detalles.getSelectionModel().selectedItemProperty().isNull());
        Button enviar = boton("REGISTRAR PEDIDO", "boton-primario", this::registrar);
        enviar.setMaxWidth(Double.MAX_VALUE);
        VBox formulario = tarjeta(
                tituloIcono(Feather.SHOPPING_CART, "NUEVO PEDIDO", "titulo-tarjeta"),
                new Separator(),
                new VBox(8, texto("ID DEL PEDIDO", "etiqueta"), campoId),
                new VBox(8, texto("CLIENTE", "etiqueta"), cuenta),
                new VBox(8, texto("SERVICIO CONTRATADO", "etiqueta"), servicio),
                new VBox(8, texto("PRODUCTO Y CANTIDAD", "etiqueta"), seleccion, existencias),
                detalles,
                fila(quitar, espacio(), total),
                enviar,
                aviso
        );
        formulario.getStyleClass().add("formulario-pedido");
        formulario.setMaxWidth(Double.MAX_VALUE);
        return formulario;
    }

    private void quitarProducto() {
        detalles.getItems().remove(detalles.getSelectionModel().getSelectedItem());
        actualizarTotal();
    }

    private void guardarProducto() {
        try {
            if (selectorProducto.getValue() == null) {
                throw new IllegalArgumentException("Selecciona un producto.");
            }
            int unidades;
            try {
                unidades = Integer.parseInt(campoCantidad.getText().strip());
            } catch (NumberFormatException exception) {
                throw new IllegalArgumentException("Ingresa una cantidad entera positiva.");
            }
            DetallePedido nuevo = new DetallePedido(selectorProducto.getValue(), unidades);
            validarTotal(nuevo);
            int indice = buscarProducto(nuevo.producto());
            if (indice < 0) {
                detalles.getItems().add(nuevo);
            } else {
                detalles.getItems().set(indice, nuevo);
            }
            actualizarTotal();
            mensaje(aviso, "Producto guardado. Guardar nuevamente el mismo producto actualiza su cantidad.", false);
        } catch (IllegalArgumentException exception) {
            mensaje(aviso, exception.getMessage(), true);
        }
    }

    private void validarTotal(DetallePedido nuevo) {
        long totalUnidades = nuevo.cantidad();
        for (DetallePedido detalle : detalles.getItems()) {
            if (!detalle.producto().equals(nuevo.producto())) {
                totalUnidades += detalle.cantidad();
            }
        }
        if (totalUnidades > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("La cantidad total de artículos es demasiado grande.");
        }
    }

    private int buscarProducto(Producto productoBuscado) {
        int indice = -1;
        for (int posicion = 0; posicion < detalles.getItems().size(); posicion++) {
            if (detalles.getItems().get(posicion).producto().equals(productoBuscado)) {
                indice = posicion;
            }
        }
        return indice;
    }

    private void registrar() {
        try {
            if (selectorCliente.getValue() == null) {
                throw new IllegalArgumentException("Selecciona una cuenta registrada.");
            }
            Pedido pedido = centro.registrarManual(
                    campoId.getText(),
                    selectorCliente.getValue().id(),
                    List.copyOf(detalles.getItems())
            );
            detalles.getItems().clear();
            actualizarTotal();
            campoId.setText(centro.siguienteIdManual());
            campoCantidad.setText("1");
            mensaje(
                    aviso,
                    "Pedido " + centro.identificador(pedido) + " registrado con " + pedido.getTotalArticulos() + " unidades.",
                    false
            );
        } catch (IllegalArgumentException exception) {
            mensaje(aviso, exception.getMessage(), true);
        }
    }

    private void actualizarTotal() {
        long unidades = detalles.getItems().stream().mapToLong(DetallePedido::cantidad).sum();
        total.setText(detalles.getItems().size() + " productos, " + unidades + " unidades");
    }

    private void actualizarExistencias() {
        Producto seleccionado = selectorProducto.getValue();
        if (seleccionado == null) {
            existencias.setText("");
            return;
        }
        int disponibles = centro.getAlmacen().disponible(seleccionado);
        existencias.setText("Disponible: " + disponibles + " unidades. "
                + "Los pedidos sin stock suficiente quedan en espera.");
    }

    public void seleccionarCliente(Cliente cuenta) {
        actualizar();
        selectorCliente.setValue(cuenta);
    }

    public void actualizar() {
        List<Cliente> cuentas = centro.getClientes().getClientes();
        if (!selectorCliente.getItems().equals(cuentas)) {
            Cliente seleccionado = selectorCliente.getValue();
            selectorCliente.getItems().setAll(cuentas);
            selectorCliente.setValue(seleccionado);
        }
        actualizarExistencias();
    }
}
