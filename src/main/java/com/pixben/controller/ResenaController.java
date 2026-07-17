package com.pixben.controller;

import com.pixben.dto.ResenaPaginaResponse;
import com.pixben.model.Usuario;
import com.pixben.mongo.Resena;
import com.pixben.repository.ResenaRepository;
import com.pixben.repository.UsuarioRepository;
import com.pixben.service.AutenticacionService;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/resenas")
@CrossOrigin(origins = "*", allowedHeaders = "*")
public class ResenaController {

    private final ResenaRepository repository;
    private final UsuarioRepository usuarioRepository;
    private final AutenticacionService autenticacionService;

    public ResenaController(
            ResenaRepository repository,
            UsuarioRepository usuarioRepository,
            AutenticacionService autenticacionService) {
        this.repository = repository;
        this.usuarioRepository = usuarioRepository;
        this.autenticacionService = autenticacionService;
    }

    @GetMapping("/todas")
    public List<Resena> todas() {
        List<Resena> resenas = repository.findAll();
        resenas.forEach(r -> enriquecer(r, null));
        return resenas;
    }

    @GetMapping("/producto/{productoId}")
    public List<Resena> listarPorProducto(@PathVariable Long productoId) {
        List<Resena> resenas = repository.findByProductoId(productoId);
        resenas.forEach(r -> enriquecer(r, null));
        return resenas;
    }

    @GetMapping("/producto/{productoId}/pagina")
    public ResenaPaginaResponse listarPagina(
            @RequestHeader(value = AutenticacionService.HEADER_SESION, required = false) String token,
            @PathVariable Long productoId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "6") int size) {
        int pagina = Math.max(page, 0);
        int tamanio = Math.min(Math.max(size, 1), 20);
        Usuario actual = autenticacionService.usuarioOpcional(token);
        Long usuarioActualId = actual == null ? null : actual.getId();

        Page<Resena> resultado = repository.findByProductoIdOrderByFechaDesc(
                productoId, PageRequest.of(pagina, tamanio));
        resultado.getContent().forEach(r -> enriquecer(r, usuarioActualId));
        return new ResenaPaginaResponse(
                resultado.getContent(), pagina, resultado.getTotalPages(),
                resultado.getTotalElements(), resultado.hasNext()
        );
    }

    @PostMapping
    public Resena guardar(
            @RequestHeader(AutenticacionService.HEADER_SESION) String token,
            @RequestBody Resena resena) {
        Usuario usuario = autenticacionService.requerirUsuario(token);
        validar(resena);
        resena.setId(null);
        resena.setUsuarioId(usuario.getId());
        resena.setUsuario(usuario.getNombre());
        resena.setAliasUsuario(nombreVisible(usuario));
        resena.setFotoPerfilUrl(usuario.getFotoPerfilUrl());
        resena.setComentario(resena.getComentario().trim());
        resena.setFecha(LocalDateTime.now());
        resena.setEditada(false);
        resena.setUsuariosMeGusta(new HashSet<>());
        return repository.save(resena);
    }

    @PutMapping("/{id}")
    public Resena editar(
            @RequestHeader(AutenticacionService.HEADER_SESION) String token,
            @PathVariable String id,
            @RequestBody Resena datos) {
        Usuario usuario = autenticacionService.requerirUsuario(token);
        Resena resena = obtener(id);
        validarPropietarioOAdmin(resena, usuario);

        if (datos.getComentario() != null) {
            String comentario = datos.getComentario().trim();
            if (comentario.isBlank() || comentario.length() > 1000) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Comentario no válido");
            }
            resena.setComentario(comentario);
        }
        if (datos.getCalificacion() != null) {
            if (datos.getCalificacion() < 1 || datos.getCalificacion() > 5) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Calificación no válida");
            }
            resena.setCalificacion(datos.getCalificacion());
        }
        resena.setEditada(true);
        return repository.save(resena);
    }

    @PostMapping("/{id}/me-gusta")
    public Map<String, Object> alternarMeGusta(
            @RequestHeader(AutenticacionService.HEADER_SESION) String token,
            @PathVariable String id) {
        Usuario usuario = autenticacionService.requerirUsuario(token);
        Resena resena = obtener(id);
        if (resena.getUsuariosMeGusta() == null) resena.setUsuariosMeGusta(new HashSet<>());
        boolean activo;
        if (resena.getUsuariosMeGusta().contains(usuario.getId())) {
            resena.getUsuariosMeGusta().remove(usuario.getId());
            activo = false;
        } else {
            resena.getUsuariosMeGusta().add(usuario.getId());
            activo = true;
        }
        repository.save(resena);
        return Map.of("activo", activo, "cantidad", resena.getCantidadMeGusta());
    }

    @DeleteMapping("/{id}")
    public void eliminar(
            @RequestHeader(AutenticacionService.HEADER_SESION) String token,
            @PathVariable String id) {
        Usuario usuario = autenticacionService.requerirUsuario(token);
        Resena resena = obtener(id);
        validarPropietarioOAdmin(resena, usuario);
        repository.delete(resena);
    }

    private void validar(Resena resena) {
        if (resena == null || resena.getProductoId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El producto es obligatorio");
        }
        if (resena.getComentario() == null || resena.getComentario().trim().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El comentario es obligatorio");
        }
        if (resena.getComentario().length() > 1000) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La reseña no puede superar 1000 caracteres");
        }
        if (resena.getCalificacion() == null || resena.getCalificacion() < 1 || resena.getCalificacion() > 5) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La calificación debe estar entre 1 y 5");
        }
    }

    private Resena obtener(String id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Reseña no encontrada"));
    }

    private void validarPropietarioOAdmin(Resena resena, Usuario usuario) {
        boolean admin = "admin".equalsIgnoreCase(usuario.getRol());
        boolean propia = resena.getUsuarioId() != null && usuario.getId().equals(resena.getUsuarioId());
        if (!admin && !propia) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No puedes modificar la reseña de otra cuenta");
        }
    }

    private void enriquecer(Resena resena, Long usuarioActualId) {
        if (resena.getUsuarioId() != null) {
            Usuario usuario = usuarioRepository.findById(resena.getUsuarioId()).orElse(null);
            if (usuario != null) {
                resena.setAliasUsuario(nombreVisible(usuario));
                resena.setFotoPerfilUrl(usuario.getFotoPerfilUrl());
                if (resena.getUsuario() == null || resena.getUsuario().isBlank()) resena.setUsuario(usuario.getNombre());
            }
        }
        if (resena.getAliasUsuario() == null || resena.getAliasUsuario().isBlank()) {
            resena.setAliasUsuario(resena.getUsuario() == null || resena.getUsuario().isBlank()
                    ? "Cliente PixBen" : resena.getUsuario());
        }
        resena.setMeGustaUsuarioActual(usuarioActualId != null
                && resena.getUsuariosMeGusta() != null
                && resena.getUsuariosMeGusta().contains(usuarioActualId));
    }

    private String nombreVisible(Usuario usuario) {
        return usuario.getAlias() != null && !usuario.getAlias().isBlank()
                ? usuario.getAlias() : usuario.getNombre();
    }
}
