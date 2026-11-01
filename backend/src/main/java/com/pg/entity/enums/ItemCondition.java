package com.pg.entity.enums;

/** Ordered from best to worst: the number is used to compare check-in with check-out. */
public enum ItemCondition {
    GOOD(0), MINOR_DAMAGE(1), DAMAGED(2), MISSING(3);

    private final int severity;

    ItemCondition(int severity) { this.severity = severity; }

    public int severity() { return severity; }
}
