package com.pg.repository;

import com.pg.entity.InspectionPhoto;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface InspectionPhotoRepository extends JpaRepository<InspectionPhoto, Long> {
    @Query("SELECT p FROM InspectionPhoto p LEFT JOIN FETCH p.item WHERE p.inspection.id = :inspectionId ORDER BY p.id")
    List<InspectionPhoto> findByInspection(@Param("inspectionId") Long inspectionId);

    long countByInspectionId(Long inspectionId);
    boolean existsByItemId(Long itemId);

    @Query("SELECT p FROM InspectionPhoto p JOIN FETCH p.inspection i JOIN FETCH i.tenant t JOIN FETCH t.user JOIN FETCH i.pg WHERE p.id = :id")
    Optional<InspectionPhoto> findWithInspection(@Param("id") Long id);
}
