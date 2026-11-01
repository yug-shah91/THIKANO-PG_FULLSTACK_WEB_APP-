package com.pg.entity.enums;

public enum BookingMode {
    INSTANT,     // a free bed is booked at once (default)
    ON_REQUEST   // tenant sends a request, the bed is held, the owner approves or declines
}
