package com.pixben.repository;

import com.pixben.mongo.Favorito;
import java.util.List;
import java.util.Optional;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface FavoritoRepository extends MongoRepository<Favorito, String> {
    List<Favorito> findByUsuario(String usuario);
    List<Favorito> findByUsuarioId(Long usuarioId);
    Optional<Favorito> findFirstByUsuarioIdAndProductoId(Long usuarioId, Long productoId);
    Optional<Favorito> findFirstByUsuarioAndProductoId(String usuario, Long productoId);
    void deleteByUsuarioId(Long usuarioId);
    void deleteByUsuario(String usuario);
}
