# Auditoría de seguridad, rendimiento y escalabilidad de PixBen
**Fecha:** 2026-11-06  
**Alcance:** `pixben-backend` (Spring Boot) y `pixben-frontend` (Netlify, HTML/CSS/JS).  
**Objetivo inicial:** preparación para ~100 visitantes/día; no equivale a 100 usuarios concurrentes.

## Cambios implementados

| Prioridad | Repositorio | Resultado |
| --- | --- | --- |
| Alta | Backend | Retirados controladores de diagnóstico que exponían el listado de usuarios y el nombre interno de la base MongoDB mediante rutas públicas. |
| Alta | Backend | El endpoint `/productos/personalizables` usa `personalizable = true` como única fuente de verdad y consulta en PostgreSQL, sin cargar todo el catálogo ni inferir el permiso del texto de la descripción. |
| Media | Frontend | El personalizador consulta `/productos/personalizables` y comprueba adicionalmente que la bandera sea `true`. |
| Media | Backend | Activada la compresión de respuestas HTTP mayores de 1 KB. |
| Media | Backend | Añadido rate limit a `POST /visitas` para evitar escrituras excesivas en MongoDB. |
| Media | Frontend | Service worker con caché separada para imágenes, limitada a 100 recursos. Las imágenes ya guardadas no se descargan otra vez en cada visita; las páginas y scripts siguen priorizando la red. |
| Tests | Ambos | Pruebas de regresión JPA, validación de rate limit, contratos de frontend y ajuste de la prueba de enlace a FAQ tras el rediseño de Contacto. |

## Hallazgos positivos
- Catálogo con paginación SQL en `/productos/pagina`, límite de 48 productos por página y caché Caffeine.
- Validación de precios y existencias de productos en servidor; el stock se modifica bajo bloqueo transaccional al confirmar pedidos.
- Roles y sesiones administrativas validados en la mayoría de rutas de gestión; contraseñas protegidas en serialización JSON.
- CORS con origen de PixBen definido explícitamente; las credenciales sensibles están previstas para variables de entorno.
- Backend y frontend desacoplados; se pueden ampliar de forma independiente.

## Riesgos pendientes
1. **No existe una medición real de concurrencia** en Render/Neon/Atlas. Los cambios no garantizan 25, 50 o 100 clientes simultáneos. Probar en ambiente de prueba y con límites conservadores antes de hacer publicidad.
2. **Render Starter tiene recursos limitados.** Medir CPU, RAM, latencia p95, errores 5xx y consumo de Hikari. Un monitoreo de uso y un umbral de alerta valen más que cambiar de plan sin evidencias.
3. **MongoDB y Neon gratuitos:** verificar espacio, rendimiento, respaldos restaurables, cuotas y tiempos de reactivación. No se modificaron datos reales.
4. **Respaldo administrativo:** actualmente genera un ZIP completo en memoria. Para un negocio con mucha información, migrar a backup en streaming y almacenamiento externo, con política de retención y cifrado.
5. **`spring.jpa.hibernate.ddl-auto=update`:** migrar progresivamente a Flyway o Liquibase antes de actualizaciones importantes. No cambiar esta opción sin preparar migraciones para los datos existentes.
6. **Rate limiting local:** funciona por instancia, no por múltiples instancias. Si se escala horizontalmente, usar un almacén compartido, y comprobar los encabezados de IP de los proxies confiables.
7. **SEO del catálogo:** faltan rutas individuales estables e indexables y un sitemap de productos. Revisar canonical, Product JSON-LD y Core Web Vitals antes de comprar anuncios.
8. **Culqi aún no se integró.** Exige tokenización/checkout oficial, secretos **solo** en backend, importes recalculados en servidor, verificación del resultado, webhook firmado según documentación vigente, idempotencia y prevención de doble pedido. Actualizar la CSP del frontend únicamente con dominios necesarios.
9. **Observabilidad y confianza comercial:** agregar un monitoreo de uptime, alertas de error y verificación manual de pedidos/pagos. Establecer una página de términos, privacidad y políticas de entrega actualizada.

## Lista de comprobación antes del lanzamiento
- [ ] CI backend y frontend en verde; revisar logs de despliegue antes de promocionar.
- [ ] Prueba de carrito invitado, usuario registrado, pedido personalizado y stock sin cobro real.
- [ ] Pruebas de carga escalonadas **solo en staging**: 5, 10, 25 y 50 usuarios concurrentes. Parar si hay degradación o errores.
- [ ] Verificar CPU/RAM de Render, conexiones Neon, espacio Atlas, créditos Netlify y consumo de CDN de imágenes.
- [ ] Copia de seguridad de prueba y restauración verificada.
- [ ] Search Console y sitemap de productos preparado; imágenes optimizadas y medición de conversiones.
- [ ] Culqi en modo sandbox antes de activarlo en producción.

## Condiciones de aprobación
Las comprobaciones de sintaxis y CI son necesarias, pero **no equivalen** a una prueba real de carga, un pentest o una compra completa. Los umbrales de capacidad se definirán con métricas reales. Mantener Netlify Free + Render Starter de momento y ampliar cada servicio según su consumo.
