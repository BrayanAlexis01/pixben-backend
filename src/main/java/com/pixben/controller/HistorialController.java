package com.pixben.controller;

import com.pixben.mongo.Historial;
import com.pixben.repository.HistorialRepository;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/historial")
@CrossOrigin(origins = "*")
public class HistorialController {

    private final HistorialRepository historialRepository;

    public HistorialController(
            HistorialRepository historialRepository) {

        this.historialRepository = historialRepository;
    }

    @PostMapping
    public Historial guardar(
            @RequestBody Historial historial) {

        return historialRepository.save(historial);
    }

    @GetMapping("/{usuario}")
    public List<Historial> listar(
            @PathVariable String usuario) {

        return historialRepository.findByUsuario(usuario);
    }
}