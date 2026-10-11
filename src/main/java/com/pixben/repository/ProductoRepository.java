/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.pixben.repository;

import com.pixben.model.Producto;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Pageable;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.math.BigDecimal;
import java.util.Optional;

public interface ProductoRepository extends JpaRepository<Producto, Long>, JpaSpecificationExecutor<Producto> {
    @Query("select distinct p.categoria from Producto p where p.categoria is not null order by p.categoria")
    List<String> listarCategorias();

    @Query("select max(p.precio) from Producto p")
    BigDecimal precioMaximo();

    List<Producto> findByDestacadoTrueOrderByIdDesc(Pageable limite);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Producto p where p.id = :id")
    Optional<Producto> findByIdForUpdate(@Param("id") Long id);

    boolean existsByNombreIgnoreCase(String nombre);
    Optional<Producto> findFirstBySkuIgnoreCase(String sku);
}
