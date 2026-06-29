package com.pixben.controller;

import com.pixben.mongo.Resena;
import com.pixben.repository.ResenaRepository;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/resenas")
@CrossOrigin(origins = "*")
public class ResenaController {

    private final ResenaRepository resenaRepository;

    public ResenaController(ResenaRepository resenaRepository) {
        this.resenaRepository = resenaRepository;
    }

    @GetMapping("/todas")
    public List<Resena> todas() {
        return resenaRepository.findAll();
    }

    @GetMapping("/producto/{productoId}")
    public List<Resena> listarPorProducto(@PathVariable Long productoId) {
        return resenaRepository.findByProductoId(productoId);
    }

    @PostMapping
    public Resena guardar(@RequestBody Resena resena) {

        resena.setFecha(LocalDateTime.now());

        resena.setEditada(false);

        return resenaRepository.save(resena);

    }

    @GetMapping("/debug")
    public String debug() {
        return "Mongo conectado";

    }

    @PutMapping("/{id}")
    public Resena editar(@PathVariable String id,
            @RequestBody Resena datos) {

        Resena r = resenaRepository.findById(id).orElse(null);

        if (r == null) {
            return null;
        }

        r.setComentario(datos.getComentario());
        r.setCalificacion(datos.getCalificacion());
        r.setEditada(true);

        return resenaRepository.save(r);
    }

    @DeleteMapping("/{id}")
    public void eliminar(@PathVariable String id) {
        resenaRepository.deleteById(id);
    }

}
