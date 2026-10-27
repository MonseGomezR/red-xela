package gt.edu.cunoc.redxela.controlador;

import javafx.scene.Node;
import gt.edu.cunoc.redxela.modelo.*;
import gt.edu.cunoc.redxela.servicio.*;
import java.util.*;
import javafx.animation.FadeTransition;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.util.Duration;
import org.kordamp.ikonli.feather.Feather;
import static gt.edu.cunoc.redxela.controlador.Componentes.*;

/** Presenta el avance de los pedidos, los recursos y la lista de espera. */
public final class VistaMonitoreo extends VBox {

    private final ContextoCentro centro;
    private final Label completados = texto("0", "kpi-valor");
    private final Label espera = texto("0.0m", "kpi-valor");
    private final Label proceso = texto("0.0m", "kpi-valor");
    private final Label ocupacionMedia = texto("0%", "kpi-valor");
    private final Label ocupados = texto("0/26", "kpi-valor");
    private final Label deadlocks = texto("00", "kpi-valor");
    private final Label media = texto("0%", "dato-cian");
    private final Label mensajeConflicto = parrafo("", "muted");
    private final Label totalActivos = texto("0 pedidos activos", "muted");
    private final Label tituloConflicto = tituloIcono(Feather.ALERT_CIRCLE, "MONITOR DE DEADLOCKS", "dato-rojo");
    private final VBox activos = new VBox(18);
    private final VBox cola = new VBox(20);
    private final Label avisoInventario = parrafo("", "dato-amarillo");
    private final List<RecursoVisual> recursos = new ArrayList<>();
    private final Button verTodos;
    private record RecursoVisual(
            TipoRecurso tipo,
            int numero,
            VBox tarjeta,
            Label titular,
            Label detalle,
            FadeTransition pulso,
            ProgressIndicator progreso
    ) {
    }

    public VistaMonitoreo(ContextoCentro centro, Runnable administrar, Runnable historial) {
        this.centro = centro;
        setSpacing(22);
        getStyleClass().add("pagina");
        GridPane indicadores = crearIndicadores();
        GridPane cuadricula = crearCuadriculaRecursos();
        ScrollPane estados = desplazable(cuadricula);
        estados.setPrefHeight(252);
        estados.setMinHeight(252);
        verTodos = boton("VER TODOS LOS PEDIDOS (0)", "boton-enlace", historial);
        verTodos.setMaxWidth(Double.MAX_VALUE);
        VBox lista = crearListaEspera();
        Button resolver = boton("Resolver Manualmente", "boton-rojo", administrar);
        resolver.setMaxWidth(Double.MAX_VALUE);
        VBox conflicto = tarjeta(tituloConflicto, mensajeConflicto, resolver);
        conflicto.getStyleClass().add("conflicto");
        VBox izquierda = new VBox(
                18,
                fila(
                        tituloIcono(Feather.CPU, "Estado de Recursos Compartidos", "titulo-seccion"),
                        espacio(),
                        tituloIcono(Feather.CIRCLE, "DISPONIBLE", "leyenda-cian"),
                        tituloIcono(Feather.CIRCLE, "OCUPADO", "leyenda-amarilla")
                ),
                estados,
                tarjeta(
                        fila(parrafo("PEDIDOS EN PROCESAMIENTO ACTIVO", "titulo-tarjeta"), espacio(), totalActivos),
                        new Separator(),
                        activos
                )
        );
        getChildren().addAll(
                avisoInventario,
                indicadores,
                columnas(izquierda, new VBox(30, conflicto, lista), 67, 880)
        );
        actualizar();
    }

    /** Construye un indicador que después recibe valores calculados por el motor. */
    private VBox kpi(Feather simbolo, Label valor, String titulo, Label detalle, String color) {
        Label icono = tituloIcono(simbolo, "", "icono-kpi");
        icono.getStyleClass().add("tinte-" + color);
        VBox indicador = tarjeta(fila(parrafo(titulo, "etiqueta"), espacio(), icono), valor, detalle);
        indicador.getStyleClass().addAll("kpi", "kpi-" + color);
        return indicador;
    }

