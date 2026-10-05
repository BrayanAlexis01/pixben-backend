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
import java.util.LinkedHashSet;
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
        ImagenProducto galeria = obtenerGaleriaUnica(productoId);
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

        ImagenProducto galeria = obtenerGaleriaUnica(productoId);
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

        ImagenProducto galeria = obtenerGaleriaUnica(productoId);
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
        ImagenProducto galeria = obtenerGaleriaUnica(productoId);
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

    /**
     * Normaliza galerías antiguas duplicadas del mismo producto.
     *
     * Antes el frontend podía subir varias variantes en paralelo; si todavía no
     * existía un documento de galería, dos solicitudes podían crear documentos
     * separados con el mismo productoId. Aquí se conservan las imágenes de todos
     * los documentos, se unifican por variante y se eliminan los duplicados.
     */
    private ImagenProducto obtenerGaleriaUnica(Long productoId) {
        List<ImagenProducto> galerias = imagenProductoRepository.findAllByProductoId(productoId);
        if (galerias == null || galerias.isEmpty()) {
            return null;
        }
        if (galerias.size() == 1) {
            return galerias.get(0);
        }

        ImagenProducto principal = galerias.get(0);
        LinkedHashSet<String> imagenesGenerales = new LinkedHashSet<>();
        Map<String, LinkedHashSet<String>> variantesAcumuladas = new LinkedHashMap<>();

        for (ImagenProducto galeria : galerias) {
            if (galeria == null) continue;

            if (galeria.getImagenes() != null) {
                galeria.getImagenes().stream()
                        .filter(url -> url != null && !url.isBlank())
                        .forEach(imagenesGenerales::add);
            }

            if (galeria.getImagenesPorVariante() != null) {
                galeria.getImagenesPorVariante().forEach((clave, urls) -> {
                    if (clave == null || clave.isBlank() || urls == null) return;
                    LinkedHashSet<String> acumuladas = variantesAcumuladas.computeIfAbsent(
                            clave.toLowerCase(),
                            ignorada -> new LinkedHashSet<>()
                    );
                    urls.stream()
                            .filter(url -> url != null && !url.isBlank())
                            .forEach(acumuladas::add);
                });
            }
        }

        principal.setProductoId(productoId);
        principal.setImagenes(imagenesGenerales.stream()
                .limit(MAXIMO_IMAGENES)
                .toList());

        Map<String, List<String>> variantesUnificadas = new LinkedHashMap<>();
        variantesAcumuladas.forEach((clave, urls) ->
                variantesUnificadas.put(
                        clave,
                        urls.stream().limit(MAXIMO_IMAGENES).toList()
                )
        );
        principal.setImagenesPorVariante(variantesUnificadas);

        ImagenProducto guardada = imagenProductoRepository.save(principal);
        imagenProductoRepository.deleteAll(galerias.subList(1, galerias.size()));
        return guardada;
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
