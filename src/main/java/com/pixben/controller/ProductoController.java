package com.pixben.controller;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import com.pixben.model.Producto;
import com.pixben.repository.ProductoRepository;
import com.pixben.service.ImagenSeguraService;
import com.pixben.service.AutenticacionService;
import java.io.IOException;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.Locale;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@CrossOrigin(
        origins = "*",
        allowedHeaders = "*",
        methods = {
            RequestMethod.GET,
            RequestMethod.POST,
            RequestMethod.PUT,
            RequestMethod.DELETE,
            RequestMethod.OPTIONS
        }
)
@RestController
@RequestMapping("/productos")
public class ProductoController {

    private final ProductoRepository productoRepository;
    private final Cloudinary cloudinary;
    private final ImagenSeguraService imagenSeguraService;
    private final AutenticacionService autenticacionService;

    public ProductoController(
            ProductoRepository productoRepository,
            Cloudinary cloudinary,
            ImagenSeguraService imagenSeguraService,
            AutenticacionService autenticacionService) {
        this.productoRepository = productoRepository;
        this.cloudinary = cloudinary;
        this.imagenSeguraService = imagenSeguraService;
        this.autenticacionService = autenticacionService;
    }

    @GetMapping
    public List<Producto> listarProductos() {
        return productoRepository.findAll();
    }

    @PostMapping
    public Producto guardarProducto(
            @RequestHeader(AutenticacionService.HEADER_SESION) String token,
            @RequestBody Producto producto) {
        autenticacionService.requerirAdmin(token);
        producto.setId(null);
        if (producto.getDestacado() == null) producto.setDestacado(false);
        if (producto.getPersonalizable() == null) producto.setPersonalizable(false);
        producto.setTallasDisponibles(usaTallas(producto) ? normalizarTallas(producto.getTallasDisponibles()) : "");
        return productoRepository.save(producto);
    }

    @GetMapping("/personalizables")
    public List<Producto> listarPersonalizables() {
        return productoRepository.findAll().stream()
                .filter(this::esPersonalizable)
                .toList();
    }

    @GetMapping("/{id}")
    public Producto obtenerProducto(@PathVariable Long id) {
        return productoRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "Producto no encontrado"
        ));
    }

    @PutMapping("/{id}")
    public Producto actualizarProducto(
            @RequestHeader(AutenticacionService.HEADER_SESION) String token,
            @PathVariable Long id,
            @RequestBody Producto producto) {

        autenticacionService.requerirAdmin(token);
        Producto existente = productoRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "Producto no encontrado"
        ));

        producto.setId(id);
        if (producto.getDestacado() == null) producto.setDestacado(false);
        if (producto.getPersonalizable() == null) producto.setPersonalizable(false);
        producto.setTallasDisponibles(usaTallas(producto) ? normalizarTallas(producto.getTallasDisponibles()) : "");

        // El formulario de edición no envía la imagen si el usuario no selecciona una nueva.
        // Conservamos la imagen actual para que no quede en null.
        if (producto.getImagen() == null || producto.getImagen().isBlank()) {
            producto.setImagen(existente.getImagen());
        }

        return productoRepository.save(producto);
    }

    @DeleteMapping("/{id}")
    public void eliminarProducto(
            @RequestHeader(AutenticacionService.HEADER_SESION) String token,
            @PathVariable Long id) {
        autenticacionService.requerirAdmin(token);
        if (!productoRepository.existsById(id)) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Producto no encontrado"
            );
        }

        productoRepository.deleteById(id);
    }

    private boolean esPersonalizable(Producto producto) {
        if (Boolean.TRUE.equals(producto.getPersonalizable())) return true;
        String texto = ((producto.getNombre() == null ? "" : producto.getNombre()) + " "
                + (producto.getDescripcion() == null ? "" : producto.getDescripcion()))
                .toLowerCase(Locale.ROOT);
        return texto.contains("personalizable") || texto.contains("para personalizar");
    }

    private boolean usaTallas(Producto producto) {
        String texto = ((producto.getCategoria() == null ? "" : producto.getCategoria()) + " "
                + (producto.getNombre() == null ? "" : producto.getNombre()))
                .toLowerCase(Locale.ROOT);
        return List.of("polo", "camiseta", "camisa", "polera", "hoodie", "sudadera",
                "casaca", "chaqueta", "pantalon", "short", "vestido")
                .stream().anyMatch(texto::contains);
    }

    private String normalizarTallas(String valor) {
        if (valor == null || valor.isBlank()) {
            return "";
        }

        Set<String> permitidas = Set.of("XS", "S", "M", "L", "XL", "XXL");
        LinkedHashSet<String> resultado = new LinkedHashSet<>();
        Arrays.stream(valor.split(","))
                .map(String::trim)
                .map(String::toUpperCase)
                .filter(permitidas::contains)
                .forEach(resultado::add);

        return String.join(",", resultado);
    }

    @PostMapping("/{id}/imagen")
    public Producto subirImagen(
            @RequestHeader(AutenticacionService.HEADER_SESION) String token,
            @PathVariable Long id,
            @RequestParam("archivo") MultipartFile archivo
    ) throws IOException {

        autenticacionService.requerirAdmin(token);
        Producto producto = productoRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "Producto no encontrado"
        ));

        if (archivo == null || archivo.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Debes seleccionar una imagen"
            );
        }

        byte[] bytes = imagenSeguraService.validar(archivo);
        Map<?, ?> resultado = cloudinary.uploader().upload(
                bytes,
                ObjectUtils.asMap(
                        "folder", "pixben/productos",
                        "resource_type", "image",
                        "use_filename", true,
                        "unique_filename", true,
                        "overwrite", false
                )
        );

        Object secureUrl = resultado.get("secure_url");
        if (secureUrl == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "Cloudinary no devolvió la URL de la imagen"
            );
        }

        producto.setImagen(secureUrl.toString());
        return productoRepository.save(producto);
    }
}
