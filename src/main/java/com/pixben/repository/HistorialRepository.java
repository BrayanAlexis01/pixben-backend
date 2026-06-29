package com.pixben.repository;

import com.pixben.mongo.Historial;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface HistorialRepository
        extends MongoRepository<Historial, String> {

    List<Historial> findByUsuario(String usuario);

}