package com.pixben.controller;

import com.pixben.mongo.ImagenProducto;
import com.pixben.repository.ImagenProductoRepository;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/imagenes")
@CrossOrigin(origins = "*")
public class ImagenProductoController {

    private final ImagenProductoRepository repository;

    public ImagenProductoController(
            ImagenProductoRepository repository) {

        this.repository = repository;
    }

    @GetMapping("/{productoId}")
    public ImagenProducto obtener(
            @PathVariable Long productoId) {

        return repository.findByProductoId(productoId);
    }

}