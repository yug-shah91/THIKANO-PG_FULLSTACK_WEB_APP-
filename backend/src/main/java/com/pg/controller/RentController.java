package com.pg.controller;

import com.pg.dto.request.RentPaymentRequest;
import com.pg.dto.response.RentRecordResponse;
import com.pg.service.RentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Tag(name = "Rent Management")
@SecurityRequirement(name = "bearerAuth")
public class RentController {

    private final RentService rentService;

    // OWNER: mark a rent record as paid
    @PutMapping("/owner/rent/{rentRecordId}/mark-paid")
    @PreAuthorize("hasRole('OWNER')")
    @Operation(summary = "Mark rent as paid — Owner only")
    public ResponseEntity<RentRecordResponse> markPaid(
            @AuthenticationPrincipal String email,
            @PathVariable Long rentRecordId,
            @jakarta.validation.Valid @RequestBody(required = false) RentPaymentRequest request) {
        return ResponseEntity.ok(rentService.markAsPaid(
                email, rentRecordId,
                request != null ? request : new RentPaymentRequest()));
    }

    // OWNER: view all pending rent
    @GetMapping("/owner/rent/pending")
    @PreAuthorize("hasRole('OWNER')")
    @Operation(summary = "Get all pending rent — Owner only")
    public ResponseEntity<List<RentRecordResponse>> getPendingRent(@AuthenticationPrincipal String email) {
        return ResponseEntity.ok(rentService.getPendingRentForPg(email));
    }

    // OWNER: view all overdue rent
    @GetMapping("/owner/rent/overdue")
    @PreAuthorize("hasRole('OWNER')")
    @Operation(summary = "Get all overdue rent — Owner only")
    public ResponseEntity<List<RentRecordResponse>> getOverdueRent(@AuthenticationPrincipal String email) {
        return ResponseEntity.ok(rentService.getOverdueRentForPg(email));
    }

    // OWNER: monthly report
    @GetMapping("/owner/rent/report")
    @PreAuthorize("hasRole('OWNER')")
    @Operation(summary = "Monthly rent report — Owner only")
    public ResponseEntity<List<RentRecordResponse>> getMonthlyReport(
            @AuthenticationPrincipal String email,
            @RequestParam int month,
            @RequestParam int year) {
        return ResponseEntity.ok(rentService.getMonthlyRentReport(email, month, year));
    }

    // OWNER: manual trigger for testing (generates current month's rent)
    @PostMapping("/owner/rent/generate")
    @PreAuthorize("hasRole('OWNER')")
    @Operation(summary = "Generate this month's rent for MY PG only")
    public ResponseEntity<String> triggerGeneration(@AuthenticationPrincipal String email) {
        // Only generates rent for THIS owner's PG
        return ResponseEntity.ok(rentService.generateRentForOwnerPg(email));
    }

    // TENANT: view own rent history
    @GetMapping("/tenant/rent")
    @PreAuthorize("hasRole('TENANT')")
    @Operation(summary = "My rent history — Tenant only")
    public ResponseEntity<List<RentRecordResponse>> getMyRentHistory(@AuthenticationPrincipal String email) {
        return ResponseEntity.ok(rentService.getMyRentHistory(email));
    }
}
