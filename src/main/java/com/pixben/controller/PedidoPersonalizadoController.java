package com.pixben.controller;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pixben.dto.ActualizarPedidoPersonalizadoRequest;
import com.pixben.dto.PedidoPersonalizadoDatos;
import com.pixben.model.Producto;
import com.pixben.model.Usuario;
import com.pixben.mongo.PedidoPersonalizado;
import com.pixben.repository.PedidoPersonalizadoRepository;
import com.pixben.repository.ProductoRepository;
import com.pixben.service.AutenticacionService;
import com.pixben.service.ImagenSeguraService;
import com.pixben.service.WebPushService;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/pedidos-personalizados")
public class PedidoPersonalizadoController {

    private static final BigDecimal TARIFA_DISENO_ASISTIDO = new BigDecimal("15.00");
    private static final Set<String> TIPOS_SERVICIO = Set.of("AUTODISENO", "DISENO_ASISTIDO");
    private static final Set<String> METODOS_PAGO_DISENO = Set.of("YAPE", "PLIN", "BCP");
    private static final Set<String> ESTADOS_PAGO_DISENO = Set.of(
            "NO_APLICA", "POR_VERIFICAR", "VERIFICADO", "RECHAZADO", "REEMBOLSADO"
    );
    private static final Set<String> ESTADOS_VALIDOS = Set.of(
            "PENDIENTE_PAGO_DISENO", "PENDIENTE_COTIZACION", "EN_REVISION", "COTIZADO", "APROBADO",
            "EN_PRODUCCION", "LISTO", "ENVIADO", "CANCELADO"
    );

    private final PedidoPersonalizadoRepository repository;
    private final ProductoRepository productoRepository;
    private final Cloudinary cloudinary;
    private final ImagenSeguraService imagenSeguraService;
    private final ObjectMapper objectMapper;
    private final AutenticacionService autenticacionService;
    private final WebPushService webPushService;

