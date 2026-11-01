package com.pg.entity;

import com.pg.entity.enums.ItemCondition;
import com.pg.entity.enums.Party;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

// One line of the report, e.g. "Window - MINOR_DAMAGE - crack in the corner".
@Entity
@Table(name = "inspection_items", indexes = @Index(name = "idx_item_inspection", columnList = "inspection_id"))
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class InspectionItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "inspection_id", nullable = false)
    @ToString.Exclude
    private Inspection inspection;

    @Column(nullable = false, length = 60)
    private String area; // "Bed", "Window", "Wall", "Cupboard", ...

    @Enumerated(EnumType.STRING)
    @Column(name = "item_condition", nullable = false, length = 20)
    private ItemCondition condition;

    @Column(length = 300)
    private String note;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Party party; // who wrote this line

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
