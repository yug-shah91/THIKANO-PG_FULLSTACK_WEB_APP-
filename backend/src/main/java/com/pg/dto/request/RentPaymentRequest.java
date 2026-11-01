package com.pg.dto.request;

import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class RentPaymentRequest {
    // Optional notes from owner when marking rent as paid
    @Size(max = 300, message = "Notes can be at most 300 characters")
    private String notes;
}
