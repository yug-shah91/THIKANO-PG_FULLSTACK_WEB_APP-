package com.pg.repository;

import com.pg.entity.Inspection;
import com.pg.entity.enums.InspectionType;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface InspectionRepository extends JpaRepository<Inspection, Long> {

    List<Inspection> findByTenantIdOrderByCreatedAtAsc(Long tenantId);

    Optional<Inspection> findByTenantIdAndType(Long tenantId, InspectionType type);

    // Locked while we add entries or confirm, so "last confirmation" can not race with an edit
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT i FROM Inspection i JOIN FETCH i.tenant t JOIN FETCH t.user JOIN FETCH i.pg WHERE i.id = :id")
    Optional<Inspection> findByIdForUpdate(@Param("id") Long id);

    @Query("SELECT i FROM Inspection i JOIN FETCH i.tenant t JOIN FETCH t.user JOIN FETCH i.pg WHERE i.id = :id")
    Optional<Inspection> findByIdWithParties(@Param("id") Long id);
}
