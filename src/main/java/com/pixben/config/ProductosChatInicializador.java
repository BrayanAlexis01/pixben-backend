package com.pixben.config;

import com.pixben.model.Producto;
import com.pixben.model.VarianteColor;
import com.pixben.repository.ProductoRepository;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Alta aditiva de los polos preparados en el chat para PixBen.
 *
 * No modifica ni elimina productos existentes. Cada producto tiene un SKU
 * reservado; si ya existe por SKU o por nombre se omite en siguientes deploys.
 */
@Component
@Order(50)
public class ProductosChatInicializador implements ApplicationRunner {

    private static final int STOCK_POR_COLOR = 30;
    private static final String TALLAS = "S,M,L,XL";

    private static final Map<String, String> HEX = crearPaleta();

    private final ProductoRepository productos;

    public ProductosChatInicializador(ProductoRepository productos) {
        this.productos = productos;
    }

    @Override
    public void run(ApplicationArguments args) {
        List<ProductoSeed> catalogo = List.of(
                seed("PX-CHAT-001", "Polo Human Being", List.of("Negro", "Gris", "Verde", "Burdeos", "Azul marino")),
                seed("PX-CHAT-002", "Polo Poseidon", List.of("Negro", "Gris", "Verde", "Burdeos", "Azul marino")),
                seed("PX-CHAT-003", "Polo Dignity", List.of("Blanco", "Gris", "Beige")),
                seed("PX-CHAT-004", "Polo Cultural", List.of("Negro", "Gris", "Verde", "Burdeos", "Azul marino")),
                seed("PX-CHAT-005", "Polo Peacekeeper", List.of("Negro", "Gris", "Verde", "Burdeos", "Azul marino")),
                seed("PX-CHAT-006", "Polo Attention Seeker", List.of("Negro", "Gris", "Verde", "Burdeos", "Azul marino")),
                seed("PX-CHAT-007", "Polo Show Me The Money", List.of("Negro", "Gris", "Beige", "Verde", "Burdeos", "Azul marino")),
                seed("PX-CHAT-008", "Polo Caution Money", List.of("Blanco", "Arena", "Beige")),
                seed("PX-CHAT-009", "Polo Break The Rules", List.of("Blanco", "Beige")),
                seed("PX-CHAT-010", "Polo Cowgirl Western", List.of("Blanco", "Beige")),
                seed("PX-CHAT-011", "Polo Neo Invaders", List.of("Negro", "Burdeos", "Verde", "Azul marino")),
                seed("PX-CHAT-012", "Polo Nightworld", List.of("Negro", "Burdeos", "Verde", "Azul marino")),
                seed("PX-CHAT-013", "Polo Shockwave", List.of("Negro", "Burdeos", "Verde", "Azul marino")),
                seed("PX-CHAT-014", "Polo Bloom Stronger", List.of("Negro", "Burdeos", "Verde", "Azul marino")),
                seed("PX-CHAT-015", "Polo Parental Advisory", List.of("Negro", "Burdeos", "Verde", "Azul marino")),
                seed("PX-CHAT-016", "Polo Landscape", List.of("Negro", "Burdeos", "Verde", "Azul marino")),
                seed("PX-CHAT-017", "Polo Romance", List.of("Negro", "Burdeos", "Verde", "Azul marino")),
                seed("PX-CHAT-018", "Polo Beauty of Art", List.of("Blanco", "Gris", "Beige")),
                seed("PX-CHAT-019", "Polo Dream Nation", List.of("Negro", "Burdeos", "Verde", "Azul marino")),
                seed("PX-CHAT-020", "Polo Intergalactic", List.of("Negro", "Burdeos", "Verde", "Azul marino")),
                seed("PX-CHAT-021", "Polo Japan City", List.of("Blanco")),
                seed("PX-CHAT-022", "Polo Samurai Way of Life", List.of("Blanco")),
                seed("PX-CHAT-023", "Polo Geisha Samurai", List.of("Blanco")),
                seed("PX-CHAT-024", "Polo Bushido Honor & Brave", List.of("Blanco")),
                seed("PX-CHAT-025", "Polo Silent Stride", List.of("Negro")),
                seed("PX-CHAT-026", "Polo Demon Slayer Inosuke y Zenitsu", List.of("Negro")),
                seed("PX-CHAT-027", "Polo Tanjiro Kamado", List.of("Negro")),
                seed("PX-CHAT-028", "Polo Nezuko Kamado Purple", List.of("Negro")),
                seed("PX-CHAT-029", "Polo Nezuko Kamado Dark", List.of("Negro")),
                seed("PX-CHAT-030", "Polo Space Cowboy", List.of("Blanco", "Gris", "Beige", "Verde", "Burdeos"))
        );

        for (ProductoSeed item : catalogo) {
            asegurarProducto(item);
        }
    }

    private void asegurarProducto(ProductoSeed item) {
        if (productos.findFirstBySkuIgnoreCase(item.sku()).isPresent()) {
            return;
        }
        if (productos.existsByNombreIgnoreCase(item.nombre())) {
            return;
        }

        Producto producto = new Producto();
        producto.setSku(item.sku());
        producto.setNombre(item.nombre());
        producto.setDescripcion("Polo gráfico PixBen. Selecciona tu talla y el color disponible antes de agregarlo al carrito.");
        producto.setCategoria("Polos");
        producto.setPrecio(BigDecimal.ZERO);
        producto.setTallasDisponibles(TALLAS);
        producto.setDestacado(false);
        producto.setPersonalizable(false);

        List<VarianteColor> variantes = new ArrayList<>();
        for (String nombreColor : item.colores()) {
            VarianteColor variante = new VarianteColor();
            variante.setClave(claveEstable(item.sku(), nombreColor));
            variante.setNombre(nombreColor);
            variante.setCodigoHex(HEX.getOrDefault(nombreColor, "#808080"));
            variante.setStock(STOCK_POR_COLOR);
            variante.setImagenIndice(variantes.size());
            variantes.add(variante);
        }
        producto.setColores(variantes);
        producto.setStock(STOCK_POR_COLOR * Math.max(1, variantes.size()));

        productos.save(producto);
    }

    private static ProductoSeed seed(String sku, String nombre, List<String> colores) {
        return new ProductoSeed(sku, nombre, colores);
    }

    private static String claveEstable(String sku, String color) {
        return UUID.nameUUIDFromBytes((sku + "|" + color).getBytes(StandardCharsets.UTF_8)).toString();
    }

    private static Map<String, String> crearPaleta() {
        Map<String, String> paleta = new LinkedHashMap<>();
        paleta.put("Negro", "#111111");
        paleta.put("Gris", "#A6A6A6");
        paleta.put("Verde", "#123F2D");
        paleta.put("Burdeos", "#6F1D2A");
        paleta.put("Azul marino", "#14213D");
        paleta.put("Blanco", "#FFFFFF");
        paleta.put("Beige", "#E8D6B3");
        paleta.put("Arena", "#D8BF93");
        return Map.copyOf(paleta);
    }

    private record ProductoSeed(String sku, String nombre, List<String> colores) {
    }
}
