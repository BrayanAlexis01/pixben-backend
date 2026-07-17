package com.pixben.dto;

import java.math.BigDecimal;
import lombok.Data;

@Data
public class ActualizarPedidoPersonalizadoRequest {
    private BigDecimal precio;
    private String estado;
    private String mensajeAdmin;
}
