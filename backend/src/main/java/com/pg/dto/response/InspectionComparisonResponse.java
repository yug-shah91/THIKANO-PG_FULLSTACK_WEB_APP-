package com.pg.dto.response;

import com.pg.entity.enums.ItemCondition;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data @Builder
public class InspectionComparisonResponse {
    private Long tenantId;
    private String tenantName;
    private String roomNumber;
    private Long checkInId;
    private Long checkOutId;
    private boolean checkInComplete;
    private boolean checkOutComplete;
    private boolean checkOutMissing;
    private int possibleNewDamage;      // rows where the room looks worse than at move-in
    private List<Row> rows;

    public enum Change { UNCHANGED, WORSE, BETTER, ONLY_AT_CHECKOUT, NOT_RECORDED_AT_CHECKOUT }

    @Data @Builder
    public static class Row {
        private String area;
        private ItemCondition checkIn;
        private ItemCondition checkOut;
        private Change change;
        private boolean damageAlreadyAtMoveIn;   // tenant is not responsible for this one
        private boolean possibleNewDamage;
        private List<String> notes;              // "Owner (move-in): ..." lines
        private List<Long> checkInPhotoIds;
        private List<Long> checkOutPhotoIds;
    }
}
