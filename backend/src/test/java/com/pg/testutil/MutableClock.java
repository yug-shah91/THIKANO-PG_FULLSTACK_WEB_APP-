package com.pg.testutil;

import java.time.*;

/** A clock tests can set and move, so "now" and expiry boundaries are exact. */
public class MutableClock extends Clock {
    private Instant now;
    private final ZoneId zone;

    public MutableClock(LocalDateTime start, ZoneId zone) {
        this.zone = zone;
        this.now = start.atZone(zone).toInstant();
    }

    public void advance(Duration d) { now = now.plus(d); }
    public LocalDateTime time() { return LocalDateTime.ofInstant(now, zone); }

    @Override public ZoneId getZone() { return zone; }
    @Override public Clock withZone(ZoneId z) { return this; }
    @Override public Instant instant() { return now; }
}