    /** Añade una unidad física al panel y prepara su indicador de uso. */
    private void agregar(GridPane cuadricula, TipoRecurso tipo, int numero, int columna, int fila, int ancho) {
        Label simbolo = tituloIcono(iconoRecurso(tipo), "", "simbolo-recurso");
        Label titular = texto("DISPONIBLE", "destacado");
        Label detalle = parrafo("Listo para procesar", "muted");
        Label etiquetaRecurso = texto(nombre(tipo, numero), "badge");
        VBox tarjetaRecurso = tarjeta(Componentes.fila(etiquetaRecurso, espacio(), simbolo), titular, detalle);
        tarjetaRecurso.setMinWidth(0);
        tarjetaRecurso.setMaxWidth(Double.MAX_VALUE);
        tarjetaRecurso.getStyleClass().add("recurso");
        ProgressIndicator progreso = null;
        if (tipo == TipoRecurso.ESCANER) {
            tarjetaRecurso.getStyleClass().add("recurso-escaner");
            progreso = new ProgressIndicator(0);
            progreso.setPrefSize(32, 32);
            progreso.setMaxSize(32, 32);
            HBox cuerpo = Componentes.fila(new VBox(7, titular, detalle), espacio(), progreso);
            tarjetaRecurso.getChildren().setAll(
                    Componentes.fila(simbolo, texto(nombre(tipo, numero), "destacado")),
                    cuerpo
            );
        }
        FadeTransition pulso = new FadeTransition(Duration.seconds(1.2), simbolo);
        pulso.setFromValue(1);
        pulso.setToValue(.35);
        pulso.setAutoReverse(true);
        pulso.setCycleCount(FadeTransition.INDEFINITE);
        recursos.add(new RecursoVisual(tipo, numero, tarjetaRecurso, titular, detalle, pulso, progreso));
        cuadricula.add(tarjetaRecurso, columna, fila, ancho, 1);
    }

    /** Traduce el tipo y número del recurso a la etiqueta que muestra la tarjeta. */
    private String nombre(TipoRecurso tipo, int numero) {
        return switch (tipo) {
            case ESTACION_EMPAQUE -> "EMPAQUE " + numero;
            case MONTACARGAS -> "MONTACARGAS " + numero;
            case AREA_CARGA -> "CARGA " + numero;
            case ESCANER -> "ESCÁNER COMPARTIDO";
            case CONTROL_CALIDAD -> "QC " + (char) ('A' + numero - 1);
            case ENCARGADO_BODEGA -> "ENCARGADO " + numero;
        };
    }

    /** Elige un icono de la biblioteca para cada tipo de recurso. */
    private Feather iconoRecurso(TipoRecurso tipo) {
        return switch (tipo) {
            case ESTACION_EMPAQUE -> Feather.PACKAGE;
            case AREA_CARGA, MONTACARGAS -> Feather.TRUCK;
            case ESCANER -> Feather.COLUMNS;
            case CONTROL_CALIDAD -> Feather.SHIELD;
            case ENCARGADO_BODEGA -> Feather.USER;
        };
    }

    /** Consulta pedidos y recursos y renueva el panel desde el hilo de JavaFX. */
    public void actualizar() {
        actualizarIndicadores();
        List<GestorRecursos.Estado> estados = centro.getRecursos().estados();
        List<MotorSimulacion.VistaPedido> pedidos = centro.getMotor().pedidos();
        actualizarRecursos(estados, pedidos);
        actualizarPedidosActivos(estados, pedidos);
        actualizarListaEspera(pedidos.size());
    }

    /** Reúne los nombres y cantidades de productos para identificar el trabajo del pedido. */
    private String resumenProductos(Pedido pedido) {
        return pedido.detalles()
                .stream()
                .map(d -> d.producto().nombre() + ": " + d.cantidad())
                .collect(java.util.stream.Collectors.joining("; ")) + ". Total: " + pedido.getTotalArticulos() + " unidades";
    }

    /** Detiene los pulsos de los iconos cuando se cierra la ventana. */
    public void detenerAnimaciones() {
        recursos.forEach(recursoVisual -> recursoVisual.pulso().stop());
    }

