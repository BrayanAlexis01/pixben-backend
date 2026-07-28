package com.pixben.mongo;

import java.time.LocalDateTime;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@Document(collection = "contactos")
public class Contacto {

    @Id
    private String id;

    private String nombre;
    @Indexed
    private String correo;
    private String asunto;
    private String mensaje;
    private LocalDateTime fecha;
    private String estado;
}
