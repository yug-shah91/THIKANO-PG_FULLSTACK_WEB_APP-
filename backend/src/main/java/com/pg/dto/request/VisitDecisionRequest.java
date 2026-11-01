package com.pg.dto.request;

import com.pg.entity.enums.VisitStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class VisitDecisionRequest {

    @NotNull(message = "Status is required")
    private VisitStatus status; // CONFIRMED or DECLINED

    @Size(max = 300, message = "Note is too long")
    private String ownerNote;
}
