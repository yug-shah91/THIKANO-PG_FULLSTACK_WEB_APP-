package com.pg.dto.response;

import com.pg.entity.enums.ChargeKind;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data @Builder
public class ChargeResponse {
    private Long id;
    private String name;
    private BigDecimal amount;
    private ChargeKind kind;
    private String note;
}
