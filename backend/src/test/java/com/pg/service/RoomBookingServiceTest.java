package com.pg.service;

import com.pg.dto.request.BookingDecisionRequest;
import com.pg.dto.request.BookingRequest;
import com.pg.dto.response.RoomBookingResponse;
import com.pg.entity.*;
import com.pg.entity.enums.*;
import com.pg.exception.BusinessException;
import com.pg.exception.UnauthorizedAccessException;
import com.pg.testutil.MutableClock;
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

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RoomBookingServiceTest {

    @Mock RoomBookingRepository bookingRepository;
    @Mock RoomRepository roomRepository;
    @Mock TenantRepository tenantRepository;
    @Mock UserRepository userRepository;
    @Mock PGRepository pgRepository;
    RoomBookingService service;
    MutableClock clock;

    User owner, tenantUser, otherOwner;
    PG pg, otherPg;
    Room room;

    @BeforeEach
    void setUp() {
        clock = new MutableClock(LocalDateTime.of(2026, 10, 28, 12, 0), java.time.ZoneId.of("Asia/Kolkata"));
        service = new RoomBookingService(bookingRepository, roomRepository, tenantRepository,
                userRepository, pgRepository, clock);
        owner = User.builder().id(1L).email("o@x.com").role(Role.OWNER).build();
        otherOwner = User.builder().id(2L).email("o2@x.com").role(Role.OWNER).build();
        tenantUser = User.builder().id(3L).name("Asha").email("t@x.com").role(Role.TENANT).build();
        pg = PG.builder().id(10L).owner(owner).isActive(true).bookingMode(BookingMode.ON_REQUEST).build();
        otherPg = PG.builder().id(11L).owner(otherOwner).isActive(true).build();
        room = Room.builder().id(7L).roomNumber("101").capacity(1).currentOccupancy(0).pg(pg).build();

        when(userRepository.findByEmail("o@x.com")).thenReturn(Optional.of(owner));
        when(userRepository.findByEmail("o2@x.com")).thenReturn(Optional.of(otherOwner));
        when(userRepository.findByEmail("t@x.com")).thenReturn(Optional.of(tenantUser));
        when(pgRepository.findByOwnerIdAndIsActiveTrue(1L)).thenReturn(Optional.of(pg));
        when(pgRepository.findByOwnerIdAndIsActiveTrue(2L)).thenReturn(Optional.of(otherPg));
        when(roomRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(room));
        when(bookingRepository.save(any(RoomBooking.class))).thenAnswer(i -> i.getArgument(0));
        // lockBooking() first reads the user and room ids, then locks user -> room -> booking
        for (long id : new long[]{50L, 51L}) {
            when(bookingRepository.findUserId(id)).thenReturn(Optional.of(3L));
            when(bookingRepository.findRoomId(id)).thenReturn(Optional.of(7L));
        }
        when(userRepository.findByIdForUpdate(3L)).thenReturn(Optional.of(tenantUser));
    }

    private void expireAll() {
        for (Long id : service.expiredIds()) service.expireOne(id);
    }

    private BookingRequest req() {
        BookingRequest r = new BookingRequest();
        r.setRoomId(7L);
        return r;
    }

    private RoomBooking pending(LocalDateTime expires) {
        return RoomBooking.builder().id(50L).user(tenantUser).pg(pg).room(room)
                .moveInDate(clock.time().toLocalDate()).status(BookingStatus.PENDING).expiresAt(expires).build();
    }

    private BookingDecisionRequest decision(BookingStatus s) {
        BookingDecisionRequest d = new BookingDecisionRequest();
        d.setStatus(s);
        return d;
    }

    @Test
    void requestHoldsTheBedSoNobodyElseCanTakeIt() {
        RoomBookingResponse r = service.request("t@x.com", req());
        assertEquals(BookingStatus.PENDING, r.getStatus());
        assertEquals(1, room.getCurrentOccupancy());
        assertEquals(RoomStatus.FULLY_OCCUPIED, room.getStatus());
        assertTrue(r.getExpiresAt().isAfter(clock.time().plusHours(47)));
    }

    @Test
    void fullRoomsInstantPgsAndSecondRequestsAreRefused() {
        room.setCurrentOccupancy(1);
        assertThrows(BusinessException.class, () -> service.request("t@x.com", req()));

        room.setCurrentOccupancy(0);
        pg.setBookingMode(BookingMode.INSTANT);
        assertThrows(BusinessException.class, () -> service.request("t@x.com", req()));

        pg.setBookingMode(BookingMode.ON_REQUEST);
        when(bookingRepository.existsByUserIdAndStatus(3L, BookingStatus.PENDING)).thenReturn(true);
        assertThrows(BusinessException.class, () -> service.request("t@x.com", req()));
        assertEquals(0, room.getCurrentOccupancy());
    }

    @Test
    void approveTurnsTheHoldIntoATenantWithoutChangingOccupancy() {
        room.setCurrentOccupancy(1);
        when(bookingRepository.findByIdForUpdate(50L)).thenReturn(Optional.of(pending(clock.time().plusHours(5))));

        RoomBookingResponse r = service.decide("o@x.com", 50L, decision(BookingStatus.APPROVED));

        assertEquals(BookingStatus.APPROVED, r.getStatus());
        assertEquals(1, room.getCurrentOccupancy());
        ArgumentCaptor<Tenant> t = ArgumentCaptor.forClass(Tenant.class);
        verify(tenantRepository).save(t.capture());
        assertSame(room, t.getValue().getRoom());
        assertSame(tenantUser, t.getValue().getUser());
    }

    @Test
    void declineAndCancelGiveTheBedBack() {
        room.setCurrentOccupancy(1);
        when(bookingRepository.findByIdForUpdate(50L)).thenReturn(Optional.of(pending(clock.time().plusHours(5))));
        service.decide("o@x.com", 50L, decision(BookingStatus.DECLINED));
        assertEquals(0, room.getCurrentOccupancy());
        verify(tenantRepository, never()).save(any());

        room.setCurrentOccupancy(1);
        when(bookingRepository.findByIdForUpdate(50L)).thenReturn(Optional.of(pending(clock.time().plusHours(5))));
        assertEquals(BookingStatus.CANCELLED, service.cancel("t@x.com", 50L).getStatus());
        assertEquals(0, room.getCurrentOccupancy());
    }

    @Test
    void lateApprovalReturnsExpiredAndTheBedIsReleased() {
        room.setCurrentOccupancy(1);
        RoomBooking b = pending(clock.time().minusMinutes(1));
        when(bookingRepository.findByIdForUpdate(50L)).thenReturn(Optional.of(b));

        // must NOT throw: an exception would roll the release back in a real transaction
        RoomBookingResponse r = assertDoesNotThrow(() -> service.decide("o@x.com", 50L, decision(BookingStatus.APPROVED)));

        assertEquals(BookingStatus.EXPIRED, r.getStatus());
        assertEquals(BookingStatus.EXPIRED, b.getStatus());
        assertEquals(0, room.getCurrentOccupancy());
        verify(tenantRepository, never()).save(any());
    }

    @Test
    void atTheExactExpiryMomentTheRequestIsStillValid() {
        room.setCurrentOccupancy(1);
        RoomBooking b = pending(clock.time()); // expires exactly now
        when(bookingRepository.findByIdForUpdate(50L)).thenReturn(Optional.of(b));
        assertEquals(BookingStatus.APPROVED, service.decide("o@x.com", 50L, decision(BookingStatus.APPROVED)).getStatus());

        RoomBooking late = pending(clock.time().minusNanos(1)); // one nanosecond ago
        when(bookingRepository.findByIdForUpdate(50L)).thenReturn(Optional.of(late));
        assertEquals(BookingStatus.EXPIRED, service.decide("o@x.com", 50L, decision(BookingStatus.APPROVED)).getStatus());
    }

    @Test
    void holdLastsExactly48HoursFromTheClock() {
        RoomBookingResponse r = service.request("t@x.com", req());
        assertEquals(clock.time().plusHours(48), r.getExpiresAt());
    }

    @Test
    void moveInDateWindowIsTodayToSixtyDays() {
        BookingRequest ok1 = req(); ok1.setMoveInDate(clock.time().toLocalDate());
        assertDoesNotThrow(() -> service.request("t@x.com", ok1));

        room.setCurrentOccupancy(0);
        BookingRequest ok2 = req(); ok2.setMoveInDate(clock.time().toLocalDate().plusDays(60));
        assertDoesNotThrow(() -> service.request("t@x.com", ok2));

        room.setCurrentOccupancy(0);
        BookingRequest yesterday = req(); yesterday.setMoveInDate(clock.time().toLocalDate().minusDays(1));
        BookingRequest tooFar = req(); tooFar.setMoveInDate(clock.time().toLocalDate().plusDays(61));
        assertThrows(BusinessException.class, () -> service.request("t@x.com", yesterday));
        assertThrows(BusinessException.class, () -> service.request("t@x.com", tooFar));
    }

    @Test
    void everyHoldIsReleasedExactlyOnce() {
        room.setCapacity(3);
        room.setCurrentOccupancy(2);          // this booking's hold + another person's tenant
        RoomBooking b = pending(clock.time().plusHours(5));
        when(bookingRepository.findByIdForUpdate(50L)).thenReturn(Optional.of(b));

        service.cancel("t@x.com", 50L);
        assertEquals(1, room.getCurrentOccupancy());

        // every later attempt (decline, cancel, scheduler) is refused and changes nothing
        assertThrows(BusinessException.class, () -> service.decide("o@x.com", 50L, decision(BookingStatus.DECLINED)));
        assertThrows(BusinessException.class, () -> service.cancel("t@x.com", 50L));
        clock.advance(java.time.Duration.ofHours(72));
        when(bookingRepository.findIdsByStatusAndExpiresAtBefore(eq(BookingStatus.PENDING), any())).thenReturn(List.of(50L));
        expireAll();
        assertEquals(1, room.getCurrentOccupancy());
        assertEquals(BookingStatus.CANCELLED, b.getStatus());
    }

    @Test
    void inconsistentOccupancyIsADataIntegrityFailureNotSilentlyIgnored() {
        room.setCurrentOccupancy(0); // a pending request holds a bed, so 0 is impossible
        RoomBooking b = pending(clock.time().plusHours(5));
        when(bookingRepository.findByIdForUpdate(50L)).thenReturn(Optional.of(b));

        assertThrows(IllegalStateException.class, () -> service.cancel("t@x.com", 50L)); // rolls the transaction back

        assertEquals(BookingStatus.PENDING, b.getStatus());   // not marked cancelled
        verify(bookingRepository, never()).save(any(RoomBooking.class));
        assertEquals(0, room.getCurrentOccupancy());           // never negative
    }

    @Test
    void anExpiredHoldIsReleasedBeforeANewRequestTakesTheBed() {
        room.setCurrentOccupancy(1); // 1 of 1 bed "held" by someone who never got an answer
        RoomBooking stale = pending(clock.time().minusHours(1));
        stale.setUser(User.builder().id(9L).email("old@x.com").build());
        when(bookingRepository.findByRoomIdAndStatusAndExpiresAtBefore(eq(7L), eq(BookingStatus.PENDING), any()))
                .thenReturn(List.of(stale));

        RoomBookingResponse r = service.request("t@x.com", req());

        assertEquals(BookingStatus.PENDING, r.getStatus());
        assertEquals(BookingStatus.EXPIRED, stale.getStatus());
        assertEquals(1, room.getCurrentOccupancy()); // old hold released, new hold taken
    }

    @Test
    void anotherOwnerCanNotDecideAndAnswerTwiceIsRefused() {
        RoomBooking b = pending(clock.time().plusHours(5));
        when(bookingRepository.findByIdForUpdate(50L)).thenReturn(Optional.of(b));
        assertThrows(UnauthorizedAccessException.class, () -> service.decide("o2@x.com", 50L, decision(BookingStatus.APPROVED)));

        b.setStatus(BookingStatus.DECLINED);
        assertThrows(BusinessException.class, () -> service.decide("o@x.com", 50L, decision(BookingStatus.APPROVED)));
    }

    @Test
    void schedulerReleasesOnlyStillPendingExpiredRequests() {
        room.setCurrentOccupancy(2);
        room.setCapacity(3);
        RoomBooking stale = pending(clock.time().minusHours(1));
        RoomBooking answeredMeanwhile = pending(clock.time().minusHours(1));
        answeredMeanwhile.setId(51L);
        answeredMeanwhile.setStatus(BookingStatus.APPROVED);
        when(bookingRepository.findIdsByStatusAndExpiresAtBefore(eq(BookingStatus.PENDING), any())).thenReturn(List.of(50L, 51L));
        when(bookingRepository.findByIdForUpdate(50L)).thenReturn(Optional.of(stale));
        when(bookingRepository.findByIdForUpdate(51L)).thenReturn(Optional.of(answeredMeanwhile));

        expireAll();

        assertEquals(BookingStatus.EXPIRED, stale.getStatus());
        assertEquals(BookingStatus.APPROVED, answeredMeanwhile.getStatus());
        assertEquals(1, room.getCurrentOccupancy()); // only one bed given back
    }

    @Test
    void everyBookingActionLocksUserThenRoomThenBooking() {
        RoomBooking b = pending(clock.time().plusHours(5));
        when(bookingRepository.findByIdForUpdate(50L)).thenReturn(Optional.of(b));

        List<String> locks = new java.util.ArrayList<>();
        when(userRepository.findByIdForUpdate(3L)).thenAnswer(i -> { locks.add("user"); return Optional.of(tenantUser); });
        when(roomRepository.findByIdForUpdate(7L)).thenAnswer(i -> { locks.add("room"); return Optional.of(room); });
        when(bookingRepository.findByIdForUpdate(50L)).thenAnswer(i -> { locks.add("booking"); return Optional.of(b); });

        service.decide("o@x.com", 50L, decision(BookingStatus.DECLINED));

        assertEquals(List.of("user", "room", "booking"), locks.subList(0, 3));
    }

    @Test
    void instantBookingIsRefusedOnAnOnRequestPg() {
        assertTrue(PgBookingPolicy.needsApproval(pg));
        pg.setBookingMode(null); // old rows
        assertFalse(PgBookingPolicy.needsApproval(pg));
    }
}
