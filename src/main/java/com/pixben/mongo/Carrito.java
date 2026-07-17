package com.pixben.mongo;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@Document(collection = "carrito")
public class Carrito {
    @Id
    private String id;
    private Long productoId;
    @Indexed
    private Long usuarioId;
    private String correo;
    private String usuario;
    private Integer cantidad;
    private String talla;
    private Boolean personalizado = false;
    private String pedidoPersonalizadoId;
}
