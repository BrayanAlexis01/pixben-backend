package com.pixben.mongo;

import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@Document(collection = "visitas")
@CompoundIndex(name = "visita_hash_ruta_dia", def = "{\'hashVisitante\': 1, \'ruta\': 1, \'dia\': 1}")
@CompoundIndex(name = "visita_dia_fecha", def = "{\'dia\': 1, \'fecha\': -1}")
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
