package com.pg.controller;

import com.pg.dto.request.VisitDecisionRequest;
import com.pg.dto.request.VisitRequestDto;
import com.pg.dto.response.VisitResponse;
import com.pg.service.VisitService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Tag(name = "PG Visits")
public class VisitController {

    private final VisitService visitService;

    // PUBLIC: the hourly slots people can pick (so the frontend never hard-codes them)
    @GetMapping("/public/visit-slots")
    @Operation(summary = "Hourly visit slots")
    public List<LocalTime> slots() {
        return VisitService.slots();
    }

    @PostMapping("/tenant/visits")
    @PreAuthorize("hasRole('TENANT')")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Ask to visit a PG in person")
    public ResponseEntity<VisitResponse> request(@AuthenticationPrincipal String email,
                                                 @Valid @RequestBody VisitRequestDto dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(visitService.request(email, dto));
    }

    @GetMapping("/tenant/visits")
    @PreAuthorize("hasRole('TENANT')")
    @SecurityRequirement(name = "bearerAuth")
    public List<VisitResponse> mine(@AuthenticationPrincipal String email) {
        return visitService.mine(email);
    }

    @PutMapping("/tenant/visits/{id}/cancel")
    @PreAuthorize("hasRole('TENANT')")
    @SecurityRequirement(name = "bearerAuth")
    public VisitResponse cancel(@AuthenticationPrincipal String email, @PathVariable Long id) {
        return visitService.cancel(email, id);
    }

    @GetMapping("/owner/visits")
    @PreAuthorize("hasRole('OWNER')")
    @SecurityRequirement(name = "bearerAuth")
    public List<VisitResponse> forOwner(@AuthenticationPrincipal String email) {
        return visitService.forOwner(email);
    }

    @GetMapping("/owner/visits/pending-count")
    @PreAuthorize("hasRole('OWNER')")
    @SecurityRequirement(name = "bearerAuth")
    public Map<String, Long> pendingCount(@AuthenticationPrincipal String email) {
        return Map.of("count", visitService.pendingCount(email));
    }

    @PutMapping("/owner/visits/{id}")
    @PreAuthorize("hasRole('OWNER')")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Confirm or decline a visit")
    public VisitResponse decide(@AuthenticationPrincipal String email, @PathVariable Long id,
                                @Valid @RequestBody VisitDecisionRequest req) {
        return visitService.decide(email, id, req);
    }
}
