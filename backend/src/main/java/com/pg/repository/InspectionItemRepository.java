package com.pg.repository;

import com.pg.entity.InspectionItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface InspectionItemRepository extends JpaRepository<InspectionItem, Long> {
    List<InspectionItem> findByInspectionIdOrderByIdAsc(Long inspectionId);
    Optional<InspectionItem> findByIdAndInspectionId(Long id, Long inspectionId);
    long countByInspectionId(Long inspectionId);
}
