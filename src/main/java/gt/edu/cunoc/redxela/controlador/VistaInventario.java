package gt.edu.cunoc.redxela.controlador;

import gt.edu.cunoc.redxela.modelo.Producto;
import gt.edu.cunoc.redxela.servicio.Almacen;
import gt.edu.cunoc.redxela.catalogo.CatalogoProductos;
import gt.edu.cunoc.redxela.servicio.ContextoCentro;
import java.util.*;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import org.kordamp.ikonli.feather.Feather;
import static gt.edu.cunoc.redxela.controlador.Componentes.*;

/** Muestra las ubicaciones del almacén y permite consultar sus existencias. */
public final class VistaInventario extends VBox {

    private final ContextoCentro centro;
    private final List<Label> celdas = new ArrayList<>();
    private final VBox catalogo = new VBox(14);
    private final TextField buscar = new TextField();
    private final Label libres = texto("0", "kpi-valor");
    private final Label fragmentacion = texto("0%", "kpi-valor");
    private final Label unidades = texto("", "dato-cian");
    private final Label mensaje = parrafo("", "muted");
    private final Button optimizar;
    private final HBox controles;

    public VistaInventario(ContextoCentro centro) {
        this.centro = centro;
        setSpacing(24);
        getStyleClass().add("pagina");
        optimizar = boton("Optimizar espacio", "boton-primario", this::optimizarEspacio);
        controles = fila(
                tituloIcono(Feather.CIRCLE, "Ocupado", "leyenda-cian"),
                tituloIcono(Feather.CIRCLE, "Reservado", "leyenda-amarilla"),
                tituloIcono(Feather.CIRCLE, "Libre", "leyenda-libre"),
                optimizar
        );
        VBox mapa = crearMapa();
        HBox datos = crearIndicadores();
        VBox izquierda = new VBox(
                32,
                mapa,
                datos,
                parrafo(
                        "Existencias iniciales de simulación: " + ContextoCentro.EXISTENCIAS_INICIALES_POR_PRODUCTO + " unidades por producto.",
                        "muted"
                ),
                mensaje
        );
        VBox derecha = crearCatalogo();
        getChildren().add(columnas(izquierda, derecha, 66, 850));
        actualizar();
    }

    public void actualizar() {
        Almacen.Resumen resumen = centro.getAlmacen().resumen();
        libres.setText(Integer.toString(resumen.libres()));
        fragmentacion.setText("%.1f%%".formatted(resumen.fragmentacion()));
        unidades.setText(resumen.unidades() + " / " + resumen.capacidad() + " unidades");
        optimizar.setDisable(resumen.reservadas() > 0);
        optimizar.setTooltip(new Tooltip("La compactación requiere que no haya stock reservado."));
        List<Almacen.Celda> estados = centro.getAlmacen().getCeldas();
        for (int i = 0; i < estados.size(); i++) {
            Almacen.Celda estado = estados.get(i);
            Label celda = celdas.get(i);
            celda.getStyleClass().setAll(
                    "bin",
                    estado.reservadas() > 0 ? "bin-reservada" : estado.unidades() > 0 ? "bin-ocupada" : "bin-libre"
            );
            celda.setText("");
            String detalle = estado.ubicacion() + ", " + (estado.unidades() == 0 ? "Libre" : estado.producto()) + "\nUnidades: " + estado.unidades() + ", Reservadas: " + estado.reservadas() + "\n" + String.join("\n", estado.lotes());
            celda.setTooltip(new Tooltip(detalle));
            celda.setOnMouseClicked(e -> mensaje.setText(detalle));
        }
        actualizarCatalogo();
    }

