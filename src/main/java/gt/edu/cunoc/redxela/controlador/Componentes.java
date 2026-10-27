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

    /** Crea un icono Feather con el tamaño y estilo comunes de la aplicación. */
    static FontIcon icono(Feather simbolo, int tamano) {
        FontIcon icono = FontIcon.of(simbolo, tamano);
        icono.getStyleClass().add("icono");
        return icono;
    }

    /** Reúne el icono y el texto de un título. */
    static Label tituloIcono(Feather simbolo, String titulo, String estilo) {
        Label label = texto(titulo, estilo);
        label.setGraphic(icono(simbolo, 20));
        label.setGraphicTextGap(12);
        return label;
    }

    /** Crea una etiqueta con una clase del archivo CSS. */
    static Label texto(String texto, String estilo) {
        Label label = new Label(texto);
        label.getStyleClass().add(estilo);
        return label;
    }

    /** Permite que un texto largo ocupe varias líneas. */
    static Label parrafo(String texto, String estilo) {
        Label label = texto(texto, estilo);
        label.setWrapText(true);
        label.setMaxWidth(Double.MAX_VALUE);
        return label;
    }

    /** Conecta un botón con la acción que debe ejecutar al pulsarse. */
    static Button boton(String texto, String estilo, Runnable accion) {
        Button boton = new Button(texto);
        boton.getStyleClass().add(estilo);
        boton.setOnAction(e -> accion.run());
        return boton;
    }

    /** Alinea controles en una fila con la misma separación. */
    static HBox fila(Node... nodos) {
        HBox fila = new HBox(10, nodos);
        fila.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        return fila;
    }

    /** Ocupa el espacio sobrante para separar controles a ambos lados de una fila. */
    static Region espacio() {
        Region espacio = new Region();
        HBox.setHgrow(espacio, Priority.ALWAYS);
        return espacio;
    }

    /** Agrupa controles con el margen y estilo de una tarjeta. */
    static VBox tarjeta(Node... contenido) {
        VBox tarjeta = new VBox(14, contenido);
        tarjeta.getStyleClass().add("tarjeta");
        return tarjeta;
    }

    /** Usa dos columnas si hay ancho suficiente y apila los paneles en una ventana estrecha. */
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

    /** Añade desplazamiento vertical y ajusta el contenido al ancho disponible. */
    static ScrollPane desplazable(Node contenido) {
        ScrollPane scroll = new ScrollPane(contenido);
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        return scroll;
    }

    /** Cambia el mensaje y su color según sea un resultado correcto o un error. */
    static void mensaje(Label label, String texto, boolean error) {
        label.setText(texto);
        label.getStyleClass().setAll(error ? "error" : "exito");
    }

    /** Crea una columna usando la función que obtiene el texto de cada fila. */
    static <T> TableColumn<T, String> columna(String nombre, Function<T, String> valor, double ancho) {
        TableColumn<T, String> columna = new TableColumn<>(nombre);
        columna.setCellValueFactory(c -> new ReadOnlyStringWrapper(valor.apply(c.getValue())));
        columna.setPrefWidth(ancho);
        return columna;
    }

    /** Abre una ventana de consulta con el mismo estilo y dueña que la pantalla de origen. */
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
