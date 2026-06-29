package com.pixben.mongo;

import java.time.LocalDateTime;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@Document(collection = "resenas")
public class Resena {

    @Id
    private String id;

    private Long productoId;

    private Long usuarioId;

    private String usuario;

    private String comentario;

    private Integer calificacion;

    private LocalDateTime fecha;

    private boolean editada;
}
