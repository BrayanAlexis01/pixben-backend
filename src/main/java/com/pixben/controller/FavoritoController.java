package com.pixben.controller;

import com.pixben.model.Producto;
import com.pixben.model.Usuario;
import com.pixben.model.VarianteColor;
import com.pixben.mongo.Favorito;
import com.pixben.repository.FavoritoRepository;
import com.pixben.repository.ProductoRepository;
import com.pixben.service.AutenticacionService;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/favoritos")
@CrossOrigin(origins = "*", allowedHeaders = "*")
public class FavoritoController {

    private final FavoritoRepository repository;
    private final ProductoRepository productoRepository;
    private final AutenticacionService autenticacionService;

    public FavoritoController(
            FavoritoRepository repository,
            ProductoRepository productoRepository,
            AutenticacionService autenticacionService) {
        this.repository = repository;
        this.productoRepository = productoRepository;
        this.autenticacionService = autenticacionService;
    }

    @PostMapping
    public Favorito guardar(
            @RequestHeader(AutenticacionService.HEADER_SESION) String token,
            @RequestBody Favorito favorito) {
        Usuario usuario = autenticacionService.requerirUsuario(token);
        if (favorito.getProductoId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El producto es obligatorio");
        }

        Producto producto = productoRepository.findById(favorito.getProductoId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Producto no encontrado"));
        String color = normalizarColor(producto, favorito.getColor());
        String talla = normalizarTalla(producto, favorito.getTalla());

        Favorito guardado = repository.findFirstByUsuarioIdAndProductoId(usuario.getId(), favorito.getProductoId())
                .orElseGet(Favorito::new);
        guardado.setProductoId(producto.getId());
        guardado.setUsuarioId(usuario.getId());
        guardado.setCorreo(usuario.getCorreo());
        guardado.setUsuario(nombreVisible(usuario));
        guardado.setColor(color);
        guardado.setTalla(talla);
        guardado.setFecha(LocalDateTime.now());
        return repository.save(guardado);
    }

    @GetMapping("/mios")
    public List<Favorito> listarMios(
            @RequestHeader(AutenticacionService.HEADER_SESION) String token) {
        Usuario usuario = autenticacionService.requerirUsuario(token);
        return repository.findByUsuarioId(usuario.getId());
    }

    @GetMapping("/estado")
    public Map<String, Object> estado(
            @RequestHeader(AutenticacionService.HEADER_SESION) String token,
            @RequestParam Long productoId) {
        Usuario usuario = autenticacionService.requerirUsuario(token);
        Optional<Favorito> favorito = repository.findFirstByUsuarioIdAndProductoId(usuario.getId(), productoId);
        return Map.of(
                "favorito", favorito.isPresent(),
                "id", favorito.map(Favorito::getId).orElse(""),
                "color", favorito.map(Favorito::getColor).orElse(""),
                "talla", favorito.map(Favorito::getTalla).orElse("")
        );
    }

    @DeleteMapping("/{id}")
    public void eliminar(
            @RequestHeader(AutenticacionService.HEADER_SESION) String token,
            @PathVariable String id) {
        Usuario usuario = autenticacionService.requerirUsuario(token);
        Favorito favorito = repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Favorito no encontrado"));
        if (favorito.getUsuarioId() == null || !usuario.getId().equals(favorito.getUsuarioId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Ese favorito no pertenece a tu cuenta");
        }
        repository.delete(favorito);
    }

    @DeleteMapping("/producto/{productoId}")
    public void eliminarPorProducto(
            @RequestHeader(AutenticacionService.HEADER_SESION) String token,
            @PathVariable Long productoId) {
        Usuario usuario = autenticacionService.requerirUsuario(token);
        repository.findFirstByUsuarioIdAndProductoId(usuario.getId(), productoId).ifPresent(repository::delete);
    }

    private String normalizarColor(Producto producto, String recibido) {
        List<VarianteColor> colores = producto.getColores() == null ? List.of() : producto.getColores();
        if (colores.isEmpty()) return "SIN_COLOR";
        String buscado = recibido == null ? "" : recibido.trim();
        if (buscado.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Selecciona un color antes de guardar el producto");
        }
        return colores.stream()
                .filter(c -> c != null && c.getNombre() != null && c.getNombre().equalsIgnoreCase(buscado))
                .map(VarianteColor::getNombre)
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "El color elegido ya no está disponible"));
    }

    private String normalizarTalla(Producto producto, String recibido) {
        if (!usaTallas(producto)) return "UNIDAD";
        String talla = recibido == null ? "" : recibido.trim().toUpperCase(Locale.ROOT);
        if (talla.isBlank() || "UNIDAD".equals(talla)) return "SIN_TALLA";
        String disponibles = producto.getTallasDisponibles();
        if (disponibles != null && !disponibles.isBlank()) {
            boolean existe = List.of(disponibles.split(",")).stream()
                    .map(String::trim).map(String::toUpperCase)
                    .anyMatch(talla::equals);
            if (!existe) return "SIN_TALLA";
        }
        return talla;
    }

    private boolean usaTallas(Producto producto) {
        String texto = ((producto.getCategoria() == null ? "" : producto.getCategoria()) + " "
                + (producto.getNombre() == null ? "" : producto.getNombre())).toLowerCase(Locale.ROOT);
        return List.of("polo", "camiseta", "camisa", "polera", "hoodie", "sudadera",
                "casaca", "chaqueta", "pantalon", "short", "vestido")
                .stream().anyMatch(texto::contains);
    }

    private String nombreVisible(Usuario usuario) {
        return usuario.getAlias() != null && !usuario.getAlias().isBlank() ? usuario.getAlias() : usuario.getNombre();
    }
}
