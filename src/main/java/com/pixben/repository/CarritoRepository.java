package com.pixben.repository;

import com.pixben.mongo.Carrito;
import java.util.List;
import java.util.Optional;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface CarritoRepository extends MongoRepository<Carrito, String> {
    List<Carrito> findByUsuario(String usuario);
    List<Carrito> findByUsuarioId(Long usuarioId);
    Optional<Carrito> findFirstByUsuarioIdAndProductoIdAndTallaAndColorAndPersonalizadoFalse(
            Long usuarioId, Long productoId, String talla, String color);
    void deleteByUsuarioId(Long usuarioId);
    void deleteByUsuario(String usuario);
}
