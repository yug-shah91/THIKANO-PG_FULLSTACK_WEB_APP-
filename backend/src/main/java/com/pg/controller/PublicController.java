package com.pg.controller;

import com.pg.dto.response.PublicPgResponse;
import com.pg.entity.enums.RoomType;
import com.pg.service.PublicPgService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

/** No login needed. See SecurityConfig: /api/public/** is open. */
@RestController
@RequestMapping("/api/public")
@RequiredArgsConstructor
@Tag(name = "Public PG search")
public class PublicController {

    private final PublicPgService publicPgService;
    private final com.pg.service.CostEstimateService costEstimateService;

    @GetMapping("/pgs")
    @Operation(summary = "Search PGs (optionally near a location)")
    public ResponseEntity<List<PublicPgResponse>> search(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) BigDecimal maxRent,
            @RequestParam(required = false) RoomType roomType,
            @RequestParam(defaultValue = "true") boolean onlyAvailable,
            @RequestParam(required = false) Double lat,
            @RequestParam(required = false) Double lng,
            @RequestParam(required = false) Double radiusKm,
            @RequestParam(required = false) String gender,
            @RequestParam(required = false) List<String> amenities) {
        return ResponseEntity.ok(
                publicPgService.search(q, maxRent, roomType, onlyAvailable, lat, lng, radiusKm, gender, amenities));
    }

    // True monthly cost: rent + required charges + the optional services the person ticked + their commute
    @GetMapping("/pgs/{id}/cost-estimate")
    @Operation(summary = "Estimated real monthly cost for one room")
    public ResponseEntity<com.pg.dto.response.CostEstimateResponse> costEstimate(
            @PathVariable Long id,
            @RequestParam Long roomId,
            @RequestParam(required = false) java.util.Set<Long> optional,
            @RequestParam(required = false) BigDecimal commutePerDay,
            @RequestParam(required = false) Integer commuteDays) {
        return ResponseEntity.ok(costEstimateService.estimate(id, roomId, optional, commutePerDay, commuteDays));
    }

    @GetMapping("/pgs/{id}")
    @Operation(summary = "PG details. The contact number is included only for logged-in users")
    public ResponseEntity<PublicPgResponse> detail(@PathVariable Long id, Authentication authentication) {
        boolean loggedIn = authentication != null
                && authentication.isAuthenticated()
                && !(authentication instanceof AnonymousAuthenticationToken);
        return ResponseEntity.ok(publicPgService.getDetail(id, loggedIn));
    }
}
