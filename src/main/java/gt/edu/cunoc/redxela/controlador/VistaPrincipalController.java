package gt.edu.cunoc.redxela.controlador;

import javafx.scene.canvas.GraphicsContext;
import gt.edu.cunoc.redxela.servicio.*;
import javafx.animation.*;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.canvas.Canvas;
import javafx.scene.paint.Color;
import javafx.scene.layout.*;
import javafx.scene.control.*;
import javafx.util.Duration;
import java.util.Map;
import static gt.edu.cunoc.redxela.controlador.Componentes.*;

/**
 * Mantiene destinos independientes y una navegación lateral estable.
 */
public final class VistaPrincipalController implements AutoCloseable {

    private enum Pagina {

        MONITOREO,
        COMPARACION,
        PEDIDO,
        CLIENTES,
        HISTORIAL,
        INVENTARIO,
        RECEPCION,
        CONFLICTOS,
        DEPURACION
    }

    private final ContextoCentro centro = new ContextoCentro();
    private VistaMonitoreo monitoreo;
    private VistaComparacion comparacion;
    private VistaInventario inventario;
    private VistaPedido pedido;
    private VistaClientes clientes;
    private VistasOperativas operaciones;
    private TableView<MotorSimulacion.VistaPedido> tablaPedidos;
    private TextArea registro;
    private VBox paginaHistorial;
    private VBox paginaDepuracion;
    private Map<Pagina, Button> submenus;
    private Timeline refresco;
    private FadeTransition pulso;
    private Pagina pagina;
    @FXML private ScrollPane contenido;
    @FXML private StackPane superficie;
    @FXML private Canvas reticula;
    @FXML private Label titulo;
    @FXML private Label estado;
    @FXML private Label capacidad;
    @FXML private Label entorno;
    @FXML private Label aviso;
    @FXML private Label pie;
    @FXML private ProgressBar barraCapacidad;
    @FXML private Button navMonitoreo;
    @FXML private Button navPedidos;
    @FXML private Button navInventario;
    @FXML private Button navAdmin;
    @FXML private Button iniciar;
    @FXML private Button pausar;
    @FXML private Button avanzar;
    @FXML private Button subNuevo;
    @FXML private Button subComparacion;
    @FXML private Button subClientes;
    @FXML private Button subHistorial;
    @FXML private Button subMapa;
    @FXML private Button subRecepcion;
    @FXML private Button subConflictos;
    @FXML private Button subDepurar;
    @FXML private HBox controlesMonitoreo;
    @FXML private HBox controlesInventario;
    @FXML private HBox controlesAdmin;
    @FXML private ToggleButton estandar;
    @FXML private ToggleButton estres;
    @FXML private ComboBox<String> velocidad;

    public VistaPrincipalController() {
    }

    @FXML
    private void initialize() {
        configurarFondo();
        crearVistas();
        configurarNavegacion();
        configurarSimulacion();
        configurarAnimaciones();

        mostrar(Pagina.MONITOREO);
        refresco.play();
    }

    private void configurarFondo() {
        reticula.setManaged(false);
        reticula.widthProperty().bind(superficie.widthProperty());
        reticula.heightProperty().bind(superficie.heightProperty());
        reticula.widthProperty().addListener((observable, anterior, actual) -> dibujarFondo());
        reticula.heightProperty().addListener((observable, anterior, actual) -> dibujarFondo());
    }

    private void crearVistas() {
        monitoreo = new VistaMonitoreo(centro, this::administrar, this::historial);
        comparacion = new VistaComparacion(centro);
        inventario = new VistaInventario(centro);
        operaciones = new VistasOperativas(centro);
        pedido = new VistaPedido(centro, this::gestionarClientes);
        clientes = new VistaClientes(centro, cuenta -> {
            pedido.seleccionarCliente(cuenta);
            nuevoPedido();
        });
        monitoreo.getChildren().add(0, controlesAdmin);
        crearHistorial();
        crearDepuracion();
    }

