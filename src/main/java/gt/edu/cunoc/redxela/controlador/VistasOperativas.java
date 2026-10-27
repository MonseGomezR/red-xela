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

/** Atiende la recepción de mercadería y las acciones para resolver conflictos. */
public final class VistasOperativas {

    private final VBox recepcion;
    private final VBox conflictos;
    private final ContextoCentro centro;
    private final TextField lote = new TextField();
    private final ComboBox<Producto> recibido = new ComboBox<>();
    private final ComboBox<Integer> muelle = new ComboBox<>();
    private final Label mensajeEntrada = parrafo("", "muted");
    private final Label conflicto = parrafo("", "muted");
    private final Label alertaEspera = parrafo("", "muted");
    private final Label cuentaConflictos = texto("0 ACTIVOS", "badge-rojo");
    private final Label tituloEspera = texto("TIEMPOS DE ESPERA", "dato-amarillo");
    private final Label tituloCiclo = texto("ESTADO DE CONFLICTOS", "dato-rojo");
    private final ComboBox<Pedido> pedidoA = new ComboBox<>();
    private final ComboBox<Pedido> pedidoB = new ComboBox<>();
    private final Label avisoConflicto = parrafo("", "muted");
    private final Button continuarA;
    private final Button continuarB;
    private final Button preparar;
    private final Button cancelar;
    private final Button aumentar;
    private UUID ultimaEntrada;

    public VistasOperativas(ContextoCentro centro) {
        this.centro = centro;
        lote.setPromptText("Cantidad a recibir");
        continuarA = boton("Continuar A", "boton-rojo", () -> resolver(true));
        continuarB = boton("Continuar B", "boton-rojo-contorno", () -> resolver(false));
        continuarA.setWrapText(true);
        continuarB.setWrapText(true);
        preparar = boton("Preparar espera circular", "boton-secundario", this::prepararConflicto);
        cancelar = boton("Cancelar preparación", "boton-secundario", this::cancelarPreparacion);
        configurarPedidosConflicto();
        aumentar = boton("AUMENTAR PRIORIDAD MANUALMENTE", "boton-amarillo", this::priorizar);
        conflictos = crearPanelConflictos();
        configurarRecepcion();
        recepcion = crearPanelRecepcion();
        actualizar();
    }

    /** Construye el formulario que envía un mensaje de mercadería recibida. */
    private VBox crearPanelRecepcion() {
        Button entrada = boton("REGISTRAR ENTRADA", "boton-secundario", this::registrarEntrada);
        entrada.setMaxWidth(Double.MAX_VALUE);
        VBox panel = tarjeta(
                tituloIcono(Feather.TRUCK, "LLEGADA DE MERCADERÍA", "titulo-tarjeta"),
                campo("SELECCIONAR PRODUCTO", recibido),
                campos(campo("CANTIDAD (LOTE)", lote), campo("MUELLE DE CARGA", muelle)),
                entrada,
                mensajeEntrada
        );
        return panel;
    }

    public VBox recepcion() {
        return recepcion;
    }

    public VBox conflictos() {
        return conflictos;
    }

    /** Coloca el nombre del dato sobre su control de entrada. */
    private VBox campo(String nombre, javafx.scene.Node control) {
        return new VBox(8, texto(nombre, "etiqueta"), control);
    }

    /** Reparte el ancho disponible entre campos de la misma fila. */
    private HBox campos(Region... elementos) {
        HBox fila = new HBox(16, elementos);
        for (Region numero : elementos) {
            HBox.setHgrow(numero, Priority.ALWAYS);
            numero.setMaxWidth(Double.MAX_VALUE);
            numero.setPrefWidth(0);
        }
        return fila;
    }

    /** Envía al monitor cuál de los dos pedidos debe continuar. */
    private void resolver(boolean continuaA) {
        avisoConflicto.setText("");
        try {
            centro.getConflictos().resolver(continuaA);
        } catch (IllegalArgumentException exception) {
            mensaje(avisoConflicto, exception.getMessage(), true);
        }
        actualizar();
    }

    /** Busca el pedido que más espera entre los que superaron su objetivo y aún no se priorizaron. */
    private Optional<MotorSimulacion.VistaPedido> demorado() {
        MotorSimulacion.VistaPedido mayorEspera = null;
        for (MotorSimulacion.VistaPedido pedido : centro.getMotor().espera()) {
            if (pedido.preferente() || pedido.estado() == EstadoPedido.EN_CONFLICTO) {
                continue;
            }
            double objetivo = pedido.pedido().nivelServicio().getEsperaObjetivo()
                    .map(duracion -> (double) duracion.toMinutes()).orElse(1.0);
            if (pedido.espera() > objetivo && (mayorEspera == null || pedido.espera() > mayorEspera.espera())) {
                mayorEspera = pedido;
            }
        }
        return Optional.ofNullable(mayorEspera);
    }

