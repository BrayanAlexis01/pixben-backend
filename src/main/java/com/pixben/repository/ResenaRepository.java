package com.pixben.repository;

import com.pixben.mongo.Resena;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface ResenaRepository extends MongoRepository<Resena, String> {
    List<Resena> findByProductoId(Long productoId);
    Page<Resena> findByProductoIdOrderByFechaDesc(Long productoId, Pageable pageable);
    List<Resena> findByUsuarioId(Long usuarioId);
}
