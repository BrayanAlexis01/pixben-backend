package com.pixben.controller;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import com.pixben.model.Producto;
import com.pixben.model.VarianteColor;
import com.pixben.mongo.ImagenProducto;
import com.pixben.repository.ImagenProductoRepository;
import com.pixben.repository.ProductoRepository;
import com.pixben.service.ImagenSeguraService;
import com.pixben.service.AutenticacionService;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/imagenes")
public class ImagenProductoController {

    private static final int MAXIMO_IMAGENES = 7;

    private final ImagenProductoRepository imagenProductoRepository;
    private final ProductoRepository productoRepository;
    private final Cloudinary cloudinary;
    private final ImagenSeguraService imagenSeguraService;
    private final AutenticacionService autenticacionService;

    public ImagenProductoController(
            ImagenProductoRepository imagenProductoRepository,
            ProductoRepository productoRepository,
            Cloudinary cloudinary,
            ImagenSeguraService imagenSeguraService,
            AutenticacionService autenticacionService) {
        this.imagenProductoRepository = imagenProductoRepository;
        this.productoRepository = productoRepository;
        this.cloudinary = cloudinary;
        this.imagenSeguraService = imagenSeguraService;
        this.autenticacionService = autenticacionService;
    }

    @GetMapping("/{productoId}")
    @Cacheable(value = "galerias", key = "#productoId")
    public ResponseEntity<ImagenProducto> obtener(@PathVariable Long productoId) {
        ImagenProducto galeria = imagenProductoRepository.findByProductoId(productoId);
        return galeria == null
                ? ResponseEntity.notFound().build()
                : ResponseEntity.ok(galeria);
    }

    @PostMapping(
            value = "/{productoId}",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    @CacheEvict(value = {"galerias", "productos"}, allEntries = true)
    public ImagenProducto subirGaleria(
            @RequestHeader(AutenticacionService.HEADER_SESION) String token,
            @PathVariable Long productoId,
            @RequestParam("archivos") List<MultipartFile> archivos
    ) throws IOException {

        autenticacionService.requerirAdmin(token);
        Producto producto = productoRepository.findById(productoId)
                .orElseThrow(() -> new ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "Producto no encontrado"
        ));

        List<MultipartFile> imagenesValidas = archivos == null
                ? List.of()
                : archivos.stream()
                        .filter(archivo -> archivo != null && !archivo.isEmpty())
                        .toList();

        if (imagenesValidas.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Debes seleccionar al menos una imagen"
            );
        }