    /** Da preferencia al pedido demorado sin intervenir los recursos de un conflicto. */
    private void priorizar() {
        try {
            demorado().ifPresent(pedido -> centro.getMotor().priorizar(pedido.pedido().id()));
        } catch (IllegalArgumentException exception) {
            // El planificador puede haber asignado el pedido después de consultar la cola.
            mensaje(avisoConflicto, exception.getMessage(), true);
        }
        actualizar();
    }

    /** Consulta el conflicto, los tiempos de espera y el resultado de la última entrada. */
    public void actualizar() {
        actualizarConflictos();
        actualizarEspera();
        centro.getRecepcion().historial()
                .stream()
                .filter(resultadoRecepcion -> resultadoRecepcion.entrada().id().equals(ultimaEntrada))
                .findFirst()
                .ifPresent(resultadoRecepcion -> mensaje(
                mensajeEntrada,
                resultadoRecepcion.estado(),
                resultadoRecepcion.estado().startsWith("Rechazado")
        ));
    }

    /** Agrupa la selección de pedidos, su espera circular y la alerta por demora. */
    private VBox crearPanelConflictos() {
        VBox ciclo = tarjeta(tituloCiclo, conflicto, campos(continuarA, continuarB));
        ciclo.getStyleClass().add("alerta-conflicto");
        aumentar.setMaxWidth(Double.MAX_VALUE);
        VBox inanicion = tarjeta(tituloEspera, alertaEspera, aumentar);
        inanicion.getStyleClass().add("alerta-inanicion");
        VBox panel = tarjeta(
                fila(
                        tituloIcono(Feather.LINK_2, "MONITOR DE DEADLOCKS", "titulo-tarjeta"),
                        espacio(),
                        cuentaConflictos
                ),
                parrafo("Selecciona dos pedidos pendientes con existencias suficientes. Ambos deben terminar su preparación antes de disputar el Área de carga 1 y el escáner. La simulación debe estar activa para avanzar.", "muted"),
                campos(campo("PEDIDO A", pedidoA), campo("PEDIDO B", pedidoB)),
                fila(preparar, cancelar),
                avisoConflicto,
                columnas(ciclo, inanicion, 50, 850)
        );
        panel.getStyleClass().add("monitor-admin");
        return panel;
    }

    /** Carga productos y muelles y define los nombres legibles de sus selectores. */
    private void configurarRecepcion() {
        recibido.getItems().setAll(CatalogoProductos.getProductos());
        recibido.getSelectionModel().selectFirst();
        recibido.setMaxWidth(Double.MAX_VALUE);
        recibido.setConverter(new StringConverter<>() {

            public String toString(Producto pedido) {
                return pedido == null ? "" : pedido.nombre() + " (" + pedido.tipoMercancia() + ")";
            }

            public Producto fromString(String texto) {
                return null;
            }
        });
        muelle.getItems().setAll(1, 2);
        muelle.setValue(1);
        muelle.setMaxWidth(Double.MAX_VALUE);
        muelle.setConverter(new StringConverter<>() {

            public String toString(Integer numero) {
                return numero == null ? "" : "Área de Carga " + numero;
            }

            public Integer fromString(String texto) {
                return null;
            }
        });
    }

    /** Convierte la cantidad a entero y envía la entrada a la cola de recepción. */
    private void registrarEntrada() {
        try {
            int cantidad = Integer.parseInt(lote.getText().strip());
            ultimaEntrada = centro.getRecepcion().enviar(recibido.getValue(), cantidad, muelle.getValue());
            mensaje(mensajeEntrada, "Entrada en cola de recepción.", false);
        } catch (NumberFormatException exception) {
            mensaje(mensajeEntrada, "Ingresa una cantidad entera positiva.", true);
        } catch (IllegalArgumentException exception) {
            mensaje(mensajeEntrada, exception.getMessage(), true);
        }
    }

