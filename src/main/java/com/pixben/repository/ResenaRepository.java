package com.pixben.repository;

import com.pixben.mongo.Resena;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface ResenaRepository extends MongoRepository<Resena, String> {

    List<Resena> findByProductoId(Long productoId);

    List<Resena> findByUsuarioId(Long usuarioId);
}
