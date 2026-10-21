# APF3 — Integrador de Software: PixBen

## Arquitectura real

- Vista: repositorio `pixben-frontend` (HTML/CSS/JavaScript en Netlify).
- Controller: `ProductoController`, `CatalogoReporteController` (API REST Spring Boot).
- Model: `Producto` (entidad JPA); también hay modelos MongoDB.
- DAO/Repository: `ProductoRepository` (Spring Data JPA), `CarritoRepository` (Spring Data MongoDB).
- Service: `CatalogoExportacionService`, `AutenticacionService`, `ImagenSeguraService`.
- Seguridad: BCrypt para contraseñas, sesión de administración obligatoria para reportes, límites y cabeceras ya existentes.

Flujo del reporte:
`admin.html → admin.js → GET /admin/reportes/catalogo.xlsx → CatalogoReporteController → CatalogoExportacionService → ProductoRepository → PostgreSQL`

## SOLID: evidencia y límites

- **S**: La construcción del Excel reside en `CatalogoExportacionService`, separada de HTTP y autorización.
- **O**: Se amplía el sistema con un controlador nuevo sin cambiar los endpoints anteriores.
- **L**: Las implementaciones Spring Data respetan el contrato de su interfaz; no se afirma demostración exhaustiva de LSP.
- **I**: La dependencia de datos está acotada a la interfaz `ProductoRepository`; la interfaz es relativamente amplia por heredar JPA.
- **D**: El servicio recibe `ProductoRepository` por constructor, usando la abstracción y permitiendo mocks.

No se declara cumplidos los cinco principios en cada clase; el refactor integral de los controladores existentes es trabajo posterior.

## Librerías Maven y usos efectivos

- Apache Commons Lang3: `StringUtils.normalizeSpace/defaultString/abbreviate` para texto de productos.
- Google Guava: `ImmutableList` para columnas del reporte.
- Apache POI: `XSSFWorkbook` para generar XLSX.
- Logback (incluido vía `spring-boot-starter-web`, configurado en `logback-spring.xml`): logs con SLF4J.

## TDD

El historial de esta rama incluye primero la prueba `CatalogoExportacionServiceTest` (RED) y después la implementación (GREEN). Ejecutar `mvn test` para comprobar el resultado real. Otras pruebas existentes cubren variantes de carrito, paginación, concurrencia y seguridad.

## Comprobación segura

- `mvn test` (usa el perfil/configuración de pruebas; el contexto completo `PixbenApplicationTests` está deshabilitado si necesita servicios externos).
- `mvn -DskipTests package`
- Abrir en NetBeans `pom.xml`, `CatalogoExportacionService.java`, `CatalogoReporteController.java` y sus pruebas.
- Revisar las acciones de GitHub si hay ejecutores disponibles.
- No fusionar esta rama a `master` ni desplegar hasta verificar compilación, pruebas y funcionamiento del panel.

## Avance funcional

El 30%/40% se justifica con una matriz que compare el número de requisitos comprometidos en el informe del proyecto contra módulos comprobados. No se puede deducir el porcentaje únicamente de líneas de código.

## Seguridad

La descarga requiere sesión administrativa y se genera en memoria sin escribir archivos en almacenamiento de producción. Se limita a 10,000 filas; las cadenas potencialmente interpretables como fórmulas de hoja de cálculo se escriben como texto neutralizado.
