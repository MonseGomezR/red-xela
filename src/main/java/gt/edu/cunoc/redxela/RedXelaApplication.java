package gt.edu.cunoc.redxela;

import java.io.IOException;
import java.net.URL;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.stage.Stage;

/** Punto de entrada de la aplicación de escritorio REDXela. */
public class RedXelaApplication extends Application {

    @Override
    public void start(Stage stage) {
        try {
            URL vista = RedXelaApplication.class.getResource("/gt/edu/cunoc/redxela/vista-principal.fxml");
            if (vista == null) {
                throw new IOException("No se encontró el archivo de la vista principal.");
            }
            // FXML mantiene la estructura visual separada del código Java.
            Scene scene = new Scene(new FXMLLoader(vista).load(), 800, 500);
            stage.setTitle("REDXela - Simulador de distribución");
            stage.setMinWidth(640);
            stage.setMinHeight(400);
            stage.setScene(scene);
            stage.show();
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

    public static void main(String[] args) {
        launch(args);
    }
}
