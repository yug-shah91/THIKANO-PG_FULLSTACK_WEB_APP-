package com.pg.dto.response;

import com.pg.entity.enums.BookingStatus;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data @Builder
public class RoomBookingResponse {
    private Long id;
    private Long pgId;
    private String pgName;
    private String roomNumber;
    private BigDecimal monthlyRent;
    private String requesterName;
    private String requesterPhone;
    private LocalDate moveInDate;
    private BookingStatus status;
    private LocalDateTime expiresAt;   // the bed is held until then
    private String ownerNote;
    private String ownerPhone;         // only once approved
    private LocalDateTime createdAt;
}