    /** Organiza las seis métricas y ajusta sus columnas al ancho disponible. */
    private GridPane crearIndicadores() {
        GridPane indicadores = new GridPane();
        indicadores.setHgap(24);
        indicadores.setVgap(20);
        indicadores.getChildren().addAll(
                kpi(Feather.CHECK_CIRCLE, completados, "PEDIDOS COMPLETADOS", texto("Sesión actual", "exito"), "cian"),
                kpi(Feather.CLOCK, espera, "TIEMPO PROM. ESPERA", texto("Promedio", "dato-amarillo"), "azul"),
                kpi(Feather.ACTIVITY, proceso, "TIEMPO PROM. PROCESAMIENTO", texto("Pedidos completados", "muted"), "azul"),
                kpi(Feather.USERS, ocupados, "RECURSOS EN USO", media, "violeta"),
                kpi(Feather.BAR_CHART_2, ocupacionMedia, "OCUPACIÓN MEDIA", texto("Tiempo de la sesión", "muted"), "violeta"),
                kpi(Feather.LINK_2, deadlocks, "DEADLOCKS DETECTADOS", texto("Espera circular", "dato-rojo"), "rojo")
        );
        indicadores.widthProperty().addListener((o, a, ancho) -> {
            int cantidadColumnas = ancho.doubleValue() < 750 ? 2 : 3;
            indicadores.getColumnConstraints().clear();
            for (int i = 0; i < cantidadColumnas; i++) {
                ColumnConstraints restriccion = new ColumnConstraints();
                restriccion.setPercentWidth(100.0 / cantidadColumnas);
                restriccion.setMinWidth(0);
                indicadores.getColumnConstraints().add(restriccion);
            }
            for (int i = 0; i < indicadores.getChildren().size(); i++) {
                GridPane.setColumnIndex(indicadores.getChildren().get(i), i % cantidadColumnas);
                GridPane.setRowIndex(indicadores.getChildren().get(i), i / cantidadColumnas);
            }
        });
        return indicadores;
    }

    /** Crea una tarjeta por cada una de las 26 unidades, dentro de un panel desplazable. */
    private GridPane crearCuadriculaRecursos() {
        GridPane cuadricula = new GridPane();
        cuadricula.setHgap(10);
        cuadricula.setVgap(10);
        List<int[]> posiciones = new ArrayList<>();
        agregar(cuadricula, TipoRecurso.ESTACION_EMPAQUE, 1, 0, 0, 1);
        agregar(cuadricula, TipoRecurso.ESTACION_EMPAQUE, 2, 1, 0, 1);
        agregar(cuadricula, TipoRecurso.MONTACARGAS, 1, 2, 0, 1);
        agregar(cuadricula, TipoRecurso.MONTACARGAS, 2, 3, 0, 1);
        agregar(cuadricula, TipoRecurso.CONTROL_CALIDAD, 1, 0, 1, 1);
        agregar(cuadricula, TipoRecurso.ESCANER, 1, 1, 1, 2);
        agregar(cuadricula, TipoRecurso.AREA_CARGA, 1, 3, 1, 1);
        int celda = 8;
        for (TipoRecurso tipo : TipoRecurso.values()) {
            for (int i = 1; i <= tipo.getCapacidad(); i++) {
                int numero = i;
                if (recursos.stream().anyMatch(recursoVisual -> recursoVisual.tipo() == tipo && recursoVisual.numero() == numero)) {
                    continue;
                }
                agregar(cuadricula, tipo, i, celda % 4, celda / 4, 1);
                celda++;
            }
        }
        // Conserva el orden de lectura y reduce la cuadrícula en ventanas estrechas.
        for (Node nodo : cuadricula.getChildren()) {
            posiciones.add(new int[] { GridPane.getColumnIndex(nodo), GridPane.getRowIndex(nodo), GridPane.getColumnSpan(nodo) });
        }
        cuadricula.widthProperty().addListener((o, a, ancho) -> {
            boolean estrecho = ancho.doubleValue() < 520;
            int cantidadColumnas = estrecho ? 2 : 4;
            cuadricula.getColumnConstraints().clear();
            for (int i = 0; i < cantidadColumnas; i++) {
                ColumnConstraints restriccion = new ColumnConstraints();
                restriccion.setPercentWidth(100.0 / cantidadColumnas);
                restriccion.setMinWidth(0);
                cuadricula.getColumnConstraints().add(restriccion);
            }
            for (int i = 0; i < cuadricula.getChildren().size(); i++) {
                Node nodo = cuadricula.getChildren().get(i);
                int[] posicionOriginal = posiciones.get(i);
                GridPane.setColumnIndex(nodo, estrecho ? i % 2 : posicionOriginal[0]);
                GridPane.setRowIndex(nodo, estrecho ? i / 2 : posicionOriginal[1]);
                GridPane.setColumnSpan(nodo, estrecho ? 1 : posicionOriginal[2]);
            }
        });
        return cuadricula;
    }

    /** Envía al motor el criterio elegido para ordenar las siguientes asignaciones. */
    private ComboBox<PoliticaAtencion> crearSelectorPolitica() {
        ComboBox<PoliticaAtencion> politica = new ComboBox<>();
        politica.getItems().setAll(PoliticaAtencion.values());
        politica.setValue(centro.getMotor().politica());
        politica.setPrefWidth(110);
        politica.setMaxWidth(110);
        politica.getStyleClass().add("politica");
        politica.setOnAction(e -> centro.getMotor().politica(politica.getValue()));
        return politica;
    }

