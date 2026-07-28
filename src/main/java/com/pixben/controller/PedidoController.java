package com.pixben.controller;

import com.pixben.dto.ActualizarGestionPedidoRequest;
import com.pixben.dto.ConsultaPedidoInvitadoRequest;
import com.pixben.model.Producto;
import com.pixben.model.Usuario;
import com.pixben.model.VarianteColor;
import com.pixben.mongo.Pedido;
import com.pixben.mongo.PedidoItem;
import com.pixben.mongo.PedidoPersonalizado;
import com.pixben.repository.PedidoPersonalizadoRepository;
import com.pixben.repository.PedidoRepository;
import com.pixben.repository.ProductoRepository;
import com.pixben.service.AutenticacionService;
import com.pixben.service.WebPushService;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/pedidos")
@CrossOrigin(origins = "*", allowedHeaders = "*")
public class PedidoController {

    private static final Pattern CORREO_VALIDO = Pattern.compile("^[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}$", Pattern.CASE_INSENSITIVE);
    private static final Set<String> METODOS_PAGO = Set.of("YAPE", "PLIN", "BCP");
    private static final Set<String> METODOS_ENVIO = Set.of("SHALOM", "INDRIVE");
    private static final String CARACTERES_CODIGO = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final SecureRandom ALEATORIO = new SecureRandom();

    private final PedidoRepository repository;
    private final ProductoRepository productoRepository;
    private final PedidoPersonalizadoRepository pedidoPersonalizadoRepository;
    private final AutenticacionService autenticacionService;
    private final WebPushService webPushService;

    public PedidoController(
            PedidoRepository repository,
            ProductoRepository productoRepository,
            PedidoPersonalizadoRepository pedidoPersonalizadoRepository,
            AutenticacionService autenticacionService,
            WebPushService webPushService) {
        this.repository = repository;
        this.productoRepository = productoRepository;
        this.pedidoPersonalizadoRepository = pedidoPersonalizadoRepository;
        this.autenticacionService = autenticacionService;
        this.webPushService = webPushService;
    }

    @PostMapping
    public Pedido guardar(
            @RequestHeader(AutenticacionService.HEADER_SESION) String token,
            @RequestBody Pedido solicitud) {
        Usuario usuario = autenticacionService.requerirUsuario(token);
        validarDatosCompra(solicitud, false);

        Pedido pedido = crearPedidoBase(solicitud);
        pedido.setUsuarioId(usuario.getId());
        pedido.setCorreo(usuario.getCorreo());
        pedido.setUsuario(nombreVisible(usuario));
        pedido.setInvitado(false);
        pedido.setNombreCliente(limpiar(solicitud.getNombreCliente(), 120));
        if (pedido.getNombreCliente().isBlank()) {
            pedido.setNombreCliente((usuario.getNombre() + " " + valor(usuario.getApellido())).trim());
        }
        pedido.setTelefono(limpiar(solicitud.getTelefono(), 30));
        pedido.setItems(construirItemsSeguros(solicitud.getItems(), usuario.getId(), true));
        completarTotales(pedido);
        return repository.save(pedido);
    }

    /** Checkout sin cuenta, con precio, talla, color y stock revalidados en servidor. */
    @PostMapping("/invitado")
    public Pedido guardarInvitado(@RequestBody Pedido solicitud) {
        validarDatosCompra(solicitud, true);

        Pedido pedido = crearPedidoBase(solicitud);
        pedido.setUsuarioId(null);
        pedido.setUsuario("Invitado");
        pedido.setInvitado(true);
        pedido.setCorreo(limpiar(solicitud.getCorreo(), 160).toLowerCase(Locale.ROOT));
        pedido.setNombreCliente(limpiar(solicitud.getNombreCliente(), 120));
        pedido.setTelefono(limpiar(solicitud.getTelefono(), 30));
        pedido.setItems(construirItemsSeguros(solicitud.getItems(), null, false));
        completarTotales(pedido);
        return repository.save(pedido);
    }

    /** Consulta segura para visitantes: se exige código de seguimiento y correo exacto. */
    @PostMapping("/invitado/consultar")
    public Pedido consultarInvitado(@RequestBody ConsultaPedidoInvitadoRequest solicitud) {
        if (solicitud == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Escribe el código y correo del pedido");
        }
        String codigo = limpiar(solicitud.getCodigo(), 40).toUpperCase(Locale.ROOT);
        String correo = limpiar(solicitud.getCorreo(), 160).toLowerCase(Locale.ROOT);
        if (codigo.isBlank() || !CORREO_VALIDO.matcher(correo).matches()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Escribe un código y correo válidos");
        }

        Pedido pedido = repository.findFirstByCodigoSeguimientoIgnoreCaseAndCorreoIgnoreCase(codigo, correo)
                .orElseGet(() -> repository.findById(codigo.toLowerCase(Locale.ROOT)).orElse(null));
        if (pedido == null || !Boolean.TRUE.equals(pedido.getInvitado())
                || pedido.getCorreo() == null || !pedido.getCorreo().equalsIgnoreCase(correo)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No encontramos un pedido con ese código y correo");
        }
        return pedido;
    }

