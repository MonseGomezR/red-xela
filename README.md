# REDXela

Aplicación de escritorio para simular la recepción, planificación y despacho
de pedidos de un centro de distribución. Desarrollada en Java con vistas
JavaFX/FXML y procesamiento concurrente previsto para las operaciones del centro.

## Estado de desarrollo

La aplicación tiene una ventana inicial y un modelo de niveles de servicio,
recursos, tipos de mercancía y pedidos. Incluye un catálogo fijo de cinco productos,
detalle por producto, cantidades positivas, identificadores únicos y total de
artículos. La ejecución de pedidos y la administración concurrente de recursos
están pendientes. Las cuentas de clientes y los formularios se incorporarán
en los siguientes avances.

## Herramientas

- JDK 21 o superior (se compila para Java 21).
- Apache Maven 3.9 o superior.
- JavaFX 21.0.2, descargado automáticamente por Maven.
- Conexión a Internet en la primera compilación para obtener dependencias.

Las versiones de las dependencias están fijadas en `pom.xml`.

## Compilar y ejecutar

Desde la carpeta que contiene `pom.xml`:

```powershell
java -version
mvn -version
mvn clean compile
mvn javafx:run
```

Debe abrirse una ventana titulada **REDXela - Simulador de distribución**, con
el nombre del proyecto y el estado del avance. Al cerrarla termina la aplicación.
Este proyecto usa módulos de Java; se ejecuta mediante Maven, no con `java -jar`.

Si Maven indica que `JAVA_HOME` no está bien definido, debe apuntar a la carpeta
del JDK, sin espacios al inicio y sin agregar `bin`. Para esta instalación local,
puedes corregirlo temporalmente en PowerShell:

```powershell
$env:JAVA_HOME = 'C:\Program Files\Apache NetBeans\jdk'
mvn -version
```

En NetBeans: **Archivo > Abrir proyecto**, seleccionar esta carpeta y usar un
JDK compatible. Para arrancar, hacer clic derecho sobre **REDXela > Ejecutar**.
El archivo `nbactions.xml` configura la acción **Ejecutar proyecto** para usar
`javafx:run`, que prepara la ruta de módulos y las dependencias de JavaFX.
Utilizar la ejecución del proyecto completo, no **Ejecutar archivo** sobre
`RedXelaApplication.java`.

## Pruebas

```powershell
mvn test
```

Las pruebas de dominio se ejecutan sin abrir la interfaz gráfica.

## Organización

```text
pom.xml                         Dependencias y compilación
src/main/java/                  Aplicación, modelo y descriptor del módulo
src/main/resources/             Vistas FXML
src/test/java/                  Pruebas del modelo de dominio
```

## Forma de trabajo

Cada commit agrupa un cambio funcional pequeño con su comprobación. Los archivos
generados de `target/` y la documentación local de `docs/` se excluyen de Git.
