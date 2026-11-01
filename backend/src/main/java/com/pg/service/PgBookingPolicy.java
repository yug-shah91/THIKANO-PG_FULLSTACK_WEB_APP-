package com.pg.service;

import com.pg.entity.PG;
import com.pg.entity.enums.BookingMode;

/** One place that answers "does this PG need the owner's approval?" (old rows have no mode = instant). */
public final class PgBookingPolicy {
    private PgBookingPolicy() {}

    public static BookingMode modeOf(PG pg) {
        return pg.getBookingMode() == null ? BookingMode.INSTANT : pg.getBookingMode();
    }

    public static boolean needsApproval(PG pg) {
        return modeOf(pg) == BookingMode.ON_REQUEST;
    }
}
