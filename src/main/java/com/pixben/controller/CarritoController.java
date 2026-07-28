package com.pixben.controller;

import com.pixben.model.Producto;
import com.pixben.model.Usuario;
import com.pixben.model.VarianteColor;
import com.pixben.mongo.Carrito;
import com.pixben.repository.CarritoRepository;
import com.pixben.repository.ProductoRepository;
import com.pixben.service.AutenticacionService;
import java.util.List;
import java.util.Locale;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/carrito")
@CrossOrigin(origins = "*", allowedHeaders = "*")
public class CarritoController {

    private final CarritoRepository repository;
    private final ProductoRepository productoRepository;
    private final AutenticacionService autenticacionService;

    public CarritoController(
            CarritoRepository repository,
            ProductoRepository productoRepository,
            AutenticacionService autenticacionService) {
        this.repository = repository;
        this.productoRepository = productoRepository;
        this.autenticacionService = autenticacionService;
    }

    @PostMapping
    public Carrito agregar(
            @RequestHeader(AutenticacionService.HEADER_SESION) String token,
            @RequestBody Carrito carrito) {
        Usuario usuario = autenticacionService.requerirUsuario(token);
        if (carrito.getProductoId() == null && !Boolean.TRUE.equals(carrito.getPersonalizado())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El producto es obligatorio");
        }

        carrito.setUsuarioId(usuario.getId());
        carrito.setCorreo(usuario.getCorreo());
        carrito.setUsuario(nombreVisible(usuario));
        carrito.setCantidad(normalizarCantidad(carrito.getCantidad()));

        if (!Boolean.TRUE.equals(carrito.getPersonalizado())) {
            Producto producto = obtenerProducto(carrito.getProductoId());
            carrito.setTalla(normalizarTalla(producto, carrito.getTalla()));
            carrito.setColor(normalizarColor(producto, carrito.getColor()));
            validarStock(producto, carrito.getColor(), carrito.getCantidad());

            Carrito existente = repository
                    .findFirstByUsuarioIdAndProductoIdAndTallaAndColorAndPersonalizadoFalse(
                            usuario.getId(), producto.getId(), carrito.getTalla(), carrito.getColor())
                    .orElse(null);
            if (existente != null) {
                int nuevaCantidad = Math.min(50, normalizarCantidad(existente.getCantidad()) + carrito.getCantidad());
                validarStock(producto, carrito.getColor(), nuevaCantidad);
                existente.setCantidad(nuevaCantidad);
                return repository.save(existente);
            }
        } else {
            carrito.setColor(valor(carrito.getColor(), "PERSONALIZADO"));
            carrito.setTalla(valor(carrito.getTalla(), "UNIDAD").toUpperCase(Locale.ROOT));
        }

        carrito.setId(null);
        return repository.save(carrito);
    }

    @GetMapping("/mios")
    public List<Carrito> listarMios(
            @RequestHeader(AutenticacionService.HEADER_SESION) String token) {
        Usuario usuario = autenticacionService.requerirUsuario(token);
        return repository.findByUsuarioId(usuario.getId());
    }

    @DeleteMapping("/{id}")
    public void eliminar(
            @RequestHeader(AutenticacionService.HEADER_SESION) String token,
            @PathVariable String id) {
        Usuario usuario = autenticacionService.requerirUsuario(token);
        Carrito item = obtenerPropio(id, usuario.getId());
        repository.delete(item);
    }

    @PutMapping("/{id}/{cantidad}")
    public Carrito actualizarCantidad(
            @RequestHeader(AutenticacionService.HEADER_SESION) String token,
            @PathVariable String id,
            @PathVariable Integer cantidad) {
        Usuario usuario = autenticacionService.requerirUsuario(token);
        Carrito carrito = obtenerPropio(id, usuario.getId());
        int nuevaCantidad = normalizarCantidad(cantidad);
        if (!Boolean.TRUE.equals(carrito.getPersonalizado()) && carrito.getProductoId() != null) {
            Producto producto = obtenerProducto(carrito.getProductoId());
            validarStock(producto, carrito.getColor(), nuevaCantidad);
        }
        carrito.setCantidad(nuevaCantidad);
        return repository.save(carrito);
    }

    @DeleteMapping("/mios")
    public void vaciarMio(
            @RequestHeader(AutenticacionService.HEADER_SESION) String token) {
        Usuario usuario = autenticacionService.requerirUsuario(token);
        repository.deleteByUsuarioId(usuario.getId());
    }

    private Carrito obtenerPropio(String id, Long usuarioId) {
        Carrito item = repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Elemento del carrito no encontrado"));
        if (item.getUsuarioId() == null || !usuarioId.equals(item.getUsuarioId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Ese producto no pertenece a tu carrito");
        }
        return item;
    }

    private Producto obtenerProducto(Long id) {
        return productoRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Producto no encontrado"));
    }

    private String normalizarColor(Producto producto, String recibido) {
        List<VarianteColor> colores = producto.getColores() == null ? List.of() : producto.getColores();
        if (colores.isEmpty()) return "SIN_COLOR";
        String buscado = valor(recibido, "");
        if (buscado.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Selecciona un color para " + producto.getNombre());
        }
        return colores.stream()
                .filter(c -> c != null && c.getNombre() != null && c.getNombre().equalsIgnoreCase(buscado))
                .map(VarianteColor::getNombre)
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT,
                        "El color seleccionado ya no está disponible para " + producto.getNombre()));
    }

    private String normalizarTalla(Producto producto, String recibido) {
        if (!usaTallas(producto)) return "UNIDAD";
        String talla = valor(recibido, "").toUpperCase(Locale.ROOT);
        if (talla.isBlank() || "UNIDAD".equals(talla)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Selecciona una talla para " + producto.getNombre());
        }
        String disponibles = producto.getTallasDisponibles();
        if (disponibles != null && !disponibles.isBlank()) {
            boolean existe = List.of(disponibles.split(",")).stream()
                    .map(String::trim).map(String::toUpperCase)
                    .anyMatch(talla::equals);
            if (!existe) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "La talla " + talla + " ya no está disponible para " + producto.getNombre());
            }
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
        return Math.max(1, Math.min(50, cantidad == null ? 1 : cantidad));
    }

    private String valor(String texto, String respaldo) {
        return texto == null ? respaldo : texto.trim();
    }

    private String nombreVisible(Usuario usuario) {
        return usuario.getAlias() != null && !usuario.getAlias().isBlank()
                ? usuario.getAlias()
                : usuario.getNombre();
    }
}
