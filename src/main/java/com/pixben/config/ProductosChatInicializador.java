package com.pixben.config;

import com.pixben.model.Producto;
import com.pixben.repository.ProductoRepository;
import java.util.List;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Limpieza de los 30 productos temporales que se agregaron desde el chat.
 *
 * Solo elimina productos identificados con los SKU PX-CHAT-001 a PX-CHAT-030.
 * No toca ningún producto que existía antes ni productos creados manualmente
 * con otros SKU.
 */
@Component
@Order(50)
public class ProductosChatInicializador implements ApplicationRunner {

    private static final List<String> SKUS_TEMPORALES = List.of(
            "PX-CHAT-001", "PX-CHAT-002", "PX-CHAT-003", "PX-CHAT-004", "PX-CHAT-005",
            "PX-CHAT-006", "PX-CHAT-007", "PX-CHAT-008", "PX-CHAT-009", "PX-CHAT-010",
            "PX-CHAT-011", "PX-CHAT-012", "PX-CHAT-013", "PX-CHAT-014", "PX-CHAT-015",
            "PX-CHAT-016", "PX-CHAT-017", "PX-CHAT-018", "PX-CHAT-019", "PX-CHAT-020",
            "PX-CHAT-021", "PX-CHAT-022", "PX-CHAT-023", "PX-CHAT-024", "PX-CHAT-025",
            "PX-CHAT-026", "PX-CHAT-027", "PX-CHAT-028", "PX-CHAT-029", "PX-CHAT-030"
    );

    private final ProductoRepository productos;

    public ProductosChatInicializador(ProductoRepository productos) {
        this.productos = productos;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        for (String sku : SKUS_TEMPORALES) {
            productos.findFirstBySkuIgnoreCase(sku).ifPresent(productos::delete);
        }
        productos.flush();
    }
}
