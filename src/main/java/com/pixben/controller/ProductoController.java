/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.pixben.controller;

import org.springframework.web.multipart.MultipartFile;
import java.nio.file.*;
import java.io.IOException;
import org.springframework.web.bind.annotation.CrossOrigin;
import com.pixben.model.Producto;
import com.pixben.repository.ProductoRepository;
import org.springframework.web.bind.annotation.*;

import java.util.List;

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

    public ProductoController(ProductoRepository productoRepository) {
        this.productoRepository = productoRepository;
    }

    @GetMapping
    public List<Producto> listarProductos() {
        return productoRepository.findAll();

    }

    @PostMapping
    public Producto guardarProducto(@RequestBody Producto producto) {
        return productoRepository.save(producto);
    }

    @GetMapping("/{id}")
    public Producto obtenerProducto(@PathVariable Long id) {
        return productoRepository.findById(id).orElse(null);
    }

    @PutMapping("/{id}")
    public Producto actualizarProducto(
            @PathVariable Long id,
            @RequestBody Producto producto) {

        producto.setId(id);

        return productoRepository.save(producto);
    }

    @DeleteMapping("/{id}")
    public void eliminarProducto(@PathVariable Long id) {
        productoRepository.deleteById(id);
    }

    @PostMapping("/{id}/imagen")
    public Producto subirImagen(
            @PathVariable Long id,
            @RequestParam("archivo") MultipartFile archivo)
            throws IOException {

        Producto producto
                = productoRepository.findById(id).orElse(null);

        if (producto == null) {
            return null;
        }

        String nombreArchivo
                = archivo.getOriginalFilename();

        Path ruta = Paths.get("./imagen");

        Files.createDirectories(ruta);

        Files.copy(
                archivo.getInputStream(),
                ruta.resolve(nombreArchivo),
                StandardCopyOption.REPLACE_EXISTING);

        producto.setImagen(nombreArchivo);

        return productoRepository.save(producto);
    }
}
