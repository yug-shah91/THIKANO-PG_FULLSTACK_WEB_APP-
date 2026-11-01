package com.pg.entity;

import com.pg.entity.enums.InspectionType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * A written + photographed record of a room's condition at move-in or move-out.
 * Both the owner and the tenant add entries and each confirms. When both have confirmed
 * it is locked and can never change, which is what makes it usable as evidence.
 */
@Entity
@Table(name = "inspections",
        uniqueConstraints = @UniqueConstraint(name = "uq_inspection_tenant_type", columnNames = {"tenant_id", "type"}),
        indexes = @Index(name = "idx_inspection_pg", columnList = "pg_id"))
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class Inspection {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tenant_id", nullable = false)
    @ToString.Exclude
    private Tenant tenant;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pg_id", nullable = false)
    @ToString.Exclude
    private PG pg;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private InspectionType type;

    // snapshot: the room can change later, the evidence must not
    @Column(name = "room_number", nullable = false, length = 20)
    private String roomNumber;

    @Column(length = 1000)
    private String note;

    @Column(name = "owner_confirmed_at")
    private LocalDateTime ownerConfirmedAt;

    @Column(name = "tenant_confirmed_at")
    private LocalDateTime tenantConfirmedAt;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    public boolean isComplete() {
        return ownerConfirmedAt != null && tenantConfirmedAt != null;
    }
}
