package com.pg.entity;

import com.pg.entity.enums.ChargeKind;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

// One extra cost the owner discloses besides room rent.
@Entity
@Table(name = "pg_charges", indexes = @Index(name = "idx_charge_pg", columnList = "pg_id"))
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class PgCharge {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pg_id", nullable = false)
    @ToString.Exclude
    private PG pg;

    @Column(nullable = false, length = 60)
    private String name;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ChargeKind kind;

    @Column(length = 120)
    private String note; // e.g. "3 meals a day", "refunded at checkout"
}
