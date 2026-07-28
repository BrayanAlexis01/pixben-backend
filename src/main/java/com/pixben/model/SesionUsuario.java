package com.pixben.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.Data;

@Entity
@Table(name = "sesiones_usuario", indexes = {
    @Index(name = "idx_sesion_usuario", columnList = "usuario_id"),
    @Index(name = "idx_sesion_expira", columnList = "expira_en")
})
@Data
public class SesionUsuario {

    @Id
    @Column(length = 96)
    private String token;

    @Column(name = "usuario_id", nullable = false)
    private Long usuarioId;

    @Column(name = "creada_en", nullable = false)
    private LocalDateTime creadaEn;

    @Column(name = "expira_en", nullable = false)
    private LocalDateTime expiraEn;
}
