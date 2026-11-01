package com.pg.entity;

import com.pg.entity.enums.RentStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "rent_records",
       uniqueConstraints = @UniqueConstraint(
           name = "uq_rent_tenant_month_year",
           columnNames = {"tenant_id", "month", "year"}
       ),
       indexes = {
           @Index(name = "idx_rent_pg", columnList = "pg_id"),
           @Index(name = "idx_rent_status", columnList = "status"),
           @Index(name = "idx_rent_month_year", columnList = "month, year")
       })
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class RentRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // The tenant this rent is for
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tenant_id", nullable = false)
    @ToString.Exclude
    private Tenant tenant;

    // Direct pg_id for fast "all rent records for PG X" queries
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pg_id", nullable = false)
    @ToString.Exclude
    private PG pg;

    // The month this record is for (1 = January, 12 = December)
    @Column(nullable = false)
    private Integer month;

    @Column(nullable = false)
    private Integer year;

    // Room number AT THE TIME the rent was generated. Copied, not looked up later,
    // so history stays correct after the tenant moves to another room.
    @Column(name = "room_number", length = 20)
    private String roomNumber;

    // Base rent amount — COPIED from room at time of creation (immutable financial record)
    @Column(name = "base_amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal baseAmount;

    // Late payment penalty — 0 if paid on time, calculated if overdue
    @Column(name = "penalty_amount", precision = 10, scale = 2)
    @Builder.Default
    private BigDecimal penaltyAmount = BigDecimal.ZERO;

    // Total = base + penalty
    @Column(name = "total_amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal totalAmount;

    // Rent must be paid by this date
    @Column(name = "due_date", nullable = false)
    private LocalDate dueDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 15)
    @Builder.Default
    private RentStatus status = RentStatus.PENDING;

    // When did the tenant pay? Null if not paid yet.
    @Column(name = "paid_at")
    private LocalDateTime paidAt;

    // Any notes from owner (e.g. "Waived penalty due to medical emergency")
    @Column(length = 300)
    private String notes;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
