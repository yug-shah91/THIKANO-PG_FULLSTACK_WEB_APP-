package com.pg.controller;

import com.pg.dto.request.PGRequest;
import com.pg.dto.response.DashboardResponse;
import com.pg.dto.response.PGResponse;
import com.pg.service.PGService;
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

import java.util.List;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Tag(name = "PG Management")
@SecurityRequirement(name = "bearerAuth")
public class PGController {

    private final PGService pgService;

    // OWNER: Register their PG for the first time
    @PostMapping("/owner/pg")
    @PreAuthorize("hasRole('OWNER')")
    @Operation(summary = "Register PG — Owner only")
    public ResponseEntity<PGResponse> registerPg(
            @AuthenticationPrincipal String email,
            @Valid @RequestBody PGRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(pgService.registerPg(email, request));
    }

    // OWNER: View their PG details
    @GetMapping("/owner/pg")
    @PreAuthorize("hasRole('OWNER')")
    @Operation(summary = "Get my PG — Owner only")
    public ResponseEntity<PGResponse> getMyPg(@AuthenticationPrincipal String email) {
        return ResponseEntity.ok(pgService.getMyPg(email));
    }

    // OWNER: Update their PG details
    @PutMapping("/owner/pg")
    @PreAuthorize("hasRole('OWNER')")
    @Operation(summary = "Update my PG — Owner only")
    public ResponseEntity<PGResponse> updateMyPg(
            @AuthenticationPrincipal String email,
            @Valid @RequestBody PGRequest request) {
        return ResponseEntity.ok(pgService.updateMyPg(email, request));
    }

    // OWNER: Get dashboard summary stats
    @GetMapping("/owner/dashboard")
    @PreAuthorize("hasRole('OWNER')")
    @Operation(summary = "Owner dashboard stats")
    public ResponseEntity<DashboardResponse> getDashboard(@AuthenticationPrincipal String email) {
        return ResponseEntity.ok(pgService.getDashboard(email));
    }

    // SUPER_ADMIN: See all PGs on the platform
    @GetMapping("/admin/pgs")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Operation(summary = "All PGs — SuperAdmin only")
    public ResponseEntity<List<PGResponse>> getAllPgs() {
        return ResponseEntity.ok(pgService.getAllPgs());
    }
}
