package gt.edu.cunoc.redxela.servicio;

public enum PoliticaAtencion {
    FIFO("FIFO, orden de llegada"), SJF("SJF, menor trabajo"),
    SERVICIO("Nivel de servicio"), ENVEJECIMIENTO("Servicio con envejecimiento");
    private final String nombre;
    PoliticaAtencion(String nombre) { this.nombre = nombre; }
    @Override public String toString() { return nombre; }
}