    public PedidoPersonalizadoController(
            PedidoPersonalizadoRepository repository,
            ProductoRepository productoRepository,
            Cloudinary cloudinary,
            ImagenSeguraService imagenSeguraService,
            ObjectMapper objectMapper,
            AutenticacionService autenticacionService,
            WebPushService webPushService) {
        this.repository = repository;
        this.productoRepository = productoRepository;
        this.cloudinary = cloudinary;
        this.imagenSeguraService = imagenSeguraService;
        this.objectMapper = objectMapper;
        this.autenticacionService = autenticacionService;
        this.webPushService = webPushService;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public PedidoPersonalizado crear(
            @RequestHeader(AutenticacionService.HEADER_SESION) String token,
            @RequestPart("datos") String datosJson,
            @RequestPart(value = "frente", required = false) MultipartFile frente,
            @RequestPart(value = "espalda", required = false) MultipartFile espalda) throws IOException {
        Usuario usuario = autenticacionService.requerirUsuario(token);
        PedidoPersonalizadoDatos datos;
        try {
            datos = objectMapper.readValue(datosJson, PedidoPersonalizadoDatos.class);
        } catch (Exception ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Los datos de la solicitud no son válidos");
        }

        validarDatos(datos);
        String tipoServicio = normalizarTipoServicio(datos.getTipoServicio());
        boolean asistido = "DISENO_ASISTIDO".equals(tipoServicio);

        Producto producto = null;
        if (datos.getProductoId() != null) {
            producto = productoRepository.findById(datos.getProductoId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Producto base no encontrado"));
        }

        boolean sinVistaPrevia = (frente == null || frente.isEmpty()) && (espalda == null || espalda.isEmpty());
        if (!asistido && sinVistaPrevia) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Debes enviar al menos una vista previa");
        }

        if (asistido) {
            validarPagoDiseno(datos);
            String indicaciones = limpiar(datos.getNotas(), 800);
            if (indicaciones == null || indicaciones.length() < 10) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Describe con un poco más de detalle lo que debe preparar el diseñador");
            }
        }

        String carpeta = "pixben/personalizados/" + UUID.randomUUID();
        String urlFrente = frente == null || frente.isEmpty() ? null : subirImagen(frente, carpeta, "frente");
        String urlEspalda = espalda == null || espalda.isEmpty() ? null : subirImagen(espalda, carpeta, "espalda");

        LocalDateTime ahora = LocalDateTime.now();
        PedidoPersonalizado pedido = new PedidoPersonalizado();
        pedido.setUsuarioId(usuario.getId());
        pedido.setUsuario(nombreVisible(usuario));
        pedido.setCorreo(usuario.getCorreo());
        pedido.setProductoId(producto == null ? null : producto.getId());
        pedido.setProductoNombre(producto == null
                ? valorLibre(datos.getProductoNombre(), asistido ? "Diseño asistido" : "Diseño libre")
                : producto.getNombre());
        pedido.setCategoria(producto == null
                ? valorLibre(datos.getCategoria(), "PERSONALIZADO_LIBRE")
                : producto.getCategoria());
        pedido.setColor(limpiar(datos.getColor(), 50));
        pedido.setTalla(limpiar(datos.getTalla(), 20));
        pedido.setCantidad(datos.getCantidad());
        pedido.setNotas(limpiar(datos.getNotas(), 800));
        pedido.setTipoServicio(tipoServicio);
        pedido.setImagenFrente(urlFrente);
        pedido.setImagenEspalda(urlEspalda);
        pedido.setPrecio(null);

        if (asistido) {
            pedido.setTarifaDiseno(TARIFA_DISENO_ASISTIDO);
            pedido.setMetodoPagoDiseno(normalizarMetodoPagoDiseno(datos.getMetodoPagoDiseno()));
            pedido.setReferenciaPagoDiseno(limpiar(datos.getReferenciaPagoDiseno(), 80));
            pedido.setEstadoPagoDiseno("POR_VERIFICAR");
            pedido.setRevisionesIncluidas(2);
            pedido.setEstado("PENDIENTE_PAGO_DISENO");
            pedido.setMensajeAdmin("Recibimos tu solicitud de diseño asistido. Verificaremos el adelanto de S/ 15.00 antes de que el diseñador empiece.");
        } else {
            pedido.setTarifaDiseno(BigDecimal.ZERO);
            pedido.setMetodoPagoDiseno(null);
            pedido.setReferenciaPagoDiseno(null);
            pedido.setEstadoPagoDiseno("NO_APLICA");
            pedido.setRevisionesIncluidas(0);
            pedido.setEstado("PENDIENTE_COTIZACION");
            pedido.setMensajeAdmin("Recibimos tu diseño. El administrador evaluará la complejidad y asignará el precio.");
        }

        pedido.setFechaCreacion(ahora);
        pedido.setFechaActualizacion(ahora);
        PedidoPersonalizado guardado = repository.save(pedido);
        webPushService.notificarNuevoPedidoAdministradores();
        return guardado;
    }

