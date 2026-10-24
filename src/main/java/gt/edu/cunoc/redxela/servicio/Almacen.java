package gt.edu.cunoc.redxela.servicio;

import gt.edu.cunoc.redxela.modelo.DetallePedido;
import gt.edu.cunoc.redxela.modelo.Producto;
import java.util.*;

/** Almacén de dos pasillos, dos niveles y doce posiciones por nivel. */
public final class Almacen {
    public static final int PASILLOS = 2, NIVELES = 2, COLUMNAS = 12, UNIDADES_POR_CELDA = 20;
    private final List<List<Tramo>> celdas = new ArrayList<>();
    private final Map<UUID, List<Asignacion>> reservas = new HashMap<>();

    private static final class Tramo {
        final UUID lote;
        final Producto producto;
        int cantidad, reservado;
        Tramo(UUID lote, Producto producto, int cantidad) {
            this.lote = lote; this.producto = producto; this.cantidad = cantidad;
        }
    }
    private record Asignacion(Tramo tramo, int cantidad) { }
    public record Celda(int indice, String ubicacion, String producto, int unidades, int reservadas,
                        List<String> lotes) { }
    public record Resumen(int capacidad, int unidades, int reservadas, int libres, double fragmentacion) { }

    public Almacen() {
        for (int i = 0; i < PASILLOS * NIVELES * COLUMNAS; i++) celdas.add(new ArrayList<>());
    }

    /** Planifica toda la entrada antes de modificar existencias. Un lote puede quedar fragmentado. */
    public synchronized UUID recibir(Producto producto, int cantidad) {
        Objects.requireNonNull(producto);
        if (cantidad <= 0) throw new IllegalArgumentException("La cantidad recibida debe ser positiva.");
        int espacio = celdas.stream().filter(c -> admite(c, producto))
                .mapToInt(c -> UNIDADES_POR_CELDA - unidades(c)).sum();
        if (cantidad > espacio) throw new IllegalArgumentException("No hay espacio suficiente para ese lote.");
        UUID lote = UUID.randomUUID();
        int pendiente = cantidad;
        // Primero completa ubicaciones del mismo producto y luego utiliza las libres.
        for (boolean vacias : new boolean[]{false, true}) {
            for (List<Tramo> celda : celdas) {
                if (pendiente == 0) break;
                if (celda.isEmpty() != vacias || !admite(celda, producto)) continue;
                int tomar = Math.min(pendiente, UNIDADES_POR_CELDA - unidades(celda));
                if (tomar > 0) celda.add(new Tramo(lote, producto, tomar));
                pendiente -= tomar;
            }
        }
        return lote;
    }

    public synchronized int stock(Producto producto) {
        return celdas.stream().flatMap(Collection::stream).filter(t -> t.producto.equals(producto))
                .mapToInt(t -> t.cantidad).sum();
    }

    public synchronized int disponible(Producto producto) {
        return celdas.stream().flatMap(Collection::stream).filter(t -> t.producto.equals(producto))
                .mapToInt(t -> t.cantidad - t.reservado).sum();
    }

    /** Reserva todos los detalles o ninguno; nunca deja una reserva parcial por falta de stock. */
    public synchronized boolean reservar(UUID pedido, List<DetallePedido> detalles) {
        Objects.requireNonNull(pedido);
        if (reservas.containsKey(pedido)) return true;
        if (detalles.isEmpty()) throw new IllegalArgumentException("La reserva necesita productos.");
        Map<Producto, Long> cantidades = new HashMap<>();
        for (DetallePedido detalle : detalles)
            cantidades.merge(detalle.producto(), (long) detalle.cantidad(), Long::sum);
        for (var item : cantidades.entrySet())
            if (disponible(item.getKey()) < item.getValue()) return false;
        List<Asignacion> asignaciones = new ArrayList<>();
        for (var item : cantidades.entrySet()) {
            long pendiente = item.getValue();
            for (List<Tramo> celda : celdas) for (Tramo tramo : celda) {
                if (!tramo.producto.equals(item.getKey()) || pendiente == 0) continue;
                int tomar = (int) Math.min(pendiente, tramo.cantidad - tramo.reservado);
                if (tomar > 0) {
                    tramo.reservado += tomar;
                    asignaciones.add(new Asignacion(tramo, tomar));
                    pendiente -= tomar;
                }
            }
        }
        reservas.put(pedido, asignaciones);
        return true;
    }

