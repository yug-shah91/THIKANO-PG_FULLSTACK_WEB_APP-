package com.pg.dto.request;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class PGRequest {
    @NotBlank(message = "PG name is required")
    private String name;

    @NotBlank(message = "Address is required")
    private String address;

    @NotBlank(message = "City is required")
    private String city;

    private String description;
    private String contactPhone;

    // Optional location (send both or none)
    @DecimalMin(value = "-90", message = "Latitude must be between -90 and 90")
    @DecimalMax(value = "90", message = "Latitude must be between -90 and 90")
    private Double latitude;

    @DecimalMin(value = "-180", message = "Longitude must be between -180 and 180")
    @DecimalMax(value = "180", message = "Longitude must be between -180 and 180")
    private Double longitude;

    // Send true to REMOVE the saved location. (Leaving latitude/longitude out means "keep as is".)
    private Boolean clearCoordinates;

    @NotNull(message = "Base monthly rent is required")
    @Positive(message = "Rent must be positive")
    private BigDecimal baseMonthlyRent;

    // Optional overrides — defaults from application.properties if not provided
    @Min(value = 1, message = "Rent due day must be between 1 and 28")
    @Max(value = 28, message = "Rent due day must be between 1 and 28")
    private Integer rentDueDay;     // default 5
    @Min(value = 0, message = "Penalty percent must be between 0 and 100")
    @Max(value = 100, message = "Penalty percent must be between 0 and 100")
    private Integer penaltyPercent; // default 5

    // optional; null keeps the current mode (new PGs start INSTANT)
    private com.pg.entity.enums.BookingMode bookingMode;
    private com.pg.entity.enums.GenderPolicy genderPolicy; // ANY, MEN_ONLY or WOMEN_ONLY (empty = ANY)
}
