package com.pg.service;

import com.pg.dto.request.RentPaymentRequest;
import com.pg.dto.response.RentRecordResponse;
import com.pg.entity.*;
import com.pg.entity.enums.RentStatus;
import com.pg.entity.enums.Role;
import com.pg.exception.BusinessException;
import com.pg.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RentServiceTest {

    @Mock RentRecordRepository rentRecordRepository;
    @Mock TenantRepository tenantRepository;
    @Mock UserRepository userRepository;
    @Mock PGRepository pgRepository;
    @org.mockito.Spy java.time.Clock clock = java.time.Clock.systemDefaultZone();
    @InjectMocks RentService service;

    private static final String OWNER_EMAIL = "owner@example.com";
    private User owner;

    @BeforeEach
    void setUp() {
        owner = User.builder().id(1L).name("Owner").email(OWNER_EMAIL).role(Role.OWNER).build();
        when(userRepository.findByEmail(OWNER_EMAIL)).thenReturn(Optional.of(owner));
    }

    private PG pg(Integer dueDay, BigDecimal baseRent, Integer penaltyPercent) {
        PG pg = PG.builder().id(10L).name("Test PG").owner(owner)
                .baseMonthlyRent(baseRent).rentDueDay(dueDay).penaltyPercent(penaltyPercent).build();
        when(pgRepository.findByOwnerIdAndIsActiveTrue(1L)).thenReturn(Optional.of(pg));
        return pg;
    }

    private Tenant tenant(PG pg, BigDecimal roomRent) {
        Room room = Room.builder().id(7L).roomNumber("101").capacity(2).monthlyRent(roomRent).pg(pg).build();
        User user = User.builder().id(2L).name("Tenant").email("t@example.com").role(Role.TENANT).build();
        return Tenant.builder().id(100L).user(user).pg(pg).room(room).build();
    }

    private RentRecord record(PG pg, Tenant tenant, RentStatus status, LocalDate due) {
        return RentRecord.builder().id(1L).tenant(tenant).pg(pg).month(1).year(2026).roomNumber("101")
                .baseAmount(new BigDecimal("1000.00")).penaltyAmount(BigDecimal.ZERO)
                .totalAmount(new BigDecimal("1000.00")).dueDate(due).status(status).build();
    }

    // ---------- generation ----------

    @Test
    void generationOnlyTouchesTheCallersPg() {
        PG pg = pg(5, new BigDecimal("5000"), 5);
        when(tenantRepository.findActiveTenantsWithRoomByPgId(10L)).thenReturn(List.of(tenant(pg, null)));

        service.generateRentForOwnerPg(OWNER_EMAIL);

        verify(tenantRepository, never()).findAllActiveTenatsWithRoom();
    }

    @Test
    void generatedRecordKeepsTheRoomNumberAndUsesPgRentWhenRoomHasNone() {
        PG pg = pg(5, new BigDecimal("5000"), 5);
        when(tenantRepository.findActiveTenantsWithRoomByPgId(10L)).thenReturn(List.of(tenant(pg, null)));
        when(rentRecordRepository.save(any(RentRecord.class))).thenAnswer(i -> i.getArgument(0));

        service.generateRentForOwnerPg(OWNER_EMAIL);

        ArgumentCaptor<RentRecord> saved = ArgumentCaptor.forClass(RentRecord.class);
        verify(rentRecordRepository).save(saved.capture());
        assertEquals("101", saved.getValue().getRoomNumber());
        assertEquals(0, new BigDecimal("5000").compareTo(saved.getValue().getBaseAmount()));
        assertEquals(RentStatus.PENDING, saved.getValue().getStatus());
    }

    @Test
    void dueDayThirtyOneIsClampedToTheLastDayOfTheMonth() {
        PG pg = pg(31, new BigDecimal("5000"), 5);
        when(tenantRepository.findActiveTenantsWithRoomByPgId(10L)).thenReturn(List.of(tenant(pg, null)));
        when(rentRecordRepository.save(any(RentRecord.class))).thenAnswer(i -> i.getArgument(0));

        service.generateRentForOwnerPg(OWNER_EMAIL); // must not throw, whatever month it is

        ArgumentCaptor<RentRecord> saved = ArgumentCaptor.forClass(RentRecord.class);
        verify(rentRecordRepository).save(saved.capture());
        assertEquals(YearMonth.now().lengthOfMonth(), saved.getValue().getDueDate().getDayOfMonth());
    }

    @Test
    void noRecordIsCreatedWithoutAValidAmount() {
        PG pg = pg(5, null, 5); // PG has no base rent
        when(tenantRepository.findActiveTenantsWithRoomByPgId(10L)).thenReturn(List.of(tenant(pg, null)));

        String message = service.generateRentForOwnerPg(OWNER_EMAIL);

        verify(rentRecordRepository, never()).save(any());
        assertTrue(message.contains("0 created"));
        assertTrue(message.contains("1 skipped"));
    }

    // ---------- penalty ----------

    @Test
    void nightlyJobAddsOneFlatPenaltyUsingTheGuardedUpdate() {
        PG pg = pg(5, new BigDecimal("1000"), 5);
        RentRecord overdue = record(pg, tenant(pg, null), RentStatus.PENDING, LocalDate.now().minusDays(3));
        when(rentRecordRepository.findOverdueRecords(any(LocalDate.class))).thenReturn(List.of(overdue));

        service.applyPenaltyToOverdueRent();

        ArgumentCaptor<BigDecimal> penalty = ArgumentCaptor.forClass(BigDecimal.class);
        ArgumentCaptor<BigDecimal> total = ArgumentCaptor.forClass(BigDecimal.class);
        verify(rentRecordRepository).markOverdue(eq(1L), penalty.capture(), total.capture(),
                eq(RentStatus.OVERDUE), eq(RentStatus.PENDING));
        assertEquals(0, new BigDecimal("50.00").compareTo(penalty.getValue()));
        assertEquals(0, new BigDecimal("1050.00").compareTo(total.getValue()));
    }

    @Test
    void missingPenaltyPercentMeansNoPenaltyInsteadOfACrash() {
        PG pg = pg(5, new BigDecimal("1000"), null);
        RentRecord overdue = record(pg, tenant(pg, null), RentStatus.PENDING, LocalDate.now().minusDays(3));
        when(rentRecordRepository.findOverdueRecords(any(LocalDate.class))).thenReturn(List.of(overdue));

        service.applyPenaltyToOverdueRent();

        ArgumentCaptor<BigDecimal> penalty = ArgumentCaptor.forClass(BigDecimal.class);
        verify(rentRecordRepository).markOverdue(eq(1L), penalty.capture(), any(BigDecimal.class),
                eq(RentStatus.OVERDUE), eq(RentStatus.PENDING));
        assertEquals(0, BigDecimal.ZERO.compareTo(penalty.getValue()));
    }

    // ---------- mark paid ----------

    @Test
    void markPaidWithoutRequestBodyWorksAndAppliesPenaltyIfJobHasNotRunYet() {
        PG pg = pg(5, new BigDecimal("1000"), 5);
        RentRecord late = record(pg, tenant(pg, null), RentStatus.PENDING, LocalDate.now().minusDays(1));
        when(rentRecordRepository.findById(1L)).thenReturn(Optional.of(late));
        when(rentRecordRepository.markPaid(eq(1L), any(LocalDateTime.class), any(BigDecimal.class),
                any(BigDecimal.class), any(), eq(RentStatus.PAID))).thenReturn(1);

        RentRecordResponse response = service.markAsPaid(OWNER_EMAIL, 1L, null); // null body: no NPE

        assertNotNull(response);
        ArgumentCaptor<BigDecimal> penalty = ArgumentCaptor.forClass(BigDecimal.class);
        ArgumentCaptor<BigDecimal> total = ArgumentCaptor.forClass(BigDecimal.class);
        verify(rentRecordRepository).markPaid(eq(1L), any(LocalDateTime.class), penalty.capture(),
                total.capture(), any(), eq(RentStatus.PAID));
        assertEquals(0, new BigDecimal("50.00").compareTo(penalty.getValue()));
        assertEquals(0, new BigDecimal("1050.00").compareTo(total.getValue()));
    }

    @Test
    void markPaidFailsCleanlyWhenSomebodyElseAlreadyPaid() {
        PG pg = pg(5, new BigDecimal("1000"), 5);
        RentRecord pending = record(pg, tenant(pg, null), RentStatus.PENDING, LocalDate.now().plusDays(2));
        when(rentRecordRepository.findById(1L)).thenReturn(Optional.of(pending));
        when(rentRecordRepository.markPaid(eq(1L), any(LocalDateTime.class), any(BigDecimal.class),
                any(BigDecimal.class), any(), eq(RentStatus.PAID))).thenReturn(0); // lost the race

        assertThrows(BusinessException.class,
                () -> service.markAsPaid(OWNER_EMAIL, 1L, new RentPaymentRequest()));
    }

    @Test
    void alreadyPaidRecordCanNotBePaidTwice() {
        PG pg = pg(5, new BigDecimal("1000"), 5);
        RentRecord paid = record(pg, tenant(pg, null), RentStatus.PAID, LocalDate.now());
        when(rentRecordRepository.findById(1L)).thenReturn(Optional.of(paid));

        assertThrows(BusinessException.class,
                () -> service.markAsPaid(OWNER_EMAIL, 1L, new RentPaymentRequest()));
        verify(rentRecordRepository, never()).markPaid(anyLong(), any(), any(), any(), any(), any());
    }

    // ---------- report + history ----------

    @Test
    void reportRejectsImpossibleMonthAndYear() {
        assertThrows(BusinessException.class, () -> service.getMonthlyRentReport(OWNER_EMAIL, 13, 2026));
        assertThrows(BusinessException.class, () -> service.getMonthlyRentReport(OWNER_EMAIL, 0, 2026));
        assertThrows(BusinessException.class, () -> service.getMonthlyRentReport(OWNER_EMAIL, 5, 1800));
    }

    @Test
    void historicalRentShowsTheRoomFromTheTimeNotTheCurrentRoom() {
        PG pg = pg(5, new BigDecimal("1000"), 5);
        Tenant movedTenant = tenant(pg, null);            // tenant is NOW in room "101"
        RentRecord old = record(pg, movedTenant, RentStatus.PAID, LocalDate.now());
        old.setRoomNumber("007");                          // but the rent was for room "007"
        movedTenant.getRoom().setRoomNumber("101");
        when(rentRecordRepository.findByPgIdAndMonthAndYear(10L, 1, 2026)).thenReturn(List.of(old));

        List<RentRecordResponse> report = service.getMonthlyRentReport(OWNER_EMAIL, 1, 2026);

        assertEquals("007", report.get(0).getRoomNumber());
    }
}
