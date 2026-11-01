package com.pg.dto.response;

import lombok.Builder;
import lombok.Data;

/** What the tenant's screen needs: may I review, and what did I write already? */
@Data @Builder
public class ReviewStatusResponse {
    private boolean eligible;
    private String reason;        // why not, when eligible is false
    private Long pgId;
    private String pgName;
    private ReviewResponse review; // my existing review, if any
}
