package com.pg.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;


@Entity
@Table(name = "pgs",
        // Version 1 rule: ONE PG per owner. Enforced by the database too, not only by PGService.
        // To support several PGs later, remove this constraint and make the services take a pgId.
        uniqueConstraints = @UniqueConstraint(name = "uq_pg_owner", columnNames = "owner_id"),
        indexes = {
        @Index(name = "idx_pg_city", columnList = "city"),
        @Index(name = "idx_pg_owner", columnList = "owner_id")
})
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class PG {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String name; // e.g. "Sharma Boys PG"

    @Column(nullable = false, length = 500)
    private String address;

    @Column(nullable = false, length = 100)
    private String city;

    @Column(length = 1000)
    private String description;

    @Column(name = "contact_phone", length = 15)
    private String contactPhone;

    // Where the PG is. Optional: PGs without coordinates still work, they just show no distance.
    @Column(name = "latitude")
    private Double latitude;

    @Column(name = "longitude")
    private Double longitude;

    // Base monthly rent — rooms can override this individually
    @Column(name = "base_monthly_rent", precision = 10, scale = 2)
    private BigDecimal baseMonthlyRent;

    // Rent due day of month (default 5 — from application.properties)
    @Column(name = "rent_due_day")
    @Builder.Default
    private Integer rentDueDay = 5;

    // How tenants book: INSTANT (default) or ON_REQUEST (owner approves). Null in old rows = INSTANT.
    @Enumerated(EnumType.STRING)
    @Column(name = "booking_mode", length = 12)
    @Builder.Default
    private com.pg.entity.enums.BookingMode bookingMode = com.pg.entity.enums.BookingMode.INSTANT;

    // Who the PG accepts. Null in old rows = ANY.
    @Enumerated(EnumType.STRING)
    @Column(name = "gender_policy", length = 12)
    @Builder.Default
    private com.pg.entity.enums.GenderPolicy genderPolicy = com.pg.entity.enums.GenderPolicy.ANY;

    // Late penalty percentage (e.g. 5 means 5% of rent amount)
    @Column(name = "penalty_percent")
    @Builder.Default
    private Integer penaltyPercent = 5;

    @Column(name = "is_active")
    @Builder.Default
    private Boolean isActive = true;

    // THE OWNER. ManyToOne so multi-PG can be added later, but version 1 allows one PG per owner (see @Table).
    // FK 'owner_id' lives in the pgs table (this is the owning side)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_id", nullable = false)
    private User owner;

    // Rooms in this PG — OneToMany, rooms hold the FK
    @OneToMany(mappedBy = "pg", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<Room> rooms = new ArrayList<>();

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
