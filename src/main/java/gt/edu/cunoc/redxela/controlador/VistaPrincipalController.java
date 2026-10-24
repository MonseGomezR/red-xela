package gt.edu.cunoc.redxela.controlador;

import gt.edu.cunoc.redxela.servicio.*;
import javafx.animation.*;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.layout.*;
import javafx.scene.control.*;
import javafx.util.Duration;
import static gt.edu.cunoc.redxela.controlador.Componentes.*;

/** Navegación común y actualización de instantáneas exclusivamente en el hilo JavaFX. */
public final class VistaPrincipalController implements AutoCloseable {
    private final ContextoCentro centro = new ContextoCentro();
    private VistaMonitoreo monitoreo;
    private VistaInventario inventario;
    private VistaAdministracion administracion;
    private Timeline refresco;
    private FadeTransition pulso;
    private int pagina;
    @FXML private ScrollPane contenido;
    @FXML private Label titulo, estado, capacidad, entorno, aviso, pie;
    @FXML private ProgressBar barraCapacidad;
    @FXML private Button navMonitoreo, navInventario, navAdmin, iniciar, pausar, avanzar;
    @FXML private VBox menuMonitoreo, menuInventario, menuAdmin;
    @FXML private HBox controlesMonitoreo, controlesInventario, controlesAdmin;
    @FXML private ToggleButton estandar, estres;
    @FXML private ComboBox<String> velocidad;

