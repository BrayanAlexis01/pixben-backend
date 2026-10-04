package com.pixben.mongo;

import java.util.ArrayList;
import java.util.List;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@Document(collection = "pedidos")
public class Pedido {
    @Id
    private String id;

    @Indexed
    private Long usuarioId;
    private String correo;
    private String usuario;
    private Boolean invitado = false;

    @Indexed
    private String codigoSeguimiento;

    private String nombreCliente;
    private String telefono;

    private List<PedidoItem> items = new ArrayList<>();
    private Double subtotal;
    private Double costoEnvio;
    private Double total;

    private String metodoPago;
    private String referenciaPago;
    private String estadoPago;

    /**
     * false/null: el inventario todavía no fue descontado.
     * true: el pago fue verificado y el inventario ya fue aplicado.
     */
    private Boolean stockAplicado = false;

    private String metodoEnvio;
    private String destinoEnvio;
    private String referenciaEnvio;
    private String estadoEnvio;

    private String estado;
    private String fecha;
}
