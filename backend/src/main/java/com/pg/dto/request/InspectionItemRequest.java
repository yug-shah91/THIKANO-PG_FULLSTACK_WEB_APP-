package com.pg.dto.request;

import com.pg.entity.enums.ItemCondition;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class InspectionItemRequest {
    @NotBlank(message = "Say which part of the room (e.g. Window)")
    @Size(max = 60, message = "Name is too long")
    private String area;

    @NotNull(message = "Condition is required")
    private ItemCondition condition;

    @Size(max = 300, message = "Note is too long")
    private String note;
}
