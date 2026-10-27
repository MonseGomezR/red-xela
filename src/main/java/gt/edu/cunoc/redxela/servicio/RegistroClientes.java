package gt.edu.cunoc.redxela.servicio;

import gt.edu.cunoc.redxela.modelo.Cliente;
import gt.edu.cunoc.redxela.modelo.NivelServicio;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Registro de cuentas en memoria durante una sesión de simulación.
 */
public final class RegistroClientes {

    private final Map<UUID, Cliente> clientes = new LinkedHashMap<>();

    public RegistroClientes() {
    }

    /**
     * La validación y el alta son atómicas para no duplicar correos entre hilos.
     */
    public synchronized Cliente registrar(String nombre, String correo, NivelServicio nivelServicio) {
        Cliente cliente = new Cliente(UUID.randomUUID(), nombre, correo, nivelServicio);
        for (Cliente existente : clientes.values()) {
            if (existente.correo().equals(cliente.correo())) {
                throw new IllegalArgumentException("Ya existe un cliente con ese correo.");
            }
        }
        clientes.put(cliente.id(), cliente);
        return cliente;
    }

    /** Devuelve la cuenta si existe; Optional vacío indica que no se encontró. */
    public synchronized Optional<Cliente> buscarPorId(UUID id) {
        return Optional.ofNullable(clientes.get(id));
    }

    /**
     * Devuelve una instantánea en orden de registro, sin exponer el mapa interno.
     */
    public synchronized List<Cliente> getClientes() {
        return List.copyOf(clientes.values());
    }
}
