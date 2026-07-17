package com.pixben.repository;

import com.pixben.mongo.PushSubscription;
import java.util.List;
import java.util.Optional;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface PushSubscriptionRepository extends MongoRepository<PushSubscription, String> {
    List<PushSubscription> findByUsuarioId(Long usuarioId);
    Optional<PushSubscription> findByEndpointHash(String endpointHash);
    void deleteByEndpointHash(String endpointHash);
}
