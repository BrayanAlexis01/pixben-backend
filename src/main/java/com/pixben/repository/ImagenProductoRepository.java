package com.pixben.repository;

import com.pixben.mongo.ImagenProducto;
import java.util.List;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface ImagenProductoRepository
        extends MongoRepository<ImagenProducto, String> {

    List<ImagenProducto> findAllByProductoId(Long productoId);

}
