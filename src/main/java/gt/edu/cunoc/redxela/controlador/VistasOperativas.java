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

final class VistasOperativas {
    private final VBox recepcion, conflictos;
    private final ContextoCentro centro;
    private final TextField lote=new TextField();
    private final ComboBox<Producto> recibido=new ComboBox<>();
    private final ComboBox<Integer> muelle=new ComboBox<>();
    private final Label mensajeEntrada=parrafo("","muted"),
            conflicto=parrafo("","muted"), alertaEspera=parrafo("","muted"), cuentaConflictos=texto("0 ACTIVOS","badge-rojo"),
            tituloEspera=texto("TIEMPOS DE ESPERA","dato-amarillo"), tituloCiclo=texto("ESTADO DE CONFLICTOS","dato-rojo");
    private final Button terminarA, terminarB, aumentar;
    private UUID ultimaEntrada;

    VistasOperativas(ContextoCentro centro) {
        this.centro=centro;
        lote.setPromptText("Cantidad a recibir");
        terminarA=boton("CONTINUAR DL-A","boton-rojo",()->resolver(true));
        terminarB=boton("CONTINUAR DL-B","boton-rojo-contorno",()->resolver(false));
        VBox ciclo=tarjeta(tituloCiclo,conflicto,campos(terminarA,terminarB));
        ciclo.getStyleClass().add("alerta-conflicto");
        aumentar=boton("AUMENTAR PRIORIDAD MANUALMENTE","boton-amarillo",this::priorizar); aumentar.setMaxWidth(Double.MAX_VALUE);
        VBox inanicion=tarjeta(tituloEspera,alertaEspera,aumentar); inanicion.getStyleClass().add("alerta-inanicion");
        conflictos=tarjeta(fila(tituloIcono(Feather.LINK_2,"MONITOR DE DEADLOCKS","titulo-tarjeta"),espacio(),cuentaConflictos),ciclo,inanicion);
        conflictos.getStyleClass().add("monitor-admin");
        recibido.getItems().setAll(CatalogoProductos.getProductos()); recibido.getSelectionModel().selectFirst(); recibido.setMaxWidth(Double.MAX_VALUE);
        recibido.setConverter(new StringConverter<>() {
            public String toString(Producto p){return p==null?"":p.nombre()+" ("+p.tipoMercancia()+")";}
            public Producto fromString(String s){return null;}
        });
        muelle.getItems().setAll(1,2); muelle.setValue(1); muelle.setMaxWidth(Double.MAX_VALUE);
        muelle.setConverter(new StringConverter<>() {
            public String toString(Integer n){return n==null?"":"Área de Carga "+n;}
            public Integer fromString(String s){return null;}
        });
        Button entrada=boton("REGISTRAR ENTRADA","boton-secundario",()->{
            try { ultimaEntrada=centro.recepcion.enviar(recibido.getValue(),Integer.parseInt(lote.getText().strip()),muelle.getValue());
                mensaje(mensajeEntrada,"Entrada en cola de recepción.",false);
            } catch(NumberFormatException ex){mensaje(mensajeEntrada,"Ingresa una cantidad entera positiva.",true);}
            catch(IllegalArgumentException ex){mensaje(mensajeEntrada,ex.getMessage(),true);}
        }); entrada.setMaxWidth(Double.MAX_VALUE);
        recepcion=tarjeta(tituloIcono(Feather.TRUCK,"LLEGADA DE MERCADERÍA","titulo-tarjeta"),
                campo("SELECCIONAR PRODUCTO",recibido),campos(campo("CANTIDAD (LOTE)",lote),campo("MUELLE DE CARGA",muelle)),entrada,mensajeEntrada);
        actualizar();
    }
    VBox recepcion(){return recepcion;}
    VBox conflictos(){return conflictos;}
    private VBox campo(String nombre,javafx.scene.Node control){return new VBox(8,texto(nombre,"etiqueta"),control);}
    private HBox campos(Region... elementos) {
        HBox fila=new HBox(16,elementos);
        for(Region n:elementos){HBox.setHgrow(n,Priority.ALWAYS); n.setMaxWidth(Double.MAX_VALUE);n.setPrefWidth(0);}
        return fila;
    }
    private void resolver(boolean continuaA) {
        try{centro.conflictos.resolver(continuaA);}catch(IllegalArgumentException ex){mensaje(conflicto,ex.getMessage(),true);}
    }
    private Optional<MotorSimulacion.VistaPedido> demorado() {
        return centro.motor.espera().stream().filter(p->!p.preferente()).filter(p->p.espera()>
                p.pedido().nivelServicio().getEsperaObjetivo().map(d->(double)d.toMinutes()).orElse(1.0))
                .max(Comparator.comparingDouble(MotorSimulacion.VistaPedido::espera));
    }
    private void priorizar() { demorado().ifPresent(p->centro.motor.priorizar(p.pedido().id())); actualizar(); }
    void actualizar() {
        var vista=centro.conflictos.vista(); cuentaConflictos.setText(vista.activo()?"1 ACTIVO":"0 ACTIVOS");
        tituloCiclo.setText(vista.activo()?"ESPERA CIRCULAR, DL-A / DL-B":"ESTADO DE CONFLICTOS");
        conflicto.setText(vista.mensaje()+(vista.activo()?"\nDL-A retiene Montacargas 1 y espera el Escáner compartido.\nDL-B retiene el Escáner y espera Montacargas 1.":""));
        terminarA.setDisable(!vista.listo());terminarB.setDisable(!vista.listo());
        var demorado=demorado(); aumentar.setDisable(demorado.isEmpty());
        tituloEspera.setText(demorado.isPresent()?"TIEMPO OBJETIVO SUPERADO":"TIEMPOS DE ESPERA");
        alertaEspera.setText(demorado.map(p->"Pedido #"+centro.identificador(p.pedido())+" (Nivel "+p.pedido().nivelServicio().getPrioridad()+") lleva %.1fm en cola. Superó su tiempo objetivo.".formatted(p.espera()))
                .orElse("No hay pedidos que superen su tiempo objetivo de espera."));
        centro.recepcion.historial().stream().filter(r->r.entrada().id().equals(ultimaEntrada)).findFirst()
                .ifPresent(r->mensaje(mensajeEntrada,r.estado(),r.estado().startsWith("Rechazado")));
    }
}
