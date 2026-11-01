package com.pg.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data @Builder
public class ReviewResponse {
    private Long id;
    private Integer rating;
    private String comment;
    private String reviewerName;   // first name + initial, e.g. "Asha K." (privacy)
    private LocalDateTime createdAt;
    private String ownerReply;
    private LocalDateTime repliedAt;
}
