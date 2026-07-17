package com.pixben.mongo;

import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@Document(collection = "visitas")
public class Visita {
    @Id
    private String id;
    @Indexed
    private String hashVisitante;
    private Long usuarioId;
    private String correo;
    private String ruta;
    private boolean autenticada;
    private LocalDate dia;
    private LocalDateTime fecha;
}