    private void configurarNavegacion() {
        submenus = Map.of(
                Pagina.COMPARACION, subComparacion,
                Pagina.PEDIDO, subNuevo,
                Pagina.CLIENTES, subClientes,
                Pagina.HISTORIAL, subHistorial,
                Pagina.INVENTARIO, subMapa,
                Pagina.RECEPCION, subRecepcion,
                Pagina.CONFLICTOS, subConflictos,
                Pagina.DEPURACION, subDepurar
        );
        controlesInventario.getChildren().setAll(inventario.controles());
    }

    private void configurarSimulacion() {
        ToggleGroup modos = new ToggleGroup();
        estandar.setToggleGroup(modos);
        estres.setToggleGroup(modos);
        estandar.setSelected(true);
        velocidad.getItems().setAll("0.5x", "1.0x", "2.0x", "4.0x", "8.0x");
        velocidad.setValue("1.0x");
        velocidad.setOnAction(e -> centro.getMotor().velocidad(Double.parseDouble(velocidad.getValue().replace("x", ""))));
        entorno.setText(System.getProperty("os.name") + ", JVM " + Runtime.version().feature());
    }

    private void configurarAnimaciones() {
        pulso = new FadeTransition(Duration.seconds(1.2), estado);
        pulso.setFromValue(1);
        pulso.setToValue(.5);
        pulso.setAutoReverse(true);
        pulso.setCycleCount(Animation.INDEFINITE);
        refresco = new Timeline(new KeyFrame(Duration.millis(350), e -> actualizar()));
        refresco.setCycleCount(Animation.INDEFINITE);
    }

    /**
     * La trama se vuelve a dibujar al redimensionar, sin intervenir en la simulación.
     */
    private void dibujarFondo() {
        GraphicsContext dibujo = reticula.getGraphicsContext2D();
        dibujo.setFill(Color.web("#0b0d0d"));
        dibujo.fillRect(0, 0, reticula.getWidth(), reticula.getHeight());
        dibujo.setFill(Color.web("#263030"));
        for (int x = 14; x < reticula.getWidth(); x += 20) {
            for (int y = 14; y < reticula.getHeight(); y += 20) {
                dibujo.fillOval(x, y, 1.2, 1.2);
            }
        }
    }

    @FXML
    private void monitorear() {
        mostrar(Pagina.MONITOREO);
    }

    @FXML
    private void compararPoliticas() {
        mostrar(Pagina.COMPARACION);
    }

    @FXML
    private void nuevoPedido() {
        mostrar(Pagina.PEDIDO);
    }

    @FXML
    private void gestionarClientes() {
        mostrar(Pagina.CLIENTES);
    }

    @FXML
    private void historial() {
        mostrar(Pagina.HISTORIAL);
    }

    @FXML
    private void almacenar() {
        mostrar(Pagina.INVENTARIO);
    }

    @FXML
    private void recepcionar() {
        mostrar(Pagina.RECEPCION);
    }

    @FXML
    private void administrar() {
        mostrar(Pagina.CONFLICTOS);
    }

    @FXML
    private void depurar() {
        mostrar(Pagina.DEPURACION);
    }

    private void mostrar(Pagina destino) {
        pagina = destino;
        Node vista = switch (destino) {
            case MONITOREO -> monitoreo;
            case COMPARACION -> comparacion;
            case INVENTARIO -> inventario;
            case PEDIDO -> pedido;
            case CLIENTES -> clientes;
            case RECEPCION -> envolver(operaciones.recepcion());
            case CONFLICTOS -> envolver(operaciones.conflictos());
            case HISTORIAL -> paginaHistorial;
            case DEPURACION -> paginaDepuracion;
        };
        contenido.setContent(vista);
        contenido.setVvalue(0);
        aviso.setText("");
        titulo.setText(switch (destino) {
            case MONITOREO -> "Panel de Control General";
            case COMPARACION -> "Comparación de políticas";
            case PEDIDO -> "Nuevo pedido";
            case CLIENTES -> "Cuentas de clientes";
            case HISTORIAL -> "Historial de pedidos";
            case INVENTARIO -> "Almacén y existencias";
            case RECEPCION -> "Recepción de mercadería";
            case CONFLICTOS -> "Resolución de conflictos";
            case DEPURACION -> "Depuración de recursos";
        });
        actualizarControles(destino);
        seleccionarMenu(destino);
        actualizar();
    }

