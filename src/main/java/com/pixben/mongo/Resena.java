package com.pixben.mongo;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Transient;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@Document(collection = "resenas")
public class Resena {
    @Id
    private String id;
    private Long productoId;
    private Long usuarioId;
    private String usuario;
    private String aliasUsuario;
    private String fotoPerfilUrl;
    private String comentario;
    private Integer calificacion;
    private LocalDateTime fecha;
    private boolean editada;
    @JsonIgnore
    private Set<Long> usuariosMeGusta = new HashSet<>();
    @Transient
    private boolean meGustaUsuarioActual;

    public int getCantidadMeGusta() {
        return usuariosMeGusta == null ? 0 : usuariosMeGusta.size();
    }
}
