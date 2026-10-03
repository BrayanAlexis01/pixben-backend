package com.pixben.controller;

import com.pixben.mongo.Reclamo;
import com.pixben.repository.ReclamoRepository;
import com.pixben.service.AutenticacionService;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/reclamos")
public class ReclamoController {
    private static final Set<String> TIPOS = Set.of("RECLAMO", "QUEJA");
    private static final Set<String> ESTADOS = Set.of("RECIBIDO", "EN_PROCESO", "RESPONDIDO", "CERRADO");
    private static final Pattern CORREO = Pattern.compile("^[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}$", Pattern.CASE_INSENSITIVE);

    private final ReclamoRepository repository;
    private final AutenticacionService autenticacionService;

    public ReclamoController(ReclamoRepository repository, AutenticacionService autenticacionService) {
        this.repository = repository;
        this.autenticacionService = autenticacionService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String,Object> crear(@RequestBody Reclamo entrada) {
        if (entrada == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Completa el formulario");
        String tipo = limpiar(entrada.getTipo(), 20).toUpperCase(Locale.ROOT);
        String correo = limpiar(entrada.getCorreo(), 160).toLowerCase(Locale.ROOT);
        if (!TIPOS.contains(tipo)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Tipo inválido");
        if (!CORREO.matcher(correo).matches()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Correo inválido");

        Reclamo r = new Reclamo();
        r.setTipo(tipo);
        r.setNombre(obligatorio(entrada.getNombre(), 120, "Escribe tu nombre"));
        r.setDocumento(obligatorio(entrada.getDocumento(), 20, "Escribe tu documento"));
        r.setCorreo(correo);
        r.setTelefono(obligatorio(entrada.getTelefono(), 30, "Escribe tu teléfono"));
        r.setPedidoReferencia(limpiar(entrada.getPedidoReferencia(), 80));
        r.setDetalle(obligatorio(entrada.getDetalle(), 3000, "Describe tu reclamo o queja"));
        r.setPedidoConsumidor(obligatorio(entrada.getPedidoConsumidor(), 1500, "Indica la solución que solicitas"));
        r.setEstado("RECIBIDO");
        r.setFecha(LocalDateTime.now());
        r = repository.save(r);
        return Map.of("id", r.getId(), "estado", r.getEstado(), "mensaje", "Registro recibido correctamente");
    }

    @GetMapping("/admin/todos")
    public List<Reclamo> listar(@RequestHeader(AutenticacionService.HEADER_SESION) String token) {
        autenticacionService.requerirAdmin(token);
        return repository.findAllByOrderByFechaDesc();
    }

    @PatchMapping("/{id}/estado")
    public Reclamo cambiarEstado(@RequestHeader(AutenticacionService.HEADER_SESION) String token,
                                 @PathVariable String id,
                                 @RequestParam String estado) {
        autenticacionService.requerirAdmin(token);
        Reclamo r = repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Registro no encontrado"));
        String normalizado = limpiar(estado, 30).toUpperCase(Locale.ROOT);
        if (!ESTADOS.contains(normalizado)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Estado inválido");
        r.setEstado(normalizado);
        return repository.save(r);
    }

    private String obligatorio(String value, int max, String message) {
        String clean = limpiar(value, max);
        if (clean.isBlank()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
        return clean;
    }

    private String limpiar(String value, int max) {
        if (value == null) return "";
        String clean = value.replaceAll("[\\p{Cntrl}&&[^\\r\\n\\t]]", "").trim();
        return clean.length() <= max ? clean : clean.substring(0, max);
    }
}
