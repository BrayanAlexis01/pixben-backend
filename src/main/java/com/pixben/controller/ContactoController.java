package com.pixben.controller;

import com.pixben.mongo.Contacto;
import com.pixben.repository.ContactoRepository;
import com.pixben.service.AutenticacionService;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/contactos")
@CrossOrigin(origins = "*", allowedHeaders = "*")
public class ContactoController {

    private static final Set<String> ESTADOS = Set.of("NUEVO", "EN_REVISION", "RESPONDIDO", "CERRADO");
    private final ContactoRepository repository;
    private final AutenticacionService autenticacionService;

    public ContactoController(ContactoRepository repository, AutenticacionService autenticacionService) {
        this.repository = repository;
        this.autenticacionService = autenticacionService;
    }

    @PostMapping
    public Contacto guardar(@RequestBody Contacto contacto) {
        if (contacto == null || vacio(contacto.getNombre()) || vacio(contacto.getCorreo())
                || vacio(contacto.getAsunto()) || vacio(contacto.getMensaje())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Completa todos los campos del formulario");
        }
        contacto.setId(null);
        contacto.setNombre(limpiar(contacto.getNombre(), 120));
        contacto.setCorreo(limpiar(contacto.getCorreo(), 160).toLowerCase());
        contacto.setAsunto(limpiar(contacto.getAsunto(), 180));
        contacto.setMensaje(limpiar(contacto.getMensaje(), 2000));
        contacto.setFecha(LocalDateTime.now());
        contacto.setEstado("NUEVO");
        return repository.save(contacto);
    }

    @GetMapping("/admin/todos")
    public List<Contacto> listar(
            @RequestHeader(AutenticacionService.HEADER_SESION) String token) {
        autenticacionService.requerirAdmin(token);
        return repository.findAllByOrderByFechaDesc();
    }

    @PatchMapping("/{id}/estado")
    public Contacto cambiarEstado(
            @RequestHeader(AutenticacionService.HEADER_SESION) String token,
            @PathVariable String id,
            @RequestParam String estado) {
        autenticacionService.requerirAdmin(token);
        Contacto contacto = obtener(id);
        String estadoNormalizado = estado == null ? "" : estado.trim().toUpperCase();
        if (!ESTADOS.contains(estadoNormalizado)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Estado no válido");
        }
        contacto.setEstado(estadoNormalizado);
        return repository.save(contacto);
    }

    @DeleteMapping("/{id}")
    public void eliminar(
            @RequestHeader(AutenticacionService.HEADER_SESION) String token,
            @PathVariable String id) {
        autenticacionService.requerirAdmin(token);
        repository.delete(obtener(id));
    }

    private Contacto obtener(String id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Mensaje no encontrado"));
    }

    private boolean vacio(String valor) {
        return valor == null || valor.isBlank();
    }

    private String limpiar(String valor, int maximo) {
        String limpio = valor.replaceAll("[\\p{Cntrl}&&[^\\r\\n\\t]]", "").trim();
        return limpio.length() <= maximo ? limpio : limpio.substring(0, maximo);
    }
}
