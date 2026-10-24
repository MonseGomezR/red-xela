package gt.edu.cunoc.redxela.controlador;

import gt.edu.cunoc.redxela.modelo.*;
import gt.edu.cunoc.redxela.servicio.ContextoCentro;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import java.util.function.Consumer;
import org.kordamp.ikonli.feather.Feather;
import static gt.edu.cunoc.redxela.controlador.Componentes.*;

/** Registro explícito de cuentas y elección del servicio contratado. */
final class VistaClientes extends VBox {
    private final ContextoCentro centro;
    private final TextField nombre=new TextField(), correo=new TextField();
    private final ComboBox<NivelServicio> nivel=new ComboBox<>();
    private final TableView<Cliente> cuentas=new TableView<>();
    private final Label aviso=parrafo("","muted");

    VistaClientes(ContextoCentro centro,Consumer<Cliente> usarCuenta){
        this.centro=centro;getStyleClass().add("pagina");setSpacing(24);
        nombre.setPromptText("Nombre completo");correo.setPromptText("Correo electrónico");
        nivel.getItems().setAll(NivelServicio.values());nivel.setValue(NivelServicio.ESTANDAR);nivel.setMaxWidth(Double.MAX_VALUE);
        VBox formulario=tarjeta(tituloIcono(Feather.USER,"REGISTRAR CLIENTE","titulo-tarjeta"),
                new VBox(8,texto("NOMBRE","etiqueta"),nombre),new VBox(8,texto("CORREO ELECTRÓNICO","etiqueta"),correo),
                new VBox(8,texto("SERVICIO CONTRATADO","etiqueta"),nivel),
                boton("Registrar cuenta","boton-primario",this::registrar),aviso);
        formulario.setMaxWidth(Double.MAX_VALUE);
        cuentas.getColumns().add(columna("Cliente",Cliente::nombre,260));
        cuentas.getColumns().add(columna("Correo electrónico",Cliente::correo,300));
        cuentas.getColumns().add(columna("Servicio contratado",c->c.nivelServicio().toString(),180));
        cuentas.setPrefHeight(390);cuentas.setPlaceholder(texto("No hay cuentas registradas.","muted"));
        cuentas.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        Button usar=boton("Crear pedido con esta cuenta","boton-secundario",()->{
            Cliente cliente=cuentas.getSelectionModel().getSelectedItem();if(cliente!=null)usarCuenta.accept(cliente);
        });usar.disableProperty().bind(cuentas.getSelectionModel().selectedItemProperty().isNull());
        VBox listado=new VBox(18,texto("CUENTAS REGISTRADAS","titulo-tarjeta"),cuentas,usar);
        getChildren().add(columnas(listado,formulario,62,850));actualizar();
    }
    private void registrar(){
        try{
            if(nivel.getValue()==null)throw new IllegalArgumentException("Selecciona el servicio contratado.");
            Cliente cliente=centro.clientes.registrar(nombre.getText(),correo.getText(),nivel.getValue());
            actualizar();cuentas.getSelectionModel().select(cliente);cuentas.scrollTo(cliente);
            nombre.clear();correo.clear();mensaje(aviso,"Cuenta registrada. Ya puedes utilizarla en un pedido.",false);
        }catch(IllegalArgumentException ex){mensaje(aviso,ex.getMessage(),true);}
    }
    void actualizar(){
        var lista=centro.clientes.getClientes();
        if(!cuentas.getItems().equals(lista)){
            Cliente seleccion=cuentas.getSelectionModel().getSelectedItem();cuentas.getItems().setAll(lista);
            if(seleccion!=null)cuentas.getSelectionModel().select(seleccion);
        }
    }
}
