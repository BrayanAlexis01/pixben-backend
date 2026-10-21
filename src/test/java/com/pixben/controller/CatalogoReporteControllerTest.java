package com.pixben.controller;

import com.pixben.service.AutenticacionService;
import com.pixben.service.CatalogoExportacionService;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Verifica la autorización antes de generar el archivo, sin acceso a Neon. */
class CatalogoReporteControllerTest {
    @Test
    void rechazaUsuarioNoAutorizadoAntesDeConsultarProductos() throws Exception {
        AutenticacionService sesiones = mock(AutenticacionService.class);
        CatalogoExportacionService exportador = mock(CatalogoExportacionService.class);
        when(sesiones.requerirAdmin("usuario"))
                .thenThrow(new ResponseStatusException(HttpStatus.FORBIDDEN));
        CatalogoReporteController controlador = new CatalogoReporteController(sesiones, exportador);
        assertThrows(ResponseStatusException.class, () -> controlador.descargarCatalogo("usuario"));
        verifyNoInteractions(exportador);
    }

    @Test
    void descargaXlsxParaAdministrador() throws Exception {
        AutenticacionService sesiones = mock(AutenticacionService.class);
        CatalogoExportacionService exportador = mock(CatalogoExportacionService.class);
        when(exportador.exportarXlsx()).thenReturn(new byte[]{1, 2, 3});
        var respuesta = new CatalogoReporteController(sesiones, exportador).descargarCatalogo("admin");
        assertEquals(200, respuesta.getStatusCode().value());
        assertArrayEquals(new byte[]{1, 2, 3}, respuesta.getBody());
        assertTrue(respuesta.getHeaders().getFirst("Content-Disposition").contains("pixben-catalogo.xlsx"));
        verify(sesiones).requerirAdmin("admin");
        verify(exportador).exportarXlsx();
    }
}
