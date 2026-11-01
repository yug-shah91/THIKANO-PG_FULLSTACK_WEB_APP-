package com.pg.dto.response;

import com.pg.entity.enums.VisitStatus;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Data
@Builder
public class VisitResponse {
    private Long id;
    private Long pgId;
    private String pgName;
    private String pgAddress;
    private String ownerPhone;   // only filled once the owner has confirmed
    private String visitorName;
    private String visitorPhone;
    private LocalDate visitDate;
    private LocalTime visitTime;
    private VisitStatus status;
    private String note;
    private String ownerNote;
    private LocalDateTime createdAt;
}
