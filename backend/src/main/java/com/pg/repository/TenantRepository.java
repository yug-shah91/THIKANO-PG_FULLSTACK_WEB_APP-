package com.pg.repository;

import com.pg.entity.Tenant;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface TenantRepository extends JpaRepository<Tenant, Long> {

    // The person's CURRENT stay: the one with is_active = true. Used for "can this person book / complain".
    @Query("SELECT t FROM Tenant t WHERE t.user.email = :email AND t.isActive = true")
    Optional<Tenant> findActiveByUserEmail(@Param("email") String email);

    // The active stay if there is one, otherwise the most recent finished stay. Used for views
    // (profile, rent history, move-out report) so a person who checked out can still see their history.
    Optional<Tenant> findFirstByUser_EmailOrderByIsActiveDescIdDesc(String email);

    default Optional<Tenant> findCurrentByUserEmail(String email) {
        return findFirstByUser_EmailOrderByIsActiveDescIdDesc(email);
    }

    // All ACTIVE tenants in a specific PG — owner's primary view
    List<Tenant> findByPgIdAndIsActiveTrue(Long pgId);

    // Paginated list of tenants for a PG (for large PGs)
    Page<Tenant> findByPgIdAndIsActiveTrue(Long pgId, Pageable pageable);

    // All tenants in a specific room (to check occupancy)
    List<Tenant> findByRoomIdAndIsActiveTrue(Long roomId);

    // Count active tenants in a PG — dashboard stat
    long countByPgIdAndIsActiveTrue(Long pgId);

    // Security check: does this tenant belong to this PG?
    // Used before operations to prevent cross-tenant access
    boolean existsByIdAndPgId(Long tenantId, Long pgId);

    // Search tenants by name within a PG
    @Query("SELECT t FROM Tenant t WHERE t.pg.id = :pgId " +
           "AND t.isActive = true " +
           "AND LOWER(t.user.name) LIKE LOWER(CONCAT('%', :name, '%'))")
    List<Tenant> searchByNameInPg(@Param("pgId") Long pgId, @Param("name") String name);

    // Find all active tenants across all PGs — for rent generation scheduler
    // Only tenants who have a room allocated (can't charge rent for unallocated tenants)
    @Query("SELECT t FROM Tenant t WHERE t.isActive = true AND t.room IS NOT NULL")
    List<Tenant> findAllActiveTenatsWithRoom();

    // Same, but only for ONE PG - used when an owner presses "Generate rent"
    @Query("SELECT t FROM Tenant t WHERE t.isActive = true AND t.room IS NOT NULL AND t.pg.id = :pgId")
    List<Tenant> findActiveTenantsWithRoomByPgId(@Param("pgId") Long pgId);

    // Loads the tenant and locks the row (prevents double allocate / double checkout)
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT t FROM Tenant t WHERE t.id = :tenantId")
    Optional<Tenant> findByIdForUpdate(@Param("tenantId") Long tenantId);
}
