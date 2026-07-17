package com.pixben.repository;

import com.pixben.mongo.Pedido;
import java.util.List;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface PedidoRepository extends MongoRepository<Pedido, String> {
    List<Pedido> findByUsuario(String usuario);
    List<Pedido> findByUsuarioId(Long usuarioId);
}
