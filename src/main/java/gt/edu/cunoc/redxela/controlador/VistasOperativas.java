package gt.edu.cunoc.redxela.controlador;

import gt.edu.cunoc.redxela.catalogo.CatalogoProductos;
import gt.edu.cunoc.redxela.modelo.*;
import gt.edu.cunoc.redxela.servicio.*;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.util.StringConverter;
import org.kordamp.ikonli.feather.Feather;
import java.util.*;
import static gt.edu.cunoc.redxela.controlador.Componentes.*;

/** Atiende la recepción de mercadería y las acciones para resolver conflictos. */
public final class VistasOperativas {

    private final VBox recepcion;
    private final VBox conflictos;
    private final ContextoCentro centro;
    private final TextField lote = new TextField();
    private final ComboBox<Producto> recibido = new ComboBox<>();
    private final ComboBox<Integer> muelle = new ComboBox<>();
    private final Label mensajeEntrada = parrafo("", "muted");
    private final Label conflicto = parrafo("", "muted");
    private final Label alertaEspera = parrafo("", "muted");
    private final Label cuentaConflictos = texto("0 ACTIVOS", "badge-rojo");
    private final Label tituloEspera = texto("TIEMPOS DE ESPERA", "dato-amarillo");
    private final Label tituloCiclo = texto("ESTADO DE CONFLICTOS", "dato-rojo");
    private final Button terminarA;
    private final Button terminarB;
    private final Button aumentar;
    private UUID ultimaEntrada;

    public VistasOperativas(ContextoCentro centro) {
        this.centro = centro;
        lote.setPromptText("Cantidad a recibir");
        terminarA = boton("CONTINUAR DL-A", "boton-rojo", () -> resolver(true));
        terminarB = boton("CONTINUAR DL-B", "boton-rojo-contorno", () -> resolver(false));
        aumentar = boton("AUMENTAR PRIORIDAD MANUALMENTE", "boton-amarillo", this::priorizar);
        conflictos = crearPanelConflictos();
        configurarRecepcion();
        recepcion = crearPanelRecepcion();
        actualizar();
    }

    private VBox crearPanelRecepcion() {
        Button entrada = boton("REGISTRAR ENTRADA", "boton-secundario", this::registrarEntrada);
        entrada.setMaxWidth(Double.MAX_VALUE);
        VBox panel = tarjeta(
                tituloIcono(Feather.TRUCK, "LLEGADA DE MERCADERÍA", "titulo-tarjeta"),
                campo("SELECCIONAR PRODUCTO", recibido),
                campos(campo("CANTIDAD (LOTE)", lote), campo("MUELLE DE CARGA", muelle)),
                entrada,
                mensajeEntrada
        );
        return panel;
    }

    public VBox recepcion() {
        return recepcion;
    }

    public VBox conflictos() {
        return conflictos;
    }

    private VBox campo(String nombre, javafx.scene.Node control) {
        return new VBox(8, texto(nombre, "etiqueta"), control);
    }

    private HBox campos(Region... elementos) {
        HBox fila = new HBox(16, elementos);
        for (Region numero : elementos) {
            HBox.setHgrow(numero, Priority.ALWAYS);
            numero.setMaxWidth(Double.MAX_VALUE);
            numero.setPrefWidth(0);
        }
        return fila;
    }

    private void resolver(boolean continuaA) {
        try {
            centro.getConflictos().resolver(continuaA);
        } catch (IllegalArgumentException exception) {
            mensaje(conflicto, exception.getMessage(), true);
        }
    }

    private Optional<MotorSimulacion.VistaPedido> demorado() {
        return centro.getMotor().espera()
                .stream()
                .filter(pedido -> !pedido.preferente())
                .filter(pedido -> pedido.espera() > pedido.pedido().nivelServicio().getEsperaObjetivo()
                .map(d -> (double) d.toMinutes()).orElse(1.0)).max(Comparator.comparingDouble(MotorSimulacion.VistaPedido::espera));
    }

    private void priorizar() {
        demorado().ifPresent(pedido -> centro.getMotor().priorizar(pedido.pedido().id()));
        actualizar();
    }

