package com.pixben.dto;

import lombok.Data;

@Data
public class ActualizarGestionPedidoRequest {
    private String estado;
    private String estadoPago;
    private String estadoEnvio;
    private Double costoEnvio;
}
