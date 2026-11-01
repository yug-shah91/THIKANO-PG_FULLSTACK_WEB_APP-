package com.pg.service;

import com.pg.dto.request.BookingRequest;
import com.pg.dto.response.TenantResponse;
import com.pg.entity.*;
import com.pg.entity.enums.Role;
import com.pg.entity.enums.RoomStatus;
import com.pg.exception.BusinessException;
import com.pg.exception.UnauthorizedAccessException;
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
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TenantServiceTest {

    @Mock TenantRepository tenantRepository;
    @Mock UserRepository userRepository;
    @Mock PGRepository pgRepository;
    @Mock RoomRepository roomRepository;
    @Mock PasswordEncoder passwordEncoder;
    @Mock RoomBookingService roomBookingService;
    @org.mockito.Spy java.time.Clock clock = java.time.Clock.systemDefaultZone();
    @InjectMocks TenantService service;

    private static final String OWNER_EMAIL = "owner@example.com";
    private PG pg;
    private User tenantUser;

    @BeforeEach
    void setUp() {
        User owner = User.builder().id(1L).email(OWNER_EMAIL).role(Role.OWNER).build();
        pg = PG.builder().id(10L).name("PG").owner(owner).build();
        tenantUser = User.builder().id(2L).name("Tenant").email("t@example.com").role(Role.TENANT).build();
        when(userRepository.findByEmail(OWNER_EMAIL)).thenReturn(Optional.of(owner));
        when(userRepository.findByEmail("t@example.com")).thenReturn(Optional.of(tenantUser));
        when(pgRepository.findByOwnerIdAndIsActiveTrue(1L)).thenReturn(Optional.of(pg));
        when(tenantRepository.save(any(Tenant.class))).thenAnswer(i -> i.getArgument(0));
        when(roomRepository.save(any(Room.class))).thenAnswer(i -> i.getArgument(0));
    }

    private Room room(long id, int capacity, int occupancy) {
        Room room = Room.builder().id(id).roomNumber("R" + id).capacity(capacity)
                .currentOccupancy(occupancy).status(RoomStatus.AVAILABLE).pg(pg).build();
        room.refreshStatus();
        when(roomRepository.findByIdForUpdate(id)).thenReturn(Optional.of(room));
        return room;
    }

    private Tenant tenant(Room currentRoom, boolean active) {
        Tenant t = Tenant.builder().id(100L).user(tenantUser).pg(pg).room(currentRoom).isActive(active).build();
        when(tenantRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(t));
        return t;
    }

    @Test
    void allocatingTakesOneBedAndMarksTheRoomFullWhenItFillsUp() {
        Room target = room(7L, 2, 1);
        Tenant t = tenant(null, true);

        TenantResponse response = service.allocateRoom(OWNER_EMAIL, 100L, 7L);

        assertEquals(2, target.getCurrentOccupancy());
        assertEquals(RoomStatus.FULLY_OCCUPIED, target.getStatus());
        assertSame(target, t.getRoom());
        assertEquals("R7", response.getRoomNumber());
    }

    @Test
    void movingRoomsFreesTheOldBedAndTakesTheNewOne() {
        Room oldRoom = room(6L, 2, 2);
        Room newRoom = room(7L, 3, 0);
        Tenant t = tenant(oldRoom, true);

        service.allocateRoom(OWNER_EMAIL, 100L, 7L);

        assertEquals(1, oldRoom.getCurrentOccupancy());
        assertEquals(RoomStatus.AVAILABLE, oldRoom.getStatus());
        assertEquals(1, newRoom.getCurrentOccupancy());
        assertSame(newRoom, t.getRoom());
    }

    @Test
    void fullRoomIsRejectedAndNothingChanges() {
        Room full = room(7L, 1, 1);
        tenant(null, true);

        assertThrows(BusinessException.class, () -> service.allocateRoom(OWNER_EMAIL, 100L, 7L));

        assertEquals(1, full.getCurrentOccupancy());
        verify(roomRepository, never()).save(any());
    }

    @Test
    void checkedOutTenantCanNotBeAllocatedARoom() {
        Room free = room(7L, 2, 0);
        tenant(null, false);

        assertThrows(BusinessException.class, () -> service.allocateRoom(OWNER_EMAIL, 100L, 7L));

        assertEquals(0, free.getCurrentOccupancy());
    }

    @Test
    void allocatingToTheRoomTheTenantIsAlreadyInIsRejected() {
        Room same = room(7L, 2, 1);
        tenant(same, true);

        assertThrows(BusinessException.class, () -> service.allocateRoom(OWNER_EMAIL, 100L, 7L));

        assertEquals(1, same.getCurrentOccupancy()); // not counted twice
    }

    @Test
    void roomOfAnotherPgIsRejected() {
        PG otherPg = PG.builder().id(99L).name("Other").build();
        Room foreign = Room.builder().id(8L).roomNumber("X").capacity(2).currentOccupancy(0)
                .status(RoomStatus.AVAILABLE).pg(otherPg).build();
        when(roomRepository.findByIdForUpdate(8L)).thenReturn(Optional.of(foreign));
        tenant(null, true);

        assertThrows(UnauthorizedAccessException.class, () -> service.allocateRoom(OWNER_EMAIL, 100L, 8L));
        assertEquals(0, foreign.getCurrentOccupancy());
    }

    @Test
    void checkoutFreesTheBedButKeepsTheLoginSoTheTenantCanSeeHistoryAndBookAgain() {
        Room r = room(7L, 2, 2);
        Tenant t = tenant(r, true);

        service.checkoutTenant(OWNER_EMAIL, 100L);

        assertEquals(1, r.getCurrentOccupancy());
        assertEquals(RoomStatus.AVAILABLE, r.getStatus());
        assertNull(t.getRoom());
        assertFalse(t.getIsActive());
        assertTrue(tenantUser.getIsActive()); // only the STAY ended, the account still works
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void checkingOutTwiceDoesNotFreeTheBedTwice() {
        Room r = room(7L, 2, 2);
        tenant(r, true);

        service.checkoutTenant(OWNER_EMAIL, 100L);
        assertThrows(BusinessException.class, () -> service.checkoutTenant(OWNER_EMAIL, 100L));

        assertEquals(1, r.getCurrentOccupancy());
    }

    // ---------- tenant books a room by themselves ----------

    private BookingRequest booking(long roomId) {
        BookingRequest b = new BookingRequest();
        b.setRoomId(roomId);
        return b;
    }

    @Test
    void tenantBooksARoomWithAFreeBedInstantly() {
        Room r = room(7L, 2, 1);

        TenantResponse response = service.bookRoom("t@example.com", booking(7L));

        assertEquals(2, r.getCurrentOccupancy());
        assertEquals(RoomStatus.FULLY_OCCUPIED, r.getStatus());
        ArgumentCaptor<Tenant> saved = ArgumentCaptor.forClass(Tenant.class);
        verify(tenantRepository).save(saved.capture());
        assertSame(tenantUser, saved.getValue().getUser());
        assertEquals(LocalDate.now(), saved.getValue().getJoinDate());
        assertEquals("R7", response.getRoomNumber());
    }

    @Test
    void fullRoomCanNotBeBooked() {
        Room full = room(7L, 1, 1);

        assertThrows(BusinessException.class, () -> service.bookRoom("t@example.com", booking(7L)));

        assertEquals(1, full.getCurrentOccupancy());
        verify(tenantRepository, never()).save(any(Tenant.class));
    }

    @Test
    void roomUnderMaintenanceCanNotBeBooked() {
        Room r = room(7L, 2, 0);
        r.setStatus(RoomStatus.UNDER_MAINTENANCE);

        assertThrows(BusinessException.class, () -> service.bookRoom("t@example.com", booking(7L)));
        assertEquals(0, r.getCurrentOccupancy());
    }

    @Test
    void pgThatIsNotActiveCanNotBeBooked() {
        Room r = room(7L, 2, 0);
        pg.setIsActive(false);

        assertThrows(BusinessException.class, () -> service.bookRoom("t@example.com", booking(7L)));
        assertEquals(0, r.getCurrentOccupancy());
    }

    @Test
    void aFormerTenantWithNoActiveStayCanBookANewRoom() {
        Room r = room(7L, 2, 0);
        // findActiveByUserEmail is empty (the old stay is inactive), so booking is allowed
        TenantResponse res = service.bookRoom("t@example.com", booking(7L));
        assertEquals(1, r.getCurrentOccupancy());
        assertEquals("R7", res.getRoomNumber());
    }

    @Test
    void movingRoomsLocksTheLowerRoomIdFirstWhateverTheDirection() {
        Room low = room(5L, 2, 0);
        Room high = room(9L, 2, 1);
        tenant(high, true);       // moving 9 -> 5
        service.allocateRoom(OWNER_EMAIL, 100L, 5L);
        org.mockito.ArgumentCaptor<Long> locked = org.mockito.ArgumentCaptor.forClass(Long.class);
        verify(roomRepository, atLeast(2)).findByIdForUpdate(locked.capture());
        assertEquals(List.of(5L, 9L), locked.getAllValues().subList(0, 2)); // lower id first
        assertEquals(1, low.getCurrentOccupancy());
        assertEquals(0, high.getCurrentOccupancy());
    }

    @Test
    void secondBookingWithTheSameAccountIsRejected() {
        room(7L, 2, 0);
        when(tenantRepository.findActiveByUserEmail("t@example.com")).thenReturn(Optional.of(Tenant.builder().build()));

        assertThrows(BusinessException.class, () -> service.bookRoom("t@example.com", booking(7L)));
    }

    @Test
    void ownerAccountCanNotBook() {
        room(7L, 2, 0);
        assertThrows(UnauthorizedAccessException.class, () -> service.bookRoom(OWNER_EMAIL, booking(7L)));
    }

    @Test
    void moveInDateMustBeBetweenTodayAndSixtyDays() {
        BookingRequest past = booking(7L);
        past.setMoveInDate(LocalDate.now().minusDays(1));
        BookingRequest tooFar = booking(7L);
        tooFar.setMoveInDate(LocalDate.now().plusDays(61));

        assertThrows(BusinessException.class, () -> service.bookRoom("t@example.com", past));
        assertThrows(BusinessException.class, () -> service.bookRoom("t@example.com", tooFar));
    }
}
