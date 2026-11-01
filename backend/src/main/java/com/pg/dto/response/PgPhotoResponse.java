package com.pg.dto.response;

import lombok.Builder;
import lombok.Data;

@Data @Builder
public class PgPhotoResponse {
    private Long id;
    private String caption;
    private Integer sortOrder;
    private Long sizeBytes;
    private boolean cover;
}