    @GetMapping("/mios")
    public List<Pedido> listarMios(
            @RequestHeader(AutenticacionService.HEADER_SESION) String token) {
        Usuario usuario = autenticacionService.requerirUsuario(token);
        return repository.findByUsuarioId(usuario.getId()).stream()
                .sorted(Comparator.comparing(Pedido::getFecha, Comparator.nullsLast(String::compareTo)).reversed())
                .toList();
    }

    @GetMapping("/admin/todos")
    public List<Pedido> listarTodos(
            @RequestHeader(AutenticacionService.HEADER_SESION) String token) {
        autenticacionService.requerirAdmin(token);
        return repository.findAll();
    }

    @PatchMapping("/{id}/estado")
    public Pedido cambiarEstado(
            @RequestHeader(AutenticacionService.HEADER_SESION) String token,
            @PathVariable String id,
            @RequestParam String estado) {
        autenticacionService.requerirAdmin(token);
        Pedido pedido = obtener(id);
        String estadoAnterior = pedido.getEstado();
        pedido.setEstado(normalizarEstado(estado, "PENDIENTE"));
        Pedido guardado = repository.save(pedido);
        if (!java.util.Objects.equals(estadoAnterior, guardado.getEstado())) {
            webPushService.notificarActualizacionPedido(guardado.getUsuarioId());
        }
        return guardado;
    }

    @PatchMapping("/{id}/gestion")
    public Pedido gestionar(
            @RequestHeader(AutenticacionService.HEADER_SESION) String token,
            @PathVariable String id,
            @RequestBody ActualizarGestionPedidoRequest datos) {
        autenticacionService.requerirAdmin(token);
        Pedido pedido = obtener(id);
        String estadoAnterior = pedido.getEstado();
        String pagoAnterior = pedido.getEstadoPago();
        String envioAnterior = pedido.getEstadoEnvio();
        if (datos.getEstado() != null) pedido.setEstado(normalizarEstado(datos.getEstado(), pedido.getEstado()));
        if (datos.getEstadoPago() != null) pedido.setEstadoPago(normalizarEstado(datos.getEstadoPago(), pedido.getEstadoPago()));
        if (datos.getEstadoEnvio() != null) pedido.setEstadoEnvio(normalizarEstado(datos.getEstadoEnvio(), pedido.getEstadoEnvio()));
        double subtotal = pedido.getSubtotal() == null ? (pedido.getTotal() == null ? 0.0 : pedido.getTotal()) : pedido.getSubtotal();
        pedido.setCostoEnvio(null);
        pedido.setSubtotal(subtotal);
        pedido.setTotal(subtotal);
        Pedido guardado = repository.save(pedido);
        boolean cambio = !java.util.Objects.equals(estadoAnterior, guardado.getEstado())
                || !java.util.Objects.equals(pagoAnterior, guardado.getEstadoPago())
                || !java.util.Objects.equals(envioAnterior, guardado.getEstadoEnvio());
        if (cambio) webPushService.notificarActualizacionPedido(guardado.getUsuarioId());
        return guardado;
    }

    @DeleteMapping("/{id}")
    public void eliminar(
            @RequestHeader(AutenticacionService.HEADER_SESION) String token,
            @PathVariable String id) {
        autenticacionService.requerirAdmin(token);
        Pedido pedido = obtener(id);
        repository.delete(pedido);
    }

    private Pedido crearPedidoBase(Pedido solicitud) {
        Pedido pedido = new Pedido();
        pedido.setId(null);
        pedido.setCodigoSeguimiento(generarCodigoSeguimiento());
        pedido.setMetodoPago(normalizarOpcion(solicitud.getMetodoPago(), METODOS_PAGO, "método de pago"));
        pedido.setReferenciaPago(limpiar(solicitud.getReferenciaPago(), 80));
        pedido.setEstadoPago("POR_VERIFICAR");
        pedido.setMetodoEnvio(normalizarOpcion(solicitud.getMetodoEnvio(), METODOS_ENVIO, "método de envío"));
        pedido.setDestinoEnvio(limpiar(solicitud.getDestinoEnvio(), 220));
        pedido.setReferenciaEnvio(limpiar(solicitud.getReferenciaEnvio(), 220));
        pedido.setEstadoEnvio("PENDIENTE_COORDINACION");
        pedido.setEstado("PENDIENTE");
        pedido.setFecha(limpiar(solicitud.getFecha(), 60));
        return pedido;
    }

