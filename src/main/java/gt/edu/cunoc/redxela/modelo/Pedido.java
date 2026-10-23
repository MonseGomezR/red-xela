package gt.edu.cunoc.redxela.modelo;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Datos inmutables de un pedido, independientes de su procesamiento. */
public record Pedido(UUID id, UUID clienteId, NivelServicio nivelServicio,
                     List<DetallePedido> detalles, EstadoPedido estado, Instant creadoEn) {
    public Pedido {
        Objects.requireNonNull(id, "El identificador del pedido es obligatorio.");
        Objects.requireNonNull(clienteId, "El cliente es obligatorio.");
        Objects.requireNonNull(nivelServicio, "El nivel de servicio es obligatorio.");
        Objects.requireNonNull(estado, "El estado es obligatorio.");
        Objects.requireNonNull(creadoEn, "La fecha de creación es obligatoria.");
        // La copia impide que el formulario o un trabajador modifique la lista original.
        detalles = List.copyOf(Objects.requireNonNull(detalles, "Los detalles son obligatorios."));
        if (detalles.isEmpty()) {
            throw new IllegalArgumentException("El pedido debe contener al menos un producto.");
        }
        var codigos = new HashSet<String>();
        int total = 0;
        for (DetallePedido detalle : detalles) {
            if (!codigos.add(detalle.producto().codigo())) {
                throw new IllegalArgumentException("El producto " + detalle.producto().codigo()
                        + " está repetido; utiliza un solo detalle con su cantidad total.");
            }
            try {
                total = Math.addExact(total, detalle.cantidad());
            } catch (ArithmeticException exception) {
                throw new IllegalArgumentException("La cantidad total de artículos es demasiado grande.", exception);
            }
        }
    }

    /** Crea un pedido registrado; todavía no reserva inventario ni recursos. */
    public static Pedido crear(UUID clienteId, NivelServicio nivelServicio, List<DetallePedido> detalles) {
        return new Pedido(UUID.randomUUID(), clienteId, nivelServicio,
                detalles, EstadoPedido.REGISTRADO, Instant.now());
    }

    /** Estimación base de trabajo que utilizarán las políticas de planificación. */
    public int getTotalArticulos() {
        return detalles.stream().mapToInt(DetallePedido::cantidad).sum();
    }
}
