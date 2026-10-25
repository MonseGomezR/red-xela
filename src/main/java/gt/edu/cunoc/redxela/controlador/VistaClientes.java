package gt.edu.cunoc.redxela.controlador;

import java.util.List;
import gt.edu.cunoc.redxela.modelo.*;
import gt.edu.cunoc.redxela.servicio.ContextoCentro;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import java.util.function.Consumer;
import org.kordamp.ikonli.feather.Feather;
import static gt.edu.cunoc.redxela.controlador.Componentes.*;

/**
 * Registro explícito de cuentas y elección del servicio contratado.
 */
public final class VistaClientes extends VBox {

    private final ContextoCentro centro;
    private final TextField nombre = new TextField();
    private final TextField correo = new TextField();
    private final ComboBox<NivelServicio> nivel = new ComboBox<>();
    private final TableView<Cliente> cuentas = new TableView<>();
    private final Label aviso = parrafo("", "muted");

    public VistaClientes(ContextoCentro centro, Consumer<Cliente> usarCuenta) {
        this.centro = centro;
        getStyleClass().add("pagina");
        setSpacing(24);
        configurarCampos();
        configurarTabla();
        VBox formulario = crearFormulario();
        VBox listado = crearListado(usarCuenta);
        getChildren().add(columnas(listado, formulario, 62, 850));
        actualizar();
    }

    private void configurarCampos() {
        nombre.setPromptText("Nombre completo");
        correo.setPromptText("Correo electrónico");
        nivel.getItems().setAll(NivelServicio.values());
        nivel.setValue(NivelServicio.ESTANDAR);
        nivel.setMaxWidth(Double.MAX_VALUE);
    }

    private VBox crearFormulario() {
        VBox formulario = tarjeta(
                tituloIcono(Feather.USER, "REGISTRAR CLIENTE", "titulo-tarjeta"),
                new VBox(8, texto("NOMBRE", "etiqueta"), nombre),
                new VBox(8, texto("CORREO ELECTRÓNICO", "etiqueta"), correo),
                new VBox(8, texto("SERVICIO CONTRATADO", "etiqueta"), nivel),
                boton("Registrar cuenta", "boton-primario", this::registrar),
                aviso
        );
        formulario.setMaxWidth(Double.MAX_VALUE);
        return formulario;
    }

    private void configurarTabla() {
        cuentas.getColumns().add(columna("Cliente", Cliente::nombre, 260));
        cuentas.getColumns().add(columna("Correo electrónico", Cliente::correo, 300));
        cuentas.getColumns().add(columna("Servicio contratado", cuenta -> cuenta.nivelServicio().toString(), 180));
        cuentas.setPrefHeight(390);
        cuentas.setPlaceholder(texto("No hay cuentas registradas.", "muted"));
        cuentas.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
    }

    private VBox crearListado(Consumer<Cliente> usarCuenta) {
        Button usar = boton("Crear pedido con esta cuenta", "boton-secundario", () -> {
            Cliente cliente = cuentas.getSelectionModel().getSelectedItem();
            if (cliente != null) {
                usarCuenta.accept(cliente);
            }
        });
        usar.disableProperty().bind(cuentas.getSelectionModel().selectedItemProperty().isNull());
        return new VBox(18, texto("CUENTAS REGISTRADAS", "titulo-tarjeta"), cuentas, usar);
    }

    private void registrar() {
        try {
            if (nivel.getValue() == null) {
                throw new IllegalArgumentException("Selecciona el servicio contratado.");
            }
            Cliente cliente = centro.getClientes().registrar(nombre.getText(), correo.getText(), nivel.getValue());
            actualizar();
            cuentas.getSelectionModel().select(cliente);
            cuentas.scrollTo(cliente);
            nombre.clear();
            correo.clear();
            mensaje(aviso, "Cuenta registrada. Ya puedes utilizarla en un pedido.", false);
        } catch (IllegalArgumentException exception) {
            mensaje(aviso, exception.getMessage(), true);
        }
    }

    public void actualizar() {
        List<Cliente> lista = centro.getClientes().getClientes();
        if (!cuentas.getItems().equals(lista)) {
            Cliente seleccion = cuentas.getSelectionModel().getSelectedItem();
            cuentas.getItems().setAll(lista);
            if (seleccion != null) {
                cuentas.getSelectionModel().select(seleccion);
            }
        }
    }
}
