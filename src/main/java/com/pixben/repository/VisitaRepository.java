package com.pixben.repository;

import com.pixben.mongo.Visita;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface VisitaRepository extends MongoRepository<Visita, String> {
    Optional<Visita> findFirstByHashVisitanteAndRutaAndDia(String hashVisitante, String ruta, LocalDate dia);
    List<Visita> findTop50ByAutenticadaTrueOrderByFechaDesc();
    void deleteByFechaBefore(LocalDateTime limite);
}
