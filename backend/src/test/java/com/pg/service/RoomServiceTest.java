package com.pg.service;

import com.pg.dto.request.RoomRequest;
import com.pg.dto.response.RoomResponse;
import com.pg.entity.*;
import com.pg.entity.enums.Role;
import com.pg.entity.enums.RoomStatus;
import com.pg.entity.enums.RoomType;
import com.pg.exception.BusinessException;
import com.pg.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RoomServiceTest {

    @Mock RoomRepository roomRepository;
    @Mock PGRepository pgRepository;
    @Mock UserRepository userRepository;
    @InjectMocks RoomService service;

    private static final String OWNER_EMAIL = "owner@example.com";
    private PG pg;
    private Room room;

    @BeforeEach
    void setUp() {
        User owner = User.builder().id(1L).email(OWNER_EMAIL).role(Role.OWNER).build();
        pg = PG.builder().id(10L).name("PG").owner(owner).baseMonthlyRent(new BigDecimal("4000")).build();
        room = Room.builder().id(7L).roomNumber("101").type(RoomType.DOUBLE).capacity(2).currentOccupancy(1)
                .monthlyRent(new BigDecimal("6000")).floorNumber(1).amenities("AC")
                .status(RoomStatus.AVAILABLE).pg(pg).build();
        when(userRepository.findByEmail(OWNER_EMAIL)).thenReturn(Optional.of(owner));
        when(pgRepository.findByOwnerIdAndIsActiveTrue(1L)).thenReturn(Optional.of(pg));
        when(roomRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(room));
        when(roomRepository.save(any(Room.class))).thenAnswer(i -> i.getArgument(0));
    }

    private RoomRequest request(String number, RoomType type, int capacity) {
        RoomRequest r = new RoomRequest();
        r.setRoomNumber(number);
        r.setType(type);
        r.setCapacity(capacity);
        return r;
    }

    @Test
    void updateAppliesEveryEditableFieldTheFormSends() {
        RoomRequest r = request(" 102 ", RoomType.TRIPLE, 3);
        r.setMonthlyRent(new BigDecimal("7000"));
        r.setFloorNumber(2);
        r.setAmenities("AC, bath");

        RoomResponse response = service.updateRoom(OWNER_EMAIL, 7L, r);

        assertEquals("102", room.getRoomNumber());
        assertEquals(RoomType.TRIPLE, room.getType());
        assertEquals(3, room.getCapacity());
        assertEquals(0, new BigDecimal("7000").compareTo(room.getMonthlyRent()));
        assertEquals(2, room.getFloorNumber());
        assertEquals("AC, bath", room.getAmenities());
        assertEquals(2, response.getAvailableBeds());
    }

    @Test
    void blankRentOnUpdateMeansPgBaseRentSameAsWhenCreating() {
        service.updateRoom(OWNER_EMAIL, 7L, request("101", RoomType.DOUBLE, 2));
        assertEquals(0, new BigDecimal("4000").compareTo(room.getMonthlyRent()));
    }

    @Test
    void emptyFloorAndAmenitiesAreCleared() {
        RoomRequest r = request("101", RoomType.DOUBLE, 2);
        r.setAmenities("   ");
        service.updateRoom(OWNER_EMAIL, 7L, r);
        assertNull(room.getFloorNumber());
        assertNull(room.getAmenities());
    }

    @Test
    void capacityCanNotDropBelowCurrentTenants() {
        room.setCurrentOccupancy(2);
        assertThrows(BusinessException.class,
                () -> service.updateRoom(OWNER_EMAIL, 7L, request("101", RoomType.SINGLE, 1)));
        assertEquals(2, room.getCapacity());
    }

    @Test
    void increasingCapacityOfAFullRoomMakesItAvailableAgain() {
        room.setCurrentOccupancy(2);
        room.setStatus(RoomStatus.FULLY_OCCUPIED);
        service.updateRoom(OWNER_EMAIL, 7L, request("101", RoomType.TRIPLE, 3));
        assertEquals(RoomStatus.AVAILABLE, room.getStatus());
    }

    @Test
    void renamingToAnExistingRoomNumberIsRejected() {
        Room other = Room.builder().id(8L).roomNumber("102").capacity(1).pg(pg).build();
        when(roomRepository.findByPgIdAndRoomNumber(10L, "102")).thenReturn(Optional.of(other));
        assertThrows(BusinessException.class,
                () -> service.updateRoom(OWNER_EMAIL, 7L, request("102", RoomType.DOUBLE, 2)));
        assertEquals("101", room.getRoomNumber());
    }

    @Test
    void leavingMaintenanceOnAFullRoomDoesNotMarkItAvailable() {
        room.setCurrentOccupancy(2);
        room.setStatus(RoomStatus.UNDER_MAINTENANCE);

        service.setMaintenanceStatus(OWNER_EMAIL, 7L, false);

        assertEquals(RoomStatus.FULLY_OCCUPIED, room.getStatus());
    }

    @Test
    void roomWithTenantsCanNotGoUnderMaintenance() {
        assertThrows(BusinessException.class, () -> service.setMaintenanceStatus(OWNER_EMAIL, 7L, true));
        assertEquals(RoomStatus.AVAILABLE, room.getStatus());
    }

    @Test
    void emptyRoomCanGoUnderMaintenance() {
        room.setCurrentOccupancy(0);
        service.setMaintenanceStatus(OWNER_EMAIL, 7L, true);
        assertEquals(RoomStatus.UNDER_MAINTENANCE, room.getStatus());
    }
}