    /** Agrupa la política activa y una vista corta de los primeros pedidos pendientes. */
    private VBox crearListaEspera() {
        ComboBox<PoliticaAtencion> politica = crearSelectorPolitica();
        Region relleno = new Region();
        VBox.setVgrow(relleno, Priority.ALWAYS);
        VBox lista = tarjeta(
                fila(parrafo("LISTA DE ESPERA", "titulo-tarjeta"), espacio(), politica),
                new Separator(),
                cola,
                relleno,
                new Separator(),
                verTodos
        );
        lista.setMinHeight(280);
        return lista;
    }

    /** Presenta las métricas actuales del motor y el estado del conflicto. */
    private void actualizarIndicadores() {
        MotorSimulacion.Estadisticas estadisticas = centro.getMotor().estadisticas();
        completados.setText(Integer.toString(estadisticas.completados()));
        espera.setText("%.1fm".formatted(estadisticas.esperaMedia()));
        proceso.setText(String.format("%.1fm", estadisticas.procesoMedio()));
        ocupacionMedia.setText(String.format("%.1f%%", estadisticas.ocupacionMedia() * 100));
        ocupados.setText(estadisticas.recursosEnUso() + "/" + estadisticas.recursosTotales());
        media.setText("%.0f%%".formatted(100.0 * estadisticas.recursosEnUso() / estadisticas.recursosTotales()));
        MonitorConflictos.Vista conflicto = centro.getConflictos().vista();
        deadlocks.setText(conflicto.activo() ? "01" : "00");
        tituloConflicto.setText(conflicto.activo() ? "DEADLOCK DETECTADO" : "MONITOR DE DEADLOCKS");
        String detalle = conflicto.mensaje();
        if (conflicto.activo()) {
            String pedidoA = centro.identificador(conflicto.pedidoA());
            String pedidoB = centro.identificador(conflicto.pedidoB());
            detalle = "Pedido " + pedidoA + " espera el Escáner, retenido por " + pedidoB + ".\n\n"
                    + "Pedido " + pedidoB + " espera Área de carga 1, retenida por " + pedidoA + ".";
        }
        mensajeConflicto.setText(detalle);
    }

    /** Muestra quién usa cada unidad y distingue los recursos retenidos en el conflicto. */
    private void actualizarRecursos(List<GestorRecursos.Estado> estados, List<MotorSimulacion.VistaPedido> pedidos) {
        Map<String, MotorSimulacion.VistaPedido> porTitular = new HashMap<>();
        pedidos.forEach(pedido -> porTitular.put("P-" + pedido.pedido().id().toString().substring(0, 8), pedido));
        for (RecursoVisual recursoVisual : recursos) {
            GestorRecursos.Estado estado = estados
                    .stream()
                    .filter(e -> e.tipo() == recursoVisual.tipo() && e.numero() == recursoVisual.numero())
                    .findFirst()
                    .orElseThrow();
            MotorSimulacion.VistaPedido pedido = porTitular.get(estado.titular());
            recursoVisual.titular().setText(estado.disponible() ? "DISPONIBLE" : pedido == null ? estado.titular() : "Pedido #" + centro.identificador(pedido.pedido()));
            recursoVisual.detalle().setText(estado.disponible() ? "Listo para procesar" : pedido == null ? "Recurso retenido" : pedido.fase());
            boolean bloqueado = pedido != null && pedido.estado() == EstadoPedido.EN_CONFLICTO;
            if (bloqueado && !recursoVisual.tarjeta().getStyleClass().contains("bloqueado")) {
                recursoVisual.tarjeta().getStyleClass().add("bloqueado");
            } else if (!bloqueado) {
                recursoVisual.tarjeta().getStyleClass().remove("bloqueado");
            }
            if (recursoVisual.progreso() != null) {
                recursoVisual.progreso().setProgress(estado.disponible() ? 0 : pedido == null ? -1 : pedido.progreso());
            }
            if (!estado.disponible() && !recursoVisual.tarjeta().getStyleClass().contains("activo")) {
                recursoVisual.tarjeta().getStyleClass().add("activo");
                recursoVisual.pulso().play();
            }
            if (estado.disponible() && recursoVisual.tarjeta().getStyleClass().remove("activo")) {
                recursoVisual.pulso().stop();
                recursoVisual.pulso().getNode().setOpacity(1);
            }
        }
    }

