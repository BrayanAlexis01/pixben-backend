package com.pixben.controller;

import com.pixben.service.AutenticacionService;
import com.pixben.service.CatalogoExportacionService;
import java.io.IOException;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Controlador REST del reporte de catálogo. Requiere rol administrador. */
@RestController
@RequestMapping("/admin/reportes")
public class CatalogoReporteController {
    private static final MediaType XLSX = MediaType.parseMediaType(
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
    private final AutenticacionService autenticacion;
    private final CatalogoExportacionService exportador;

    public CatalogoReporteController(AutenticacionService autenticacion,
                                    CatalogoExportacionService exportador) {
        this.autenticacion = autenticacion;
        this.exportador = exportador;
    }

    /** Descarga de solo lectura: no altera productos, stock ni pedidos. */
    @GetMapping(value = "/catalogo.xlsx", produces =
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
    public ResponseEntity<byte[]> descargarCatalogo(
            @RequestHeader(AutenticacionService.HEADER_SESION) String token)
            throws IOException {
        autenticacion.requerirAdmin(token);
        byte[] bytes = exportador.exportarXlsx();
        return ResponseEntity.ok()
                .contentType(XLSX)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename("pixben-catalogo.xlsx").build().toString())
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(bytes);
    }
}
