package gt.edu.cunoc.redxela.controlador;

import gt.edu.cunoc.redxela.modelo.*;
import gt.edu.cunoc.redxela.servicio.*;
import java.util.*;
import javafx.animation.FadeTransition;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.util.Duration;
import static gt.edu.cunoc.redxela.controlador.Componentes.*;

final class VistaMonitoreo extends VBox {
    private final ContextoCentro centro;
    private final Label completados = texto("0", "kpi-valor"), espera = texto("0.0m", "kpi-valor"),
            ocupados = texto("0/26", "kpi-valor"), deadlocks = texto("00", "kpi-valor");
    private final Label media = texto("Sin actividad", "dato-cian"), mensajeConflicto = parrafo("", "muted");
    private final VBox activos = new VBox(12), cola = new VBox(12);
    private final Map<TipoRecurso, VBox> tarjetas = new EnumMap<>(TipoRecurso.class);
    private final Map<TipoRecurso, Label> titulares = new EnumMap<>(TipoRecurso.class);
    private final Map<TipoRecurso, List<Label>> unidades = new EnumMap<>(TipoRecurso.class);
    private final Map<TipoRecurso, FadeTransition> pulsos = new EnumMap<>(TipoRecurso.class);

    VistaMonitoreo(ContextoCentro centro, Runnable administrar, Runnable historial) {
        this.centro = centro; setSpacing(24); getStyleClass().add("pagina");
        TilePane indicadores = new TilePane(18, 18);
        indicadores.getChildren().addAll(kpi("✓", completados, "PEDIDOS COMPLETADOS", texto("Despachos de la sesión", "muted")),
                kpi("◷", espera, "TIEMPO PROM. ESPERA", texto("Minutos simulados", "muted")),
                kpi("◉", ocupados, "RECURSOS EN USO", media),
                kpi("⚠", deadlocks, "DEADLOCKS ACTIVOS", texto("Espera circular", "dato-rojo")));
        indicadores.widthProperty().addListener((o,a,w) -> {
            int columnas = w.doubleValue() < 750 ? 2 : 4;
            indicadores.setPrefTileWidth((w.doubleValue() - 18 * (columnas - 1)) / columnas);
        });
        TilePane recursos = new TilePane(12, 12);
        recursos.widthProperty().addListener((o,a,w) -> recursos.setPrefTileWidth(Math.max(175, (w.doubleValue() - 12) / 2)));
        for (TipoRecurso tipo : TipoRecurso.values()) {
            Label pulso = texto("●", "dato-cian");
            Label cantidad = texto("Disponibles", "muted"); titulares.put(tipo, cantidad);
            FlowPane celdas = new FlowPane(5, 5); List<Label> etiquetas = new ArrayList<>();
            for (int i=1; i<=tipo.getCapacidad(); i++) { Label celda = texto("%02d".formatted(i), "recurso-unidad"); etiquetas.add(celda); celdas.getChildren().add(celda); }
            VBox tarjeta = tarjeta(fila(pulso, texto(tipo.toString().toUpperCase(Locale.ROOT), "etiqueta")), celdas, cantidad);
            tarjeta.getStyleClass().add("recurso"); tarjetas.put(tipo, tarjeta); unidades.put(tipo, etiquetas);
            FadeTransition fade = new FadeTransition(Duration.seconds(1.1), pulso);
            fade.setFromValue(1); fade.setToValue(0.25); fade.setAutoReverse(true); fade.setCycleCount(FadeTransition.INDEFINITE);
            pulsos.put(tipo, fade); recursos.getChildren().add(tarjeta);
        }
        ComboBox<PoliticaAtencion> politica = new ComboBox<>(); politica.getItems().setAll(PoliticaAtencion.values());
        politica.setValue(centro.motor.politica()); politica.setMaxWidth(Double.MAX_VALUE);
        politica.setOnAction(e -> centro.motor.politica(politica.getValue()));
        VBox listaEspera = tarjeta(texto("LISTA DE ESPERA", "titulo-tarjeta"), politica, cola,
                boton("VER TODOS LOS PEDIDOS", "boton-secundario", historial));
        VBox conflicto = tarjeta(texto("⚠  MONITOR DE DEADLOCKS", "dato-rojo"), mensajeConflicto,
                boton("Administrar conflictos", "boton-rojo", administrar));
        conflicto.getStyleClass().add("conflicto");
        VBox izquierda = new VBox(18, texto("▦  Estado de Recursos Compartidos", "titulo-seccion"), recursos,
                tarjeta(fila(texto("PEDIDOS EN PROCESAMIENTO ACTIVO", "titulo-tarjeta"), espacio()), activos));
        getChildren().addAll(indicadores, columnas(izquierda, new VBox(22, conflicto, listaEspera), 64, 850));
        actualizar();
    }
    private VBox kpi(String icono, Label valor, String titulo, Label detalle) {
        VBox box = tarjeta(fila(texto(icono, "icono-kpi"), espacio()), valor, texto(titulo, "etiqueta"), detalle);
        box.getStyleClass().add("kpi"); return box;
    }
    void actualizar() {
        var stats = centro.motor.estadisticas();
        completados.setText(Integer.toString(stats.completados())); espera.setText("%.1fm".formatted(stats.esperaMedia()));
        ocupados.setText(stats.recursosEnUso()+"/"+stats.recursosTotales());
        media.setText("%.1f%% ocupación media".formatted(stats.ocupacionMedia()*100));
        var conflicto = centro.conflictos.vista(); deadlocks.setText(conflicto.activo() ? "01" : "00"); mensajeConflicto.setText(conflicto.mensaje());
        for (TipoRecurso tipo : TipoRecurso.values()) {
            var estados = centro.recursos.estados().stream().filter(e -> e.tipo()==tipo).toList();
            long uso = estados.stream().filter(e -> !e.disponible()).count();
            titulares.get(tipo).setText((estados.size()-uso)+" disponibles · "+uso+" en uso");
            VBox card = tarjetas.get(tipo);
            if (uso > 0 && !card.getStyleClass().contains("activo")) { card.getStyleClass().add("activo"); pulsos.get(tipo).play(); }
            if (uso == 0 && card.getStyleClass().remove("activo")) { pulsos.get(tipo).stop(); }
            for (int i=0;i<estados.size();i++) {
                var estado = estados.get(i); Label celda = unidades.get(tipo).get(i);
                celda.getStyleClass().setAll("recurso-unidad", estado.disponible()?"disponible":"ocupado");
                celda.setTooltip(new Tooltip(tipo+" "+estado.numero()+" · "+(estado.disponible()?"Disponible":estado.titular())));
            }
        }
        activos.getChildren().clear();
        var procesando = centro.motor.pedidos().stream().filter(p -> p.estado()==EstadoPedido.EN_PROCESO).toList();
        if (procesando.isEmpty()) activos.getChildren().add(parrafo("No hay pedidos activos. Inicia la simulación o registra un pedido manual.", "muted"));
        for (var pedido : procesando) {
            ProgressBar barra = new ProgressBar(pedido.progreso()); barra.setMaxWidth(Double.MAX_VALUE);
            String nombre = centro.clientes.buscarPorId(pedido.pedido().clienteId()).orElseThrow().nombre();
            activos.getChildren().add(new VBox(7, fila(texto("#"+corto(pedido.pedido())+" · "+nombre,"destacado"), espacio(),
                    texto("%.1fm restantes".formatted(pedido.restante()),"dato-cian")),
                    texto(pedido.fase()+" · "+pedido.pedido().getTotalArticulos()+" unidades · "+pedido.pedido().nivelServicio(),"muted"), barra));
        }
        cola.getChildren().clear();
        var pendientes = centro.motor.espera();
        if (pendientes.isEmpty()) cola.getChildren().add(texto("La lista de espera está vacía.", "muted"));
        for (var pedido : pendientes.stream().limit(6).toList()) {
            VBox fila = new VBox(5, fila(texto("Pedido #"+corto(pedido.pedido()),"destacado"), espacio(),
                    texto("N"+pedido.pedido().nivelServicio().getPrioridad(),"dato-cian")),
                    parrafo(pedido.fase()+" · "+pedido.pedido().getTotalArticulos()+" artículos · %.1fm".formatted(pedido.espera()),"muted"));
            fila.getStyleClass().add("fila-espera"); cola.getChildren().add(fila);
        }
        if (pendientes.size()>6) cola.getChildren().add(texto("+"+(pendientes.size()-6)+" pedidos en espera", "muted"));
    }
    static String corto(Pedido pedido) { return pedido.id().toString().substring(0,8).toUpperCase(Locale.ROOT); }
    void detenerAnimaciones() { pulsos.values().forEach(FadeTransition::stop); }
}
