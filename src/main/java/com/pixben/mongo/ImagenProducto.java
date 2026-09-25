package com.pixben.mongo;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

@Data
@Document(collection = "imagenes_producto")
public class ImagenProducto {

    @Id
    private String id;

    @Indexed(unique = true)
    private Long productoId;

    /** Galería general usada por productos sin variantes y como compatibilidad con el catálogo anterior. */
    private List<String> imagenes;

    /** Galerías independientes por clave estable de VarianteColor. MongoDB permite agregar este campo sin migrar documentos existentes. */
    private Map<String, List<String>> imagenesPorVariante = new LinkedHashMap<>();

}