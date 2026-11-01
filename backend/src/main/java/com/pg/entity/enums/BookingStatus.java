package com.pg.entity.enums;

public enum BookingStatus {
    PENDING,    // waiting for the owner; the bed is held
    APPROVED,   // owner said yes: the person is now a tenant
    DECLINED,   // owner said no: bed released
    CANCELLED,  // tenant withdrew: bed released
    EXPIRED     // owner did not answer in time: bed released
}
