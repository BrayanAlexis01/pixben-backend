package com.pixben.mongo;

import java.time.LocalDateTime;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@Document(collection = "favoritos")
@CompoundIndex(name = "favorito_usuario_producto", def = "{\'usuarioId\': 1, \'productoId\': 1}")
public class Favorito {
    @Id
    private String id;
    private Long productoId;
    @Indexed
    private Long usuarioId;
    private String correo;
    private String usuario;
    private String talla;
    private String color;
    private LocalDateTime fecha;
}
