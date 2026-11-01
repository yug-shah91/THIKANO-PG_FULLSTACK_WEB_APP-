package com.pg.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ReviewRequest {
    @NotNull(message = "Choose a rating from 1 to 5")
    @Min(value = 1, message = "Rating must be 1 to 5")
    @Max(value = 5, message = "Rating must be 1 to 5")
    private Integer rating;

    @Size(max = 1000, message = "Review is too long (max 1000 characters)")
    private String comment;
}
