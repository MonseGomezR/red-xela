package gt.edu.cunoc.redxela.controlador;

import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import java.util.function.Function;
import javafx.beans.property.ReadOnlyStringWrapper;
import org.kordamp.ikonli.feather.Feather;
import org.kordamp.ikonli.javafx.FontIcon;

/**
 * Elementos compartidos por las pantallas del centro.
 */
final class Componentes {

    private Componentes() {
    }

    static FontIcon icono(Feather simbolo, int tamano) {
        FontIcon icono = FontIcon.of(simbolo, tamano);
        icono.getStyleClass().add("icono");
        return icono;
    }

    static Label tituloIcono(Feather simbolo, String titulo, String estilo) {
        Label label = texto(titulo, estilo);
        label.setGraphic(icono(simbolo, 20));
        label.setGraphicTextGap(12);
        return label;
    }

    static Label texto(String texto, String estilo) {
        Label label = new Label(texto);
        label.getStyleClass().add(estilo);
        return label;
    }

    static Label parrafo(String texto, String estilo) {
        Label label = texto(texto, estilo);
        label.setWrapText(true);
        label.setMaxWidth(Double.MAX_VALUE);
        return label;
    }

    static Button boton(String texto, String estilo, Runnable accion) {
        Button boton = new Button(texto);
        boton.getStyleClass().add(estilo);
        boton.setOnAction(e -> accion.run());
        return boton;
    }

    static HBox fila(Node... nodos) {
        HBox fila = new HBox(10, nodos);
        fila.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        return fila;
    }

    static Region espacio() {
        Region espacio = new Region();
        HBox.setHgrow(espacio, Priority.ALWAYS);
        return espacio;
    }

    static VBox tarjeta(Node... contenido) {
        VBox tarjeta = new VBox(14, contenido);
        tarjeta.getStyleClass().add("tarjeta");
        return tarjeta;
    }

    static GridPane columnas(Node izquierda, Node derecha, double proporcion, double umbral) {
        GridPane grid = new GridPane();
        grid.setHgap(22);
        grid.setVgap(22);
        grid.add(izquierda, 0, 0);
        grid.add(derecha, 1, 0);
        grid.widthProperty().addListener((o, a, ancho) -> {
            boolean apilado = ancho.doubleValue() < umbral;
            grid.getColumnConstraints().clear();
            ColumnConstraints primera = new ColumnConstraints();
            primera.setPercentWidth(apilado ? 100 : proporcion);
            grid.getColumnConstraints().add(primera);
            if (!apilado) {
                ColumnConstraints segunda = new ColumnConstraints();
                segunda.setPercentWidth(100 - proporcion);
                grid.getColumnConstraints().add(segunda);
            }
            GridPane.setColumnIndex(derecha, apilado ? 0 : 1);
            GridPane.setRowIndex(derecha, apilado ? 1 : 0);
        });
        GridPane.setValignment(izquierda, javafx.geometry.VPos.TOP);
        GridPane.setValignment(derecha, javafx.geometry.VPos.TOP);
        return grid;
    }

    static ScrollPane desplazable(Node contenido) {
        ScrollPane scroll = new ScrollPane(contenido);
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        return scroll;
    }

    static void mensaje(Label label, String texto, boolean error) {
        label.setText(texto);
        label.getStyleClass().setAll(error ? "error" : "exito");
    }

    static <T> TableColumn<T, String> columna(String nombre, Function<T, String> valor, double ancho) {
        TableColumn<T, String> columna = new TableColumn<>(nombre);
        columna.setCellValueFactory(c -> new ReadOnlyStringWrapper(valor.apply(c.getValue())));
        columna.setPrefWidth(ancho);
        return columna;
    }

    static void dialogo(String titulo, Node contenido, Node origen) {
        Dialog<Void> dialogo = new Dialog<>();
        dialogo.setTitle(titulo);
        dialogo.initOwner(origen.getScene().getWindow());
        dialogo.getDialogPane().getStylesheets().add(Componentes.class.getResource("/gt/edu/cunoc/redxela/estilos.css").toExternalForm());
        dialogo.getDialogPane().setContent(contenido);
        dialogo.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        dialogo.setResizable(true);
        dialogo.showAndWait();
    }
}
