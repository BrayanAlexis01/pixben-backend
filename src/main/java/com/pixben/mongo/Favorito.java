package com.pixben.mongo;

import java.time.LocalDateTime;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@Document(collection = "favoritos")
public class Favorito {
    @Id
    private String id;
    private Long productoId;
    @Indexed
    private Long usuarioId;
    private String correo;
    private String usuario;
    private LocalDateTime fecha;
}
