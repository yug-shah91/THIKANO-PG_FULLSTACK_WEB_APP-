package com.pg.repository;

import com.pg.entity.RentRecord;
import com.pg.entity.enums.RentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface RentRecordRepository extends JpaRepository<RentRecord, Long> {

    // Check if rent already generated for a tenant for a specific month/year
    // Used by scheduler to prevent duplicate rent records
    // Postgres advisory lock held until the transaction ends. Rent generation takes it so two
    // runs at the same time (scheduler + owner click, two owners) go one after the other.
    @org.springframework.data.jpa.repository.Query(value = "SELECT CAST(pg_advisory_xact_lock(:key) AS text)", nativeQuery = true)
    String lockRentGeneration(@org.springframework.data.repository.query.Param("key") long key);

    boolean existsByTenantIdAndMonthAndYear(Long tenantId, Integer month, Integer year);

    // Get a specific month's rent for a tenant
    Optional<RentRecord> findByTenantIdAndMonthAndYear(Long tenantId, Integer month, Integer year);

    // All rent records for a specific tenant — their payment history
    List<RentRecord> findByTenantIdOrderByYearDescMonthDesc(Long tenantId);

    // All PENDING/OVERDUE rent records for a PG — owner's collection view
    List<RentRecord> findByPgIdAndStatusOrderByDueDateAsc(Long pgId, RentStatus status);

    // All rent records for a PG in a specific month — monthly overview
    List<RentRecord> findByPgIdAndMonthAndYear(Long pgId, Integer month, Integer year);

    // Unpaid rent records past due date — for penalty application scheduler
    // Finds all PENDING records where due_date has passed
    @Query("SELECT r FROM RentRecord r JOIN FETCH r.pg WHERE r.status = :pending AND r.dueDate < :today")
    List<RentRecord> findByStatusAndDueDateBefore(@Param("pending") RentStatus pending, @Param("today") LocalDate today);

    default List<RentRecord> findOverdueRecords(LocalDate today) {
        return findByStatusAndDueDateBefore(RentStatus.PENDING, today);
    }

    // Guarded updates: the WHERE on status makes them atomic, so the nightly penalty job
    // and an owner pressing "Mark paid" can never overwrite each other.
    // They return the number of rows changed (0 = somebody else got there first).

    // PENDING -> OVERDUE with penalty
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE RentRecord r SET r.penaltyAmount = :penalty, r.totalAmount = :total, r.status = :overdue " +
           "WHERE r.id = :id AND r.status = :pending")
    int markOverdue(@Param("id") Long id,
                    @Param("penalty") java.math.BigDecimal penalty,
                    @Param("total") java.math.BigDecimal total,
                    @Param("overdue") RentStatus overdue,
                    @Param("pending") RentStatus pending);

    // PENDING/OVERDUE -> PAID. status and paidAt are always written together.
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE RentRecord r SET r.status = :paid, r.paidAt = :paidAt, r.penaltyAmount = :penalty, " +
           "r.totalAmount = :total, r.notes = :notes WHERE r.id = :id AND r.status <> :paid")
    int markPaid(@Param("id") Long id,
                 @Param("paidAt") java.time.LocalDateTime paidAt,
                 @Param("penalty") java.math.BigDecimal penalty,
                 @Param("total") java.math.BigDecimal total,
                 @Param("notes") String notes,
                 @Param("paid") RentStatus paid);

    // Total outstanding rent for a PG (sum of all PENDING + OVERDUE total amounts)
    @Query("SELECT COALESCE(SUM(r.totalAmount), 0) FROM RentRecord r " +
           "WHERE r.pg.id = :pgId AND r.status IN (:pending, :overdue)")
    java.math.BigDecimal sumOutstandingForPg(@Param("pgId") Long pgId,
                                             @Param("pending") RentStatus pending,
                                             @Param("overdue") RentStatus overdue);

    default java.math.BigDecimal getTotalOutstandingForPg(Long pgId) {
        return sumOutstandingForPg(pgId, RentStatus.PENDING, RentStatus.OVERDUE);
    }

    // rent per month since `fromIndex` (= year * 12 + month): [year, month, expected, collected]
    @Query("SELECT r.year, r.month, SUM(r.totalAmount), "
            + "SUM(CASE WHEN r.status = :paid THEN r.totalAmount ELSE 0 END) "
            + "FROM RentRecord r WHERE r.pg.id = :pgId AND (r.year * 12 + r.month) >= :fromIndex "
            + "GROUP BY r.year, r.month ORDER BY r.year, r.month")
    List<Object[]> monthlyTotals(@Param("pgId") Long pgId, @Param("fromIndex") int fromIndex, @Param("paid") RentStatus paid);

    // Number of unpaid records in ALL months (same scope as getTotalOutstandingForPg)
    long countByPgIdAndStatus(Long pgId, RentStatus status);

    // Collection rate: how many paid vs total for a month
    long countByPgIdAndMonthAndYearAndStatus(Long pgId, Integer month, Integer year, RentStatus status);
}
