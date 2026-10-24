package gt.edu.cunoc.redxela.controlador;

import gt.edu.cunoc.redxela.catalogo.CatalogoProductos;
import gt.edu.cunoc.redxela.modelo.*;
import gt.edu.cunoc.redxela.servicio.*;
import javafx.collections.FXCollections;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import java.util.*;
import static gt.edu.cunoc.redxela.controlador.Componentes.*;

final class VistaAdministracion extends VBox {
    private final ContextoCentro centro;
    private final ComboBox<Cliente> cliente=new ComboBox<>();
    private final ComboBox<Producto> producto=new ComboBox<>(), recibido=new ComboBox<>();
    private final TextField cantidad=new TextField("12"), lote=new TextField("50");
    private final ComboBox<Integer> muelle=new ComboBox<>();
    private final Label servicio=texto("", "dato-cian"), mensajePedido=parrafo("", "muted"), mensajeEntrada=parrafo("", "muted"),
            conflicto=parrafo("", "muted"), alertaEspera=parrafo("", "dato-amarillo");
    private final ListView<DetallePedido> detalles=new ListView<>(FXCollections.observableArrayList());
    private final Button resolverA, resolverB, provocar;
    private final VBox entradas=new VBox(8);
    private UUID ultimaEntrada;
    private String ultimaRespuesta="";

