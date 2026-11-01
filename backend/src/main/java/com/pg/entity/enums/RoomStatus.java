package com.pg.entity.enums;

public enum RoomStatus {
    AVAILABLE,    // Has empty beds — can accept new tenants
    FULLY_OCCUPIED, // No empty beds
    UNDER_MAINTENANCE // Room is being repaired — no new tenants
}
