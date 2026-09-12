package com.pixben.dto;

import lombok.Data;

@Data
public class PedidoPersonalizadoDatos {
    private Long usuarioId;
    private String usuario;
    private String correo;
    private Long productoId;
    private String productoNombre;
    private String categoria;
    private String color;
    private String talla;
    private Integer cantidad;
    private String notas;
}