    VistaAdministracion(ContextoCentro centro) {
        this.centro=centro; setSpacing(22); getStyleClass().add("pagina");
        CheckBox automaticos=new CheckBox("Llegadas automáticas"); automaticos.setSelected(centro.automatico());
        automaticos.setOnAction(e -> centro.automatico(automaticos.isSelected()));
        ToggleButton estres=new ToggleButton("STRESS TEST"); estres.getStyleClass().add("modo-estres");
        estres.setOnAction(e -> centro.estres(estres.isSelected()));
        getChildren().add(fila(texto("INYECCIÓN Y CONTROL DE CONFLICTOS", "etiqueta"),espacio(),automaticos,estres));
        cliente.getItems().setAll(centro.clientes.getClientes()); cliente.getSelectionModel().select(2); cliente.setMaxWidth(Double.MAX_VALUE);
        servicio.setText(cliente.getValue().nivelServicio().toString());
        cliente.valueProperty().addListener((o,a,b)->servicio.setText(b==null?"Sin cuenta":b.nivelServicio().toString()));
        producto.getItems().setAll(CatalogoProductos.getProductos()); producto.getSelectionModel().selectFirst(); producto.setMaxWidth(Double.MAX_VALUE);
        FlowPane tipos=new FlowPane(6,6); ToggleGroup grupo=new ToggleGroup();
        for(TipoMercancia tipo:TipoMercancia.values()) {
            ToggleButton boton=new ToggleButton(tipo.toString()); boton.setToggleGroup(grupo); boton.getStyleClass().add("tipo-mercancia");
            boton.setOnAction(e->{
                var seleccion=boton.isSelected()?CatalogoProductos.getProductos().stream().filter(p->p.tipoMercancia()==tipo).toList():CatalogoProductos.getProductos();
                producto.getItems().setAll(seleccion); producto.getSelectionModel().selectFirst();
            }); tipos.getChildren().add(boton);
        }
        Slider deslizador=new Slider(1,100,12); deslizador.setBlockIncrement(1); deslizador.setMajorTickUnit(1); deslizador.setSnapToTicks(true);
        deslizador.valueProperty().addListener((o,a,b)->cantidad.setText(Integer.toString((int)Math.round(b.doubleValue()))));
        cantidad.setPrefWidth(75); cantidad.setMaxWidth(90);
        detalles.setPrefHeight(110); detalles.setPlaceholder(texto("Agrega uno o más productos al pedido.","muted"));
        detalles.setCellFactory(lista->new ListCell<>() {
            @Override protected void updateItem(DetallePedido item,boolean vacia) {
                super.updateItem(item,vacia); setText(vacia||item==null?null:item.producto().nombre()+" · "+item.cantidad()+"u · "+item.producto().tipoMercancia());
            }
        });
        Label total=texto("0 artículos", "dato-cian");
        detalles.getItems().addListener((javafx.collections.ListChangeListener<DetallePedido>)e->total.setText(
                detalles.getItems().stream().mapToLong(DetallePedido::cantidad).sum()+" artículos"));
        Button inyectar=boton("INYECTAR PEDIDO AL SISTEMA", "boton-primario",this::inyectar); inyectar.setMaxWidth(Double.MAX_VALUE);
        VBox manual=tarjeta(texto("▣  CREAR PEDIDO MANUAL","titulo-tarjeta"),new Separator(),
                fila(texto("ID PEDIDO · AUTOGENERADO","etiqueta"),espacio(),servicio),
                texto("CLIENTE / CUENTA", "etiqueta"),cliente,
                boton("+ Registrar nueva cuenta","boton-enlace",this::nuevaCuenta),
                texto("TIPO DE MERCANCÍA","etiqueta"),tipos,producto,
                texto("CANTIDAD DE UNIDADES","etiqueta"),deslizador,
                fila(cantidad,texto("1u — 100u en el control deslizante", "muted")),
                fila(boton("Agregar producto","boton-secundario",this::agregar),
                        boton("Quitar","boton-secundario",()->detalles.getItems().remove(detalles.getSelectionModel().getSelectedItem())),espacio(),total),
                detalles,inyectar,mensajePedido);
        provocar=boton("Provocar espera circular","boton-secundario",()->{
            try{centro.conflictos.provocar();conflicto.getStyleClass().setAll("muted");}catch(IllegalArgumentException ex){mensaje(conflicto,ex.getMessage(),true);}
        });
        resolverA=boton("CONTINUAR A", "boton-rojo",()->resolver(true));
        resolverB=boton("CONTINUAR B", "boton-secundario",()->resolver(false));
        VBox monitor=tarjeta(fila(texto("⚠  MONITOR DE DEADLOCKS","titulo-tarjeta"),espacio()),new Separator(),
                conflicto,parrafo("A: Montacargas 1 → Escáner compartido\nB: Escáner compartido → Montacargas 1", "mono"),
                fila(resolverA,resolverB),provocar,new Separator(),alertaEspera,
                boton("Aumentar prioridad del más antiguo","boton-amarillo",this::priorizar));
        monitor.getStyleClass().add("conflicto");
        recibido.getItems().setAll(CatalogoProductos.getProductos()); recibido.getSelectionModel().selectLast(); recibido.setMaxWidth(Double.MAX_VALUE);
        muelle.getItems().setAll(1,2); muelle.setValue(1); muelle.setMaxWidth(Double.MAX_VALUE); lote.setPrefWidth(100);
        VBox campoCantidad=new VBox(8,texto("CANTIDAD (LOTE)","etiqueta"),lote);
        VBox campoMuelle=new VBox(8,texto("MUELLE DE CARGA","etiqueta"),muelle);
        HBox campos=fila(campoCantidad,campoMuelle); HBox.setHgrow(campoCantidad,Priority.ALWAYS); HBox.setHgrow(campoMuelle,Priority.ALWAYS);
        Button entrada=boton("REGISTRAR ENTRADA", "boton-secundario",()->{
            try{ultimaEntrada=centro.recepcion.enviar(recibido.getValue(),entero(lote),muelle.getValue());
                mensaje(mensajeEntrada,"Entrada enviada a inventario. Esperando el escáner y el muelle "+muelle.getValue()+".",false);
            }catch(IllegalArgumentException ex){mensaje(mensajeEntrada,ex.getMessage(),true);}
        }); entrada.setMaxWidth(Double.MAX_VALUE);
        VBox recepcion=tarjeta(texto("↳  LLEGADA DE MERCADERÍA","titulo-tarjeta"),texto("SELECCIONAR PRODUCTO","etiqueta"),
                recibido,campos,entrada,mensajeEntrada,entradas);
        getChildren().add(columnas(manual,new VBox(22,monitor,recepcion),50,900)); actualizar();
    }
    private int entero(TextField campo) {
        try{int valor=Integer.parseInt(campo.getText().strip()); if(valor<=0)throw new NumberFormatException(); return valor;}
        catch(NumberFormatException ex){throw new IllegalArgumentException("Ingresa una cantidad entera positiva.");}
    }
    private void agregar() {
        try{
            Producto p=producto.getValue(); if(p==null)throw new IllegalArgumentException("Selecciona un producto.");
            int cantidadNueva=entero(cantidad); long total=detalles.getItems().stream().mapToLong(DetallePedido::cantidad).sum()+cantidadNueva;
            if(total>Integer.MAX_VALUE)throw new IllegalArgumentException("La cantidad total es demasiado grande.");
            for(int i=0;i<detalles.getItems().size();i++) {
                var anterior=detalles.getItems().get(i);
                if(anterior.producto().equals(p)) {detalles.getItems().set(i,new DetallePedido(p,anterior.cantidad()+cantidadNueva));mensaje(mensajePedido,"Unidades sumadas al producto.",false);return;}
            }
            detalles.getItems().add(new DetallePedido(p,cantidadNueva)); mensaje(mensajePedido,"Producto agregado.",false);
        }catch(IllegalArgumentException ex){mensaje(mensajePedido,ex.getMessage(),true);}
    }
    private void inyectar() {
        try{
            if(cliente.getValue()==null)throw new IllegalArgumentException("Selecciona una cuenta de cliente.");
            Pedido pedido=centro.registrar(cliente.getValue().id(),List.copyOf(detalles.getItems()));
            detalles.getItems().clear(); mensaje(mensajePedido,"Pedido #"+VistaMonitoreo.corto(pedido)+" registrado con servicio "+pedido.nivelServicio()+".",false);
        }catch(IllegalArgumentException ex){mensaje(mensajePedido,ex.getMessage(),true);}
    }
    private void nuevaCuenta() {
        TextField nombre=new TextField(),correo=new TextField(); ComboBox<NivelServicio> nivel=new ComboBox<>();
        nombre.setPromptText("Nombre del cliente");correo.setPromptText("nombre@dominio.com");nivel.getItems().setAll(NivelServicio.values());nivel.setValue(NivelServicio.ESTANDAR);
        Label aviso=parrafo("El servicio de la cuenta se aplicará a sus nuevos pedidos.","muted");
        VBox formulario=tarjeta(texto("NUEVA CUENTA","titulo-tarjeta"),nombre,correo,nivel,aviso);
        formulario.setPrefWidth(430);
        formulario.getChildren().add(boton("Crear cuenta","boton-primario",()->{
            try{Cliente nuevo=centro.clientes.registrar(nombre.getText(),correo.getText(),nivel.getValue());
                cliente.getItems().setAll(centro.clientes.getClientes());cliente.setValue(nuevo);nombre.clear();correo.clear();
                mensaje(aviso,"Cuenta creada y seleccionada. Puedes cerrar esta ventana.",false);
            }catch(IllegalArgumentException ex){mensaje(aviso,ex.getMessage(),true);}
        })); dialogo("Cuenta de cliente",formulario,this);
    }
    private void resolver(boolean a) {
        try{centro.conflictos.resolver(a);conflicto.getStyleClass().setAll("muted");}catch(IllegalArgumentException ex){mensaje(conflicto,ex.getMessage(),true);}
    }
    private void priorizar() {
        var antiguo=centro.motor.espera().stream().max(Comparator.comparingDouble(MotorSimulacion.VistaPedido::espera));
        if(antiguo.isEmpty()){alertaEspera.setText("No hay pedidos en espera.");return;}
        try{centro.motor.priorizar(antiguo.get().pedido().id()); alertaEspera.setText("Prioridad manual aplicada al pedido más antiguo.");}
        catch(IllegalArgumentException ex){alertaEspera.setText(ex.getMessage());}
    }
    void actualizar() {
        var vista=centro.conflictos.vista();
        if(vista.activo()||vista.recuperando()||!conflicto.getStyleClass().contains("error"))conflicto.setText(vista.mensaje());
        resolverA.setDisable(!vista.listo());resolverB.setDisable(!vista.listo());provocar.setDisable(vista.activo()||vista.recuperando());
        var fueraDeTiempo=centro.motor.espera().stream().filter(p->p.espera()>
                p.pedido().nivelServicio().getEsperaObjetivo().map(d->(double)d.toMinutes()).orElse(1.0)).findFirst();
        if(fueraDeTiempo.isPresent())alertaEspera.setText("TIEMPO OBJETIVO SUPERADO · Pedido #"+VistaMonitoreo.corto(fueraDeTiempo.get().pedido()));
        else alertaEspera.setText("Sin alertas por tiempo de espera.");
        var historial=centro.recepcion.historial();
        for(var resultado:historial)if(resultado.entrada().id().equals(ultimaEntrada)&&!resultado.estado().equals(ultimaRespuesta)){
            ultimaRespuesta=resultado.estado();mensaje(mensajeEntrada,resultado.estado(),resultado.estado().startsWith("Rechazado"));
        }
        entradas.getChildren().clear();
        historial.stream().skip(Math.max(0,historial.size()-3)).forEach(r->entradas.getChildren().add(parrafo(
                r.entrada().producto().nombre()+" · "+r.entrada().cantidad()+"u · "+r.estado(),"muted")));
    }
    void enfocarRecepcion() { recibido.requestFocus(); }
}