    @GetMapping("/{id}")
    public PedidoPersonalizado obtenerPropioOAdmin(
            @RequestHeader(AutenticacionService.HEADER_SESION) String token,
            @PathVariable String id) {
        Usuario usuario = autenticacionService.requerirUsuario(token);
        PedidoPersonalizado pedido = obtener(id);
        if (!"admin".equalsIgnoreCase(usuario.getRol())
                && (pedido.getUsuarioId() == null || !usuario.getId().equals(pedido.getUsuarioId()))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "La solicitud no pertenece a tu cuenta");
        }
        return pedido;
    }

    @GetMapping("/mios")
    public List<PedidoPersonalizado> listarMios(
            @RequestHeader(AutenticacionService.HEADER_SESION) String token) {
        Usuario usuario = autenticacionService.requerirUsuario(token);
        return repository.findByUsuarioIdOrderByFechaCreacionDesc(usuario.getId());
    }

    @GetMapping("/admin/todos")
    public List<PedidoPersonalizado> listarAdmin(
            @RequestHeader(AutenticacionService.HEADER_SESION) String token) {
        autenticacionService.requerirAdmin(token);
        return repository.findAllByOrderByFechaCreacionDesc();
    }

    @PatchMapping("/{id}")
    public PedidoPersonalizado actualizar(
            @RequestHeader(AutenticacionService.HEADER_SESION) String token,
            @PathVariable String id,
            @org.springframework.web.bind.annotation.RequestBody ActualizarPedidoPersonalizadoRequest cambios) {
        autenticacionService.requerirAdmin(token);
        PedidoPersonalizado pedido = obtener(id);
        String estadoAnterior = pedido.getEstado();
        BigDecimal precioAnterior = pedido.getPrecio();
        String mensajeAnterior = pedido.getMensajeAdmin();
        String pagoDisenoAnterior = pedido.getEstadoPagoDiseno();

        if (cambios.getEstadoPagoDiseno() != null && !cambios.getEstadoPagoDiseno().isBlank()) {
            if (!esDisenoAsistido(pedido)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "Esta solicitud no utiliza adelanto de diseño");
            }
            String estadoPago = cambios.getEstadoPagoDiseno().trim().toUpperCase(Locale.ROOT);
            if (!ESTADOS_PAGO_DISENO.contains(estadoPago) || "NO_APLICA".equals(estadoPago)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Estado de pago de diseño no válido");
            }
            pedido.setEstadoPagoDiseno(estadoPago);
            if ("VERIFICADO".equals(estadoPago) && "PENDIENTE_PAGO_DISENO".equals(pedido.getEstado())
                    && (cambios.getEstado() == null || cambios.getEstado().isBlank())) {
                pedido.setEstado("EN_REVISION");
            }
            if ("RECHAZADO".equals(estadoPago)
                    && (cambios.getEstado() == null || cambios.getEstado().isBlank())) {
                pedido.setEstado("PENDIENTE_PAGO_DISENO");
            }
        }

        if (cambios.getPrecio() != null) {
            if (cambios.getPrecio().compareTo(BigDecimal.ZERO) < 0) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El precio no puede ser negativo");
            }
            exigirPagoDisenoVerificadoSiCorresponde(pedido);
            pedido.setPrecio(cambios.getPrecio());
            if (cambios.getEstado() == null || cambios.getEstado().isBlank()) pedido.setEstado("COTIZADO");
        }

        if (cambios.getEstado() != null && !cambios.getEstado().isBlank()) {
            String estado = cambios.getEstado().trim().toUpperCase(Locale.ROOT);
            if (!ESTADOS_VALIDOS.contains(estado)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Estado no válido");
            }
            if (esDisenoAsistido(pedido)
                    && "VERIFICADO".equalsIgnoreCase(pedido.getEstadoPagoDiseno())
                    && "PENDIENTE_PAGO_DISENO".equals(estado)) {
                estado = "EN_REVISION";
            }
            if (esDisenoAsistido(pedido)
                    && !Set.of("PENDIENTE_PAGO_DISENO", "CANCELADO").contains(estado)) {
                exigirPagoDisenoVerificadoSiCorresponde(pedido);
            }
            pedido.setEstado(estado);
        }

        if (cambios.getMensajeAdmin() != null) {
            pedido.setMensajeAdmin(limpiar(cambios.getMensajeAdmin(), 800));
        }
        pedido.setFechaActualizacion(LocalDateTime.now());
        PedidoPersonalizado guardado = repository.save(pedido);
        boolean cambio = !java.util.Objects.equals(estadoAnterior, guardado.getEstado())
                || !java.util.Objects.equals(precioAnterior, guardado.getPrecio())
                || !java.util.Objects.equals(mensajeAnterior, guardado.getMensajeAdmin())
                || !java.util.Objects.equals(pagoDisenoAnterior, guardado.getEstadoPagoDiseno());
        if (cambio) webPushService.notificarActualizacionPedido(guardado.getUsuarioId());
        return guardado;
    }

    @PostMapping("/{id}/aceptar")
    public PedidoPersonalizado aceptarCotizacion(
            @RequestHeader(AutenticacionService.HEADER_SESION) String token,
            @PathVariable String id) {
        Usuario usuario = autenticacionService.requerirUsuario(token);
        PedidoPersonalizado pedido = obtener(id);
        if (pedido.getUsuarioId() == null || !usuario.getId().equals(pedido.getUsuarioId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "La solicitud no pertenece a tu cuenta");
        }
        if (pedido.getPrecio() == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "La solicitud todavía no tiene precio");
        }
        exigirPagoDisenoVerificadoSiCorresponde(pedido);
        pedido.setEstado("APROBADO");
        pedido.setFechaActualizacion(LocalDateTime.now());
        PedidoPersonalizado guardado = repository.save(pedido);
        webPushService.notificarNuevoPedidoAdministradores();
        return guardado;
    }

    @DeleteMapping("/{id}")
    public void eliminar(
            @RequestHeader(AutenticacionService.HEADER_SESION) String token,
            @PathVariable String id) {
        autenticacionService.requerirAdmin(token);
        repository.delete(obtener(id));
    }

    private PedidoPersonalizado obtener(String id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Solicitud no encontrada"));
    }

    private void validarDatos(PedidoPersonalizadoDatos datos) {
        if (datos == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Los datos de la solicitud son obligatorios");
        }
        if (datos.getCantidad() == null || datos.getCantidad() < 1 || datos.getCantidad() > 50) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La cantidad debe estar entre 1 y 50");
        }
        normalizarTipoServicio(datos.getTipoServicio());
    }

    private void validarPagoDiseno(PedidoPersonalizadoDatos datos) {
        normalizarMetodoPagoDiseno(datos.getMetodoPagoDiseno());
        String referencia = limpiar(datos.getReferenciaPagoDiseno(), 80);
        if (referencia == null || referencia.length() < 3) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Escribe el número o código de operación del adelanto de diseño");
        }
    }

    private String normalizarTipoServicio(String valor) {
        String tipo = valor == null || valor.isBlank()
                ? "AUTODISENO"
                : valor.trim().toUpperCase(Locale.ROOT);
        if (!TIPOS_SERVICIO.contains(tipo)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Tipo de servicio personalizado no válido");
        }
        return tipo;
    }

    private String normalizarMetodoPagoDiseno(String valor) {
        String metodo = valor == null ? "" : valor.trim().toUpperCase(Locale.ROOT);
        if (!METODOS_PAGO_DISENO.contains(metodo)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Selecciona Yape, Plin o BCP para el adelanto de diseño");
        }
        return metodo;
    }

    private boolean esDisenoAsistido(PedidoPersonalizado pedido) {
        return pedido != null && "DISENO_ASISTIDO".equalsIgnoreCase(pedido.getTipoServicio());
    }

    private void exigirPagoDisenoVerificadoSiCorresponde(PedidoPersonalizado pedido) {
        if (esDisenoAsistido(pedido) && !"VERIFICADO".equalsIgnoreCase(pedido.getEstadoPagoDiseno())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Verifica primero el adelanto de S/ 15.00 antes de iniciar o cotizar el trabajo del diseñador");
        }
    }

    private String subirImagen(MultipartFile archivo, String carpeta, String nombre) throws IOException {
        byte[] bytes = imagenSeguraService.validar(archivo);
        Map<?, ?> resultado = cloudinary.uploader().upload(
                bytes,
                ObjectUtils.asMap(
                        "folder", carpeta,
                        "public_id", nombre,
                        "resource_type", "image",
                        "overwrite", true,
                        "format", "png",
                        "quality", "auto",
                        "flags", "strip_profile"
                )
        );
        Object secureUrl = resultado.get("secure_url");
        if (secureUrl == null) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Cloudinary no devolvió la URL de la vista previa");
        }
        return secureUrl.toString();
    }

    private String limpiar(String valor, int maximo) {
        if (valor == null) return null;
        String limpio = valor.replaceAll("[\\p{Cntrl}&&[^\\r\\n\\t]]", "").trim();
        return limpio.length() <= maximo ? limpio : limpio.substring(0, maximo);
    }

    private String valorLibre(String valor, String respaldo) {
        String limpio = limpiar(valor, 120);
        return limpio == null || limpio.isBlank() ? respaldo : limpio;
    }

    private String nombreVisible(Usuario usuario) {
        return usuario.getAlias() != null && !usuario.getAlias().isBlank() ? usuario.getAlias() : usuario.getNombre();
    }
}
