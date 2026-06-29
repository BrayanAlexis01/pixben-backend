package com.pixben.repository;

import com.pixben.mongo.Carrito;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface CarritoRepository
        extends MongoRepository<Carrito, String> {

    List<Carrito> findByUsuario(String usuario);
}