        if (imagenesValidas.size() > MAXIMO_IMAGENES) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Solo puedes subir hasta 7 imágenes por producto"
            );
        }

        List<String> urls = subirImagenesCloudinary(imagenesValidas, "pixben/productos/" + productoId);

        ImagenProducto galeria = imagenProductoRepository.findByProductoId(productoId);
        if (galeria == null) {
            galeria = new ImagenProducto();
            galeria.setProductoId(productoId);
        }

        galeria.setImagenes(urls);
        ImagenProducto guardada = imagenProductoRepository.save(galeria);

        // La primera imagen también queda como portada del producto.
        producto.setImagen(urls.get(0));
        productoRepository.save(producto);

        return guardada;
    }

    @PostMapping(
            value = "/{productoId}/variantes/{claveColor}",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    @CacheEvict(value = {"galerias", "productos"}, allEntries = true)
    public ImagenProducto subirGaleriaVariante(
            @RequestHeader(AutenticacionService.HEADER_SESION) String token,
            @PathVariable Long productoId,
            @PathVariable String claveColor,
            @RequestParam("archivos") List<MultipartFile> archivos
    ) throws IOException {

        autenticacionService.requerirAdmin(token);
        Producto producto = productoRepository.findById(productoId)
                .orElseThrow(() -> new ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "Producto no encontrado"
        ));

        VarianteColor variante = producto.getColores() == null ? null : producto.getColores().stream()
                .filter(color -> color != null && color.getClave() != null
                && color.getClave().equalsIgnoreCase(claveColor))
                .findFirst()
                .orElse(null);

        if (variante == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La variante de color no pertenece a este producto");
        }

        List<MultipartFile> imagenesValidas = archivos == null
                ? List.of()
                : archivos.stream()
                        .filter(archivo -> archivo != null && !archivo.isEmpty())
                        .toList();

        if (imagenesValidas.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Debes seleccionar al menos una imagen para este color");
        }
        if (imagenesValidas.size() > MAXIMO_IMAGENES) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Solo puedes subir hasta 7 imágenes por color");
        }

        List<String> urls = subirImagenesCloudinary(imagenesValidas,
                "pixben/productos/" + productoId + "/variantes/" + claveColor);

        ImagenProducto galeria = imagenProductoRepository.findByProductoId(productoId);
        if (galeria == null) {
            galeria = new ImagenProducto();
            galeria.setProductoId(productoId);
        }
        Map<String, List<String>> porVariante = galeria.getImagenesPorVariante() == null
                ? new LinkedHashMap<>()
                : new LinkedHashMap<>(galeria.getImagenesPorVariante());
        porVariante.put(claveColor.toLowerCase(), urls);
        galeria.setImagenesPorVariante(porVariante);
        ImagenProducto guardada = imagenProductoRepository.save(galeria);

        // La primera variante actúa como portada general del producto para catálogo, SEO y compatibilidad.
        if (producto.getColores() != null && !producto.getColores().isEmpty()
                && producto.getColores().get(0).getClave() != null
                && producto.getColores().get(0).getClave().equalsIgnoreCase(claveColor)) {
            producto.setImagen(urls.get(0));
            productoRepository.save(producto);
        }

        return guardada;
    }

    @DeleteMapping("/{productoId}/variantes/{claveColor}")
    @CacheEvict(value = {"galerias", "productos"}, allEntries = true)
    public ImagenProducto eliminarGaleriaVariante(
            @RequestHeader(AutenticacionService.HEADER_SESION) String token,
            @PathVariable Long productoId,
            @PathVariable String claveColor) {
        autenticacionService.requerirAdmin(token);
        Producto producto = productoRepository.findById(productoId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Producto no encontrado"));
        boolean existe = producto.getColores() != null && producto.getColores().stream()
                .anyMatch(color -> color != null && color.getClave() != null && color.getClave().equalsIgnoreCase(claveColor));
        if (!existe) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La variante de color no pertenece a este producto");
        }
        ImagenProducto galeria = imagenProductoRepository.findByProductoId(productoId);
        if (galeria == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Este producto no tiene galería registrada");
        }
        Map<String, List<String>> porVariante = galeria.getImagenesPorVariante() == null
                ? new LinkedHashMap<>()
                : new LinkedHashMap<>(galeria.getImagenesPorVariante());
        porVariante.remove(claveColor.toLowerCase());
        galeria.setImagenesPorVariante(porVariante);
        return imagenProductoRepository.save(galeria);
    }

    private List<String> subirImagenesCloudinary(List<MultipartFile> archivos, String carpeta) throws IOException {
        List<String> urls = new ArrayList<>();
        for (MultipartFile archivo : archivos) {
            byte[] bytes = imagenSeguraService.validar(archivo);
            Map<?, ?> resultado = cloudinary.uploader().upload(
                    bytes,
                    ObjectUtils.asMap(
                            "folder", carpeta,
                            "resource_type", "image",
                            "use_filename", true,
                            "unique_filename", true,
                            "overwrite", false
                    )
            );
            Object secureUrl = resultado.get("secure_url");
            if (secureUrl == null) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Cloudinary no devolvió la URL de una de las imágenes");
            }
            urls.add(secureUrl.toString());
        }
        return urls;
    }

}
