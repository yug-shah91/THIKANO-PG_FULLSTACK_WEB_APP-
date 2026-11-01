package com.pg.repository;

import com.pg.entity.PG;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface PGRepository extends JpaRepository<PG, Long> {

    // Find all PGs belonging to a specific owner (by owner's user id)
    // Used to enforce: owner can only see their own PG(s)
    List<PG> findByOwnerId(Long ownerId);

    // Find active PG by owner — most operations need this
    Optional<PG> findByOwnerIdAndIsActiveTrue(Long ownerId);

    // Check if owner already has a PG registered (v1: one PG per owner)
    boolean existsByOwnerId(Long ownerId);

    // For the public search page
    List<PG> findByIsActiveTrue();

    // Same, but the text filter runs IN THE DATABASE (name, city or address contains the text).
    // `pattern` is already lower-case and escaped, e.g. "%pune%".
    @org.springframework.data.jpa.repository.Query("SELECT p FROM PG p WHERE p.isActive = true AND (LOWER(p.city) LIKE :pattern ESCAPE '\\' "
            + "OR LOWER(p.name) LIKE :pattern ESCAPE '\\' OR LOWER(p.address) LIKE :pattern ESCAPE '\\')")
    List<PG> findActiveMatching(@org.springframework.data.repository.query.Param("pattern") String pattern);

    // locks the PG row so two simultaneous uploads can not both pass the photo limit
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("SELECT p FROM PG p WHERE p.id = :id")
    Optional<PG> findByIdForUpdate(@org.springframework.data.repository.query.Param("id") Long id);
}
