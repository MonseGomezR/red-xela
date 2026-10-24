package gt.edu.cunoc.redxela.controlador;

import gt.edu.cunoc.redxela.servicio.*;
import javafx.animation.*;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.layout.*;
import javafx.scene.control.*;
import javafx.util.Duration;
import java.util.Map;
import static gt.edu.cunoc.redxela.controlador.Componentes.*;

/** Mantiene destinos independientes y una navegación lateral estable. */
public final class VistaPrincipalController implements AutoCloseable {
    private enum Pagina { MONITOREO, PEDIDO, HISTORIAL, INVENTARIO, RECEPCION, CONFLICTOS, DEPURACION }
    private final ContextoCentro centro=new ContextoCentro();
    private VistaMonitoreo monitoreo;
    private VistaInventario inventario;
    private VistasOperativas operaciones;
    private TableView<MotorSimulacion.VistaPedido> tablaPedidos;
    private TextArea registro;
    private VBox paginaHistorial, paginaDepuracion;
    private Map<Pagina,Button> submenus;
    private Timeline refresco;
    private FadeTransition pulso;
    private Pagina pagina;
    @FXML private ScrollPane contenido;
    @FXML private Label titulo, estado, capacidad, entorno, aviso, pie;
    @FXML private ProgressBar barraCapacidad;
    @FXML private Button navMonitoreo, navPedidos, navInventario, navAdmin, iniciar, pausar, avanzar;
    @FXML private Button subNuevo, subHistorial, subMapa, subRecepcion, subConflictos, subDepurar;
    @FXML private HBox controlesMonitoreo, controlesInventario, controlesAdmin;
    @FXML private ToggleButton estandar, estres;
    @FXML private ComboBox<String> velocidad;

    @FXML private void initialize() {
        monitoreo=new VistaMonitoreo(centro,this::administrar,this::historial);
        inventario=new VistaInventario(centro);
        operaciones=new VistasOperativas(centro);
        monitoreo.getChildren().add(0,controlesAdmin);
        crearHistorial();crearDepuracion();
        submenus=Map.of(Pagina.PEDIDO,subNuevo,Pagina.HISTORIAL,subHistorial,Pagina.INVENTARIO,subMapa,
                Pagina.RECEPCION,subRecepcion,Pagina.CONFLICTOS,subConflictos,Pagina.DEPURACION,subDepurar);
        controlesInventario.getChildren().setAll(inventario.controles());
        ToggleGroup modos=new ToggleGroup();estandar.setToggleGroup(modos);estres.setToggleGroup(modos);estandar.setSelected(true);
        velocidad.getItems().setAll("0.5x","1.0x","2.0x","4.0x","8.0x");velocidad.setValue("1.0x");
        velocidad.setOnAction(e->centro.motor.velocidad(Double.parseDouble(velocidad.getValue().replace("x",""))));
        entorno.setText(System.getProperty("os.name")+", JVM "+Runtime.version().feature());
        pulso=new FadeTransition(Duration.seconds(1.2),estado);pulso.setFromValue(1);pulso.setToValue(.5);
        pulso.setAutoReverse(true);pulso.setCycleCount(Animation.INDEFINITE);
        refresco=new Timeline(new KeyFrame(Duration.millis(350),e->actualizar()));refresco.setCycleCount(Animation.INDEFINITE);
        mostrar(Pagina.MONITOREO);refresco.play();
    }
    @FXML private void monitorear(){mostrar(Pagina.MONITOREO);}
    @FXML private void nuevoPedido(){mostrar(Pagina.PEDIDO);}
    @FXML private void historial(){mostrar(Pagina.HISTORIAL);}
    @FXML private void almacenar(){mostrar(Pagina.INVENTARIO);}
    @FXML private void recepcionar(){mostrar(Pagina.RECEPCION);}
    @FXML private void administrar(){mostrar(Pagina.CONFLICTOS);}
    @FXML private void depurar(){mostrar(Pagina.DEPURACION);}

