package com.pg.service;

import com.pg.dto.response.CostEstimateResponse;
import com.pg.entity.PG;
import com.pg.entity.PgCharge;
import com.pg.entity.Room;
import com.pg.entity.enums.ChargeKind;
import com.pg.entity.enums.RoomStatus;
import com.pg.entity.enums.RoomType;
import com.pg.exception.BusinessException;
import com.pg.exception.ResourceNotFoundException;
import com.pg.repository.PGRepository;
import com.pg.repository.PgChargeRepository;
import com.pg.repository.RoomRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CostEstimateServiceTest {

    @Mock PGRepository pgRepository;
    @Mock RoomRepository roomRepository;
    @Mock PgChargeRepository chargeRepository;
    @InjectMocks CostEstimateService service;

    PG pg;
    Room room;

    private static BigDecimal n(String s) { return new BigDecimal(s); }

    private PgCharge charge(long id, String name, String amount, ChargeKind kind) {
        return PgCharge.builder().id(id).pg(pg).name(name).amount(n(amount)).kind(kind).build();
    }

    @BeforeEach
    void setUp() {
        pg = PG.builder().id(1L).name("Sunrise").isActive(true).baseMonthlyRent(n("4000")).build();
        room = Room.builder().id(5L).roomNumber("101").type(RoomType.DOUBLE).capacity(2)
                .monthlyRent(n("6000")).pg(pg).build();
        when(pgRepository.findById(1L)).thenReturn(Optional.of(pg));
        when(roomRepository.findById(5L)).thenReturn(Optional.of(room));
        when(chargeRepository.findByPgIdOrderByIdAsc(1L)).thenReturn(List.of(
                charge(10, "Electricity", "800", ChargeKind.REQUIRED_MONTHLY),
                charge(11, "Meals", "3000", ChargeKind.OPTIONAL_MONTHLY),
                charge(12, "Laundry", "500", ChargeKind.OPTIONAL_MONTHLY),
                charge(13, "Security deposit", "10000", ChargeKind.ONE_TIME)));
    }

    @Test
    void addsRentRequiredChargesChosenServicesAndCommute() {
        CostEstimateResponse r = service.estimate(1L, 5L, Set.of(11L), n("40"), 22);

        // 6000 rent + 800 electricity + 3000 meals + (40 x 22 = 880) commute
        assertEquals(0, n("10680").compareTo(r.getMonthlyTotal()));
        assertEquals(0, n("4680").compareTo(r.getExtraOverRent()));
        assertEquals(0, n("880").compareTo(r.getCommute()));
        assertEquals(1, r.getChosenOptional().size());
    }

    @Test
    void oneTimeChargesAreSeparateAndFormTheFirstMonthTotal() {
        CostEstimateResponse r = service.estimate(1L, 5L, null, null, null);

        assertEquals(0, n("6800").compareTo(r.getMonthlyTotal()));      // rent + electricity only
        assertEquals(0, n("10000").compareTo(r.getOneTimeTotal()));
        assertEquals(0, n("16800").compareTo(r.getFirstMonthTotal()));  // money needed on day one
    }

    @Test
    void unchosenOptionalServicesAreNotCounted() {
        CostEstimateResponse r = service.estimate(1L, 5L, Set.of(), null, null);
        assertTrue(r.getChosenOptional().isEmpty());
        assertEquals(0, n("6800").compareTo(r.getMonthlyTotal()));
    }

    @Test
    void roomWithoutOwnRentUsesThePgBaseRent() {
        room.setMonthlyRent(null);
        assertEquals(0, n("4800").compareTo(service.estimate(1L, 5L, null, null, null).getMonthlyTotal()));
    }

    @Test
    void choosingARequiredOrUnknownChargeAsOptionalIsRejected() {
        assertThrows(BusinessException.class, () -> service.estimate(1L, 5L, Set.of(10L), null, null)); // required one
        assertThrows(BusinessException.class, () -> service.estimate(1L, 5L, Set.of(999L), null, null)); // not in this PG
        assertThrows(BusinessException.class, () -> service.estimate(1L, 5L, Set.of(13L), null, null)); // one-time one
    }

    @Test
    void badCommuteValuesAreRejected() {
        assertThrows(BusinessException.class, () -> service.estimate(1L, 5L, null, n("-1"), 10));
        assertThrows(BusinessException.class, () -> service.estimate(1L, 5L, null, n("6000"), 10));
        assertThrows(BusinessException.class, () -> service.estimate(1L, 5L, null, n("40"), 40));
        assertThrows(BusinessException.class, () -> service.estimate(1L, 5L, null, n("40"), -1));
    }

    @Test
    void roomFromAnotherPgOrInMaintenanceOrInactivePgIsRejected() {
        PG other = PG.builder().id(2L).isActive(true).build();
        room.setPg(other);
        assertThrows(ResourceNotFoundException.class, () -> service.estimate(1L, 5L, null, null, null));

        room.setPg(pg);
        room.setStatus(RoomStatus.UNDER_MAINTENANCE);
        assertThrows(BusinessException.class, () -> service.estimate(1L, 5L, null, null, null));

        room.setStatus(RoomStatus.AVAILABLE);
        pg.setIsActive(false);
        assertThrows(ResourceNotFoundException.class, () -> service.estimate(1L, 5L, null, null, null));
    }
}
