package com.pg.entity.enums;

public enum ChargeKind {
    REQUIRED_MONTHLY,  // everyone pays it every month (e.g. maintenance, electricity estimate)
    OPTIONAL_MONTHLY,  // tenant chooses it (e.g. meals, laundry, AC)
    ONE_TIME           // paid once (e.g. refundable security deposit, joining fee)
}
