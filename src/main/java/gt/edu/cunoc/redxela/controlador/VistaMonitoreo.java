package gt.edu.cunoc.redxela.controlador;

import gt.edu.cunoc.redxela.modelo.*;
import gt.edu.cunoc.redxela.servicio.*;
import java.util.*;
import javafx.animation.FadeTransition;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.util.Duration;
import org.kordamp.ikonli.feather.Feather;
import static gt.edu.cunoc.redxela.controlador.Componentes.*;

final class VistaMonitoreo extends VBox {
    private final ContextoCentro centro;
    private final Label completados=texto("0","kpi-valor"), espera=texto("0.0m","kpi-valor"),
            ocupados=texto("0/26","kpi-valor"), deadlocks=texto("00","kpi-valor"), media=texto("0%","dato-cian"),
            mensajeConflicto=parrafo("","muted"), totalActivos=texto("0 pedidos activos","muted"),
            tituloConflicto=tituloIcono(Feather.ALERT_CIRCLE,"MONITOR DE DEADLOCKS","dato-rojo");
    private final VBox activos=new VBox(18), cola=new VBox(20);
    private final Label avisoInventario=parrafo("","dato-amarillo");
    private final List<RecursoVisual> recursos=new ArrayList<>();
    private final Button verTodos;
    private record RecursoVisual(TipoRecurso tipo,int numero,VBox tarjeta,Label titular,Label detalle,FadeTransition pulso,ProgressIndicator progreso){}

