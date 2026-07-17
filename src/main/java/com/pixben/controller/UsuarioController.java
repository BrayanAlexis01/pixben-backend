package com.pixben.controller;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import com.pixben.dto.ActualizarPerfilRequest;
import com.pixben.dto.CambiarPasswordRequest;
import com.pixben.dto.UsuarioSesionDto;
import com.pixben.model.Usuario;
import com.pixben.repository.UsuarioRepository;
import com.pixben.service.AutenticacionService;
import com.pixben.service.ImagenSeguraService;
import java.io.IOException;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/usuarios")
@CrossOrigin(origins = "*", allowedHeaders = "*")
public class UsuarioController {

    private static final Set<String> DOMINIOS_CLIENTE = Set.of(
            "gmail.com", "hotmail.com", "outlook.com", "live.com"
    );
    private static final Pattern CORREO_BASICO = Pattern.compile(
            "^[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}$",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern PASSWORD_FUERTE = Pattern.compile(
            "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[^A-Za-z0-9]).{8,72}$"
    );

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;
    private final Cloudinary cloudinary;
    private final ImagenSeguraService imagenSeguraService;
    private final AutenticacionService autenticacionService;

    public UsuarioController(
            UsuarioRepository usuarioRepository,
            PasswordEncoder passwordEncoder,
            Cloudinary cloudinary,
            ImagenSeguraService imagenSeguraService,
            AutenticacionService autenticacionService) {
        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
        this.cloudinary = cloudinary;
        this.imagenSeguraService = imagenSeguraService;
        this.autenticacionService = autenticacionService;
    }

    @PostMapping
    public UsuarioSesionDto registrar(@RequestBody Usuario usuario) {
        String correo = limpiarCorreo(usuario.getCorreo());
        validarCorreoCliente(correo);

        if (usuarioRepository.existsByCorreoIgnoreCase(correo)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Ya existe una cuenta con ese correo");
        }
        validarPasswordFuerte(usuario.getPassword());

        usuario.setCorreo(correo);
        usuario.setNombre(limpiarObligatorio(usuario.getNombre(), 80, "El nombre es obligatorio"));
        usuario.setApellido(limpiarObligatorio(usuario.getApellido(), 100, "El apellido es obligatorio"));
        usuario.setAlias(limpiar(usuario.getAlias(), 60));
        usuario.setPassword(passwordEncoder.encode(usuario.getPassword()));
        // El registro público nunca puede crear administradores.
        usuario.setRol("cliente");

        Usuario guardado = usuarioRepository.save(usuario);
        return convertir(guardado, autenticacionService.crearSesion(guardado));
    }

    @PostMapping("/login")
    public ResponseEntity<UsuarioSesionDto> login(@RequestBody Usuario datos) {
        String correo = limpiarCorreo(datos.getCorreo());
        Usuario usuario = usuarioRepository.findByCorreoIgnoreCase(correo).orElse(null);

        if (usuario == null || !passwordValida(datos.getPassword(), usuario.getPassword())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        boolean modificado = asegurarNombre(usuario);
        if (!esPasswordCifrado(usuario.getPassword())) {
            usuario.setPassword(passwordEncoder.encode(datos.getPassword()));
            modificado = true;
        }
        if (modificado) usuario = usuarioRepository.save(usuario);

        String token = autenticacionService.crearSesion(usuario);
        return ResponseEntity.ok(convertir(usuario, token));
    }

    @PostMapping("/logout")
    public Map<String, String> logout(
            @RequestHeader(value = AutenticacionService.HEADER_SESION, required = false) String token) {
        autenticacionService.cerrarSesion(token);
        return Map.of("mensaje", "Sesión cerrada");
    }

    @GetMapping("/me")
    public UsuarioSesionDto obtenerActual(
            @RequestHeader(AutenticacionService.HEADER_SESION) String token) {
        return convertir(autenticacionService.requerirUsuario(token), token);
    }

    @PutMapping("/me/perfil")
    public UsuarioSesionDto actualizarPerfil(
            @RequestHeader(AutenticacionService.HEADER_SESION) String token,
            @RequestBody ActualizarPerfilRequest cambios) {
        Usuario usuario = autenticacionService.requerirUsuario(token);
        usuario.setNombre(limpiarObligatorio(cambios.getNombre(), 80, "El nombre es obligatorio"));
        usuario.setApellido(limpiarObligatorio(cambios.getApellido(), 100, "El apellido es obligatorio"));
        usuario.setAlias(limpiar(cambios.getAlias(), 60));
        return convertir(usuarioRepository.save(usuario), token);
    }

    @PatchMapping("/me/password")
    public Map<String, String> cambiarPassword(
            @RequestHeader(AutenticacionService.HEADER_SESION) String token,
            @RequestBody CambiarPasswordRequest datos) {
        Usuario usuario = autenticacionService.requerirUsuario(token);
        if (!passwordValida(datos.getPasswordActual(), usuario.getPassword())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La contraseña actual no es correcta");
        }
        validarPasswordFuerte(datos.getPasswordNueva());
        if (!datos.getPasswordNueva().equals(datos.getConfirmarPassword())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Las contraseñas nuevas no coinciden");
        }
        usuario.setPassword(passwordEncoder.encode(datos.getPasswordNueva()));
        usuarioRepository.save(usuario);
        return Map.of("mensaje", "Contraseña actualizada correctamente");
    }

    @PostMapping(value = "/me/foto", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public UsuarioSesionDto actualizarFoto(
            @RequestHeader(AutenticacionService.HEADER_SESION) String token,
            @RequestPart("foto") MultipartFile foto) throws IOException {
        Usuario usuario = autenticacionService.requerirUsuario(token);
        byte[] bytes = imagenSeguraService.validar(foto);
        Map<?, ?> resultado = cloudinary.uploader().upload(
                bytes,
                ObjectUtils.asMap(
                        "folder", "pixben/perfiles",
                        "public_id", "usuario-" + usuario.getId(),
                        "overwrite", true,
                        "resource_type", "image",
                        "quality", "auto",
                        "flags", "strip_profile"
                )
        );
        Object url = resultado.get("secure_url");
        if (url == null) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Cloudinary no devolvió la URL de la foto");
        }
        usuario.setFotoPerfilUrl(url.toString());
        return convertir(usuarioRepository.save(usuario), token);
    }

    private boolean passwordValida(String enviada, String guardada) {
        if (enviada == null || guardada == null) return false;
        return esPasswordCifrado(guardada)
                ? passwordEncoder.matches(enviada, guardada)
                : enviada.equals(guardada);
    }

    private boolean esPasswordCifrado(String password) {
        return password != null && (password.startsWith("$2a$") || password.startsWith("$2b$") || password.startsWith("$2y$"));
    }

    private boolean asegurarNombre(Usuario usuario) {
        if (usuario.getNombre() != null && !usuario.getNombre().isBlank()) return false;
        String correo = usuario.getCorreo() == null ? "usuario" : usuario.getCorreo();
        int arroba = correo.indexOf('@');
        usuario.setNombre(arroba > 0 ? correo.substring(0, arroba) : correo);
        return true;
    }

    private UsuarioSesionDto convertir(Usuario usuario, String token) {
        asegurarNombre(usuario);
        return new UsuarioSesionDto(
                usuario.getId(), usuario.getNombre(), usuario.getApellido(), usuario.getCorreo(),
                usuario.getRol(), usuario.getAlias(), usuario.getFotoPerfilUrl(), token
        );
    }

    private void validarCorreoCliente(String correo) {
        if (!CORREO_BASICO.matcher(correo).matches()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Ingresa un correo electrónico válido");
        }
        String dominio = correo.substring(correo.lastIndexOf('@') + 1);
        if (!DOMINIOS_CLIENTE.contains(dominio)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Para registrarte usa un correo Gmail, Hotmail, Outlook o Live"
            );
        }
    }

    private void validarPasswordFuerte(String password) {
        if (password == null || !PASSWORD_FUERTE.matcher(password).matches()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "La contraseña debe tener 8 caracteres, mayúscula, minúscula, número y símbolo"
            );
        }
    }

    private String limpiarCorreo(String valor) {
        return valor == null ? "" : valor.trim().toLowerCase(Locale.ROOT);
    }

    private String limpiarObligatorio(String valor, int maximo, String mensaje) {
        String limpio = limpiar(valor, maximo);
        if (limpio == null || limpio.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, mensaje);
        }
        return limpio;
    }

    private String limpiar(String valor, int maximo) {
        if (valor == null) return null;
        String limpio = valor.replaceAll("[\\p{Cntrl}&&[^\\r\\n\\t]]", "").trim();
        return limpio.length() <= maximo ? limpio : limpio.substring(0, maximo);
    }
}
