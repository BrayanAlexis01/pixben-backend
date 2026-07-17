package com.pixben.controller;

import com.pixben.model.Usuario;
import com.pixben.mongo.Historial;
import com.pixben.repository.HistorialRepository;
import com.pixben.service.AutenticacionService;
import java.time.LocalDateTime;
import java.util.List;
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
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/historial")
@CrossOrigin(origins = "*", allowedHeaders = "*")
public class HistorialController {

    private static final int MAXIMO_POR_USUARIO = 100;
    private final HistorialRepository repository;
    private final AutenticacionService autenticacionService;

    public HistorialController(HistorialRepository repository, AutenticacionService autenticacionService) {
        this.repository = repository;
        this.autenticacionService = autenticacionService;
    }

    @PostMapping
    public Historial guardar(
            @RequestHeader(AutenticacionService.HEADER_SESION) String token,
            @RequestBody Historial entrada) {
        Usuario usuario = autenticacionService.requerirUsuario(token);
        if (entrada.getProductoId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El producto es obligatorio");
        }

        Optional<Historial> existente = repository.findFirstByUsuarioIdAndProductoId(usuario.getId(), entrada.getProductoId());
        Historial historial = existente.orElseGet(Historial::new);
        historial.setUsuarioId(usuario.getId());
        historial.setUsuario(nombreVisible(usuario));
        historial.setCorreo(usuario.getCorreo());
        historial.setProductoId(entrada.getProductoId());
        historial.setFecha(LocalDateTime.now());
        Historial guardado = repository.save(historial);
        limitar(usuario.getId());
        return guardado;
    }

    @GetMapping("/mios")
    public List<Historial> listarMios(
            @RequestHeader(AutenticacionService.HEADER_SESION) String token) {
        Usuario usuario = autenticacionService.requerirUsuario(token);
        return repository.findByUsuarioIdOrderByFechaDesc(usuario.getId());
    }

    @DeleteMapping("/{id}")
    public void eliminar(
            @RequestHeader(AutenticacionService.HEADER_SESION) String token,
            @PathVariable String id) {
        Usuario usuario = autenticacionService.requerirUsuario(token);
        Historial historial = repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Registro no encontrado"));
        if (historial.getUsuarioId() == null || !usuario.getId().equals(historial.getUsuarioId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Ese registro no pertenece a tu historial");
        }
        repository.delete(historial);
    }

    @DeleteMapping("/mios")
    public void limpiarMio(
            @RequestHeader(AutenticacionService.HEADER_SESION) String token) {
        Usuario usuario = autenticacionService.requerirUsuario(token);
        repository.deleteByUsuarioId(usuario.getId());
    }

    @DeleteMapping("/producto/{productoId}")
    public void eliminarProducto(
            @RequestHeader(AutenticacionService.HEADER_SESION) String token,
            @PathVariable Long productoId) {
        Usuario usuario = autenticacionService.requerirUsuario(token);
        repository.deleteByUsuarioIdAndProductoId(usuario.getId(), productoId);
    }

    private void limitar(Long usuarioId) {
        List<Historial> registros = repository.findByUsuarioIdOrderByFechaDesc(usuarioId);
        if (registros.size() <= MAXIMO_POR_USUARIO) return;
        repository.deleteAll(registros.subList(MAXIMO_POR_USUARIO, registros.size()));
    }

    private String nombreVisible(Usuario usuario) {
        return usuario.getAlias() != null && !usuario.getAlias().isBlank() ? usuario.getAlias() : usuario.getNombre();
    }
}
