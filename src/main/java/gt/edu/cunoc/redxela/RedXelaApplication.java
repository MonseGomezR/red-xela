package gt.edu.cunoc.redxela;

import java.io.InputStream;
import java.io.IOException;
import java.net.URL;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.stage.Stage;
import javafx.scene.text.Font;
import gt.edu.cunoc.redxela.controlador.VistaPrincipalController;

/**
 * Punto de entrada de la aplicación de escritorio REDXela.
 */
public class RedXelaApplication extends Application {

    private VistaPrincipalController controlador;

    public RedXelaApplication() {
    }

    /** Carga fuentes y FXML y muestra la ventana principal en el hilo de JavaFX. */
    @Override
    public void start(Stage stage) {
        try {
            for (String fuente : new String[] { "Inter-Regular", "Inter-Bold", "JetBrainsMono-Regular", "JetBrainsMono-Bold" }) {
                try (InputStream archivo = RedXelaApplication.class.getResourceAsStream("/gt/edu/cunoc/redxela/fuentes/" + fuente + ".ttf")) {
                    if (archivo != null) {
                        Font.loadFont(archivo, 12);
                    }
                }
            }
            URL vista = RedXelaApplication.class.getResource("/gt/edu/cunoc/redxela/vista-principal.fxml");
            if (vista == null) {
                throw new IOException("No se encontró el archivo de la vista principal.");
            }
            // FXML mantiene la estructura visual separada del código Java.
            FXMLLoader cargador = new FXMLLoader(vista);
            Scene scene = new Scene(cargador.load(), 1320, 820);
            controlador = cargador.getController();
            stage.setTitle("REDXela - Simulador de distribución");
            stage.setMinWidth(1050);
            stage.setMinHeight(680);
            stage.setScene(scene);
            stage.show();
            stage.setMaximized(true);
        } catch (IOException exception) {
            System.err.println("No se pudo cargar la interfaz: " + exception.getMessage());
            Alert error = new Alert(Alert.AlertType.ERROR);
            error.setTitle("Error de inicio");
            error.setHeaderText("No se pudo abrir REDXela");
            error.setContentText("Verifica la instalación y los recursos de la aplicación.");
            error.showAndWait();
            Platform.exit();
        }
    }

    /** Entrega el arranque a JavaFX, que después llama a start. */
    public static void main(String[] args) {
        launch(args);
    }

    /** Al cerrar la ventana, detiene las animaciones y todos los trabajadores del centro. */
    @Override
    public void stop() {
        if (controlador != null) {
            controlador.close();
        }
    }
}
