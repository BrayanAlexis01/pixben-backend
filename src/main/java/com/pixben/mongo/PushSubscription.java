package com.pixben.mongo;

import java.time.LocalDateTime;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@Document(collection = "push_subscriptions")
public class PushSubscription {
    @Id
    private String id;

    @Indexed
    private Long usuarioId;

    @Indexed(unique = true)
    private String endpointHash;

    private String endpoint;
    private String userAgent;
    private LocalDateTime fechaCreacion;
    private LocalDateTime fechaActualizacion;
}
