package com.pg.dto.response;

import lombok.Builder;
import lombok.Data;

import java.util.Map;

@Data @Builder
public class ReviewSummaryResponse {
    private Double average;               // null when there are no reviews
    private long count;
    private Map<Integer, Long> distribution; // stars -> how many (1..5, all present)
}