    private void actualizarCatalogo() {
        catalogo.getChildren().clear();
        String filtro = buscar.getText().strip().toLowerCase(Locale.ROOT);
        for (Producto producto : CatalogoProductos.getProductos()) {
            if (!(producto.codigo() + " " + producto.nombre() + " " + producto.tipoMercancia()).toLowerCase(Locale.ROOT).contains(filtro)) {
                continue;
            }
            int stock = centro.getAlmacen().stock(producto), disponible = centro.getAlmacen().disponible(producto);
            ProgressBar nivel = new ProgressBar(stock == 0 ? 0 : (double) disponible / stock);
            nivel.setMaxWidth(Double.MAX_VALUE);
            nivel.setTooltip(new Tooltip("Proporción de existencias disponibles, sin reservar."));
            if (disponible == 0) {
                nivel.getStyleClass().add("stock-bajo");
            }
            Label categoria = texto(producto.tipoMercancia().toString(), "badge");
            categoria.getStyleClass().add("categoria-" + producto.tipoMercancia().name().toLowerCase(Locale.ROOT));
            VBox tarjetaProducto = tarjeta(
                    fila(parrafo(producto.nombre(), "destacado"), espacio(), categoria),
                    fila(
                            texto("Stock actual: " + stock + "u", "muted"),
                            espacio(),
                            texto("Disponible: " + disponible + "u", disponible == 0 ? "dato-rojo" : "muted")
                    ),
                    nivel
            );
            tarjetaProducto.getStyleClass().add("producto-card");
            catalogo.getChildren().add(tarjetaProducto);
        }
        if (catalogo.getChildren().isEmpty()) {
            catalogo.getChildren().add(texto("No hay productos que coincidan.", "muted"));
        }
    }

    public HBox controles() {
        return controles;
    }

    private void optimizarEspacio() {
        try {
            centro.getAlmacen().optimizar();
            mensaje(mensaje, "Ubicaciones compactadas. Los lotes y cantidades se conservaron.", false);
            actualizar();
        } catch (IllegalStateException exception) {
            mensaje(mensaje, exception.getMessage(), true);
        }
    }

    private VBox crearMapa() {
        VBox mapa = new VBox(26);
        for (int pasillo = 0; pasillo < 2; pasillo++) {
            GridPane cuadricula = new GridPane();
            cuadricula.setHgap(4);
            cuadricula.setVgap(5);
            for (int columna = 0; columna < 12; columna++) {
                ColumnConstraints restriccion = new ColumnConstraints();
                restriccion.setPercentWidth(100.0 / 12);
                cuadricula.getColumnConstraints().add(restriccion);
            }
            for (int fila = 0; fila < 2; fila++) {
                for (int columna = 0; columna < 12; columna++) {
                    Label celda = texto("", "bin");
                    celda.setMaxWidth(Double.MAX_VALUE);
                    celda.setMinWidth(0);
                    celdas.add(celda);
                    cuadricula.add(celda, columna, fila);
                }
            }
            mapa.getChildren().add(new VBox(
                    16,
                    tituloIcono(Feather.GRID, "PASILLO " + (pasillo == 0 ? "A" : "B") + ", ALMACENAMIENTO", "etiqueta"),
                    cuadricula
            ));
        }
        mapa.setSpacing(32);
        return mapa;
    }

    private HBox crearIndicadores() {
        HBox datos = new HBox(
                16,
                tarjeta(texto("TOTAL UBICACIONES", "etiqueta"), texto("48", "kpi-valor"), unidades),
                tarjeta(
                        texto("UBICACIONES LIBRES", "etiqueta"),
                        libres,
                        texto("Disponibles para nuevos lotes", "muted")
                ),
                tarjeta(
                        texto("FRAGMENTACIÓN", "etiqueta"),
                        fragmentacion,
                        texto("Espacio libre fuera del mayor bloque", "muted")
                )
        );
        datos.getChildren().forEach(indicador -> {
            HBox.setHgrow(indicador, Priority.ALWAYS);
            ((Region) indicador).setPrefWidth(0);
        });
        return datos;
    }

    private VBox crearCatalogo() {
        buscar.setPromptText("Buscar producto, código o categoría...");
        buscar.textProperty().addListener((o, a, b) -> actualizarCatalogo());
        ScrollPane lista = desplazable(catalogo);
        lista.setPrefHeight(425);
        HBox busqueda = fila(icono(Feather.SEARCH, 17), buscar);
        HBox.setHgrow(buscar, Priority.ALWAYS);
        VBox derecha = new VBox(24, texto("CATÁLOGO DE PRODUCTOS", "titulo-tarjeta"), busqueda, new Separator(), lista);
        derecha.getStyleClass().add("catalogo-panel");
        return derecha;
    }
}
