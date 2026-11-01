package com.pg.controller;

import com.pg.dto.request.BookingRequest;
import com.pg.dto.request.TenantRequest;
import com.pg.dto.response.TenantResponse;
import com.pg.service.TenantService;
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
@Tag(name = "Tenant Management")
@SecurityRequirement(name = "bearerAuth")
public class TenantController {

    private final TenantService tenantService;

    // TENANT: book a room. Succeeds at once if a bed is free, otherwise 400 "fully occupied"
    @PostMapping("/tenant/bookings")
    @PreAuthorize("hasRole('TENANT')")
    @Operation(summary = "Book a room - instant, no owner approval")
    public ResponseEntity<TenantResponse> book(
            @AuthenticationPrincipal String email,
            @Valid @RequestBody BookingRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(tenantService.bookRoom(email, request));
    }

    // OWNER: register a new tenant into their PG
    @PostMapping("/owner/tenants")
    @PreAuthorize("hasRole('OWNER')")
    @Operation(summary = "Register new tenant — Owner only")
    public ResponseEntity<TenantResponse> registerTenant(
            @AuthenticationPrincipal String email,
            @Valid @RequestBody TenantRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(tenantService.registerTenant(email, request));
    }

    // OWNER: view all active tenants in their PG
    @GetMapping("/owner/tenants")
    @PreAuthorize("hasRole('OWNER')")
    @Operation(summary = "Get all tenants — Owner only")
    public ResponseEntity<List<TenantResponse>> getAllTenants(@AuthenticationPrincipal String email) {
        return ResponseEntity.ok(tenantService.getAllTenants(email));
    }

    // OWNER: search tenants by name
    @GetMapping("/owner/tenants/search")
    @PreAuthorize("hasRole('OWNER')")
    @Operation(summary = "Search tenants by name — Owner only")
    public ResponseEntity<List<TenantResponse>> searchTenants(
            @AuthenticationPrincipal String email,
            @RequestParam String name) {
        return ResponseEntity.ok(tenantService.searchTenants(email, name));
    }

    // OWNER: allocate or move tenant to a room
    @PutMapping("/owner/tenants/{tenantId}/allocate-room/{roomId}")
    @PreAuthorize("hasRole('OWNER')")
    @Operation(summary = "Allocate room to tenant — Owner only")
    public ResponseEntity<TenantResponse> allocateRoom(
            @AuthenticationPrincipal String email,
            @PathVariable Long tenantId,
            @PathVariable Long roomId) {
        return ResponseEntity.ok(tenantService.allocateRoom(email, tenantId, roomId));
    }

    // OWNER: checkout tenant (mark as inactive, free their room)
    @PutMapping("/owner/tenants/{tenantId}/checkout")
    @PreAuthorize("hasRole('OWNER')")
    @Operation(summary = "Checkout tenant — Owner only")
    public ResponseEntity<TenantResponse> checkoutTenant(
            @AuthenticationPrincipal String email,
            @PathVariable Long tenantId) {
        return ResponseEntity.ok(tenantService.checkoutTenant(email, tenantId));
    }

    // TENANT: view own profile
    @GetMapping("/tenant/profile")
    @PreAuthorize("hasRole('TENANT')")
    @Operation(summary = "Get my profile — Tenant only")
    public ResponseEntity<TenantResponse> getMyProfile(@AuthenticationPrincipal String email) {
        return ResponseEntity.ok(tenantService.getMyProfile(email));
    }
}
