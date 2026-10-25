package gt.edu.cunoc.redxela.servicio;

/** Guarda las medidas de una política dentro de una comparación. */
public final class ResultadoComparacion {

    private final int ejecucion;
    private final PoliticaAtencion politica;
    private final int pedidos;
    private final MotorSimulacion.Estadisticas estadisticas;

    public ResultadoComparacion(int ejecucion, PoliticaAtencion politica, int pedidos,
            MotorSimulacion.Estadisticas estadisticas) {
        this.ejecucion = ejecucion;
        this.politica = politica;
        this.pedidos = pedidos;
        this.estadisticas = estadisticas;
    }

    public int getEjecucion() {
        return ejecucion;
    }

    public PoliticaAtencion getPolitica() {
        return politica;
    }

    public int getPedidos() {
        return pedidos;
    }

    public int getCompletados() {
        return estadisticas.completados();
    }

    public int getPendientes() {
        return pedidos - estadisticas.completados();
    }

    public double getEsperaMedia() {
        return estadisticas.esperaMedia();
    }

    public double getProcesoMedio() {
        return estadisticas.procesoMedio();
    }

    public double getOcupacionMedia() {
        return estadisticas.ocupacionMedia();
    }

    public double getMinutos() {
        return estadisticas.minutos();
    }
}
