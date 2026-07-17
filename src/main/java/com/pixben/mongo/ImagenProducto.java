package com.pixben.mongo;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.List;

@Data
@Document(collection = "imagenes_producto")
public class ImagenProducto {

    @Id
    private String id;

    private Long productoId;

    private List<String> imagenes;

}