package com.pg.repository;

import com.pg.entity.PgCharge;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PgChargeRepository extends JpaRepository<PgCharge, Long> {
    List<PgCharge> findByPgIdOrderByIdAsc(Long pgId);
    Optional<PgCharge> findByIdAndPgId(Long id, Long pgId);
    long countByPgId(Long pgId);
}
