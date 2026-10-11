package com.pixben.repository;

import com.pixben.model.Producto;
import com.pixben.controller.ProductoController;
import com.pixben.model.VarianteColor;
import java.util.List;
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

    @Test
    void endpointPaginaAplicaFiltrosRealesYSigueRespetandoColores() {
        Producto uno = new Producto();
        uno.setNombre("Polo Negro");
        uno.setCategoria("Polos");
        uno.setDescripcion("Polo de prueba");
        uno.setPrecio(new BigDecimal("39.00"));
        uno.setStock(2);
        uno.setTallasDisponibles("S,M,L");
        VarianteColor negro = new VarianteColor();
        negro.setNombre("Negro");
        negro.setCodigoHex("#000000");
        negro.setStock(2);
        uno.setColores(List.of(negro));
        repository.saveAndFlush(uno);
        ProductoController controlador = new ProductoController(repository, null, null, null);

        var resultado = controlador.listarPagina(0, 16, "Polo", "Polos", "Negro",
                "M", BigDecimal.ZERO, new BigDecimal("40"), true, "popular");
        assertEquals(1, resultado.getTotalElements());
        assertEquals("Polo Negro", resultado.getContent().get(0).getNombre());

        var vacio = controlador.listarPagina(0, 16, "Polo", "Polos", "Azul",
                "M", BigDecimal.ZERO, new BigDecimal("40"), true, "popular");
        assertEquals(0, vacio.getTotalElements());
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
