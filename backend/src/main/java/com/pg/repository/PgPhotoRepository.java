package com.pg.repository;

import com.pg.entity.PgPhoto;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface PgPhotoRepository extends JpaRepository<PgPhoto, Long> {
    List<PgPhoto> findByPgIdOrderBySortOrderAscIdAsc(Long pgId);

    // photos of many PGs in ONE query (search page: we only use the first of each PG)
    List<PgPhoto> findByPgIdInOrderBySortOrderAscIdAsc(Collection<Long> pgIds);

    Optional<PgPhoto> findByIdAndPgId(Long id, Long pgId);
    long countByPgId(Long pgId);
}
