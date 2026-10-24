module gt.edu.cunoc.redxela {
    requires javafx.controls;
    requires javafx.fxml;
    requires org.kordamp.ikonli.javafx;
    requires org.kordamp.ikonli.feather;

    opens gt.edu.cunoc.redxela.controlador to javafx.fxml;

    exports gt.edu.cunoc.redxela;
    exports gt.edu.cunoc.redxela.modelo;
    exports gt.edu.cunoc.redxela.catalogo;
    exports gt.edu.cunoc.redxela.servicio;
}
