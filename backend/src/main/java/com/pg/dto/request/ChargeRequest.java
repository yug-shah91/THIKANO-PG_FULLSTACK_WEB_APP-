package com.pg.dto.request;

import com.pg.entity.enums.ChargeKind;
import jakarta.validation.constraints.*;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class ChargeRequest {

    @NotBlank(message = "Name is required")
    @Size(max = 60, message = "Name is too long")
    private String name;

    @NotNull(message = "Amount is required")
    @DecimalMin(value = "0", message = "Amount can not be negative")
    @DecimalMax(value = "100000", message = "Amount is too large")
    private BigDecimal amount;

    @NotNull(message = "Type is required")
    private ChargeKind kind;

    @Size(max = 120, message = "Note is too long")
    private String note;
}
