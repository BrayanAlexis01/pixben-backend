package com.pixben.controller;

import com.pixben.model.Usuario;
import com.pixben.mongo.Carrito;
import com.pixben.repository.CarritoRepository;
import com.pixben.service.AutenticacionService;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/carrito")
@CrossOrigin(origins = "*", allowedHeaders = "*")
public class CarritoController {

    private final CarritoRepository repository;
    private final AutenticacionService autenticacionService;

    public CarritoController(CarritoRepository repository, AutenticacionService autenticacionService) {
        this.repository = repository;
        this.autenticacionService = autenticacionService;
    }

    @PostMapping
    public Carrito agregar(
            @RequestHeader(AutenticacionService.HEADER_SESION) String token,
            @RequestBody Carrito carrito) {
        Usuario usuario = autenticacionService.requerirUsuario(token);
        if (carrito.getProductoId() == null && !Boolean.TRUE.equals(carrito.getPersonalizado())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El producto es obligatorio");
        }
        carrito.setId(null);
        carrito.setUsuarioId(usuario.getId());
        carrito.setCorreo(usuario.getCorreo());
        carrito.setUsuario(nombreVisible(usuario));
        if (carrito.getCantidad() == null || carrito.getCantidad() < 1) carrito.setCantidad(1);
        return repository.save(carrito);
    }

    @GetMapping("/mios")
    public List<Carrito> listarMios(
            @RequestHeader(AutenticacionService.HEADER_SESION) String token) {
        Usuario usuario = autenticacionService.requerirUsuario(token);
        return repository.findByUsuarioId(usuario.getId());
    }

    @DeleteMapping("/{id}")
    public void eliminar(
            @RequestHeader(AutenticacionService.HEADER_SESION) String token,
            @PathVariable String id) {
        Usuario usuario = autenticacionService.requerirUsuario(token);
        Carrito item = obtenerPropio(id, usuario.getId());
        repository.delete(item);
    }

    @PutMapping("/{id}/{cantidad}")
    public Carrito actualizarCantidad(
            @RequestHeader(AutenticacionService.HEADER_SESION) String token,
            @PathVariable String id,
            @PathVariable Integer cantidad) {
        Usuario usuario = autenticacionService.requerirUsuario(token);
        Carrito carrito = obtenerPropio(id, usuario.getId());
        carrito.setCantidad(Math.max(1, cantidad));
        return repository.save(carrito);
    }

    @DeleteMapping("/mios")
    public void vaciarMio(
            @RequestHeader(AutenticacionService.HEADER_SESION) String token) {
        Usuario usuario = autenticacionService.requerirUsuario(token);
        repository.deleteByUsuarioId(usuario.getId());
    }

    private Carrito obtenerPropio(String id, Long usuarioId) {
        Carrito item = repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Elemento del carrito no encontrado"));
        if (item.getUsuarioId() == null || !usuarioId.equals(item.getUsuarioId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Ese producto no pertenece a tu carrito");
        }
        return item;
    }

    private String nombreVisible(Usuario usuario) {
        return usuario.getAlias() != null && !usuario.getAlias().isBlank()
                ? usuario.getAlias()
                : usuario.getNombre();
    }
}
