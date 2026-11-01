package com.pg.dto.response;

import com.pg.entity.enums.RoomType;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * What the public (not logged in) pages may see about a PG.
 * Deliberately has NO owner email, no tenant data, no rent records.
 */
@Data @Builder
public class PublicPgResponse {
    private Long id;
    private String name;
    private String address;
    private String city;
    private String description;
    private BigDecimal startingRent;     // cheapest bookable room (or cheapest room if all are full)
    private Integer freeBeds;            // beds that can be booked right now
    private List<RoomType> roomTypes;    // types that have a free bed
    private com.pg.entity.enums.BookingMode bookingMode; // INSTANT or ON_REQUEST
    private com.pg.entity.enums.GenderPolicy genderPolicy; // ANY, MEN_ONLY, WOMEN_ONLY
    private List<String> amenities;      // distinct facilities across the rooms, lower case (wifi, ac, ...)
    private Long coverPhotoId;          // first gallery photo (search card)
    private List<Long> photoIds;         // detail page: whole gallery, cover first
    private Double ratingAvg;            // null when nobody reviewed yet
    private Integer reviewCount;
    private Double latitude;
    private Double longitude;
    private Double distanceKm;           // only when the caller sent their location

    // detail page only:
    private String ownerName;
    private String contactPhone;         // only when the caller is logged in
    private Integer rentDueDay;
    private Integer penaltyPercent;
    private List<PublicRoomResponse> rooms;
    private List<ChargeResponse> charges;   // extra costs the owner disclosed
}