    public void actualizar() {
        actualizarConflictos();
        actualizarEspera();
        centro.getRecepcion().historial()
                .stream()
                .filter(resultadoRecepcion -> resultadoRecepcion.entrada().id().equals(ultimaEntrada))
                .findFirst()
                .ifPresent(resultadoRecepcion -> mensaje(
                mensajeEntrada,
                resultadoRecepcion.estado(),
                resultadoRecepcion.estado().startsWith("Rechazado")
        ));
    }

    private VBox crearPanelConflictos() {
        VBox ciclo = tarjeta(tituloCiclo, conflicto, campos(terminarA, terminarB));
        ciclo.getStyleClass().add("alerta-conflicto");
        aumentar.setMaxWidth(Double.MAX_VALUE);
        VBox inanicion = tarjeta(tituloEspera, alertaEspera, aumentar);
        inanicion.getStyleClass().add("alerta-inanicion");
        VBox panel = tarjeta(
                fila(
                        tituloIcono(Feather.LINK_2, "MONITOR DE DEADLOCKS", "titulo-tarjeta"),
                        espacio(),
                        cuentaConflictos
                ),
                columnas(ciclo, inanicion, 50, 850)
        );
        panel.getStyleClass().add("monitor-admin");
        return panel;
    }

    private void configurarRecepcion() {
        recibido.getItems().setAll(CatalogoProductos.getProductos());
        recibido.getSelectionModel().selectFirst();
        recibido.setMaxWidth(Double.MAX_VALUE);
        recibido.setConverter(new StringConverter<>() {

            public String toString(Producto pedido) {
                return pedido == null ? "" : pedido.nombre() + " (" + pedido.tipoMercancia() + ")";
            }

            public Producto fromString(String texto) {
                return null;
            }
        });
        muelle.getItems().setAll(1, 2);
        muelle.setValue(1);
        muelle.setMaxWidth(Double.MAX_VALUE);
        muelle.setConverter(new StringConverter<>() {

            public String toString(Integer numero) {
                return numero == null ? "" : "Área de Carga " + numero;
            }

            public Integer fromString(String texto) {
                return null;
            }
        });
    }

    private void registrarEntrada() {
        try {
            int cantidad = Integer.parseInt(lote.getText().strip());
            ultimaEntrada = centro.getRecepcion().enviar(recibido.getValue(), cantidad, muelle.getValue());
            mensaje(mensajeEntrada, "Entrada en cola de recepción.", false);
        } catch (NumberFormatException exception) {
            mensaje(mensajeEntrada, "Ingresa una cantidad entera positiva.", true);
        } catch (IllegalArgumentException exception) {
            mensaje(mensajeEntrada, exception.getMessage(), true);
        }
    }

    private void actualizarConflictos() {
        MonitorConflictos.Vista vista = centro.getConflictos().vista();
        cuentaConflictos.setText(vista.activo() ? "1 ACTIVO" : "0 ACTIVOS");
        tituloCiclo.setText(vista.activo() ? "ESPERA CIRCULAR, DL-A / DL-B" : "ESTADO DE CONFLICTOS");
        conflicto.setText(vista.mensaje() + (vista.activo() ? "\nDL-A retiene Montacargas 1 y espera el Escáner compartido.\nDL-B retiene el Escáner y espera Montacargas 1." : ""));
        terminarA.setDisable(!vista.listo());
        terminarB.setDisable(!vista.listo());
    }

    private void actualizarEspera() {
        Optional<MotorSimulacion.VistaPedido> demorado = demorado();
        aumentar.setDisable(demorado.isEmpty());
        tituloEspera.setText(demorado.isPresent() ? "TIEMPO OBJETIVO SUPERADO" : "TIEMPOS DE ESPERA");
        alertaEspera.setText(demorado.map(pedido -> "Pedido #" + centro.identificador(pedido.pedido()) + " (Nivel " + pedido.pedido().nivelServicio().getPrioridad() + ") lleva %.1fm en cola. Superó su tiempo objetivo.".formatted(pedido.espera())).orElse("No hay pedidos que superen su tiempo objetivo de espera."));
    }
}
