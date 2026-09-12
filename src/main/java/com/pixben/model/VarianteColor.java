package com.pixben.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.Data;

/**
 * Variante de color disponible para un producto.
 * El índice de imagen sigue el orden de la galería (0 = portada).
 */
@Embeddable
@Data
public class VarianteColor {

    /** Identificador estable de la variante para asociar su galería sin depender del nombre o del orden. */
    @Column(name = "clave_color", length = 36)
    private String clave;

    @Column(name = "nombre_color", length = 60)
    private String nombre;

    @Column(name = "codigo_hex", length = 7)
    private String codigoHex;

    @Column(name = "stock_color")
    private Integer stock;

    @Column(name = "imagen_indice")
    private Integer imagenIndice;
}
