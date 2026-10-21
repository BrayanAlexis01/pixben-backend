package com.pixben.service;

import com.pixben.model.Producto;
import com.pixben.repository.ProductoRepository;
import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.util.List;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * APF3: prueba del comportamiento esperado antes de implementar la exportación (TDD).
 * No modifica los productos reales ni requiere acceso a la base de producción.
 */
class CatalogoExportacionServiceTest {

    @Test
    void exportaCatalogoExcelConDatosCorrectos() throws Exception {
        ProductoRepository repositorio = mock(ProductoRepository.class);
        Producto polo = new Producto();
        polo.setId(1L);
        polo.setNombre("  Polo   Oversize  ");
        polo.setCategoria("Polos");
        polo.setSku("PIX-001");
        polo.setPrecio(new BigDecimal("59.90"));
        polo.setStock(30);
        polo.setDestacado(true);
        polo.setPersonalizable(false);
        when(repositorio.findAll(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(polo)));

        CatalogoExportacionService servicio = new CatalogoExportacionService(repositorio);
        byte[] archivo = servicio.exportarXlsx();

        assertTrue(archivo.length > 100);
        try (var libro = WorkbookFactory.create(new ByteArrayInputStream(archivo))) {
            var hoja = libro.getSheet("Catalogo PixBen");
            assertNotNull(hoja);
            assertEquals("Producto", hoja.getRow(0).getCell(1).getStringCellValue());
            assertEquals("Polo Oversize", hoja.getRow(1).getCell(1).getStringCellValue());
            assertEquals(59.90, hoja.getRow(1).getCell(4).getNumericCellValue(), 0.001);
            assertEquals(30, hoja.getRow(1).getCell(5).getNumericCellValue());
        }
        verify(repositorio).findAll(any(Pageable.class));
    }

    @Test
    void neutralizaContenidoQuePareceFormulaDeExcel() throws Exception {
        ProductoRepository repositorio = mock(ProductoRepository.class);
        Producto producto = new Producto();
        producto.setId(2L);
        producto.setNombre("=HYPERLINK(\"https://ejemplo.test\",\"peligro\")");
        producto.setCategoria("Prueba");
        when(repositorio.findAll(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(producto)));

        byte[] archivo = new CatalogoExportacionService(repositorio).exportarXlsx();
        try (var libro = WorkbookFactory.create(new ByteArrayInputStream(archivo))) {
            assertTrue(libro.getSheetAt(0).getRow(1).getCell(1).getStringCellValue().startsWith("'="));
        }
    }
}
