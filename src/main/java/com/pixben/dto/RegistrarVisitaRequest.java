package com.pixben.dto;

import lombok.Data;

@Data
public class RegistrarVisitaRequest {
    private String visitanteId;
    private Long usuarioId;
    private String correo;
    private String ruta;
}
