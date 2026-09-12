package com.pixben.model;

import jakarta.persistence.*;
import lombok.Data;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

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

    /** Código interno opcional para ubicar productos y prevenir duplicados accidentales. */
    @Column(length = 80)
    private String sku;

    /** Tallas habilitadas por el administrador, separadas por comas. */
    @Column(name = "tallas_disponibles", length = 80)
    private String tallasDisponibles;

    /** Variantes de color y stock configuradas por el administrador. Cada variante puede tener su propia galería en MongoDB. */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "producto_colores", joinColumns = @JoinColumn(name = "producto_id"))
    @OrderColumn(name = "orden")
    private List<VarianteColor> colores = new ArrayList<>();

    private Boolean destacado = false;

    /** Indica si el producto puede utilizarse como base en el editor de personalización. */
    private Boolean personalizable = false;
}
