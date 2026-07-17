package com.pixben.controller;

import com.pixben.model.Categoria;
import com.pixben.repository.CategoriaRepository;
import com.pixben.service.AutenticacionService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/categorias")
@CrossOrigin(origins="*")
public class CategoriaController {

    private final CategoriaRepository repository;
    private final AutenticacionService autenticacionService;

    public CategoriaController(CategoriaRepository repository, AutenticacionService autenticacionService) {
        this.repository = repository;
        this.autenticacionService = autenticacionService;
    }

    @GetMapping
    public List<Categoria> listar() {
        return repository.findAll();
    }

    @PostMapping
    public Categoria guardar(
            @RequestHeader(AutenticacionService.HEADER_SESION) String token,
            @RequestBody Categoria categoria) {
        autenticacionService.requerirAdmin(token);
        return repository.save(categoria);
    }

    @DeleteMapping("/{id}")
    public void eliminar(
            @RequestHeader(AutenticacionService.HEADER_SESION) String token,
            @PathVariable Long id) {
        autenticacionService.requerirAdmin(token);
        repository.deleteById(id);
    }

}