    /** Muestra qué retiene cada pedido y habilita la recuperación solo cuando el ciclo está listo. */
    private void actualizarConflictos() {
        MonitorConflictos.Vista vista = centro.getConflictos().vista();
        cuentaConflictos.setText(vista.activo() ? "1 ACTIVO" : "0 ACTIVOS");
        tituloCiclo.setText(vista.activo() ? "ESPERA CIRCULAR EN DESPACHO" : "ESTADO DE CONFLICTOS");
        String detalle = vista.mensaje();
        if (vista.activo()) {
            detalle += "\nPedido " + centro.identificador(vista.pedidoA()) + " retiene Área de carga 1 y espera el Escáner.";
            detalle += "\nPedido " + centro.identificador(vista.pedidoB()) + " retiene el Escáner y espera Área de carga 1.";
        }
        conflicto.setText(detalle);
        continuarA.setText(vista.pedidoA() == null ? "Continuar A" : "Continuar " + centro.identificador(vista.pedidoA()));
        continuarB.setText(vista.pedidoB() == null ? "Continuar B" : "Continuar " + centro.identificador(vista.pedidoB()));
        continuarA.setDisable(!vista.listo());
        continuarB.setDisable(!vista.listo());
        actualizarPedidosConflicto(vista);
    }

    /** Muestra código, servicio y cantidad de los pedidos en los dos selectores. */
    private void configurarPedidosConflicto() {
        for (ComboBox<Pedido> selector : Arrays.asList(pedidoA, pedidoB)) {
            selector.setMaxWidth(Double.MAX_VALUE);
            selector.setPromptText("Selecciona un pedido pendiente");
            selector.setConverter(new StringConverter<Pedido>() {
                @Override
                public String toString(Pedido pedido) {
                    return pedido == null ? "" : centro.identificador(pedido) + ", " + pedido.nivelServicio()
                            + ", " + pedido.getTotalArticulos() + " unidades";
                }

                @Override
                public Pedido fromString(String texto) {
                    return null;
                }
            });
        }
    }

    /** Actualiza los candidatos cuando no hay escenario activo y conserva la selección válida. */
    private void actualizarPedidosConflicto(MonitorConflictos.Vista vista) {
        boolean enCurso = vista.activo() || vista.preparando() || vista.recuperando();
        pedidoA.setDisable(enCurso);
        pedidoB.setDisable(enCurso);
        preparar.setDisable(enCurso);
        cancelar.setDisable(!vista.preparando());
        if (!enCurso) {
            List<Pedido> pendientes = new ArrayList<>();
            for (MotorSimulacion.VistaPedido pedido : centro.getMotor().espera()) {
                if (pedido.estado() != EstadoPedido.EN_CONFLICTO) {
                    pendientes.add(pedido.pedido());
                }
            }
            for (ComboBox<Pedido> selector : Arrays.asList(pedidoA, pedidoB)) {
                if (!selector.getItems().equals(pendientes)) {
                    Pedido seleccionado = selector.getValue();
                    selector.getItems().setAll(pendientes);
                    selector.setValue(pendientes.contains(seleccionado) ? seleccionado : null);
                }
            }
        }
    }

    /** Programa un conflicto entre dos pedidos existentes y muestra errores de selección o stock. */
    private void prepararConflicto() {
        avisoConflicto.setText("");
        try {
            UUID idA = pedidoA.getValue() == null ? null : pedidoA.getValue().id();
            UUID idB = pedidoB.getValue() == null ? null : pedidoB.getValue().id();
            centro.getMotor().provocarConflicto(idA, idB);
        } catch (IllegalArgumentException exception) {
            mensaje(avisoConflicto, exception.getMessage(), true);
        }
        actualizar();
    }

    /** Devuelve los pedidos al flujo normal si el escenario todavía se está preparando. */
    private void cancelarPreparacion() {
        avisoConflicto.setText("");
        try {
            centro.getConflictos().cancelarPreparacion();
        } catch (IllegalArgumentException exception) {
            mensaje(avisoConflicto, exception.getMessage(), true);
        }
        actualizar();
    }

    /** Muestra la demora del candidato a recibir prioridad manual. */
    private void actualizarEspera() {
        Optional<MotorSimulacion.VistaPedido> demorado = demorado();
        aumentar.setDisable(demorado.isEmpty());
        tituloEspera.setText(demorado.isPresent() ? "TIEMPO OBJETIVO SUPERADO" : "TIEMPOS DE ESPERA");
        alertaEspera.setText(demorado.map(pedido -> "Pedido #" + centro.identificador(pedido.pedido()) + " (Nivel " + pedido.pedido().nivelServicio().getPrioridad() + ") lleva %.1fm en cola. Superó su tiempo objetivo.".formatted(pedido.espera())).orElse("No hay pedidos que superen su tiempo objetivo de espera."));
    }
}
