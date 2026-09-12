package com.pixben.service;

import com.pixben.model.SesionUsuario;
import com.pixben.model.Usuario;
import com.pixben.repository.SesionUsuarioRepository;
import com.pixben.repository.UsuarioRepository;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AutenticacionService {

    public static final String HEADER_SESION = "X-Session-Token";
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int DIAS_VIGENCIA = 30;

    private final SesionUsuarioRepository sesionRepository;
    private final UsuarioRepository usuarioRepository;

    public AutenticacionService(
            SesionUsuarioRepository sesionRepository,
            UsuarioRepository usuarioRepository) {
        this.sesionRepository = sesionRepository;
        this.usuarioRepository = usuarioRepository;
    }

    @Transactional
    public String crearSesion(Usuario usuario) {
        sesionRepository.deleteByExpiraEnBefore(LocalDateTime.now());
        byte[] bytes = new byte[48];
        RANDOM.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);

        SesionUsuario sesion = new SesionUsuario();
        sesion.setToken(token);
        sesion.setUsuarioId(usuario.getId());
        sesion.setCreadaEn(LocalDateTime.now());
        sesion.setExpiraEn(LocalDateTime.now().plusDays(DIAS_VIGENCIA));
        sesionRepository.save(sesion);
        return token;
    }

    @Transactional(readOnly = true)
    public Usuario requerirUsuario(String token) {
        if (token == null || token.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Debes iniciar sesión");
        }

        SesionUsuario sesion = sesionRepository.findById(token.trim())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "La sesión no es válida"));

        if (sesion.getExpiraEn() == null || sesion.getExpiraEn().isBefore(LocalDateTime.now())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "La sesión expiró. Inicia sesión nuevamente");
        }

        return usuarioRepository.findById(sesion.getUsuarioId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "La cuenta de la sesión ya no existe"));
    }

    @Transactional(readOnly = true)
    public Usuario usuarioOpcional(String token) {
        if (token == null || token.isBlank()) return null;
        try {
            return requerirUsuario(token);
        } catch (ResponseStatusException ex) {
            return null;
        }
    }

    @Transactional(readOnly = true)
    public Usuario requerirAdmin(String token) {
        Usuario usuario = requerirUsuario(token);
        if (!"admin".equalsIgnoreCase(usuario.getRol())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Acceso exclusivo para administradores");
        }
        return usuario;
    }

    @Transactional
    public void cerrarSesion(String token) {
        if (token != null && !token.isBlank()) {
            sesionRepository.deleteById(token.trim());
        }
    }
}