    public synchronized void liberar(UUID pedido) {
        List<Asignacion> asignaciones = reservas.remove(pedido);
        if (asignaciones != null)
            asignaciones.forEach(a -> a.tramo.reservado -= a.cantidad);
    }

    public synchronized void despachar(UUID pedido) {
        List<Asignacion> asignaciones = reservas.remove(pedido);
        if (asignaciones == null) throw new IllegalStateException("El pedido no tiene una reserva de inventario.");
        for (Asignacion a : asignaciones) {
            a.tramo.reservado -= a.cantidad;
            a.tramo.cantidad -= a.cantidad;
        }
        celdas.forEach(c -> c.removeIf(t -> t.cantidad == 0));
    }

    /** Compacta sin cambiar productos, cantidades o identidad de los lotes. */
    public synchronized void optimizar() {
        if (!reservas.isEmpty()) throw new IllegalStateException("Espera a que terminen las reservas antes de optimizar.");
        List<Tramo> tramos = celdas.stream().flatMap(Collection::stream)
                .sorted(Comparator.comparing(t -> t.producto.codigo())).toList();
        celdas.forEach(List::clear);
        int indice = 0;
        for (Tramo original : tramos) {
            int pendiente = original.cantidad;
            while (pendiente > 0) {
                List<Tramo> celda = celdas.get(indice);
                if (!admite(celda, original.producto) || unidades(celda) == UNIDADES_POR_CELDA) { indice++; continue; }
                int tomar = Math.min(pendiente, UNIDADES_POR_CELDA - unidades(celda));
                celda.add(new Tramo(original.lote, original.producto, tomar));
                pendiente -= tomar;
            }
        }
    }

    public synchronized List<Celda> getCeldas() {
        List<Celda> resultado = new ArrayList<>();
        for (int i = 0; i < celdas.size(); i++) {
            List<Tramo> celda = celdas.get(i);
            String ubicacion = "%s-%d-%02d".formatted(i < 24 ? "A" : "B", (i % 24) / 12 + 1, i % 12 + 1);
            resultado.add(new Celda(i, ubicacion, celda.isEmpty() ? "" : celda.get(0).producto.codigo(),
                    unidades(celda), celda.stream().mapToInt(t -> t.reservado).sum(),
                    celda.stream().map(t -> t.lote + ": " + t.cantidad + "u").toList()));
        }
        return List.copyOf(resultado);
    }

    public synchronized Resumen resumen() {
        int libres = 0, mayorBloque = 0, bloque = 0, total = 0, reservado = 0;
        for (List<Tramo> celda : celdas) {
            total += unidades(celda);
            reservado += celda.stream().mapToInt(t -> t.reservado).sum();
            if (celda.isEmpty()) { libres++; bloque++; mayorBloque = Math.max(mayorBloque, bloque); }
            else bloque = 0;
        }
        double fragmentacion = libres == 0 ? 0 : 100.0 * (libres - mayorBloque) / libres;
        return new Resumen(celdas.size() * UNIDADES_POR_CELDA, total, reservado, libres, fragmentacion);
    }

    private boolean admite(List<Tramo> celda, Producto producto) {
        return celda.isEmpty() || celda.get(0).producto.equals(producto);
    }
    private int unidades(List<Tramo> celda) { return celda.stream().mapToInt(t -> t.cantidad).sum(); }
}
