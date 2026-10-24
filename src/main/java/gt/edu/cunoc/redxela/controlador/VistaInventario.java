package gt.edu.cunoc.redxela.controlador;

import gt.edu.cunoc.redxela.catalogo.CatalogoProductos;
import gt.edu.cunoc.redxela.servicio.ContextoCentro;
import java.util.*;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import static gt.edu.cunoc.redxela.controlador.Componentes.*;

final class VistaInventario extends VBox {
    private final ContextoCentro centro;
    private final List<Label> celdas = new ArrayList<>();
    private final VBox catalogo = new VBox(14);
    private final TextField buscar = new TextField();
    private final Label libres = texto("0", "kpi-valor"), fragmentacion = texto("0%", "kpi-valor"),
            unidades = texto("", "dato-cian"), mensaje = parrafo("", "muted");
    private final Button optimizar;

    VistaInventario(ContextoCentro centro) {
        this.centro = centro; setSpacing(24); getStyleClass().add("pagina");
        optimizar = boton("Optimizar espacio", "boton-primario", () -> {
            try { centro.almacen.optimizar(); mensaje(mensaje, "Ubicaciones compactadas. Los lotes y cantidades se conservaron.", false); actualizar(); }
            catch (IllegalStateException ex) { mensaje(mensaje, ex.getMessage(), true); }
        });
        getChildren().add(fila(texto("● Ocupado", "dato-cian"), texto("● Reservado", "dato-amarillo"),
                texto("● Libre", "muted"), espacio(), optimizar));
        VBox mapa = new VBox(26);
        for (int pasillo=0; pasillo<2; pasillo++) {
            GridPane grid = new GridPane(); grid.setHgap(4); grid.setVgap(5);
            for(int columna=0; columna<12; columna++) { ColumnConstraints c = new ColumnConstraints(); c.setPercentWidth(100.0/12); grid.getColumnConstraints().add(c); }
            for(int fila=0; fila<2; fila++) for(int columna=0; columna<12; columna++) {
                Label celda = texto("", "bin"); celda.setMaxWidth(Double.MAX_VALUE); celda.setMinWidth(0);
                celdas.add(celda); grid.add(celda,columna,fila);
            }
            mapa.getChildren().add(new VBox(16, texto("▦  PASILLO "+(pasillo==0?"A":"B")+" · DOS NIVELES", "etiqueta"),grid));
        }
        HBox datos = new HBox(16, tarjeta(texto("TOTAL UBICACIONES","etiqueta"),texto("48","kpi-valor"),unidades),
                tarjeta(texto("UBICACIONES LIBRES","etiqueta"),libres,texto("Disponibles para nuevos lotes","muted")),
                tarjeta(texto("FRAGMENTACIÓN","etiqueta"),fragmentacion,texto("Espacio libre fuera del mayor bloque","muted")));
        datos.getChildren().forEach(n -> { HBox.setHgrow(n,Priority.ALWAYS); ((Region)n).setPrefWidth(0); });
        VBox izquierda = new VBox(28, mapa, datos, mensaje,
                parrafo("Cada ubicación admite 20 unidades de un producto. Un lote puede ocupar ubicaciones no contiguas. Selecciona una celda para consultar su contenido.", "muted"));
        buscar.setPromptText("Buscar producto, código o categoría…"); buscar.textProperty().addListener((o,a,b)->actualizarCatalogo());
        VBox derecha = tarjeta(texto("CATÁLOGO DE PRODUCTOS", "titulo-tarjeta"), buscar, catalogo);
        getChildren().add(columnas(izquierda,derecha,66,850)); actualizar();
    }
    void actualizar() {
        var resumen=centro.almacen.resumen();
        libres.setText(Integer.toString(resumen.libres())); fragmentacion.setText("%.1f%%".formatted(resumen.fragmentacion()));
        unidades.setText(resumen.unidades()+" / "+resumen.capacidad()+" unidades");
        optimizar.setDisable(resumen.reservadas()>0);
        optimizar.setTooltip(new Tooltip("La compactación requiere que no haya stock reservado."));
        var estados=centro.almacen.getCeldas();
        for(int i=0;i<estados.size();i++) {
            var estado=estados.get(i); Label celda=celdas.get(i);
            celda.getStyleClass().setAll("bin",estado.reservadas()>0?"bin-reservada":estado.unidades()>0?"bin-ocupada":"bin-libre");
            celda.setText(estado.unidades()>0?Integer.toString(estado.unidades()):"");
            String detalle=estado.ubicacion()+" · "+(estado.unidades()==0?"Libre":estado.producto())+"\nUnidades: "+estado.unidades()
                    +" · Reservadas: "+estado.reservadas()+"\n"+String.join("\n",estado.lotes());
            celda.setTooltip(new Tooltip(detalle));
            celda.setOnMouseClicked(e -> mensaje.setText(detalle));
        }
        actualizarCatalogo();
    }
    private void actualizarCatalogo() {
        catalogo.getChildren().clear(); String filtro=buscar.getText().strip().toLowerCase(Locale.ROOT);
        for(var producto:CatalogoProductos.getProductos()) {
            if(!(producto.codigo()+" "+producto.nombre()+" "+producto.tipoMercancia()).toLowerCase(Locale.ROOT).contains(filtro))continue;
            int stock=centro.almacen.stock(producto), disponible=centro.almacen.disponible(producto);
            ProgressBar nivel=new ProgressBar(Math.min(1,stock/60.0)); nivel.setMaxWidth(Double.MAX_VALUE);
            if(disponible<20)nivel.getStyleClass().add("stock-bajo");
            VBox card=tarjeta(fila(parrafo(producto.nombre(),"destacado"),espacio(),texto(producto.tipoMercancia().toString(),"badge")),
                    fila(texto("Stock: "+stock+"u · Disponible: "+disponible+"u","muted"),espacio()),nivel,
                    texto(disponible<20?"STOCK BAJO · mínimo 20u":producto.codigo()+" · mínimo 20u",disponible<20?"dato-rojo":"muted"));
            card.getStyleClass().add("producto-card"); catalogo.getChildren().add(card);
        }
        if(catalogo.getChildren().isEmpty())catalogo.getChildren().add(texto("No hay productos que coincidan.","muted"));
    }
}
