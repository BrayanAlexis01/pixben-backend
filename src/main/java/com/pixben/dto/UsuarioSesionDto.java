package com.pixben.dto;

public record UsuarioSesionDto(
        Long id,
        String nombre,
        String apellido,
        String correo,
        String rol,
        String alias,
        String fotoPerfilUrl,
        String token
) {
}
