package com.pixben.repository;

import com.pixben.mongo.Contacto;
import java.util.List;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface ContactoRepository extends MongoRepository<Contacto, String> {
    List<Contacto> findAllByOrderByFechaDesc();
}
