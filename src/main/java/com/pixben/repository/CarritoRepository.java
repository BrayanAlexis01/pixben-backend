package com.pixben.repository;

import com.pixben.mongo.Carrito;
import java.util.List;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface CarritoRepository extends MongoRepository<Carrito, String> {
    List<Carrito> findByUsuario(String usuario);
    List<Carrito> findByUsuarioId(Long usuarioId);
    void deleteByUsuarioId(Long usuarioId);
    void deleteByUsuario(String usuario);
}
