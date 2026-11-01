package com.pg.repository;

import com.pg.entity.Review;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface ReviewRepository extends JpaRepository<Review, Long> {

    Optional<Review> findByTenantId(Long tenantId);

    @Query(value = "SELECT r FROM Review r JOIN FETCH r.tenant t JOIN FETCH t.user WHERE r.pg.id = :pgId ORDER BY r.createdAt DESC",
           countQuery = "SELECT COUNT(r) FROM Review r WHERE r.pg.id = :pgId")
    Page<Review> findByPg(@Param("pgId") Long pgId, Pageable pageable);

    @Query("SELECT r FROM Review r JOIN FETCH r.tenant t JOIN FETCH t.user WHERE r.id = :id")
    Optional<Review> findWithTenant(@Param("id") Long id);

    // average + count for MANY PGs in one query: rows are [pgId, average, count]
    @Query("SELECT r.pg.id, AVG(r.rating), COUNT(r) FROM Review r WHERE r.pg.id IN :pgIds GROUP BY r.pg.id")
    List<Object[]> summaries(@Param("pgIds") Collection<Long> pgIds);

    // rows are [rating, count] so the PG page can show how the stars are spread
    @Query("SELECT r.rating, COUNT(r) FROM Review r WHERE r.pg.id = :pgId GROUP BY r.rating")
    List<Object[]> distribution(@Param("pgId") Long pgId);
}
