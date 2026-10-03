package com.pixben.mongo;

import java.time.LocalDateTime;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@Document(collection = "reclamos")
public class Reclamo {
    @Id
    private String id;
    private String tipo;
    private String nombre;
    private String documento;
    private String correo;
    private String telefono;
    private String pedidoReferencia;
    private String detalle;
    private String pedidoConsumidor;
    private String estado = "RECIBIDO";
    private LocalDateTime fecha;
}
