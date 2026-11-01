package com.pg.dto.request;

import com.pg.entity.enums.ComplaintStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ComplaintUpdateRequest {

    @NotNull(message = "Status is required")
    private ComplaintStatus status;

    @Size(max = 400, message = "Response can be at most 400 characters")
    private String ownerResponse; // owner's note when updating status
}
