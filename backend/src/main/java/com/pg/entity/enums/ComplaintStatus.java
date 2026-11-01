package com.pg.entity.enums;

public enum ComplaintStatus {
    OPEN,           // Tenant just raised it
    IN_PROGRESS,    // Owner acknowledged and working on it
    RESOLVED,       // Owner marked it resolved
    CLOSED          // Tenant confirmed resolution (or auto-closed after 7 days)
}