    private void mostrar(Pagina destino) {
        pagina=destino;
        Node vista=switch(destino){
            case MONITOREO->monitoreo;case INVENTARIO->inventario;case PEDIDO->envolver(operaciones.pedido());
            case RECEPCION->envolver(operaciones.recepcion());case CONFLICTOS->envolver(operaciones.conflictos());
            case HISTORIAL->paginaHistorial;case DEPURACION->paginaDepuracion;
        };
        contenido.setContent(vista);contenido.setVvalue(0);aviso.setText("");
        titulo.setText(switch(destino){
            case MONITOREO->"Panel de Control General";case PEDIDO->"Nuevo pedido";case HISTORIAL->"Historial de pedidos";
            case INVENTARIO->"Almacén y existencias";case RECEPCION->"Recepción de mercadería";
            case CONFLICTOS->"Resolución de conflictos";case DEPURACION->"Depuración de recursos";
        });
        visible(controlesMonitoreo,destino==Pagina.MONITOREO);visible(estado,destino==Pagina.MONITOREO);
        visible(controlesInventario,destino==Pagina.INVENTARIO);visible(controlesAdmin,destino==Pagina.MONITOREO);
        visible(barraCapacidad,destino==Pagina.MONITOREO);visible(capacidad,destino==Pagina.MONITOREO);visible(entorno,destino==Pagina.MONITOREO);
        pie.setText(destino==Pagina.MONITOREO?"CAPACIDAD ALMACÉN:":"REDXELA");
        for(Button boton:new Button[]{navMonitoreo,navPedidos,navInventario,navAdmin})boton.getStyleClass().remove("seleccionado");
        submenus.values().forEach(b->b.getStyleClass().remove("seleccionado"));
        Button principal=switch(destino){
            case MONITOREO->navMonitoreo;case PEDIDO,HISTORIAL->navPedidos;
            case INVENTARIO,RECEPCION->navInventario;case CONFLICTOS,DEPURACION->navAdmin;
        };
        principal.getStyleClass().add("seleccionado");
        if(submenus.containsKey(destino))submenus.get(destino).getStyleClass().add("seleccionado");
        actualizar();
    }
    private VBox envolver(Node panel){
        VBox contenedor=new VBox(panel);contenedor.getStyleClass().add("pagina");
        if(panel instanceof Region region)region.setMaxWidth(900);
        return contenedor;
    }
    private void activar(boolean activa){
        centro.motor.activar(activa);
        if(activa)pulso.play();else{pulso.stop();estado.setOpacity(1);}
        actualizar();
    }
    private void visible(Node nodo,boolean mostrar){nodo.setVisible(mostrar);nodo.setManaged(mostrar);}
    @FXML private void reanudar(){activar(true);}
    @FXML private void pausar(){activar(false);}
    @FXML private void modoEstandar(){centro.estres(false);estandar.setSelected(true);}
    @FXML private void modoEstres(){centro.estres(true);estres.setSelected(true);}
    @FXML private void paso(){
        try{centro.motor.paso();aviso.setText("Avance de un minuto simulado.");}
        catch(IllegalArgumentException ex){aviso.setText(ex.getMessage());}
        actualizar();
    }
    private void actualizar(){
        boolean activa=centro.motor.activa();estado.setText(activa?(centro.estres()?"STRESS TEST ACTIVO":"SIMULACIÓN ACTIVA"):"SIMULACIÓN PAUSADA");
        estado.getStyleClass().setAll("estado-sesion",activa?"exito":"muted");
        iniciar.setDisable(activa);pausar.setDisable(!activa);avanzar.setDisable(activa);
        var resumen=centro.almacen.resumen();double porcentaje=(double)resumen.unidades()/resumen.capacidad();
        capacidad.setText("%.0f%%".formatted(porcentaje*100));barraCapacidad.setProgress(porcentaje);
        switch(pagina){
            case MONITOREO->monitoreo.actualizar();case INVENTARIO->inventario.actualizar();
            case PEDIDO,RECEPCION,CONFLICTOS->operaciones.actualizar();
            case HISTORIAL->actualizarHistorial();case DEPURACION->actualizarRegistro();
        }
    }
    private void crearHistorial(){
        tablaPedidos=new TableView<>();
        tablaPedidos.getColumns().add(columna("ID del pedido",v->centro.identificador(v.pedido()),150));
        tablaPedidos.getColumns().add(columna("Cliente",v->centro.clientes.buscarPorId(v.pedido().clienteId()).orElseThrow().nombre(),180));
        tablaPedidos.getColumns().add(columna("Servicio",v->v.pedido().nivelServicio().toString(),120));
        tablaPedidos.getColumns().add(columna("Estado",v->v.estado().toString(),130));
        tablaPedidos.getColumns().add(columna("Unidades",v->Integer.toString(v.pedido().getTotalArticulos()),80));
        tablaPedidos.getColumns().add(columna("Productos",v->v.pedido().detalles().stream().map(d->d.producto().nombre()+", "+d.cantidad()+" unidades").collect(java.util.stream.Collectors.joining("; ")),270));
        tablaPedidos.setPrefHeight(500);tablaPedidos.setPlaceholder(texto("Todavía no se han registrado pedidos.","muted"));
        paginaHistorial=new VBox(20,tablaPedidos);paginaHistorial.getStyleClass().add("pagina");
    }
    private void actualizarHistorial(){
        var seleccion=tablaPedidos.getSelectionModel().getSelectedItem();
        tablaPedidos.getItems().setAll(centro.motor.pedidos());
        if(seleccion!=null)tablaPedidos.getItems().stream().filter(p->p.pedido().id().equals(seleccion.pedido().id()))
                .findFirst().ifPresent(p->tablaPedidos.getSelectionModel().select(p));
    }
    private void crearDepuracion(){
        registro=new TextArea();registro.setEditable(false);registro.setPrefHeight(460);registro.getStyleClass().add("mono");
        Label resultado=parrafo("","muted");
        Button provocar=boton("Simular espera circular","boton-secundario",()->{
            try{centro.conflictos.provocar();mensaje(resultado,"Escenario iniciado. Consulta Resolución de conflictos.",false);}
            catch(IllegalArgumentException ex){mensaje(resultado,ex.getMessage(),true);}
        });
        paginaDepuracion=new VBox(16,registro,fila(provocar,boton("Ver conflictos","boton-enlace",this::administrar)),resultado);
        paginaDepuracion.getStyleClass().add("pagina");
    }
    private void actualizarRegistro(){
        StringBuilder texto=new StringBuilder("RECURSOS COMPARTIDOS\n");
        centro.recursos.estados().forEach(r->texto.append(r.tipo()).append(" ").append(r.numero()).append(": ")
                .append(r.disponible()?"Disponible":r.titular()).append("\n"));
        texto.append("\nRECEPCIÓN\n");centro.recepcion.historial().forEach(r->texto.append(r.entrada().id()).append(": ").append(r.estado()).append("\n"));
        texto.append("\nCONFLICTOS\n").append(centro.conflictos.vista().mensaje());
        if(!registro.getText().equals(texto.toString())){int posicion=registro.getCaretPosition();double scroll=registro.getScrollTop();registro.setText(texto.toString());registro.positionCaret(Math.min(posicion,registro.getLength()));registro.setScrollTop(scroll);}
    }
    @Override public void close(){
        if(refresco!=null)refresco.stop();if(pulso!=null)pulso.stop();
        if(monitoreo!=null)monitoreo.detenerAnimaciones();centro.close();
    }
}
