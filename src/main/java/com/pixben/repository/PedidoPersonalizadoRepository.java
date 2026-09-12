package com.pixben.repository;

import com.pixben.mongo.PedidoPersonalizado;
import java.util.List;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface PedidoPersonalizadoRepository extends MongoRepository<PedidoPersonalizado, String> {
    List<PedidoPersonalizado> findByUsuarioOrderByFechaCreacionDesc(String usuario);
    List<PedidoPersonalizado> findByUsuarioIdOrderByFechaCreacionDesc(Long usuarioId);
    List<PedidoPersonalizado> findAllByOrderByFechaCreacionDesc();
}
