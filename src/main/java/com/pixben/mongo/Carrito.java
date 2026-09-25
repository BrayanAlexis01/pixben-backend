package com.pixben.mongo;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@Document(collection = "carrito")
@CompoundIndex(name = "carrito_usuario_producto_variante", def = "{\'usuarioId\': 1, \'productoId\': 1, \'talla\': 1, \'color\': 1, \'personalizado\': 1}")
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
    private String color;
    private Boolean personalizado = false;
    private String pedidoPersonalizadoId;
}
