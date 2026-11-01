package com.pg.dto.response;

import com.pg.entity.enums.RoomType;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data @Builder
public class PublicRoomResponse {
    private Long id;
    private String roomNumber;
    private RoomType type;
    private Integer floorNumber;
    private Integer capacity;
    private Integer availableBeds;
    private BigDecimal monthlyRent;
    private String amenities;
    private Boolean bookable;   // false = full, can not be booked
}
