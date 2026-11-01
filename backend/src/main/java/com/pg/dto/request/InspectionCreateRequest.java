package com.pg.dto.request;

import com.pg.entity.enums.InspectionType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class InspectionCreateRequest {
    @NotNull(message = "Tenant is required")
    private Long tenantId;

    @NotNull(message = "Type is required")
    private InspectionType type;

    @Size(max = 1000, message = "Note is too long")
    private String note;
}
