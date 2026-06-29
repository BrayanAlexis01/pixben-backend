package com.pixben.repository;

import com.pixben.mongo.Favorito;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface FavoritoRepository
        extends MongoRepository<Favorito, String> {

    List<Favorito> findByUsuario(String usuario);

}