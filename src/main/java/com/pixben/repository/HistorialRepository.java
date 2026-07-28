package com.pixben.repository;

import com.pixben.mongo.Historial;
import java.util.List;
import java.util.Optional;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface HistorialRepository extends MongoRepository<Historial, String> {
    List<Historial> findByUsuarioOrderByFechaDesc(String usuario);
    List<Historial> findByUsuarioIdOrderByFechaDesc(Long usuarioId);
    Optional<Historial> findFirstByUsuarioIdAndProductoId(Long usuarioId, Long productoId);
    Optional<Historial> findFirstByUsuarioAndProductoId(String usuario, Long productoId);
    void deleteByUsuarioId(Long usuarioId);
    void deleteByUsuario(String usuario);
    void deleteByUsuarioIdAndProductoId(Long usuarioId, Long productoId);
    void deleteByUsuarioAndProductoId(String usuario, Long productoId);
}
