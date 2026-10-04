package com.pixben.repository;

import com.pixben.mongo.Reclamo;
import java.util.List;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface ReclamoRepository extends MongoRepository<Reclamo, String> {
    List<Reclamo> findAllByOrderByFechaDesc();
}
