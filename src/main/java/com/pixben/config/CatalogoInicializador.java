package com.pixben.config;

import com.pixben.model.Categoria;
import com.pixben.repository.CategoriaRepository;
import java.util.List;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class CatalogoInicializador implements ApplicationRunner {

    private final CategoriaRepository categorias;

    public CatalogoInicializador(CategoriaRepository categorias) {
        this.categorias = categorias;
    }

    @Override
    public void run(ApplicationArguments args) {
        List.of("Film DTF Premium", "Bolsos de Tocuyo").forEach(this::asegurarCategoria);
    }

    private void asegurarCategoria(String nombre) {
        if (categorias.existsByNombreIgnoreCase(nombre)) return;
        Categoria categoria = new Categoria();
        categoria.setNombre(nombre);
        categorias.save(categoria);
    }
}
