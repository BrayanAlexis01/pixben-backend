package com.pixben.controller;

import com.pixben.model.Usuario;
import com.pixben.mongo.Favorito;
import com.pixben.repository.FavoritoRepository;
import com.pixben.service.AutenticacionService;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/favoritos")
@CrossOrigin(origins = "*", allowedHeaders = "*")
public class FavoritoController {

    private final FavoritoRepository repository;
    private final AutenticacionService autenticacionService;

    public FavoritoController(FavoritoRepository repository, AutenticacionService autenticacionService) {
        this.repository = repository;
        this.autenticacionService = autenticacionService;
    }

    @PostMapping
    public Favorito guardar(
            @RequestHeader(AutenticacionService.HEADER_SESION) String token,
            @RequestBody Favorito favorito) {
        Usuario usuario = autenticacionService.requerirUsuario(token);
        if (favorito.getProductoId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El producto es obligatorio");
        }
        Optional<Favorito> existente = repository.findFirstByUsuarioIdAndProductoId(usuario.getId(), favorito.getProductoId());
        if (existente.isPresent()) return existente.get();

        favorito.setId(null);
        favorito.setUsuarioId(usuario.getId());
        favorito.setCorreo(usuario.getCorreo());
        favorito.setUsuario(nombreVisible(usuario));
        favorito.setFecha(LocalDateTime.now());
        return repository.save(favorito);
    }

    @GetMapping("/mios")
    public List<Favorito> listarMios(
            @RequestHeader(AutenticacionService.HEADER_SESION) String token) {
        Usuario usuario = autenticacionService.requerirUsuario(token);
        return repository.findByUsuarioId(usuario.getId());
    }

    @GetMapping("/estado")
    public Map<String, Object> estado(
            @RequestHeader(AutenticacionService.HEADER_SESION) String token,
            @RequestParam Long productoId) {
        Usuario usuario = autenticacionService.requerirUsuario(token);
        Optional<Favorito> favorito = repository.findFirstByUsuarioIdAndProductoId(usuario.getId(), productoId);
        return Map.of("favorito", favorito.isPresent(), "id", favorito.map(Favorito::getId).orElse(""));
    }

    @DeleteMapping("/{id}")
    public void eliminar(
            @RequestHeader(AutenticacionService.HEADER_SESION) String token,
            @PathVariable String id) {
        Usuario usuario = autenticacionService.requerirUsuario(token);
        Favorito favorito = repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Favorito no encontrado"));
        if (favorito.getUsuarioId() == null || !usuario.getId().equals(favorito.getUsuarioId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Ese favorito no pertenece a tu cuenta");
        }
        repository.delete(favorito);
    }

    @DeleteMapping("/producto/{productoId}")
    public void eliminarPorProducto(
            @RequestHeader(AutenticacionService.HEADER_SESION) String token,
            @PathVariable Long productoId) {
        Usuario usuario = autenticacionService.requerirUsuario(token);
        repository.findFirstByUsuarioIdAndProductoId(usuario.getId(), productoId).ifPresent(repository::delete);
    }

    private String nombreVisible(Usuario usuario) {
        return usuario.getAlias() != null && !usuario.getAlias().isBlank() ? usuario.getAlias() : usuario.getNombre();
    }
}
