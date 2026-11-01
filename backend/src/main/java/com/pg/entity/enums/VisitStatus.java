package com.pg.entity.enums;

public enum VisitStatus {
    PENDING,    // tenant asked, owner has not answered yet
    CONFIRMED,  // owner said yes
    DECLINED,   // owner said no (or cancelled a confirmed visit)
    CANCELLED   // tenant cancelled
}
