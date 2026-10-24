package gt.edu.cunoc.redxela.controlador;

import gt.edu.cunoc.redxela.catalogo.CatalogoProductos;
import gt.edu.cunoc.redxela.modelo.*;
import gt.edu.cunoc.redxela.servicio.ContextoCentro;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import org.kordamp.ikonli.feather.Feather;
import static gt.edu.cunoc.redxela.controlador.Componentes.*;

/** Mantiene un borrador de varios productos sin alterar el inventario. */
final class VistaPedido extends VBox {
    private final ContextoCentro centro;
    private final TextField id=new TextField(), cantidad=new TextField("1");
    private final ComboBox<Cliente> cliente=new ComboBox<>();
    private final ComboBox<Producto> producto=new ComboBox<>();
    private final Label servicio=texto("Selecciona una cuenta","dato-cian"), total=texto("0 productos, 0 unidades","dato-cian"),
            existencias=texto("","muted"), aviso=parrafo("","muted");
    private final TableView<DetallePedido> detalles=new TableView<>();

    VistaPedido(ContextoCentro centro,Runnable registrarCliente){
        this.centro=centro;getStyleClass().add("pagina");
        id.setText(centro.siguienteIdManual());cliente.setPromptText("Selecciona una cuenta registrada");cliente.setMaxWidth(Double.MAX_VALUE);
        cliente.valueProperty().addListener((o,a,b)->servicio.setText(b==null?"Selecciona una cuenta":"N"+b.nivelServicio().getPrioridad()+" - "+b.nivelServicio()));
        producto.getItems().setAll(CatalogoProductos.getProductos());producto.getSelectionModel().selectFirst();producto.setMaxWidth(Double.MAX_VALUE);
        producto.valueProperty().addListener((o,a,b)->actualizarExistencias());
        cantidad.setPrefWidth(100);cantidad.setMaxWidth(120);
        HBox cuenta=fila(cliente,boton("Registrar cliente","boton-secundario",registrarCliente));HBox.setHgrow(cliente,Priority.ALWAYS);
        HBox seleccion=fila(producto,cantidad,boton("Guardar producto","boton-secundario",this::guardarProducto));HBox.setHgrow(producto,Priority.ALWAYS);
        detalles.getColumns().add(columna("Producto",d->d.producto().nombre(),350));
        detalles.getColumns().add(columna("Tipo de mercancía",d->d.producto().tipoMercancia().toString(),210));
        detalles.getColumns().add(columna("Unidades",d->Integer.toString(d.cantidad()),100));
        detalles.setPrefHeight(200);detalles.setPlaceholder(texto("Agrega los productos del pedido.","muted"));
        detalles.getSelectionModel().selectedItemProperty().addListener((o,a,b)->{
            if(b!=null){producto.setValue(b.producto());cantidad.setText(Integer.toString(b.cantidad()));}
        });
        Button quitar=boton("Quitar producto","boton-secundario",()->{detalles.getItems().remove(detalles.getSelectionModel().getSelectedItem());actualizarTotal();});
        quitar.disableProperty().bind(detalles.getSelectionModel().selectedItemProperty().isNull());
        Button enviar=boton("REGISTRAR PEDIDO","boton-primario",this::registrar);enviar.setMaxWidth(Double.MAX_VALUE);
        VBox formulario=tarjeta(tituloIcono(Feather.SHOPPING_CART,"NUEVO PEDIDO","titulo-tarjeta"),new Separator(),
                new VBox(8,texto("ID DEL PEDIDO","etiqueta"),id),new VBox(8,texto("CLIENTE","etiqueta"),cuenta),
                new VBox(8,texto("SERVICIO CONTRATADO","etiqueta"),servicio),
                new VBox(8,texto("PRODUCTO Y CANTIDAD","etiqueta"),seleccion,existencias),
                detalles,fila(quitar,espacio(),total),enviar,aviso);
        formulario.setMaxWidth(900);getChildren().add(formulario);actualizar();
    }
    private void guardarProducto(){
        try{
            if(producto.getValue()==null)throw new IllegalArgumentException("Selecciona un producto.");
            int unidades;
            try{unidades=Integer.parseInt(cantidad.getText().strip());}catch(NumberFormatException ex){throw new IllegalArgumentException("Ingresa una cantidad entera positiva.");}
            DetallePedido nuevo=new DetallePedido(producto.getValue(),unidades);
            long suma=detalles.getItems().stream().filter(d->!d.producto().equals(nuevo.producto())).mapToLong(DetallePedido::cantidad).sum()+unidades;
            if(suma>Integer.MAX_VALUE)throw new IllegalArgumentException("La cantidad total de artículos es demasiado grande.");
            int indice=-1;for(int i=0;i<detalles.getItems().size();i++)if(detalles.getItems().get(i).producto().equals(nuevo.producto()))indice=i;
            if(indice<0)detalles.getItems().add(nuevo);else detalles.getItems().set(indice,nuevo);
            actualizarTotal();mensaje(aviso,"Producto guardado. Guardar nuevamente el mismo producto actualiza su cantidad.",false);
        }catch(IllegalArgumentException ex){mensaje(aviso,ex.getMessage(),true);}
    }
    private void registrar(){
        try{
            if(cliente.getValue()==null)throw new IllegalArgumentException("Selecciona una cuenta registrada.");
            Pedido pedido=centro.registrarManual(id.getText(),cliente.getValue().id(),java.util.List.copyOf(detalles.getItems()));
            detalles.getItems().clear();actualizarTotal();id.setText(centro.siguienteIdManual());cantidad.setText("1");
            mensaje(aviso,"Pedido "+centro.identificador(pedido)+" registrado con "+pedido.getTotalArticulos()+" unidades.",false);
        }catch(IllegalArgumentException ex){mensaje(aviso,ex.getMessage(),true);}
    }
    private void actualizarTotal(){total.setText(detalles.getItems().size()+" productos, "+detalles.getItems().stream().mapToLong(DetallePedido::cantidad).sum()+" unidades");}
    private void actualizarExistencias(){
        Producto p=producto.getValue();existencias.setText(p==null?"":"Disponible: "+centro.almacen.disponible(p)+" unidades. Los pedidos sin stock suficiente quedan en espera.");
    }
    void seleccionarCliente(Cliente cuenta){actualizar();cliente.setValue(cuenta);}
    void actualizar(){
        var cuentas=centro.clientes.getClientes();
        if(!cliente.getItems().equals(cuentas)){Cliente seleccionado=cliente.getValue();cliente.getItems().setAll(cuentas);cliente.setValue(seleccionado);}
        actualizarExistencias();
    }
}
