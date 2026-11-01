package com.pg.entity;

import com.pg.entity.enums.RoomStatus;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RoomTest {

    @Test
    void builderStartsWithZeroOccupancy() {
        Room room = Room.builder().capacity(2).build();
        assertEquals(0, room.getCurrentOccupancy());
    }

    @Test
    void fullRoomBecomesFullyOccupied() {
        Room room = Room.builder().capacity(2).currentOccupancy(2).status(RoomStatus.AVAILABLE).build();
        room.refreshStatus();
        assertEquals(RoomStatus.FULLY_OCCUPIED, room.getStatus());
    }

    @Test
    void roomWithFreeBedBecomesAvailable() {
        Room room = Room.builder().capacity(3).currentOccupancy(1).status(RoomStatus.FULLY_OCCUPIED).build();
        room.refreshStatus();
        assertEquals(RoomStatus.AVAILABLE, room.getStatus());
    }

    @Test
    void maintenanceIsNeverOverwrittenByRefresh() {
        Room room = Room.builder().capacity(3).currentOccupancy(0).status(RoomStatus.UNDER_MAINTENANCE).build();
        room.refreshStatus();
        assertEquals(RoomStatus.UNDER_MAINTENANCE, room.getStatus());
    }
}
