package com.pixben.controller;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import com.pixben.model.Producto;
import com.pixben.mongo.ImagenProducto;
import com.pixben.repository.ImagenProductoRepository;
import com.pixben.repository.ProductoRepository;
import com.pixben.service.ImagenSeguraService;
import com.pixben.service.AutenticacionService;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
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
@CrossOrigin(origins = "*")
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

        List<String> urls = new ArrayList<>();

        for (MultipartFile archivo : imagenesValidas) {
            byte[] bytes = imagenSeguraService.validar(archivo);
            Map<?, ?> resultado = cloudinary.uploader().upload(
                    bytes,
                    ObjectUtils.asMap(
                            "folder", "pixben/productos/" + productoId,
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
                        "Cloudinary no devolvió la URL de una de las imágenes"
                );
            }

            urls.add(secureUrl.toString());
        }

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
}
