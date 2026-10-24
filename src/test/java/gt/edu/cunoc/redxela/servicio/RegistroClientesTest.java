package gt.edu.cunoc.redxela.servicio;

import gt.edu.cunoc.redxela.modelo.NivelServicio;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

class RegistroClientesTest {
    private final RegistroClientes registro = new RegistroClientes();

    @Test
    void registraLaCuentaConElServicioContratadoYDatosNormalizados() {
        var cliente = registro.registrar("  Ana López  ", " ANA@EJEMPLO.COM ", NivelServicio.PRIORITARIO);
        assertEquals("Ana López", cliente.nombre());
        assertEquals("ana@ejemplo.com", cliente.correo());
        assertEquals(NivelServicio.PRIORITARIO, cliente.nivelServicio());
        assertEquals(cliente, registro.buscarPorId(cliente.id()).orElseThrow());
        assertTrue(registro.buscarPorId(UUID.randomUUID()).isEmpty());
    }

    @Test
    void rechazaElMismoCorreoSinDejarUnaSegundaCuenta() {
        var cliente = registro.registrar("Ana", "ana@ejemplo.com", NivelServicio.EXPRES);
        assertThrows(IllegalArgumentException.class, () ->
                registro.registrar("Otra persona", " ANA@EJEMPLO.COM ", NivelServicio.ECONOMICO));
        assertEquals(java.util.List.of(cliente), registro.getClientes());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "ana", "ana@", "@ejemplo.com", "ana@ejemplo", "a b@ejemplo.com", "a@b@ejemplo.com"})
    void rechazaCorreosInvalidosSinModificarElRegistro(String correo) {
        assertThrows(IllegalArgumentException.class, () ->
                registro.registrar("Ana", correo, NivelServicio.ESTANDAR));
        assertTrue(registro.getClientes().isEmpty());
    }

    @Test
    void rechazaUnNombreVacioYUnServicioAusente() {
        assertThrows(IllegalArgumentException.class, () ->
                registro.registrar(" ", "ana@ejemplo.com", NivelServicio.ESTANDAR));
        assertThrows(NullPointerException.class, () -> registro.registrar("Ana", "ana@ejemplo.com", null));
        assertTrue(registro.getClientes().isEmpty());
    }

    @Test
    void laListaEsUnaInstantaneaInmutableYConservaElOrdenDeRegistro() {
        var primero = registro.registrar("Ana", "ana@ejemplo.com", NivelServicio.EXPRES);
        var instantanea = registro.getClientes();
        var segundo = registro.registrar("Luis", "luis@ejemplo.com", NivelServicio.ESTANDAR);
        assertEquals(java.util.List.of(primero), instantanea);
        assertEquals(java.util.List.of(primero, segundo), registro.getClientes());
        assertNotEquals(primero.id(), segundo.id());
        assertThrows(UnsupportedOperationException.class, instantanea::clear);
    }
}
