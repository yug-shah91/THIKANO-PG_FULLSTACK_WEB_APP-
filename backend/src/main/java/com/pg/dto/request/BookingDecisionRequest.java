package com.pg.dto.request;

import com.pg.entity.enums.BookingStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class BookingDecisionRequest {
    @NotNull(message = "Status is required")
    private BookingStatus status; // APPROVED or DECLINED

    @Size(max = 300, message = "Note is too long")
    private String ownerNote;
}