    VistaMonitoreo(ContextoCentro centro,Runnable administrar,Runnable historial) {
        this.centro=centro;setSpacing(30);getStyleClass().add("pagina");
        GridPane indicadores=new GridPane();indicadores.setHgap(24);indicadores.setVgap(20);
        indicadores.getChildren().addAll(kpi(Feather.CHECK_CIRCLE,completados,"PEDIDOS COMPLETADOS",texto("Sesión actual","exito"),"cian"),
                kpi(Feather.CLOCK,espera,"TIEMPO PROM. ESPERA",texto("Promedio","dato-amarillo"),"azul"),
                kpi(Feather.USERS,ocupados,"RECURSOS EN USO",media,"violeta"),
                kpi(Feather.LINK_2,deadlocks,"DEADLOCKS DETECTADOS",texto("Espera circular","dato-rojo"),"rojo"));
        indicadores.widthProperty().addListener((o,a,w)->{
            int n=w.doubleValue()<750?2:4;indicadores.getColumnConstraints().clear();
            for(int i=0;i<n;i++){ColumnConstraints c=new ColumnConstraints();c.setPercentWidth(100.0/n);c.setMinWidth(0);indicadores.getColumnConstraints().add(c);}
            for(int i=0;i<4;i++){GridPane.setColumnIndex(indicadores.getChildren().get(i),i%n);GridPane.setRowIndex(indicadores.getChildren().get(i),i/n);}
        });
        GridPane grid=new GridPane();grid.setHgap(14);grid.setVgap(14);
        List<int[]> posiciones=new ArrayList<>();
        agregar(grid,TipoRecurso.ESTACION_EMPAQUE,1,0,0,1);agregar(grid,TipoRecurso.ESTACION_EMPAQUE,2,1,0,1);
        agregar(grid,TipoRecurso.MONTACARGAS,1,2,0,1);agregar(grid,TipoRecurso.MONTACARGAS,2,3,0,1);
        agregar(grid,TipoRecurso.CONTROL_CALIDAD,1,0,1,1);agregar(grid,TipoRecurso.ESCANER,1,1,1,2);agregar(grid,TipoRecurso.AREA_CARGA,1,3,1,1);
        int celda=8;
        for(TipoRecurso t:TipoRecurso.values())for(int i=1;i<=t.getCapacidad();i++){
            int numero=i;if(recursos.stream().anyMatch(r->r.tipo()==t&&r.numero()==numero))continue;
            agregar(grid,t,i,celda%4,celda/4,1);celda++;
        }
        // Conserva el orden de lectura y reduce la cuadrícula en ventanas estrechas.
        for(var n:grid.getChildren())posiciones.add(new int[]{GridPane.getColumnIndex(n),GridPane.getRowIndex(n),GridPane.getColumnSpan(n)});
        grid.widthProperty().addListener((o,a,w)->{
            boolean estrecho=w.doubleValue()<520;int n=estrecho?2:4;grid.getColumnConstraints().clear();
            for(int i=0;i<n;i++){ColumnConstraints c=new ColumnConstraints();c.setPercentWidth(100.0/n);c.setMinWidth(0);grid.getColumnConstraints().add(c);}
            for(int i=0;i<grid.getChildren().size();i++){
                var nodo=grid.getChildren().get(i);int[] p=posiciones.get(i);
                GridPane.setColumnIndex(nodo,estrecho?i%2:p[0]);GridPane.setRowIndex(nodo,estrecho?i/2:p[1]);GridPane.setColumnSpan(nodo,estrecho?1:p[2]);
            }
        });
        ScrollPane estados=desplazable(grid);estados.setPrefHeight(252);estados.setMinHeight(252);
        ComboBox<PoliticaAtencion> politica=new ComboBox<>();politica.getItems().setAll(PoliticaAtencion.values());politica.setValue(centro.motor.politica());
        politica.setPrefWidth(110);politica.setMaxWidth(110);politica.getStyleClass().add("politica");politica.setOnAction(e->centro.motor.politica(politica.getValue()));
        verTodos=boton("VER TODOS LOS PEDIDOS (0)","boton-enlace",historial);verTodos.setMaxWidth(Double.MAX_VALUE);
        Region relleno=new Region();VBox.setVgrow(relleno,Priority.ALWAYS);
        VBox lista=tarjeta(fila(parrafo("LISTA DE ESPERA","titulo-tarjeta"),espacio(),politica),new Separator(),cola,relleno,new Separator(),verTodos);lista.setMinHeight(410);
        Button resolver=boton("Resolver Manualmente","boton-rojo",administrar);resolver.setMaxWidth(Double.MAX_VALUE);
        VBox conflicto=tarjeta(tituloConflicto,mensajeConflicto,resolver);conflicto.getStyleClass().add("conflicto");
        VBox izquierda=new VBox(24,fila(tituloIcono(Feather.CPU,"Estado de Recursos Compartidos","titulo-seccion"),espacio(),
                tituloIcono(Feather.CIRCLE,"DISPONIBLE","leyenda-cian"),tituloIcono(Feather.CIRCLE,"OCUPADO","leyenda-amarilla")),estados,
                tarjeta(fila(parrafo("PEDIDOS EN PROCESAMIENTO ACTIVO","titulo-tarjeta"),espacio(),totalActivos),new Separator(),activos));
        getChildren().addAll(avisoInventario,indicadores,columnas(izquierda,new VBox(30,conflicto,lista),67,880));actualizar();
    }
    private VBox kpi(Feather simbolo,Label valor,String titulo,Label detalle,String color){
        Label icono=tituloIcono(simbolo,"","icono-kpi");icono.getStyleClass().add("tinte-"+color);
        VBox box=tarjeta(fila(icono,espacio(),detalle),valor,texto(titulo,"etiqueta"));box.getStyleClass().addAll("kpi","kpi-"+color);return box;
    }
    private void agregar(GridPane grid,TipoRecurso tipo,int numero,int columna,int fila,int ancho){
        Label simbolo=tituloIcono(iconoRecurso(tipo),"","simbolo-recurso"), titular=texto("DISPONIBLE","destacado"),detalle=parrafo("Listo para procesar","muted");
        Label badge=texto(nombre(tipo,numero),"badge");
        VBox card=tarjeta(Componentes.fila(simbolo,espacio(),badge),titular,detalle);card.setMinWidth(0);card.setMaxWidth(Double.MAX_VALUE);card.getStyleClass().add("recurso");
        ProgressIndicator progreso=null;
        if(tipo==TipoRecurso.ESCANER){
            progreso=new ProgressIndicator(0);progreso.setPrefSize(44,44);progreso.setMaxSize(44,44);
            HBox cuerpo=Componentes.fila(new VBox(7,titular,detalle),espacio(),progreso);
            card.getChildren().setAll(Componentes.fila(simbolo,texto(nombre(tipo,numero),"destacado")),cuerpo);
        }
        FadeTransition pulso=new FadeTransition(Duration.seconds(1.2),simbolo);pulso.setFromValue(1);pulso.setToValue(.35);pulso.setAutoReverse(true);pulso.setCycleCount(FadeTransition.INDEFINITE);
        recursos.add(new RecursoVisual(tipo,numero,card,titular,detalle,pulso,progreso));grid.add(card,columna,fila,ancho,1);
    }
    private static String nombre(TipoRecurso t,int n){return switch(t){
        case ESTACION_EMPAQUE->"EMPAQUE "+n;case MONTACARGAS->"MONTACARGAS "+n;case AREA_CARGA->"CARGA "+n;
        case ESCANER->"ESCÁNER COMPARTIDO";case CONTROL_CALIDAD->"QC "+(char)('A'+n-1);case ENCARGADO_BODEGA->"ENCARGADO "+n;
    };}
    private static Feather iconoRecurso(TipoRecurso t){return switch(t){
        case ESTACION_EMPAQUE->Feather.PACKAGE;case AREA_CARGA,MONTACARGAS->Feather.TRUCK;case ESCANER->Feather.COLUMNS;
        case CONTROL_CALIDAD->Feather.SHIELD;case ENCARGADO_BODEGA->Feather.USER;
    };}
    void actualizar(){
        var stats=centro.motor.estadisticas();completados.setText(Integer.toString(stats.completados()));espera.setText("%.1fm".formatted(stats.esperaMedia()));
        ocupados.setText(stats.recursosEnUso()+"/"+stats.recursosTotales());media.setText("%.0f%%".formatted(100.0*stats.recursosEnUso()/stats.recursosTotales()));
        var conflicto=centro.conflictos.vista();deadlocks.setText(conflicto.activo()?"01":"00");
        tituloConflicto.setText(conflicto.activo()?"DEADLOCK DETECTADO":"MONITOR DE DEADLOCKS");
        mensajeConflicto.setText(conflicto.activo()?"DL-A necesita Escáner compartido (retenido por DL-B).\n\nDL-B necesita Montacargas 1 (retenido por DL-A).":conflicto.mensaje());
        var estados=centro.recursos.estados();var pedidos=centro.motor.pedidos();
        Map<String,MotorSimulacion.VistaPedido> porTitular=new HashMap<>();
        pedidos.forEach(p->porTitular.put("P-"+p.pedido().id().toString().substring(0,8),p));
        for(var r:recursos){
            var estado=estados.stream().filter(e->e.tipo()==r.tipo()&&e.numero()==r.numero()).findFirst().orElseThrow();
            var pedido=porTitular.get(estado.titular());
            r.titular().setText(estado.disponible()?"DISPONIBLE":pedido==null?estado.titular():"Pedido #"+centro.identificador(pedido.pedido()));
            r.detalle().setText(estado.disponible()?"Listo para procesar":pedido==null?"Recurso retenido":pedido.fase());
            if(r.progreso()!=null)r.progreso().setProgress(estado.disponible()?0:pedido==null?-1:pedido.progreso());
            if(!estado.disponible()&&!r.tarjeta().getStyleClass().contains("activo")){r.tarjeta().getStyleClass().add("activo");r.pulso().play();}
            if(estado.disponible()&&r.tarjeta().getStyleClass().remove("activo")){r.pulso().stop();r.pulso().getNode().setOpacity(1);}
        }
        activos.getChildren().clear();var procesando=pedidos.stream().filter(p->p.estado()==EstadoPedido.EN_PROCESO).toList();totalActivos.setText(procesando.size()+" pedidos activos");
        if(procesando.isEmpty())activos.getChildren().add(parrafo("No hay pedidos en procesamiento.","muted"));
        for(var p:procesando){
            ProgressBar barra=new ProgressBar(p.progreso());barra.setMaxWidth(Double.MAX_VALUE);
            String nombre=centro.clientes.buscarPorId(p.pedido().clienteId()).orElseThrow().nombre();
            VBox datos=new VBox(7,fila(texto("#"+centro.identificador(p.pedido())+" - "+nombre,"destacado"),espacio(),texto("%.1fm restantes".formatted(p.restante()),"mono")),
                    texto(p.pedido().detalles().getFirst().producto().tipoMercancia()+", "+p.pedido().getTotalArticulos()+" UNIDADES","etiqueta"),barra);HBox.setHgrow(datos,Priority.ALWAYS);
            HBox iconos=new HBox(5);String titular="P-"+p.pedido().id().toString().substring(0,8);
            estados.stream().filter(e->titular.equals(e.titular())).map(GestorRecursos.Estado::tipo).distinct().forEach(t->iconos.getChildren().add(icono(iconoRecurso(t),14)));
            activos.getChildren().add(fila(texto("N"+p.pedido().nivelServicio().getPrioridad(),"nivel-pedido"),datos,new VBox(7,texto("RECURSOS","etiqueta"),iconos)));
        }
        cola.getChildren().clear();var pendientes=centro.motor.espera();verTodos.setText("VER TODOS LOS PEDIDOS ("+pedidos.size()+")");
        long sinStock=pendientes.stream().filter(p->p.estado()==EstadoPedido.ESPERANDO_INVENTARIO).count();
        avisoInventario.setVisible(sinStock>0);avisoInventario.setManaged(sinStock>0);
        avisoInventario.setText((sinStock==1?"Hay un pedido esperando existencias. ":"Hay "+sinStock+" pedidos esperando existencias. ")
                +"Registra una entrada en Inventario, Recibir mercadería.");
        if(pendientes.isEmpty())cola.getChildren().add(texto("La lista de espera está vacía.","muted"));
        for(var p:pendientes.stream().limit(6).toList()){
            VBox item=new VBox(6,fila(texto("Pedido #"+centro.identificador(p.pedido()),"destacado"),espacio(),texto(p.pedido().nivelServicio().toString(),"dato-cian")),
                    parrafo(p.pedido().detalles().getFirst().producto().tipoMercancia()+", "+p.pedido().getTotalArticulos()+" artículos, %.1fm en cola".formatted(p.espera()),"muted"),
                    texto(p.fase(),p.estado()==EstadoPedido.ESPERANDO_INVENTARIO?"dato-amarillo":"muted"));
            item.getStyleClass().add("fila-espera");cola.getChildren().add(item);
        }
    }
    static String corto(Pedido p){return p.id().toString().substring(0,8).toUpperCase(Locale.ROOT);}
    void detenerAnimaciones(){recursos.forEach(r->r.pulso().stop());}
}
