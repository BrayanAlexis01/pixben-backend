package com.pixben.repository;

import com.pixben.model.Producto;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class StockConcurrencyTest {
    @Autowired private ProductoRepository productos;
    @Autowired private TransactionTemplate transacciones;

    @Test
    void dosComprasNoPuedenConsumirLaMismaUnidad() throws Exception {
        Long id = transacciones.execute(estado -> {
            Producto producto = new Producto();
            producto.setNombre("Prueba de inventario");
            producto.setCategoria("Polos");
            producto.setDescripcion("Producto de prueba, no real");
            producto.setPrecio(new BigDecimal("35.00"));
            producto.setStock(1);
            return productos.saveAndFlush(producto).getId();
        });

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch preparados = new CountDownLatch(2);
        CountDownLatch inicio = new CountDownLatch(1);
        Callable<Boolean> compra = () -> {
            preparados.countDown();
            if (!inicio.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("Sin inicio");
            return transacciones.execute(estado -> {
                Producto producto = productos.findByIdForUpdate(id).orElseThrow();
                int stock = producto.getStock() == null ? 0 : producto.getStock();
                if (stock == 0) return false;
                producto.setStock(stock - 1);
                productos.saveAndFlush(producto);
                return true;
            });
        };
        try {
            List<Future<Boolean>> compras = new ArrayList<>();
            compras.add(executor.submit(compra));
            compras.add(executor.submit(compra));
            assertTrue(preparados.await(5, TimeUnit.SECONDS));
            inicio.countDown();
            int confirmadas = 0;
            for (Future<Boolean> futuro : compras) {
                if (Boolean.TRUE.equals(futuro.get(15, TimeUnit.SECONDS))) confirmadas++;
            }
            assertEquals(1, confirmadas, "Solo una compra debe descontar la ultima unidad");
            assertEquals(0, transacciones.execute(estado ->
                productos.findById(id).orElseThrow().getStock()));
        } finally {
            inicio.countDown();
            executor.shutdownNow();
        }
    }
}
