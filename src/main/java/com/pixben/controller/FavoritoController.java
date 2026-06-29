package com.pixben.controller;

import com.pixben.mongo.Favorito;
import com.pixben.repository.FavoritoRepository;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/favoritos")
@CrossOrigin(origins = "*")
public class FavoritoController {

    private final FavoritoRepository favoritoRepository;

    public FavoritoController(
            FavoritoRepository favoritoRepository) {

        this.favoritoRepository = favoritoRepository;
    }

    @PostMapping
    public Favorito guardar(
            @RequestBody Favorito favorito) {

        return favoritoRepository.save(favorito);
    }

    @GetMapping("/{usuario}")
    public List<Favorito> listar(
            @PathVariable String usuario) {

        return favoritoRepository.findByUsuario(usuario);
    }
}