package gt.edu.cunoc.redxela.servicio;

import gt.edu.cunoc.redxela.modelo.DetallePedido;
import gt.edu.cunoc.redxela.modelo.Producto;
import java.util.*;

/**
 * Almacén de dos pasillos, dos niveles y doce posiciones por nivel.
 * synchronized evita que dos hilos reserven o descuenten las mismas unidades al mismo tiempo.
 */
public final class Almacen {

    public static final int PASILLOS = 2;
    public static final int NIVELES = 2;
    public static final int COLUMNAS = 12;
    public static final int UNIDADES_POR_CELDA = 20;
    private final List<List<Tramo>> celdas = new ArrayList<>();
    private final Map<UUID, List<Asignacion>> reservas = new HashMap<>();

    /** Parte de un lote que ocupa una celda; puede tener unidades apartadas para pedidos. */
    private static final class Tramo {

        private final UUID lote;
        private final Producto producto;
        private int cantidad;
        private int reservado;

        private Tramo(UUID lote, Producto producto, int cantidad) {
            this.lote = lote;
            this.producto = producto;
            this.cantidad = cantidad;
        }
    }

    /** Indica de qué parte del lote se apartaron las unidades de un pedido. */
    private record Asignacion(Tramo tramo, int cantidad) {
    }

    /** Datos de una ubicación que se muestran en el mapa del almacén. */
    public record Celda(
            int indice,
            String ubicacion,
            String producto,
            int unidades,
            int reservadas,
            List<String> lotes
    ) {
    }

    public record Resumen(int capacidad, int unidades, int reservadas, int libres, double fragmentacion) {
    }

    /** Crea 48 ubicaciones vacías con capacidad total de 960 unidades. */
    public Almacen() {
        for (int i = 0; i < PASILLOS * NIVELES * COLUMNAS; i++) {
            celdas.add(new ArrayList<>());
        }
    }

    /**
     * Planifica toda la entrada antes de modificar existencias. Un lote puede quedar fragmentado.
     */
    public synchronized UUID recibir(Producto producto, int cantidad) {
        Objects.requireNonNull(producto);
        if (cantidad <= 0) {
            throw new IllegalArgumentException("La cantidad recibida debe ser positiva.");
        }
        int espacio = 0;
        for (List<Tramo> celda : celdas) {
            if (admite(celda, producto)) {
                espacio += UNIDADES_POR_CELDA - unidades(celda);
            }
        }
        if (cantidad > espacio) {
            throw new IllegalArgumentException("No hay espacio suficiente para ese lote.");
        }
        UUID lote = UUID.randomUUID();
        int pendiente = cantidad;
        // Primero completa ubicaciones del mismo producto y luego utiliza las libres.
        for (int pasada = 0; pasada < 2; pasada++) {
            boolean vacias = pasada == 1;
            for (List<Tramo> celda : celdas) {
                if (pendiente == 0) {
                    break;
                }
                if (celda.isEmpty() != vacias || !admite(celda, producto)) {
                    continue;
                }
                int unidadesAsignadas = Math.min(pendiente, UNIDADES_POR_CELDA - unidades(celda));
                if (unidadesAsignadas > 0) {
                    celda.add(new Tramo(lote, producto, unidadesAsignadas));
                }
                pendiente -= unidadesAsignadas;
            }
        }
        return lote;
    }

    /** Cuenta todas las unidades del producto, incluidas las que están reservadas. */
    public synchronized int stock(Producto producto) {
        int total = 0;
        for (List<Tramo> celda : celdas) {
            for (Tramo tramo : celda) {
                if (tramo.producto.equals(producto)) {
                    total += tramo.cantidad;
                }
            }
        }
        return total;
    }

    /** Cuenta solo lo que todavía se puede asignar a otro pedido. */
    public synchronized int disponible(Producto producto) {
        int total = 0;
        for (List<Tramo> celda : celdas) {
            for (Tramo tramo : celda) {
                if (tramo.producto.equals(producto)) {
                    total += tramo.cantidad - tramo.reservado;
                }
            }
        }
        return total;
    }

    /** Devuelve las cantidades de todos los productos en una sola lectura. */
    public synchronized Map<Producto, Integer> existencias() {
        Map<Producto, Integer> cantidades = new LinkedHashMap<>();
        for (List<Tramo> celda : celdas) {
            for (Tramo tramo : celda) {
                int anterior = cantidades.getOrDefault(tramo.producto, 0);
                cantidades.put(tramo.producto, anterior + tramo.cantidad);
            }
        }
        return cantidades;
    }

    /**
     * Reserva todos los detalles o ninguno; nunca deja una reserva parcial por falta de stock.
     */
    public synchronized boolean reservar(UUID pedido, List<DetallePedido> detalles) {
        Objects.requireNonNull(pedido);
        if (reservas.containsKey(pedido)) {
            return true;
        }
        if (detalles.isEmpty()) {
            throw new IllegalArgumentException("La reserva necesita productos.");
        }
        Map<Producto, Long> cantidades = new HashMap<>();
        for (DetallePedido detalle : detalles) {
            long anterior = cantidades.getOrDefault(detalle.producto(), 0L);
            cantidades.put(detalle.producto(), anterior + detalle.cantidad());
        }
        for (Map.Entry<Producto, Long> productoReservado : cantidades.entrySet()) {
            if (disponible(productoReservado.getKey()) < productoReservado.getValue()) {
                return false;
            }
        }
        List<Asignacion> asignaciones = new ArrayList<>();
        for (Map.Entry<Producto, Long> productoReservado : cantidades.entrySet()) {
            long pendiente = productoReservado.getValue();
            for (List<Tramo> celda : celdas) {
                for (Tramo tramo : celda) {
                    if (!tramo.producto.equals(productoReservado.getKey()) || pendiente == 0) {
                        continue;
                    }
                    int unidadesAsignadas = (int) Math.min(pendiente, tramo.cantidad - tramo.reservado);
                    if (unidadesAsignadas > 0) {
                        tramo.reservado += unidadesAsignadas;
                        asignaciones.add(new Asignacion(tramo, unidadesAsignadas));
                        pendiente -= unidadesAsignadas;
                    }
                }
            }
        }
        reservas.put(pedido, asignaciones);
        return true;
    }

