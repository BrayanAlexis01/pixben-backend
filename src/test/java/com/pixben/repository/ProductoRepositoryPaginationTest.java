package com.pixben.repository;

import com.pixben.model.Producto;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
class ProductoRepositoryPaginationTest {
    @Autowired ProductoRepository repository;

    @Test
    void filtraOrdenaYPaginaSinModificarProductos() {
        crear("Polo A", "Polos", "35", true);
        crear("Polo B", "Polos", "40", false);
        crear("Taza", "Tazas", "12", false);
        Specification<Producto> polos = (root, query, cb) ->
                cb.equal(cb.lower(root.get("categoria")), "polos");
        Page<Producto> primera = repository.findAll(polos,
                PageRequest.of(0, 1, Sort.by(Sort.Order.desc("destacado"), Sort.Order.asc("nombre"))));
        Page<Producto> segunda = repository.findAll(polos,
                PageRequest.of(1, 1, Sort.by(Sort.Order.desc("destacado"), Sort.Order.asc("nombre"))));
        assertEquals(2, primera.getTotalElements());
        assertEquals("Polo A", primera.getContent().get(0).getNombre());
        assertEquals("Polo B", segunda.getContent().get(0).getNombre());
        assertTrue(repository.listarCategorias().contains("Polos"));
        assertEquals(0, new BigDecimal("40").compareTo(repository.precioMaximo()));
        assertEquals(1, repository.findByDestacadoTrueOrderByIdDesc(PageRequest.of(0, 12)).size());
    }

    private void crear(String nombre, String categoria, String precio, boolean destacado) {
        Producto p = new Producto();
        p.setNombre(nombre);
        p.setCategoria(categoria);
        p.setDescripcion("Producto de prueba");
        p.setPrecio(new BigDecimal(precio));
        p.setStock(20);
        p.setDestacado(destacado);
        repository.saveAndFlush(p);
    }
}
