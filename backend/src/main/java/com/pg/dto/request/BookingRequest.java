package com.pg.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;

@Data
public class BookingRequest {

    @NotNull(message = "Room is required")
    private Long roomId;

    // Optional: defaults to today. Must be within the next 60 days.
    private LocalDate moveInDate;

    @Size(max = 100, message = "Emergency contact name is too long")
    private String emergencyContactName;

    @Size(max = 15, message = "Emergency contact phone is too long")
    private String emergencyContactPhone;
}