    @FXML private void initialize() {
        monitoreo = new VistaMonitoreo(centro, this::administrar, this::historial);
        inventario = new VistaInventario(centro);
        administracion = new VistaAdministracion(centro);
        controlesInventario.getChildren().setAll(inventario.controles());
        ToggleGroup modos=new ToggleGroup();estandar.setToggleGroup(modos);estres.setToggleGroup(modos);estandar.setSelected(true);
        velocidad.getItems().setAll("0.5x","1.0x","2.0x","4.0x","8.0x"); velocidad.setValue("1.0x");
        velocidad.setOnAction(e -> centro.motor.velocidad(Double.parseDouble(velocidad.getValue().replace("x",""))));
        entorno.setText(System.getProperty("os.name")+"  ·  JVM "+Runtime.version().feature());
        pulso = new FadeTransition(Duration.seconds(1.2), estado); pulso.setFromValue(1);pulso.setToValue(.5);
        pulso.setAutoReverse(true);pulso.setCycleCount(Animation.INDEFINITE);
        refresco = new Timeline(new KeyFrame(Duration.millis(350),e->actualizar()));refresco.setCycleCount(Animation.INDEFINITE);
        mostrar(0);refresco.play();
    }
    @FXML private void monitorear() { mostrar(0); }
    @FXML private void almacenar() { mostrar(1); }
    @FXML private void administrar() { mostrar(2); }
    @FXML private void recepcionar() { mostrar(2); administracion.enfocarRecepcion(); }
    private void mostrar(int numero) {
        pagina=numero; Node vista=numero==0?monitoreo:numero==1?inventario:administracion;
        contenido.setContent(vista);contenido.setVvalue(0);
        titulo.setText(numero==0?"Panel de Control General":numero==1?"Administración de Almacenamiento":"Centro de Control Administrativo");
        visible(menuMonitoreo,numero==0);visible(menuInventario,numero==1);visible(menuAdmin,numero==2);
        visible(controlesMonitoreo,numero==0);visible(controlesInventario,numero==1);visible(controlesAdmin,numero==2);visible(estado,numero==0);
        visible(barraCapacidad,numero==0);visible(capacidad,numero==0);visible(entorno,numero==0);
        pie.setText(numero==0?"CAPACIDAD ALMACÉN:":numero==1?"VISUALIZANDO DISTRIBUCIÓN FÍSICA DE REDXELA":"PANEL ADMINISTRATIVO Y RESOLUCIÓN DE CONFLICTOS REDXELA");
        navAdmin.setText(numero==2?"Admin de Conflictos":"Conflictos (Deadlocks)");
        for(Button boton:new Button[]{navMonitoreo,navInventario,navAdmin})boton.getStyleClass().remove("seleccionado");
        (numero==0?navMonitoreo:numero==1?navInventario:navAdmin).getStyleClass().add("seleccionado");
        actualizar();
    }
    private void activar(boolean activa) {
        centro.motor.activar(activa);
        if(activa)pulso.play();else{pulso.stop();estado.setOpacity(1);}
        actualizar();
    }
    private void visible(Node nodo,boolean mostrar){nodo.setVisible(mostrar);nodo.setManaged(mostrar);}
    @FXML private void reanudar(){activar(true);}
    @FXML private void pausar(){activar(false);}
    @FXML private void modoEstandar(){centro.estres(false);estandar.setSelected(true);}
    @FXML private void modoEstres(){centro.estres(true);estres.setSelected(true);}
    @FXML private void paso() {
        try{centro.motor.paso();aviso.setText("Avance de un minuto simulado.");}
        catch(IllegalArgumentException ex){aviso.setText(ex.getMessage());}
        actualizar();
    }
    private void actualizar() {
        boolean activa=centro.motor.activa(); estado.setText(activa?(centro.estres()?"STRESS TEST ACTIVO":"SIMULACIÓN ACTIVA"):"SIMULACIÓN PAUSADA");
        estado.getStyleClass().setAll("estado-sesion",activa?"exito":"muted");
        iniciar.setDisable(activa);pausar.setDisable(!activa);avanzar.setDisable(activa);
        var resumen=centro.almacen.resumen();double porcentaje=(double)resumen.unidades()/resumen.capacidad();
        capacidad.setText("%.0f%%".formatted(porcentaje*100));barraCapacidad.setProgress(porcentaje);
        if(pagina==0)monitoreo.actualizar();else if(pagina==1)inventario.actualizar();else administracion.actualizar();
    }
    @FXML private void historial() {
        TableView<MotorSimulacion.VistaPedido> tabla=new TableView<>();
        tabla.getColumns().add(columna("ID del pedido",v->centro.identificador(v.pedido()),170));
        tabla.getColumns().add(columna("Cliente",v->centro.clientes.buscarPorId(v.pedido().clienteId()).orElseThrow().nombre(),200));
        tabla.getColumns().add(columna("Servicio",v->v.pedido().nivelServicio().toString(),120));
        tabla.getColumns().add(columna("Estado",v->v.estado().toString(),160));
        tabla.getColumns().add(columna("Unidades",v->Integer.toString(v.pedido().getTotalArticulos()),80));
        tabla.getColumns().add(columna("Productos",v->v.pedido().detalles().stream().map(d->d.producto().nombre()+" ×"+d.cantidad()).collect(java.util.stream.Collectors.joining(", ")),300));
        tabla.getItems().setAll(centro.motor.pedidos());tabla.setPrefSize(1000,450);
        tabla.setPlaceholder(texto("Todavía no se han registrado pedidos.","muted"));
        dialogo("Historial de pedidos · instantánea actual",tabla,contenido);
    }
    @FXML private void depurar() {
        StringBuilder registro=new StringBuilder("RECURSOS COMPARTIDOS\n");
        centro.recursos.estados().forEach(r->registro.append(r.tipo()).append(" ").append(r.numero()).append(" → ")
                .append(r.disponible()?"Disponible":r.titular()).append("\n"));
        registro.append("\nRECEPCIÓN\n");centro.recepcion.historial().forEach(r->registro.append(r.entrada().id()).append(" → ").append(r.estado()).append("\n"));
        registro.append("\nCONFLICTOS\n").append(centro.conflictos.vista().mensaje());
        TextArea area=new TextArea(registro.toString());area.setEditable(false);area.setPrefSize(760,480);area.getStyleClass().add("mono");
        Label resultado=parrafo("","muted");
        Button provocar=boton("Simular espera circular","boton-secundario",()->{
            try{centro.conflictos.provocar();mensaje(resultado,"Escenario iniciado. Consulta el monitor de conflictos.",false);}
            catch(IllegalArgumentException ex){mensaje(resultado,ex.getMessage(),true);}
        });
        dialogo("Modo Depuración",new VBox(16,area,provocar,resultado),contenido);
    }
    @Override public void close() {
        if(refresco!=null)refresco.stop();if(pulso!=null)pulso.stop();
        if(monitoreo!=null)monitoreo.detenerAnimaciones();centro.close();
    }
}
