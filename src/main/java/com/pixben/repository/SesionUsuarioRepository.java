package com.pixben.repository;

import com.pixben.model.SesionUsuario;
import java.time.LocalDateTime;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SesionUsuarioRepository extends JpaRepository<SesionUsuario, String> {
    void deleteByExpiraEnBefore(LocalDateTime limite);
}
