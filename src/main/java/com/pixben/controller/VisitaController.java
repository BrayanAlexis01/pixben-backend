package com.pixben.controller;

import com.pixben.dto.RegistrarVisitaRequest;
import com.pixben.model.Usuario;
import com.pixben.mongo.Visita;
import com.pixben.repository.VisitaRepository;
import com.pixben.service.AutenticacionService;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/visitas")
@CrossOrigin(origins = "*", allowedHeaders = "*")
public class VisitaController {

    private final VisitaRepository repository;
    private final AutenticacionService autenticacionService;

    public VisitaController(VisitaRepository repository, AutenticacionService autenticacionService) {
        this.repository = repository;
        this.autenticacionService = autenticacionService;
    }

    @PostMapping
    public Map<String, String> registrar(
            @RequestHeader(value = AutenticacionService.HEADER_SESION, required = false) String token,
            @RequestBody RegistrarVisitaRequest datos) {
        Usuario usuario = autenticacionService.usuarioOpcional(token);
        String base = usuario != null
                ? "usuario:" + usuario.getId()
                : "anonimo:" + valorSeguro(datos.getVisitanteId(), "sin-id");
        String hash = sha256(base);
        String ruta = limpiarRuta(datos.getRuta());
        LocalDate hoy = LocalDate.now();

        if (repository.findFirstByHashVisitanteAndRutaAndDia(hash, ruta, hoy).isEmpty()) {
            Visita visita = new Visita();
            visita.setHashVisitante(hash);
            visita.setUsuarioId(usuario == null ? null : usuario.getId());
            visita.setCorreo(usuario == null ? null : limpiarCorreo(usuario.getCorreo()));
            visita.setRuta(ruta);
            visita.setAutenticada(usuario != null);
            visita.setDia(hoy);
            visita.setFecha(LocalDateTime.now());
            repository.save(visita);
        }

        repository.deleteByFechaBefore(LocalDateTime.now().minusMonths(6));
        return Map.of("estado", "registrada");
    }

    @GetMapping("/admin/resumen")
    public Map<String, Object> resumen(
            @RequestHeader(AutenticacionService.HEADER_SESION) String token) {
        autenticacionService.requerirAdmin(token);
        List<Visita> visitas = repository.findAll();
        LocalDate hoy = LocalDate.now();
        long visitasHoy = visitas.stream().filter(v -> hoy.equals(v.getDia())).count();
        long anonimos = visitas.stream().filter(v -> !v.isAutenticada()).map(Visita::getHashVisitante).distinct().count();
        long autenticados = visitas.stream().filter(Visita::isAutenticada).map(Visita::getUsuarioId).filter(x -> x != null).distinct().count();

        Map<String, Long> porDia = visitas.stream()
                .filter(v -> v.getDia() != null && !v.getDia().isBefore(hoy.minusDays(6)))
                .collect(Collectors.groupingBy(v -> v.getDia().toString(), LinkedHashMap::new, Collectors.counting()));

        List<Map<String, Object>> recientes = repository.findTop50ByAutenticadaTrueOrderByFechaDesc().stream()
                .limit(12)
                .map(v -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("correo", v.getCorreo());
                    item.put("ruta", v.getRuta());
                    item.put("fecha", v.getFecha());
                    return item;
                }).toList();

        return Map.of(
                "vistasTotales", visitas.size(),
                "vistasHoy", visitasHoy,
                "visitantesAnonimos", anonimos,
                "usuariosAutenticados", autenticados,
                "visitasPorDia", porDia,
                "recientesAutenticadas", recientes,
                "privacidad", "No se almacena dirección IP ni identidad de visitantes anónimos"
        );
    }

    @DeleteMapping("/admin/todas")
    public Map<String, Object> borrarTodas(
            @RequestHeader(AutenticacionService.HEADER_SESION) String token) {
        autenticacionService.requerirAdmin(token);
        long eliminadas = repository.count();
        repository.deleteAll();
        return Map.of("eliminadas", eliminadas, "estado", "analitica_limpiada");
    }

    @DeleteMapping("/admin/anteriores")
    public Map<String, Object> borrarAnteriores(
            @RequestHeader(AutenticacionService.HEADER_SESION) String token,
            @RequestParam(defaultValue = "30") int dias) {
        autenticacionService.requerirAdmin(token);
        int diasSeguros = Math.max(1, Math.min(dias, 3650));
        LocalDateTime limite = LocalDateTime.now().minusDays(diasSeguros);
        long antes = repository.count();
        repository.deleteByFechaBefore(limite);
        long eliminadas = antes - repository.count();
        return Map.of("eliminadas", eliminadas, "dias", diasSeguros, "estado", "analitica_reducida");
    }

    private String limpiarRuta(String ruta) {
        String valor = valorSeguro(ruta, "/").replaceAll("[\\p{Cntrl}]", "").trim();
        return valor.length() <= 200 ? valor : valor.substring(0, 200);
    }

    private String limpiarCorreo(String correo) {
        if (correo == null) return null;
        String valor = correo.trim().toLowerCase();
        return valor.length() <= 180 ? valor : valor.substring(0, 180);
    }

    private String valorSeguro(String valor, String respaldo) {
        return valor == null || valor.isBlank() ? respaldo : valor;
    }

    private String sha256(String valor) {
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256").digest(valor.getBytes(StandardCharsets.UTF_8));
            StringBuilder salida = new StringBuilder();
            for (byte b : bytes) salida.append(String.format("%02x", b));
            return salida.toString();
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 no disponible", ex);
        }
    }
}
