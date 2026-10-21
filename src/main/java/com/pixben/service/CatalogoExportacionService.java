package com.pixben.service;

import com.google.common.collect.ImmutableList;
import com.pixben.model.Producto;
import com.pixben.repository.ProductoRepository;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import org.apache.commons.lang3.StringUtils;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

/**
 * Genera un respaldo tabular del catálogo para administradores.
 *
 * <p>Principio SRP (SOLID): solamente construye el XLSX, sin gestionar HTTP
 * ni autenticar usuarios. Acceso a datos mediante la interfaz Repository/DAO.</p>
 */
@Service
public class CatalogoExportacionService {
    private static final Logger LOGGER = LoggerFactory.getLogger(CatalogoExportacionService.class);
    private static final int LOTE = 200;
    private static final int MAX_FILAS = 10000;
    private static final ImmutableList<String> COLUMNAS = ImmutableList.of(
            "ID", "Producto", "SKU", "Categoria", "Precio (S/)", "Stock",
            "Destacado", "Personalizable");
    private final ProductoRepository productoRepository;

    /** Inyección por constructor: depende de una abstracción y es fácil de probar con Mockito. */
    public CatalogoExportacionService(ProductoRepository productoRepository) {
        this.productoRepository = productoRepository;
    }

    /**
     * Exporta como máximo 10 000 productos, sin actualizar ninguna entidad.
     * Las celdas de texto nunca se interpretan como fórmulas de Excel.
     *
     * @return archivo OOXML .xlsx
     * @throws IOException si el libro no puede serializarse
     */
    public byte[] exportarXlsx() throws IOException {
        try (XSSFWorkbook libro = new XSSFWorkbook();
             ByteArrayOutputStream salida = new ByteArrayOutputStream()) {
            Sheet hoja = libro.createSheet("Catalogo PixBen");
            Row cabecera = hoja.createRow(0);
            for (int i = 0; i < COLUMNAS.size(); i++) {
                cabecera.createCell(i).setCellValue(COLUMNAS.get(i));
            }
            int fila = 1;
            int pagina = 0;
            Page<Producto> resultados;
            do {
                resultados = productoRepository.findAll(PageRequest.of(pagina++, LOTE));
                for (Producto p : resultados.getContent()) {
                    if (fila > MAX_FILAS) break;
                    Row registro = hoja.createRow(fila++);
                    texto(registro, 0, p.getId() == null ? "" : p.getId().toString());
                    texto(registro, 1, p.getNombre());
                    texto(registro, 2, p.getSku());
                    texto(registro, 3, p.getCategoria());
                    numero(registro, 4, p.getPrecio());
                    numero(registro, 5, p.getStock());
                    texto(registro, 6, Boolean.TRUE.equals(p.getDestacado()) ? "Si" : "No");
                    texto(registro, 7, Boolean.TRUE.equals(p.getPersonalizable()) ? "Si" : "No");
                }
            } while (resultados.hasNext() && fila <= MAX_FILAS);
            for (int i = 0; i < COLUMNAS.size(); i++) hoja.setColumnWidth(i, 20 * 256);
            libro.write(salida);
            LOGGER.info("Catalogo exportado a XLSX: {} registros", fila - 1);
            return salida.toByteArray();
        }
    }

    private static void texto(Row fila, int columna, String valor) {
        String limpio = StringUtils.normalizeSpace(StringUtils.defaultString(valor));
        // La comilla inicial impide interpretar entradas como fórmulas.
        if (!limpio.isEmpty() && "=+-@".indexOf(limpio.charAt(0)) >= 0) limpio = "'" + limpio;
        Cell celda = fila.createCell(columna);
        celda.setCellValue(StringUtils.abbreviate(limpio, 32000));
    }

    private static void numero(Row fila, int columna, Number valor) {
        if (valor != null) fila.createCell(columna).setCellValue(valor.doubleValue());
    }
}
