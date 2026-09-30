package com.pixben.mongo;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@Document(collection = "pedidos_personalizados")
public class PedidoPersonalizado {
    @Id
    private String id;
    @Indexed
    private Long usuarioId;
    @Indexed
    private String usuario;
    private String correo;
    private Long productoId;
    private String productoNombre;
    private String categoria;
    private String color;
    private String talla;
    private Integer cantidad;
    private String notas;
    private String tipoServicio;
    private BigDecimal tarifaDiseno;
    private String metodoPagoDiseno;
    private String referenciaPagoDiseno;
    private String estadoPagoDiseno;
    private Integer revisionesIncluidas;
    private String imagenFrente;
    private String imagenEspalda;
    private BigDecimal precio;
    private String estado;
    private String mensajeAdmin;
    private LocalDateTime fechaCreacion;
    private LocalDateTime fechaActualizacion;
}
