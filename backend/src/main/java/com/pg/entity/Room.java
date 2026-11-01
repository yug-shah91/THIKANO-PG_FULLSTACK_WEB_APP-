package com.pg.entity;

import com.pg.entity.enums.RoomStatus;
import com.pg.entity.enums.RoomType;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "rooms",
       uniqueConstraints = @UniqueConstraint(name = "uq_room_pg_number", columnNames = {"pg_id", "room_number"}),
       indexes = {
        @Index(name = "idx_room_pg", columnList = "pg_id"),
        @Index(name = "idx_room_status", columnList = "status")
})
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class Room {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "room_number", nullable = false, length = 20)
    private String roomNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RoomType type;


    @Column(nullable = false)
    private Integer capacity;


    @Column(name = "current_occupancy")
    @Builder.Default
    private Integer currentOccupancy = 0;


    @Column(name = "monthly_rent", precision = 10, scale = 2)
    private BigDecimal monthlyRent;


    @Column(name = "floor_number")
    private Integer floorNumber;

    @Column(length = 300)
    private String amenities;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 25)
    @Builder.Default
    private RoomStatus status = RoomStatus.AVAILABLE;


    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pg_id", nullable = false)
    @ToString.Exclude
    private PG pg;


    @OneToMany(mappedBy = "room", cascade = CascadeType.ALL)
    @Builder.Default
    private List<Tenant> tenants = new ArrayList<>();

    /**
     * Single place that decides AVAILABLE vs FULLY_OCCUPIED from the occupancy.
     * A room UNDER_MAINTENANCE is left alone - only setMaintenanceStatus() changes that.
     */
    public void refreshStatus() {
        if (status == RoomStatus.UNDER_MAINTENANCE) {
            return;
        }
        int occupied = currentOccupancy == null ? 0 : currentOccupancy;
        status = occupied >= capacity ? RoomStatus.FULLY_OCCUPIED : RoomStatus.AVAILABLE;
    }
}
