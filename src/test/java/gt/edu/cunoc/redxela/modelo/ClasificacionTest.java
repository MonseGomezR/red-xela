package gt.edu.cunoc.redxela.modelo;

import java.time.Duration;
import java.util.Arrays;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ClasificacionTest {
    @Test
    void distingueAtencionInmediataDeLosPlazosDeEspera() {
        assertTrue(NivelServicio.EXPRES.esInmediato());
        assertTrue(NivelServicio.EXPRES.getEsperaObjetivo().isEmpty());
        var plazos = Map.of(NivelServicio.PRIORITARIO, 10,
                NivelServicio.ESTANDAR, 30, NivelServicio.PROGRAMADO, 60,
                NivelServicio.ECONOMICO, 120);
        plazos.forEach((nivel, minutos) -> {
            assertFalse(nivel.esInmediato());
            assertEquals(Duration.ofMinutes(minutos), nivel.getEsperaObjetivo().orElseThrow());
        });
        assertEquals(Arrays.asList(1, 2, 3, 4, 5), Arrays.stream(NivelServicio.values())
                .map(NivelServicio::getPrioridad).toList());
    }

    @Test
    void cadaMercanciaTieneUnaCombinacionDistintaYFactible() {
        assertEquals(5, TipoMercancia.values().length);
        assertEquals(5L, Arrays.stream(TipoMercancia.values())
                .map(tipo -> tipo.getRecursosRequeridos().keySet()).distinct().count());
        for (TipoMercancia tipo : TipoMercancia.values()) {
            assertFalse(tipo.getDescripcion().isBlank());
            tipo.getRecursosRequeridos().forEach((recurso, cantidad) ->
                    assertTrue(cantidad > 0 && cantidad <= recurso.getCapacidad()));
        }
    }

    @Test
    void lasNecesidadesNoSePuedenModificarDesdeFuera() {
        assertThrows(UnsupportedOperationException.class, () ->
                TipoMercancia.GENERAL.getRecursosRequeridos().put(TipoRecurso.ESCANER, 1));
    }

    @Test
    void fragilYElectronicaCompartenRecursosParaEscenariosDeContencion() {
        for (TipoRecurso recurso : new TipoRecurso[]{
                TipoRecurso.ESTACION_EMPAQUE, TipoRecurso.CONTROL_CALIDAD}) {
            assertTrue(TipoMercancia.FRAGIL.getRecursosRequeridos().containsKey(recurso));
            assertTrue(TipoMercancia.ELECTRONICA.getRecursosRequeridos().containsKey(recurso));
        }
    }

    @Test
    void conservaLasCapacidadesDelCentro() {
        assertEquals(6, TipoRecurso.ESTACION_EMPAQUE.getCapacidad());
        assertEquals(2, TipoRecurso.AREA_CARGA.getCapacidad());
        assertEquals(10, TipoRecurso.ENCARGADO_BODEGA.getCapacidad());
        assertEquals(3, TipoRecurso.MONTACARGAS.getCapacidad());
        assertEquals(4, TipoRecurso.CONTROL_CALIDAD.getCapacidad());
        assertEquals(1, TipoRecurso.ESCANER.getCapacidad());
    }
}
