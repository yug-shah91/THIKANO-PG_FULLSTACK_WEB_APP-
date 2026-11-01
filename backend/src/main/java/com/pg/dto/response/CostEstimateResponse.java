package com.pg.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/** The "true monthly cost" of living in one room of one PG. */
@Data @Builder
public class CostEstimateResponse {
    private String pgName;
    private String roomNumber;
    private BigDecimal rent;
    private List<ChargeResponse> requiredCharges;   // always paid
    private List<ChargeResponse> chosenOptional;    // the ones the person ticked
    private BigDecimal commute;                     // their own commute estimate per month
    private BigDecimal monthlyTotal;                // rent + required + chosen + commute
    private BigDecimal advertisedRent;              // what the listing shows
    private BigDecimal extraOverRent;               // monthlyTotal - rent
    private List<ChargeResponse> oneTimeCharges;    // deposit, joining fee
    private BigDecimal oneTimeTotal;
    private BigDecimal firstMonthTotal;             // monthlyTotal + oneTimeTotal (money needed on day one)
}
