package com.pg.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data @Builder
public class DashboardResponse {
    private String pgName;
    private Long totalRooms;
    private Long availableRooms;
    private Long fullyOccupiedRooms;
    private Long activeTenants;
    private Long openComplaints;
    private Long pendingRentCount;
    private BigDecimal totalOutstandingRent;
    private Long overdueRentCount;

    private Long totalBeds;            // beds in rooms that are not under maintenance
    private Long occupiedBeds;         // active tenants
    private Long availableBeds;        // free beds right now (holds count as taken)
    private Integer occupancyPercent;  // occupiedBeds / totalBeds
    private BigDecimal revenueThisMonth;   // rent PAID for this month's records
    private BigDecimal expectedThisMonth;  // all rent due for this month
    private List<MonthRevenue> rentTrend;  // last 6 months, oldest first

    @Data @Builder
    public static class MonthRevenue {
        private Integer year;
        private Integer month;
        private BigDecimal collected;
        private BigDecimal expected;
    }
}
