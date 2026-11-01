package com.pg.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalTime;

@Data
public class VisitRequestDto {

    @NotNull(message = "PG is required")
    private Long pgId;

    @NotNull(message = "Pick a day")
    private LocalDate visitDate;

    @NotNull(message = "Pick a time")
    private LocalTime visitTime;

    @Size(max = 300, message = "Note is too long")
    private String note;
}
