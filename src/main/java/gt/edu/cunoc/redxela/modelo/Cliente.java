package gt.edu.cunoc.redxela.modelo;

import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Cuenta de cliente y servicio contratado para sus nuevos pedidos.
 */
public record Cliente(UUID id, String nombre, String correo, NivelServicio nivelServicio) {

    private static final Pattern CORREO_VALIDO = Pattern.compile("[^\\s@]+@[^\\s@]+\\.[^\\s@]+", Pattern.UNICODE_CHARACTER_CLASS);
    public Cliente {
        Objects.requireNonNull(id, "El identificador del cliente es obligatorio.");
        Objects.requireNonNull(nombre, "El nombre es obligatorio.");
        Objects.requireNonNull(correo, "El correo es obligatorio.");
        Objects.requireNonNull(nivelServicio, "El nivel de servicio es obligatorio.");
        nombre = nombre.strip();
        correo = correo.strip().toLowerCase(Locale.ROOT);
        if (nombre.isEmpty()) {
            throw new IllegalArgumentException("Ingresa el nombre del cliente.");
        }
        if (!CORREO_VALIDO.matcher(correo).matches()) {
            throw new IllegalArgumentException("Ingresa un correo válido, por ejemplo nombre@dominio.com.");
        }
    }

    @Override
    public String toString() {
        return nombre + " (" + correo + ")";
    }
}
