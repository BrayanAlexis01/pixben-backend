package com.pixben.model;

import jakarta.persistence.*;
import lombok.Data;
import java.math.BigDecimal;

@Entity
@Table(name = "productos")
@Data
public class Producto {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String nombre;
    private String descripcion;
    private BigDecimal precio;
    private Integer stock;
    private String imagen;
    private String categoria;

    /** Tallas habilitadas por el administrador, separadas por comas. */
    @Column(name = "tallas_disponibles", length = 80)
    private String tallasDisponibles;
    private Boolean destacado = false;

    /** Indica si el producto puede utilizarse como base en el editor de personalización. */
    private Boolean personalizable = false;
}
