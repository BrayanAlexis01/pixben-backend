package com.pixben.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

@Entity
@Table(name = "usuarios")
@Data
public class Usuario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(length = 80)
    private String nombre;

    @Column(length = 100)
    private String apellido;

    @Column(length = 180)
    private String correo;

    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    @Column(length = 120)
    private String password;

    @Column(length = 30)
    private String rol;

    @Column(length = 60)
    private String alias;

    @Column(name = "foto_perfil_url", length = 1000)
    private String fotoPerfilUrl;
}
