package com.pg.controller;

import com.pg.dto.request.BookingDecisionRequest;
import com.pg.dto.request.BookingRequest;
import com.pg.dto.response.RoomBookingResponse;
import com.pg.service.RoomBookingService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/** Booking requests for PGs in ON_REQUEST mode. */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Tag(name = "Booking requests")
@SecurityRequirement(name = "bearerAuth")
public class RoomBookingController {

    private final RoomBookingService service;

    @PostMapping("/tenant/booking-requests")
    @PreAuthorize("hasRole('TENANT')")
    public ResponseEntity<RoomBookingResponse> request(@AuthenticationPrincipal String email,
                                                       @Valid @RequestBody BookingRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.request(email, req));
    }

    @GetMapping("/tenant/booking-requests")
    @PreAuthorize("hasRole('TENANT')")
    public List<RoomBookingResponse> mine(@AuthenticationPrincipal String email) {
        return service.mine(email);
    }

    @PutMapping("/tenant/booking-requests/{id}/cancel")
    @PreAuthorize("hasRole('TENANT')")
    public RoomBookingResponse cancel(@AuthenticationPrincipal String email, @PathVariable Long id) {
        return service.cancel(email, id);
    }

    @GetMapping("/owner/booking-requests")
    @PreAuthorize("hasRole('OWNER')")
    public org.springframework.data.domain.Page<RoomBookingResponse> forOwner(
            @AuthenticationPrincipal String email,
            @RequestParam(defaultValue = "true") boolean pending,   // true = waiting, false = answered
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return service.forOwner(email, pending, page, size);
    }

    @GetMapping("/owner/booking-requests/pending-count")
    @PreAuthorize("hasRole('OWNER')")
    public Map<String, Long> pendingCount(@AuthenticationPrincipal String email) {
        return Map.of("count", service.pendingCount(email));
    }

    @PutMapping("/owner/booking-requests/{id}")
    @PreAuthorize("hasRole('OWNER')")
    public RoomBookingResponse decide(@AuthenticationPrincipal String email, @PathVariable Long id,
                                      @Valid @RequestBody BookingDecisionRequest req) {
        return service.decide(email, id, req);
    }
}
