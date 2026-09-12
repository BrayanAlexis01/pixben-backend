PIXBen backend corregido - 14/07/2026

ABRIR EN NETBEANS
1. Extrae el ZIP.
2. Abre directamente la carpeta "pixben-backend". En esa misma carpeta deben estar pom.xml, Dockerfile, src e imagen.
3. Ejecuta Clean and Build. El test de contexto está deshabilitado y en local se usa H2 como respaldo.

SUBIR A GITHUB
La raíz del repositorio debe mostrar directamente:
- Dockerfile
- pom.xml
- mvnw
- mvnw.cmd
- src/
- imagen/
No debe existir una carpeta adicional llamada backend dentro del repositorio.

VARIABLES REQUERIDAS EN RENDER
- SPRING_DATASOURCE_URL (debe empezar por jdbc:postgresql://)
- SPRING_DATASOURCE_USERNAME
- SPRING_DATASOURCE_PASSWORD
- SPRING_DATA_MONGODB_URI
- CLOUDINARY_CLOUD_NAME
- CLOUDINARY_API_KEY
- CLOUDINARY_API_SECRET

VERIFICACIÓN DESPUÉS DEL DEPLOY
1. /healthz debe responder status=ok y version=pixben-backend-2026-07-14
2. /mongo-info debe responder pixben
3. /contactos/admin/todos debe responder [] o una lista
4. /pedidos/admin/todos debe responder [] o una lista
5. /pedidos-personalizados/admin/todos debe responder [] o una lista
6. /imagen/polomessi.webp debe abrir la imagen antigua

FUNCIONES INCLUIDAS
- Galería de 1 a 7 imágenes por producto en Cloudinary.
- La primera imagen queda como portada del producto.
- URLs de galería guardadas en MongoDB Atlas.
- Pedidos normales.
- Pedidos personalizados con cotización y estados.
- Mensajes de contacto visibles en el panel administrador.
- Carrito, favoritos e historial en MongoDB Atlas.
- CORS global para Netlify y pruebas locales.
- Compatibilidad con imágenes antiguas de la carpeta imagen/.