    private List<PedidoItem> construirItemsSeguros(List<PedidoItem> recibidos, Long usuarioId, boolean permitirPersonalizados) {
        List<PedidoItem> seguros = new ArrayList<>();
        for (PedidoItem recibido : recibidos) {
            if (recibido == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Uno de los productos no es válido");
            }
            if (Boolean.TRUE.equals(recibido.getPersonalizado())) {
                if (!permitirPersonalizados || usuarioId == null) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            "Las solicitudes personalizadas requieren una cuenta");
                }
                seguros.add(construirPersonalizadoSeguro(recibido, usuarioId));
            } else {
                seguros.add(construirProductoSeguro(recibido));
            }
        }
        return seguros;
    }

    private PedidoItem construirProductoSeguro(PedidoItem recibido) {
        if (recibido.getProductoId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Todos los productos deben tener un ID válido");
        }
        Producto producto = productoRepository.findById(recibido.getProductoId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Uno de los productos ya no está disponible"));
        int cantidad = normalizarCantidad(recibido.getCantidad());
        String talla = normalizarTalla(producto, recibido.getTalla());
        String color = normalizarColor(producto, recibido.getColor());
        validarStock(producto, color, cantidad);

        double precio = producto.getPrecio() == null ? 0.0 : producto.getPrecio().doubleValue();
        PedidoItem seguro = new PedidoItem();
        seguro.setProductoId(producto.getId());
        seguro.setNombre(producto.getNombre());
        seguro.setCantidad(cantidad);
        seguro.setTalla(talla);
        seguro.setColor(color);
        seguro.setPrecioUnitario(precio);
        seguro.setSubtotal(precio * cantidad);
        seguro.setPersonalizado(false);
        seguro.setPedidoPersonalizadoId(null);
        seguro.setImagen(producto.getImagen());
        return seguro;
    }

    private PedidoItem construirPersonalizadoSeguro(PedidoItem recibido, Long usuarioId) {
        String solicitudId = limpiar(recibido.getPedidoPersonalizadoId(), 80);
        PedidoPersonalizado personalizado = pedidoPersonalizadoRepository.findById(solicitudId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "La solicitud personalizada no existe"));
        if (personalizado.getUsuarioId() == null || !usuarioId.equals(personalizado.getUsuarioId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "La solicitud personalizada no pertenece a tu cuenta");
        }
        if (personalizado.getPrecio() == null || !Set.of("APROBADO", "EN_PRODUCCION", "LISTO", "ENVIADO").contains(valor(personalizado.getEstado()).toUpperCase(Locale.ROOT))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "La cotización personalizada todavía no está aceptada");
        }
        int cantidad = normalizarCantidad(personalizado.getCantidad());
        double subtotal = personalizado.getPrecio().doubleValue();

        PedidoItem seguro = new PedidoItem();
        seguro.setProductoId(personalizado.getProductoId());
        seguro.setNombre(valor(personalizado.getProductoNombre(), "Diseño personalizado"));
        seguro.setCantidad(cantidad);
        seguro.setTalla(valor(personalizado.getTalla(), "UNIDAD").toUpperCase(Locale.ROOT));
        seguro.setColor(valor(personalizado.getColor(), "PERSONALIZADO"));
        seguro.setPrecioUnitario(cantidad > 0 ? subtotal / cantidad : subtotal);
        seguro.setSubtotal(subtotal);
        seguro.setPersonalizado(true);
        seguro.setPedidoPersonalizadoId(personalizado.getId());
        seguro.setImagen(valor(personalizado.getImagenFrente(), personalizado.getImagenEspalda()));
        return seguro;
    }

    private void completarTotales(Pedido pedido) {
        double subtotal = pedido.getItems().stream()
                .map(PedidoItem::getSubtotal)
                .filter(java.util.Objects::nonNull)
                .mapToDouble(Double::doubleValue)
                .sum();
        pedido.setSubtotal(subtotal);
        pedido.setCostoEnvio(null);
        pedido.setTotal(subtotal);
    }

    private void validarDatosCompra(Pedido pedido, boolean invitado) {
        if (pedido == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El pedido no es válido");
        if (invitado && limpiar(pedido.getNombreCliente(), 120).length() < 3) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Escribe tu nombre completo");
        }
        if (invitado) {
            String correo = limpiar(pedido.getCorreo(), 160);
            if (!CORREO_VALIDO.matcher(correo).matches()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Escribe un correo válido");
            }
        }
        if (limpiar(pedido.getTelefono(), 30).length() < 7) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Escribe un teléfono válido");
        }
        if (pedido.getItems() == null || pedido.getItems().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El carrito está vacío");
        }
        normalizarOpcion(pedido.getMetodoPago(), METODOS_PAGO, "método de pago");
        normalizarOpcion(pedido.getMetodoEnvio(), METODOS_ENVIO, "método de envío");
        if (limpiar(pedido.getDestinoEnvio(), 220).isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Escribe el destino del envío");
        }
        if (limpiar(pedido.getReferenciaPago(), 80).isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Escribe el código o número de operación");
        }
    }

    private String normalizarColor(Producto producto, String recibido) {
        List<VarianteColor> colores = producto.getColores() == null ? List.of() : producto.getColores();
        if (colores.isEmpty()) return "SIN_COLOR";
        String buscado = limpiar(recibido, 60);
        if (buscado.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Selecciona un color para " + producto.getNombre());
        }
        return colores.stream()
                .filter(c -> c != null && c.getNombre() != null && c.getNombre().equalsIgnoreCase(buscado))
                .map(VarianteColor::getNombre)
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT,
                        "El color " + buscado + " ya no está disponible para " + producto.getNombre()));
    }

    private String normalizarTalla(Producto producto, String tallaRecibida) {
        if (!usaTallas(producto)) return "UNIDAD";
        String talla = limpiar(tallaRecibida, 20).toUpperCase(Locale.ROOT);
        if (talla.isBlank() || "UNIDAD".equals(talla)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Selecciona una talla para " + producto.getNombre());
        }
        String disponibles = producto.getTallasDisponibles();
        if (disponibles == null || disponibles.isBlank()) return talla;
        boolean existe = List.of(disponibles.split(",")).stream()
                .map(String::trim)
                .map(String::toUpperCase)
                .anyMatch(talla::equals);
        if (!existe) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "La talla " + talla + " ya no está disponible para " + producto.getNombre());
        }
        return talla;
    }

    private void validarStock(Producto producto, String color, int cantidad) {
        int disponible = producto.getStock() == null ? 0 : producto.getStock();
        if (producto.getColores() != null && !producto.getColores().isEmpty()) {
            disponible = producto.getColores().stream()
                    .filter(c -> c != null && c.getNombre() != null && c.getNombre().equalsIgnoreCase(color))
                    .map(VarianteColor::getStock)
                    .mapToInt(stock -> stock == null ? 0 : stock)
                    .findFirst().orElse(0);
        }
        if (cantidad > disponible) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "No hay stock suficiente para " + producto.getNombre()
                    + ("SIN_COLOR".equals(color) ? "" : " en color " + color));
        }
    }

    private boolean usaTallas(Producto producto) {
        String texto = ((producto.getCategoria() == null ? "" : producto.getCategoria()) + " "
                + (producto.getNombre() == null ? "" : producto.getNombre())).toLowerCase(Locale.ROOT);
        return List.of("polo", "camiseta", "camisa", "polera", "hoodie", "sudadera",
                "casaca", "chaqueta", "pantalon", "short", "vestido")
                .stream().anyMatch(texto::contains);
    }

    private int normalizarCantidad(Integer cantidad) {
        int valor = cantidad == null ? 1 : cantidad;
        if (valor < 1 || valor > 50) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La cantidad debe estar entre 1 y 50");
        }
        return valor;
    }

    private String generarCodigoSeguimiento() {
        for (int intento = 0; intento < 20; intento++) {
            StringBuilder codigo = new StringBuilder("PX-");
            for (int i = 0; i < 8; i++) {
                codigo.append(CARACTERES_CODIGO.charAt(ALEATORIO.nextInt(CARACTERES_CODIGO.length())));
            }
            String valor = codigo.toString();
            if (!repository.existsByCodigoSeguimiento(valor)) return valor;
        }
        return "PX-" + System.currentTimeMillis();
    }

    private String normalizarOpcion(String valor, Set<String> permitidas, String campo) {
        String normalizado = limpiar(valor, 40).toUpperCase(Locale.ROOT);
        if (!permitidas.contains(normalizado)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Selecciona un " + campo + " válido");
        }
        return normalizado;
    }

    private String limpiar(String valor, int maximo) {
        if (valor == null) return "";
        String limpio = valor.replaceAll("[\\p{Cntrl}&&[^\\r\\n\\t]]", "").trim();
        return limpio.length() <= maximo ? limpio : limpio.substring(0, maximo);
    }

    private Pedido obtener(String id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Pedido no encontrado"));
    }

    private String normalizarEstado(String estado, String respaldo) {
        if (estado == null || estado.isBlank()) return respaldo == null ? "PENDIENTE" : respaldo;
        return estado.trim().toUpperCase().replace(' ', '_');
    }

    private String nombreVisible(Usuario usuario) {
        return usuario.getAlias() != null && !usuario.getAlias().isBlank() ? usuario.getAlias() : usuario.getNombre();
    }

    private String valor(String valor) {
        return valor == null ? "" : valor;
    }

    private String valor(String valor, String respaldo) {
        return valor == null || valor.isBlank() ? (respaldo == null ? "" : respaldo) : valor;
    }
}