    /** Devuelve las unidades reservadas a disponibilidad sin descontar existencias. */
    public synchronized void liberar(UUID pedido) {
        List<Asignacion> asignaciones = reservas.remove(pedido);
        if (asignaciones != null) {
            for (Asignacion asignacion : asignaciones) {
                asignacion.tramo.reservado -= asignacion.cantidad;
            }
        }
    }

    /** Descuenta las unidades una sola vez, cuando termina el despacho del pedido. */
    public synchronized void despachar(UUID pedido) {
        List<Asignacion> asignaciones = reservas.remove(pedido);
        if (asignaciones == null) {
            throw new IllegalStateException("El pedido no tiene una reserva de inventario.");
        }
        for (Asignacion asignacion : asignaciones) {
            asignacion.tramo.reservado -= asignacion.cantidad;
            asignacion.tramo.cantidad -= asignacion.cantidad;
        }
        for (List<Tramo> celda : celdas) {
            for (int i = celda.size() - 1; i >= 0; i--) {
                if (celda.get(i).cantidad == 0) {
                    celda.remove(i);
                }
            }
        }
    }

    /**
     * Compacta sin cambiar productos, cantidades o identidad de los lotes.
     */
    public synchronized void optimizar() {
        if (!reservas.isEmpty()) {
            throw new IllegalStateException("Espera a que terminen las reservas antes de optimizar.");
        }
        List<Tramo> tramos = new ArrayList<>();
        for (List<Tramo> celda : celdas) {
            tramos.addAll(celda);
            celda.clear();
        }
        tramos.sort(Comparator.comparing(tramo -> tramo.producto.codigo()));
        int indice = 0;
        for (Tramo original : tramos) {
            int pendiente = original.cantidad;
            while (pendiente > 0) {
                List<Tramo> celda = celdas.get(indice);
                if (!admite(celda, original.producto) || unidades(celda) == UNIDADES_POR_CELDA) {
                    indice++;
                    continue;
                }
                int unidadesAsignadas = Math.min(pendiente, UNIDADES_POR_CELDA - unidades(celda));
                celda.add(new Tramo(original.lote, original.producto, unidadesAsignadas));
                pendiente -= unidadesAsignadas;
            }
        }
    }

    /** Convierte las ubicaciones y sus lotes a datos de lectura para la pantalla. */
    public synchronized List<Celda> getCeldas() {
        List<Celda> resultado = new ArrayList<>();
        for (int i = 0; i < celdas.size(); i++) {
            List<Tramo> celda = celdas.get(i);
            int celdasPorPasillo = NIVELES * COLUMNAS;
            String pasillo = i < celdasPorPasillo ? "A" : "B";
            int nivel = (i % celdasPorPasillo) / COLUMNAS + 1;
            int columna = i % COLUMNAS + 1;
            String ubicacion = "%s-%d-%02d".formatted(pasillo, nivel, columna);
            int reservadas = 0;
            List<String> lotes = new ArrayList<>();
            for (Tramo tramo : celda) {
                reservadas += tramo.reservado;
                lotes.add(tramo.lote + ": " + tramo.cantidad + "u");
            }
            resultado.add(new Celda(
                    i,
                    ubicacion,
                    celda.isEmpty() ? "" : celda.get(0).producto.codigo(),
                    unidades(celda),
                    reservadas,
                    List.copyOf(lotes)
            ));
        }
        return List.copyOf(resultado);
    }

    /** Mide la capacidad, las reservas y el espacio libre fuera del mayor bloque seguido. */
    public synchronized Resumen resumen() {
        int libres = 0;
        int mayorBloque = 0;
        int bloque = 0;
        int total = 0;
        int reservado = 0;
        for (List<Tramo> celda : celdas) {
            total += unidades(celda);
            for (Tramo tramo : celda) {
                reservado += tramo.reservado;
            }
            if (celda.isEmpty()) {
                libres++;
                bloque++;
                mayorBloque = Math.max(mayorBloque, bloque);
            } else {
                bloque = 0;
            }
        }
        double fragmentacion = libres == 0 ? 0 : 100.0 * (libres - mayorBloque) / libres;
        return new Resumen(celdas.size() * UNIDADES_POR_CELDA, total, reservado, libres, fragmentacion);
    }

    /** Una celda puede estar vacía o guardar lotes del mismo producto. */
    private boolean admite(List<Tramo> celda, Producto producto) {
        return celda.isEmpty() || celda.get(0).producto.equals(producto);
    }

    /** Suma las unidades de todos los lotes que comparten una celda. */
    private int unidades(List<Tramo> celda) {
        int total = 0;
        for (Tramo tramo : celda) {
            total += tramo.cantidad;
        }
        return total;
    }
}
