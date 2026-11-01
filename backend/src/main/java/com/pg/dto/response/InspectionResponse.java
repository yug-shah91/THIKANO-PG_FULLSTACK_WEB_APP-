package com.pg.dto.response;

import com.pg.entity.enums.InspectionType;
import com.pg.entity.enums.ItemCondition;
import com.pg.entity.enums.Party;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data @Builder
public class InspectionResponse {
    private Long id;
    private Long tenantId;
    private String tenantName;
    private String roomNumber;
    private InspectionType type;
    private String note;
    private LocalDateTime createdAt;
    private LocalDateTime ownerConfirmedAt;
    private LocalDateTime tenantConfirmedAt;
    private boolean complete;            // both confirmed: locked for ever
    private Party viewer;                // who is looking (so the UI knows which buttons to show)
    private List<Item> items;
    private List<Photo> photos;

    @Data @Builder
    public static class Item {
        private Long id;
        private String area;
        private ItemCondition condition;
        private String note;
        private Party party;
        private LocalDateTime createdAt;
    }

    @Data @Builder
    public static class Photo {
        private Long id;
        private Long itemId;
        private String caption;
        private Party party;
        private LocalDateTime uploadedAt;
        private String sha256;
        private Long sizeBytes;
    }
}
