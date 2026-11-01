package com.pg.service;

import com.pg.dto.response.DashboardResponse;
import com.pg.entity.PG;
import com.pg.entity.User;
import com.pg.entity.enums.RentStatus;
import com.pg.entity.enums.Role;
import com.pg.entity.enums.RoomStatus;
import com.pg.repository.*;
import com.pg.testutil.MutableClock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PGServiceTest {

    @Mock PGRepository pgRepository;
    @Mock UserRepository userRepository;
    @Mock RoomRepository roomRepository;
    @Mock TenantRepository tenantRepository;
    @Mock RentRecordRepository rentRecordRepository;
    @Mock ComplaintRepository complaintRepository;
    PGService service;

    @BeforeEach
    void setUp() {
        // "now" is 15 Nov 2026, so the 6 points are Jun, Jul, Aug, Sep, Oct, Nov 2026
        MutableClock clock = new MutableClock(LocalDateTime.of(2026, 11, 15, 12, 0), java.time.ZoneId.of("Asia/Kolkata"));
        service = new PGService(clock, pgRepository, userRepository, roomRepository, tenantRepository,
                rentRecordRepository, complaintRepository);
        User owner = User.builder().id(1L).email("o@x.com").role(Role.OWNER).build();
        PG pg = PG.builder().id(10L).name("Sunrise").owner(owner).build();
        when(userRepository.findByEmail("o@x.com")).thenReturn(Optional.of(owner));
        when(pgRepository.findByOwnerIdAndIsActiveTrue(1L)).thenReturn(Optional.of(pg));
        when(rentRecordRepository.getTotalOutstandingForPg(10L)).thenReturn(BigDecimal.ZERO);
    }

    @Test
    void dashboardShowsOccupancyAndFreeBedsFromBedCounts() {
        when(roomRepository.sumUsableBeds(10L, RoomStatus.UNDER_MAINTENANCE)).thenReturn(20L);
        when(roomRepository.sumFreeBeds(10L, RoomStatus.UNDER_MAINTENANCE)).thenReturn(6L);
        when(tenantRepository.countByPgIdAndIsActiveTrue(10L)).thenReturn(14L);

        DashboardResponse d = service.getDashboard("o@x.com");

        assertEquals(20, d.getTotalBeds());
        assertEquals(14, d.getOccupiedBeds());
        assertEquals(6, d.getAvailableBeds());
        assertEquals(70, d.getOccupancyPercent());
    }

    @Test
    void occupancyIsZeroNotADivisionErrorWhenThereAreNoBeds() {
        when(roomRepository.sumUsableBeds(10L, RoomStatus.UNDER_MAINTENANCE)).thenReturn(0L);
        assertEquals(0, service.getDashboard("o@x.com").getOccupancyPercent());
    }

    @Test
    void rentTrendAlwaysHasSixMonthsOldestFirstWithZerosForMonthsWithoutRent() {
        // only Oct and Nov 2026 have rent records: [year, month, expected, collected]
        when(rentRecordRepository.monthlyTotals(eq(10L), anyInt(), eq(RentStatus.PAID))).thenReturn(List.of(
                new Object[]{2026, 10, new BigDecimal("50000"), new BigDecimal("50000")},
                new Object[]{2026, 11, new BigDecimal("52000"), new BigDecimal("30000")}));

        DashboardResponse d = service.getDashboard("o@x.com");

        var t = d.getRentTrend();
        assertEquals(6, t.size());
        assertEquals(6, t.get(0).getMonth());                       // Jun 2026 first
        assertEquals(0, BigDecimal.ZERO.compareTo(t.get(0).getExpected()));
        assertEquals(11, t.get(5).getMonth());                      // Nov 2026 last
        assertEquals(0, new BigDecimal("30000").compareTo(d.getRevenueThisMonth()));
        assertEquals(0, new BigDecimal("52000").compareTo(d.getExpectedThisMonth()));
        // the query asks from 5 months back: Jun 2026
        verify(rentRecordRepository).monthlyTotals(10L, 2026 * 12 + 6, RentStatus.PAID);
    }

    @Test
    void trendAcrossAYearBoundaryStaysInOrder() {
        MutableClock jan = new MutableClock(LocalDateTime.of(2027, 1, 10, 12, 0), java.time.ZoneId.of("Asia/Kolkata"));
        PGService s = new PGService(jan, pgRepository, userRepository, roomRepository, tenantRepository,
                rentRecordRepository, complaintRepository);
        var t = s.getDashboard("o@x.com").getRentTrend();
        assertEquals(List.of(8, 9, 10, 11, 12, 1), t.stream().map(DashboardResponse.MonthRevenue::getMonth).toList());
        assertEquals(2026, t.get(0).getYear());
        assertEquals(2027, t.get(5).getYear());
    }
}
