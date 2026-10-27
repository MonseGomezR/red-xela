package gt.edu.cunoc.redxela.controlador;

import gt.edu.cunoc.redxela.servicio.ComparadorPoliticas;
import gt.edu.cunoc.redxela.servicio.ContextoCentro;
import gt.edu.cunoc.redxela.servicio.ResultadoComparacion;
import java.util.List;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableView;
import javafx.scene.control.TableColumn;
import javafx.scene.layout.VBox;
import static gt.edu.cunoc.redxela.controlador.Componentes.*;

/** Muestra por separado los resultados de cada política y cada comparación. */
public final class VistaComparacion extends VBox {

    private final ContextoCentro centro;
    private final TableView<ResultadoComparacion> resultados = new TableView<>();
    private final Label estado = parrafo("", "dato-cian");
    private final Label error = parrafo("", "error");
    private final Button iniciar;

    public VistaComparacion(ContextoCentro centro) {
        this.centro = centro;
        setSpacing(18);
        getStyleClass().add("pagina");
        iniciar = boton("Iniciar comparación", "boton-primario", this::comparar);
        resultados.getColumns().add(columna("Ejecución", r -> Integer.toString(r.getEjecucion()), 75));
        resultados.getColumns().add(columna("Política", r -> r.getPolitica().toString(), 190));
        resultados.getColumns().add(columna("Pedidos", r -> Integer.toString(r.getPedidos()), 70));
        resultados.getColumns().add(columna("Completados", r -> Integer.toString(r.getCompletados()), 100));
        resultados.getColumns().add(columna("Sin completar", r -> Integer.toString(r.getPendientes()), 100));
        resultados.getColumns().add(columna("Espera media", r -> String.format("%.2f min", r.getEsperaMedia()), 110));
        resultados.getColumns().add(columna("Proceso medio", r -> String.format("%.2f min", r.getProcesoMedio()), 120));
        resultados.getColumns().add(columna("Ocupación media", r -> String.format("%.1f%%", r.getOcupacionMedia() * 100), 125));
        resultados.getColumns().add(columna("Minutos", r -> String.format("%.2f", r.getMinutos()), 80));
        // Las filas conservan el orden de ejecución y sus cuatro políticas juntas.
        for (TableColumn<ResultadoComparacion, ?> columna : resultados.getColumns()) {
            columna.setSortable(false);
        }
        resultados.setPrefHeight(330);
        resultados.setPlaceholder(texto("Todavía no hay resultados de comparación.", "muted"));
        getChildren().addAll(
                texto("COMPARACIÓN DE POLÍTICAS", "titulo-tarjeta"),
                parrafo("Cada política recibe al inicio los mismos pedidos pendientes y una copia del inventario disponible. Los pedidos en proceso quedan fuera de la comparación.", "muted"),
                parrafo("Cada ejecución dura aproximadamente 30 minutos simulados a velocidad 8x, sin nuevas llegadas. Los promedios de espera y proceso corresponden a los pedidos completados.", "muted"),
                fila(iniciar, estado), error, resultados
        );
        actualizar();
    }

    /** Pide una comparación al servicio y muestra cualquier dato que falte corregir. */
    private void comparar() {
        error.setText("");
        try {
            centro.getComparador().iniciar(centro.getMotor(), centro.getAlmacen());
        } catch (IllegalArgumentException exception) {
            error.setText(exception.getMessage());
        }
        actualizar();
    }

    /** Muestra resultados nuevos y deshabilita el inicio mientras otra ejecución sigue activa. */
    public void actualizar() {
        ComparadorPoliticas comparador = centro.getComparador();
        List<ResultadoComparacion> lista = comparador.getResultados();
        if (resultados.getItems().size() != lista.size()) {
            resultados.getItems().setAll(lista);
        }
        estado.setText(comparador.getEstado());
        iniciar.setDisable(comparador.isEjecutando() || centro.getMotor().activa());
    }
}