    private void actualizarControles(Pagina destino) {
        visible(controlesMonitoreo, destino == Pagina.MONITOREO);
        visible(estado, destino == Pagina.MONITOREO);
        visible(controlesInventario, destino == Pagina.INVENTARIO);
        visible(controlesAdmin, destino == Pagina.MONITOREO);
        visible(barraCapacidad, destino == Pagina.MONITOREO);
        visible(capacidad, destino == Pagina.MONITOREO);
        visible(entorno, destino == Pagina.MONITOREO);
        pie.setText(destino == Pagina.MONITOREO ? "CAPACIDAD ALMACÉN:" : "REDXELA");
    }

    private void seleccionarMenu(Pagina destino) {
        for (Button boton : new Button[] { navMonitoreo, navPedidos, navInventario, navAdmin }) {
            boton.getStyleClass().remove("seleccionado");
        }
        submenus.values().forEach(botonSubmenu -> botonSubmenu.getStyleClass().remove("seleccionado"));
        Button principal = switch (destino) {
            case MONITOREO, COMPARACION -> navMonitoreo;
            case PEDIDO, CLIENTES, HISTORIAL -> navPedidos;
            case INVENTARIO, RECEPCION -> navInventario;
            case CONFLICTOS, DEPURACION -> navAdmin;
        };
        principal.getStyleClass().add("seleccionado");
        if (submenus.containsKey(destino)) {
            submenus.get(destino).getStyleClass().add("seleccionado");
        }
    }

    private VBox envolver(Node panel) {
        VBox contenedor = new VBox(panel);
        contenedor.getStyleClass().add("pagina");
        if (panel instanceof Region region) {
            region.setMaxWidth(Double.MAX_VALUE);
        }
        return contenedor;
    }

    private void activar(boolean activa) {
        centro.getMotor().activar(activa);
        if (activa) {
            pulso.play();
        } else {
            pulso.stop();
            estado.setOpacity(1);
        }
        actualizar();
    }

    private void visible(Node nodo, boolean mostrar) {
        nodo.setVisible(mostrar);
        nodo.setManaged(mostrar);
    }

    @FXML
    private void reanudar() {
        activar(true);
    }

    @FXML
    private void pausar() {
        activar(false);
    }

    @FXML
    private void modoEstandar() {
        centro.estres(false);
        estandar.setSelected(true);
    }

    @FXML
    private void modoEstres() {
        centro.estres(true);
        estres.setSelected(true);
    }

    @FXML
    private void paso() {
        try {
            centro.getMotor().paso();
            aviso.setText("Avance de un minuto simulado.");
        } catch (IllegalArgumentException exception) {
            aviso.setText(exception.getMessage());
        }
        actualizar();
    }

    private void actualizar() {
        boolean activa = centro.getMotor().activa();
        estado.setText(activa ? (centro.estres() ? "STRESS TEST ACTIVO" : "SIMULACIÓN ACTIVA") : "SIMULACIÓN PAUSADA");
        estado.getStyleClass().setAll("estado-sesion", activa ? "exito" : "muted");
        iniciar.setDisable(activa);
        pausar.setDisable(!activa);
        avanzar.setDisable(activa);
        Almacen.Resumen resumen = centro.getAlmacen().resumen();
        double porcentaje = (double) resumen.unidades() / resumen.capacidad();
        capacidad.setText("%.0f%%".formatted(porcentaje * 100));
        barraCapacidad.setProgress(porcentaje);
        switch (pagina) {
            case MONITOREO -> monitoreo.actualizar();
            case COMPARACION -> comparacion.actualizar();
            case INVENTARIO -> inventario.actualizar();
            case PEDIDO -> pedido.actualizar();
            case CLIENTES -> clientes.actualizar();
            case RECEPCION, CONFLICTOS -> operaciones.actualizar();
            case HISTORIAL -> actualizarHistorial();
            case DEPURACION -> actualizarRegistro();
        }
    }

