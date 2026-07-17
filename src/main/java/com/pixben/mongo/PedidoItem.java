package com.pixben.mongo;

import lombok.Data;

@Data
public class PedidoItem {
    private Long productoId;
    private String nombre;
    private Integer cantidad;
    private String talla;
    private Double precioUnitario;
    private Double subtotal;
    private Boolean personalizado = false;
    private String pedidoPersonalizadoId;
    private String imagen;
}
