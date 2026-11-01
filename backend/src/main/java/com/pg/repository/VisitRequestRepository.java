package com.pg.repository;

import com.pg.entity.VisitRequest;
import com.pg.entity.enums.VisitStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface VisitRequestRepository extends JpaRepository<VisitRequest, Long> {

    @Query("SELECT v FROM VisitRequest v JOIN FETCH v.pg JOIN FETCH v.user WHERE v.user.id = :userId ORDER BY v.visitDate DESC, v.visitTime DESC")
    List<VisitRequest> findMine(@Param("userId") Long userId);

    @Query("SELECT v FROM VisitRequest v JOIN FETCH v.pg JOIN FETCH v.user WHERE v.pg.id = :pgId ORDER BY v.visitDate ASC, v.visitTime ASC")
    List<VisitRequest> findForPg(@Param("pgId") Long pgId);

    long countByPgIdAndStatusAndVisitDateGreaterThanEqual(Long pgId, VisitStatus status, LocalDate from);

    // active = still waiting or confirmed, and not in the past
    long countByUserIdAndStatusInAndVisitDateGreaterThanEqual(Long userId, Collection<VisitStatus> statuses, LocalDate from);

    boolean existsByUserIdAndPgIdAndStatusInAndVisitDateGreaterThanEqual(Long userId, Long pgId, Collection<VisitStatus> statuses, LocalDate from);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT v FROM VisitRequest v JOIN FETCH v.pg JOIN FETCH v.user WHERE v.id = :id")
    Optional<VisitRequest> findByIdForUpdate(@Param("id") Long id);
}