    private void crearHistorial() {
        tablaPedidos = new TableView<>();
        tablaPedidos.getColumns().add(columna(
                "ID del pedido",
                vistaPedido -> centro.identificador(vistaPedido.pedido()),
                150
        ));
        tablaPedidos.getColumns().add(columna(
                "Cliente",
                vistaPedido -> centro.getClientes().buscarPorId(vistaPedido.pedido().clienteId()).orElseThrow().nombre(),
                180
        ));
        tablaPedidos.getColumns().add(columna(
                "Servicio",
                vistaPedido -> vistaPedido.pedido().nivelServicio().toString(),
                120
        ));
        tablaPedidos.getColumns().add(columna("Estado", vistaPedido -> vistaPedido.estado().toString(), 130));
        tablaPedidos.getColumns().add(columna(
                "Unidades",
                vistaPedido -> Integer.toString(vistaPedido.pedido().getTotalArticulos()),
                80
        ));
        tablaPedidos.getColumns().add(columna(
                "Productos",
                vistaPedido -> vistaPedido.pedido().detalles()
                        .stream()
                        .map(detalle -> detalle.producto().nombre() + ", " + detalle.cantidad() + " unidades")
                        .collect(java.util.stream.Collectors.joining("; ")),
                270
        ));
        tablaPedidos.setPrefHeight(500);
        tablaPedidos.setPlaceholder(texto("Todavía no se han registrado pedidos.", "muted"));
        paginaHistorial = new VBox(20, tablaPedidos);
        paginaHistorial.getStyleClass().add("pagina");
    }

    private void actualizarHistorial() {
        MotorSimulacion.VistaPedido seleccion = tablaPedidos.getSelectionModel().getSelectedItem();
        tablaPedidos.getItems().setAll(centro.getMotor().pedidos());
        if (seleccion != null) {
            tablaPedidos.getItems()
                    .stream()
                    .filter(pedidoRegistrado -> pedidoRegistrado.pedido().id().equals(seleccion.pedido().id()))
                    .findFirst()
                    .ifPresent(pedidoRegistrado -> tablaPedidos.getSelectionModel().select(pedidoRegistrado));
        }
    }

    private void crearDepuracion() {
        registro = new TextArea();
        registro.setEditable(false);
        registro.setPrefHeight(460);
        registro.getStyleClass().add("mono");
        Label resultado = parrafo("", "muted");
        Button provocar = boton("Simular espera circular", "boton-secundario", () -> {
            try {
                centro.getConflictos().provocar();
                mensaje(resultado, "Escenario iniciado. Consulta Resolución de conflictos.", false);
            } catch (IllegalArgumentException exception) {
                mensaje(resultado, exception.getMessage(), true);
            }
        });
        paginaDepuracion = new VBox(
                16,
                registro,
                fila(provocar, boton("Ver conflictos", "boton-enlace", this::administrar)),
                resultado
        );
        paginaDepuracion.getStyleClass().add("pagina");
    }

    private void actualizarRegistro() {
        StringBuilder texto = new StringBuilder("RECURSOS COMPARTIDOS\n");
        centro.getRecursos().estados().forEach(resultado -> texto.append(resultado.tipo()).append(" ").append(resultado.numero()).append(": ").append(resultado.disponible() ? "Disponible" : resultado.titular()).append("\n"));
        texto.append("\nRECEPCIÓN\n");
        centro.getRecepcion().historial().forEach(resultado -> texto.append(resultado.entrada().id()).append(": ").append(resultado.estado()).append("\n"));
        texto.append("\nCONFLICTOS\n").append(centro.getConflictos().vista().mensaje());
        if (!registro.getText().equals(texto.toString())) {
            int posicion = registro.getCaretPosition();
            double scroll = registro.getScrollTop();
            registro.setText(texto.toString());
            registro.positionCaret(Math.min(posicion, registro.getLength()));
            registro.setScrollTop(scroll);
        }
    }

    @Override
    public void close() {
        if (refresco != null) {
            refresco.stop();
        }
        if (pulso != null) {
            pulso.stop();
        }
        if (monitoreo != null) {
            monitoreo.detenerAnimaciones();
        }
        centro.close();
    }
}
