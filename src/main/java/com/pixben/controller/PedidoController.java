package com.pixben.controller;

import com.pixben.dto.ActualizarGestionPedidoRequest;
import com.pixben.model.Producto;
import com.pixben.model.Usuario;
import com.pixben.mongo.Pedido;
import com.pixben.mongo.PedidoItem;
import com.pixben.repository.PedidoRepository;
import com.pixben.repository.ProductoRepository;
import com.pixben.service.AutenticacionService;
import com.pixben.service.WebPushService;
import java.math.BigDecimal;
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

    private final PedidoRepository repository;
    private final ProductoRepository productoRepository;
    private final AutenticacionService autenticacionService;
    private final WebPushService webPushService;

    public PedidoController(
            PedidoRepository repository,
            ProductoRepository productoRepository,
            AutenticacionService autenticacionService,
            WebPushService webPushService) {
        this.repository = repository;
        this.productoRepository = productoRepository;
        this.autenticacionService = autenticacionService;
        this.webPushService = webPushService;
    }

    @PostMapping
    public Pedido guardar(
            @RequestHeader(AutenticacionService.HEADER_SESION) String token,
            @RequestBody Pedido pedido) {
        Usuario usuario = autenticacionService.requerirUsuario(token);
        pedido.setId(null);
        pedido.setUsuarioId(usuario.getId());
        pedido.setCorreo(usuario.getCorreo());
        pedido.setUsuario(nombreVisible(usuario));
        pedido.setInvitado(false);
        if (pedido.getNombreCliente() == null || pedido.getNombreCliente().isBlank()) {
            pedido.setNombreCliente((usuario.getNombre() + " " + valor(usuario.getApellido())).trim());
        }
        normalizarPedido(pedido);
        return repository.save(pedido);
    }

    /**
     * Checkout sin cuenta. El navegador solo envía IDs y cantidades; el servidor
     * vuelve a consultar los precios reales en PostgreSQL para impedir que el
     * cliente altere el total desde las herramientas del navegador.
     */
    @PostMapping("/invitado")
    public Pedido guardarInvitado(@RequestBody Pedido solicitud) {
        validarDatosInvitado(solicitud);

        List<PedidoItem> itemsSeguros = new ArrayList<>();
        double subtotal = 0.0;

        for (PedidoItem recibido : solicitud.getItems()) {
            if (recibido == null || recibido.getProductoId() == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Todos los productos deben tener un ID válido");
            }
            if (Boolean.TRUE.equals(recibido.getPersonalizado())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Las solicitudes personalizadas requieren una cuenta para consultar y aceptar la cotización");
            }

            Producto producto = productoRepository.findById(recibido.getProductoId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Uno de los productos ya no está disponible"));

            int cantidad = recibido.getCantidad() == null ? 1 : recibido.getCantidad();
            if (cantidad < 1 || cantidad > 50) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La cantidad debe estar entre 1 y 50");
            }
            if (producto.getStock() != null && cantidad > producto.getStock()) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "No hay stock suficiente para " + producto.getNombre());
            }

            String talla = normalizarTalla(recibido.getTalla());
            validarTallaDisponible(producto, talla);

            double precio = producto.getPrecio() == null ? 0.0 : producto.getPrecio().doubleValue();
            double subtotalItem = precio * cantidad;
            subtotal += subtotalItem;

            PedidoItem seguro = new PedidoItem();
            seguro.setProductoId(producto.getId());
            seguro.setNombre(producto.getNombre());
            seguro.setCantidad(cantidad);
            seguro.setTalla(talla);
            seguro.setPrecioUnitario(precio);
            seguro.setSubtotal(subtotalItem);
            seguro.setPersonalizado(false);
            seguro.setPedidoPersonalizadoId(null);
            seguro.setImagen(producto.getImagen());
            itemsSeguros.add(seguro);
        }

        Pedido pedido = new Pedido();
        pedido.setId(null);
        pedido.setUsuarioId(null);
        pedido.setUsuario("Invitado");
        pedido.setInvitado(true);
        pedido.setCorreo(limpiar(solicitud.getCorreo(), 160).toLowerCase(Locale.ROOT));
        pedido.setNombreCliente(limpiar(solicitud.getNombreCliente(), 120));
        pedido.setTelefono(limpiar(solicitud.getTelefono(), 30));
        pedido.setItems(itemsSeguros);
        pedido.setSubtotal(subtotal);
        pedido.setTotal(subtotal);
        pedido.setCostoEnvio(null);
        pedido.setMetodoPago(normalizarOpcion(solicitud.getMetodoPago(), METODOS_PAGO, "método de pago"));
        pedido.setReferenciaPago(limpiar(solicitud.getReferenciaPago(), 80));
        pedido.setEstadoPago("POR_VERIFICAR");
        pedido.setMetodoEnvio(normalizarOpcion(solicitud.getMetodoEnvio(), METODOS_ENVIO, "método de envío"));
        pedido.setDestinoEnvio(limpiar(solicitud.getDestinoEnvio(), 220));
        pedido.setReferenciaEnvio(limpiar(solicitud.getReferenciaEnvio(), 220));
        pedido.setEstadoEnvio("PENDIENTE_COORDINACION");
        pedido.setEstado("PENDIENTE");
        pedido.setFecha(limpiar(solicitud.getFecha(), 60));

        return repository.save(pedido);
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

    private void normalizarPedido(Pedido pedido) {
        if (pedido.getEstado() == null || pedido.getEstado().isBlank()) pedido.setEstado("PENDIENTE");
        if (pedido.getEstadoPago() == null || pedido.getEstadoPago().isBlank()) pedido.setEstadoPago("POR_VERIFICAR");
        if (pedido.getEstadoEnvio() == null || pedido.getEstadoEnvio().isBlank()) pedido.setEstadoEnvio("PENDIENTE_COORDINACION");
        if (pedido.getSubtotal() == null) pedido.setSubtotal(pedido.getTotal() == null ? 0.0 : pedido.getTotal());
        pedido.setCostoEnvio(null);
        pedido.setTotal(Math.max(0.0, pedido.getSubtotal()));
    }

    private void validarDatosInvitado(Pedido pedido) {
        if (pedido == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El pedido no es válido");
        if (limpiar(pedido.getNombreCliente(), 120).length() < 3) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Escribe tu nombre completo");
        }
        String correo = limpiar(pedido.getCorreo(), 160);
        if (!CORREO_VALIDO.matcher(correo).matches()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Escribe un correo válido");
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

    private void validarTallaDisponible(Producto producto, String talla) {
        if (!usaTallas(producto)) return;
        if ("UNIDAD".equals(talla)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Selecciona una talla para " + producto.getNombre());
        }
        String disponibles = producto.getTallasDisponibles();
        if (disponibles == null || disponibles.isBlank()) return; // Compatibilidad con productos antiguos.
        boolean existe = List.of(disponibles.split(",")).stream()
                .map(String::trim)
                .map(String::toUpperCase)
                .anyMatch(talla::equals);
        if (!existe) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "La talla " + talla + " ya no está disponible para " + producto.getNombre());
        }
    }

    private boolean usaTallas(Producto producto) {
        String texto = ((producto.getCategoria() == null ? "" : producto.getCategoria()) + " "
                + (producto.getNombre() == null ? "" : producto.getNombre())).toLowerCase(Locale.ROOT);
        return List.of("polo", "camiseta", "camisa", "polera", "hoodie", "sudadera",
                "casaca", "chaqueta", "pantalon", "short", "vestido")
                .stream().anyMatch(texto::contains);
    }

    private String normalizarTalla(String talla) {
        String valor = limpiar(talla, 20).toUpperCase(Locale.ROOT);
        return valor.isBlank() ? "UNIDAD" : valor;
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
}
