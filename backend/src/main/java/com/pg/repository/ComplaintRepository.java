package com.pg.repository;

import com.pg.entity.Complaint;
import com.pg.entity.enums.ComplaintCategory;
import com.pg.entity.enums.ComplaintStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface ComplaintRepository extends JpaRepository<Complaint, Long> {

    // All complaints for a PG — owner sees all their tenants' complaints
    Page<Complaint> findByPgIdOrderByCreatedAtDesc(Long pgId, Pageable pageable);

    // Filter by status within a PG — e.g. all OPEN complaints
    List<Complaint> findByPgIdAndStatusOrderByCreatedAtDesc(Long pgId, ComplaintStatus status);

    // Filter by category within a PG — e.g. all ELECTRICAL complaints
    List<Complaint> findByPgIdAndCategoryOrderByCreatedAtDesc(Long pgId, ComplaintCategory category);

    // Tenant sees only their own complaints
    List<Complaint> findByTenantIdOrderByCreatedAtDesc(Long tenantId);

    // Count open complaints in a PG — dashboard urgency indicator
    long countByPgIdAndStatus(Long pgId, ComplaintStatus status);

    // Security check: does this complaint belong to this PG?
    boolean existsByIdAndPgId(Long complaintId, Long pgId);

    // Security check: does this complaint belong to this tenant?
    boolean existsByIdAndTenantId(Long complaintId, Long tenantId);

    // Closes every RESOLVED complaint older than the cutoff with ONE SQL UPDATE
    // (instead of loading all complaints into memory). Returns how many were closed.
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE Complaint c SET c.status = :closed, c.updatedAt = :now, " +
           "c.ownerResponse = CASE WHEN c.ownerResponse IS NULL OR c.ownerResponse = '' " +
           "THEN :note ELSE CONCAT(c.ownerResponse, ' | ', :note) END " +
           "WHERE c.status = :resolved AND c.resolvedAt IS NOT NULL AND c.resolvedAt < :cutoff")
    int autoCloseResolvedBefore(@Param("cutoff") LocalDateTime cutoff,
                                @Param("now") LocalDateTime now,
                                @Param("note") String note,
                                @Param("resolved") ComplaintStatus resolved,
                                @Param("closed") ComplaintStatus closed);

    // How many complaints per category — analytics for premium feature
    @Query("SELECT c.category, COUNT(c) FROM Complaint c " +
           "WHERE c.pg.id = :pgId GROUP BY c.category")
    List<Object[]> countByCategory(@Param("pgId") Long pgId);
}
