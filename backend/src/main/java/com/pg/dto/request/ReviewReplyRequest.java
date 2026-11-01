package com.pg.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ReviewReplyRequest {
    @NotBlank(message = "Write a reply")
    @Size(max = 500, message = "Reply is too long (max 500 characters)")
    private String reply;
}