    /** Muestra preparación, despacho o bloqueo con el progreso de cada pedido activo. */
    private void actualizarPedidosActivos(
            List<GestorRecursos.Estado> estados,
            List<MotorSimulacion.VistaPedido> pedidos
    ) {
        activos.getChildren().clear();
        List<MotorSimulacion.VistaPedido> procesando = pedidos
                .stream()
                .filter(pedido -> pedido.estado() == EstadoPedido.EN_PROCESO || pedido.estado() == EstadoPedido.EN_CONFLICTO)
                .toList();
        totalActivos.setText(procesando.size() + " pedidos activos");
        if (procesando.isEmpty()) {
            activos.getChildren().add(parrafo("No hay pedidos en procesamiento.", "muted"));
        }
        for (MotorSimulacion.VistaPedido pedido : procesando) {
            ProgressBar barra = new ProgressBar(pedido.progreso());
            barra.setMaxWidth(Double.MAX_VALUE);
            String nombre = centro.getClientes().buscarPorId(pedido.pedido().clienteId()).orElseThrow().nombre();
            VBox datos = new VBox(
                    7,
                    fila(
                            texto("#" + centro.identificador(pedido.pedido()) + " - " + nombre, "destacado"),
                            espacio(),
                            texto(pedido.estado() == EstadoPedido.EN_CONFLICTO ? "Bloqueado"
                                    : String.format("%.1fm restantes", pedido.restante()),
                                    pedido.estado() == EstadoPedido.EN_CONFLICTO ? "dato-rojo" : "mono")
                    ),
                    parrafo(resumenProductos(pedido.pedido()), "etiqueta"),
                    parrafo(pedido.fase(), pedido.estado() == EstadoPedido.EN_CONFLICTO ? "dato-rojo" : "muted"),
                    barra
            );
            HBox.setHgrow(datos, Priority.ALWAYS);
            HBox iconos = new HBox(5);
            String titular = "P-" + pedido.pedido().id().toString().substring(0, 8);
            estados
                    .stream()
                    .filter(e -> titular.equals(e.titular()))
                    .map(GestorRecursos.Estado::tipo).distinct()
                    .forEach(tipo -> iconos.getChildren().add(icono(iconoRecurso(tipo), 14)));
            HBox registro = fila(
                    texto("N" + pedido.pedido().nivelServicio().getPrioridad(), "nivel-pedido"),
                    datos,
                    new VBox(7, texto("RECURSOS", "etiqueta"), iconos)
            );
            registro.getStyleClass().add("fila-proceso");
            activos.getChildren().add(registro);
        }
    }

    /** Muestra los primeros seis de la cola y avisa cuando faltan existencias. */
    private void actualizarListaEspera(int cantidadPedidos) {
        cola.getChildren().clear();
        List<MotorSimulacion.VistaPedido> pendientes = centro.getMotor().espera();
        verTodos.setText("VER TODOS LOS PEDIDOS (" + cantidadPedidos + ")");
        long sinStock = pendientes
                .stream()
                .filter(pedido -> pedido.estado() == EstadoPedido.ESPERANDO_INVENTARIO).count();
        avisoInventario.setVisible(sinStock > 0);
        avisoInventario.setManaged(sinStock > 0);
        avisoInventario.setText((sinStock == 1 ? "Hay un pedido esperando existencias. " : "Hay " + sinStock + " pedidos esperando existencias. ") + "Registra una entrada en Inventario, Recibir mercadería.");
        if (pendientes.isEmpty()) {
            cola.getChildren().add(texto("La lista de espera está vacía.", "muted"));
        }
        for (MotorSimulacion.VistaPedido pedido : pendientes.stream().limit(6).toList()) {
            VBox item = new VBox(
                    6,
                    fila(
                            texto("Pedido #" + centro.identificador(pedido.pedido()), "destacado"),
                            espacio(),
                            texto(pedido.pedido().nivelServicio().toString(), "dato-cian")
                    ),
                    parrafo(resumenProductos(pedido.pedido()) + ", %.1fm en cola".formatted(pedido.espera()), "muted"),
                    texto(
                            pedido.fase(),
                            pedido.estado() == EstadoPedido.EN_CONFLICTO ? "dato-rojo"
                                    : pedido.estado() == EstadoPedido.ESPERANDO_INVENTARIO ? "dato-amarillo" : "muted"
                    )
            );
            item.getStyleClass().add("fila-espera");
            cola.getChildren().add(item);
        }
    }
}
