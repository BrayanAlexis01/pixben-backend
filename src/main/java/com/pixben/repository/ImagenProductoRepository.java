package com.pixben.repository;

import com.pixben.mongo.ImagenProducto;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface ImagenProductoRepository
        extends MongoRepository<ImagenProducto, String> {

    ImagenProducto findByProductoId(Long productoId);

}