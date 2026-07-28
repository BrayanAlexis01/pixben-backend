package com.pixben.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pixben.model.Usuario;
import com.pixben.repository.CarritoRepository;
import com.pixben.repository.CategoriaRepository;
import com.pixben.repository.ContactoRepository;
import com.pixben.repository.FavoritoRepository;
import com.pixben.repository.HistorialRepository;
import com.pixben.repository.ImagenProductoRepository;
import com.pixben.repository.PedidoPersonalizadoRepository;
import com.pixben.repository.PedidoRepository;
import com.pixben.repository.ProductoRepository;
import com.pixben.repository.ResenaRepository;
import com.pixben.repository.UsuarioRepository;
import com.pixben.repository.VisitaRepository;
import com.pixben.service.AutenticacionService;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin")
@CrossOrigin(origins = "*", allowedHeaders = "*")
public class RespaldoController {

    private final AutenticacionService autenticacionService;
    private final ObjectMapper objectMapper;
    private final ProductoRepository productoRepository;
    private final CategoriaRepository categoriaRepository;
    private final UsuarioRepository usuarioRepository;
    private final PedidoRepository pedidoRepository;
    private final PedidoPersonalizadoRepository pedidoPersonalizadoRepository;
    private final ContactoRepository contactoRepository;
    private final CarritoRepository carritoRepository;
    private final FavoritoRepository favoritoRepository;
    private final HistorialRepository historialRepository;
    private final ResenaRepository resenaRepository;
    private final VisitaRepository visitaRepository;
    private final ImagenProductoRepository imagenProductoRepository;

    public RespaldoController(
            AutenticacionService autenticacionService,
            ObjectMapper objectMapper,
            ProductoRepository productoRepository,
            CategoriaRepository categoriaRepository,
            UsuarioRepository usuarioRepository,
            PedidoRepository pedidoRepository,
            PedidoPersonalizadoRepository pedidoPersonalizadoRepository,
            ContactoRepository contactoRepository,
            CarritoRepository carritoRepository,
            FavoritoRepository favoritoRepository,
            HistorialRepository historialRepository,
            ResenaRepository resenaRepository,
            VisitaRepository visitaRepository,
            ImagenProductoRepository imagenProductoRepository) {
        this.autenticacionService = autenticacionService;
        this.objectMapper = objectMapper;
        this.productoRepository = productoRepository;
        this.categoriaRepository = categoriaRepository;
        this.usuarioRepository = usuarioRepository;
        this.pedidoRepository = pedidoRepository;
        this.pedidoPersonalizadoRepository = pedidoPersonalizadoRepository;
        this.contactoRepository = contactoRepository;
        this.carritoRepository = carritoRepository;
        this.favoritoRepository = favoritoRepository;
        this.historialRepository = historialRepository;
        this.resenaRepository = resenaRepository;
        this.visitaRepository = visitaRepository;
        this.imagenProductoRepository = imagenProductoRepository;
    }

    @GetMapping(value = "/respaldo", produces = "application/zip")
    public ResponseEntity<byte[]> descargar(
            @RequestHeader(AutenticacionService.HEADER_SESION) String token) throws IOException {
        autenticacionService.requerirAdmin(token);

        ByteArrayOutputStream salida = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(salida)) {
            agregarJson(zip, "productos.json", productoRepository.findAll());
            agregarJson(zip, "categorias.json", categoriaRepository.findAll());
            agregarJson(zip, "usuarios_sin_password.json", usuariosSeguros());
            agregarJson(zip, "pedidos.json", pedidoRepository.findAll());
            agregarJson(zip, "pedidos_personalizados.json", pedidoPersonalizadoRepository.findAll());
            agregarJson(zip, "mensajes_contacto.json", contactoRepository.findAll());
            agregarJson(zip, "carritos.json", carritoRepository.findAll());
            agregarJson(zip, "favoritos.json", favoritoRepository.findAll());
            agregarJson(zip, "historial.json", historialRepository.findAll());
            agregarJson(zip, "resenas.json", resenaRepository.findAll());
            agregarJson(zip, "analitica_visitas.json", visitaRepository.findAll());
            agregarJson(zip, "galerias_productos.json", imagenProductoRepository.findAll());

            Map<String, Object> info = new LinkedHashMap<>();
            info.put("generadoEn", LocalDateTime.now());
            info.put("nota", "No contiene contraseñas, tokens ni secretos de infraestructura.");
            agregarJson(zip, "LEEME.json", info);
        }

        String fecha = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType("application/zip"));
        headers.setContentDisposition(ContentDisposition.attachment()
                .filename("pixben-respaldo-" + fecha + ".zip")
                .build());
        return ResponseEntity.ok().headers(headers).body(salida.toByteArray());
    }

    private List<Map<String, Object>> usuariosSeguros() {
        return usuarioRepository.findAll().stream().map(this::usuarioSeguro).toList();
    }

    private Map<String, Object> usuarioSeguro(Usuario usuario) {
        Map<String, Object> datos = new LinkedHashMap<>();
        datos.put("id", usuario.getId());
        datos.put("nombre", usuario.getNombre());
        datos.put("apellido", usuario.getApellido());
        datos.put("correo", usuario.getCorreo());
        datos.put("rol", usuario.getRol());
        datos.put("alias", usuario.getAlias());
        datos.put("fotoPerfilUrl", usuario.getFotoPerfilUrl());
        return datos;
    }

    private void agregarJson(ZipOutputStream zip, String nombre, Object datos) throws IOException {
        zip.putNextEntry(new ZipEntry(nombre));
        zip.write(objectMapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(datos));
        zip.closeEntry();
    }
}
