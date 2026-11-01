package com.pg.controller;

import com.pg.dto.request.ComplaintRequest;
import com.pg.dto.request.ComplaintUpdateRequest;
import com.pg.dto.response.ComplaintResponse;
import com.pg.entity.enums.ComplaintStatus;
import com.pg.service.ComplaintService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Tag(name = "Complaints")
@SecurityRequirement(name = "bearerAuth")
public class ComplaintController {

    private final ComplaintService complaintService;

    // TENANT: raise a new complaint
    @PostMapping("/tenant/complaints")
    @PreAuthorize("hasRole('TENANT')")
    @Operation(summary = "Raise complaint — Tenant only")
    public ResponseEntity<ComplaintResponse> raise(
            @AuthenticationPrincipal String email,
            @Valid @RequestBody ComplaintRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(complaintService.raiseComplaint(email, request));
    }

    // TENANT: view own complaints
    @GetMapping("/tenant/complaints")
    @PreAuthorize("hasRole('TENANT')")
    @Operation(summary = "My complaints — Tenant only")
    public ResponseEntity<List<ComplaintResponse>> getMyComplaints(@AuthenticationPrincipal String email) {
        return ResponseEntity.ok(complaintService.getMyComplaints(email));
    }

    // TENANT: close a resolved complaint
    @PutMapping("/tenant/complaints/{id}/close")
    @PreAuthorize("hasRole('TENANT')")
    @Operation(summary = "Close resolved complaint — Tenant only")
    public ResponseEntity<ComplaintResponse> closeComplaint(
            @AuthenticationPrincipal String email,
            @PathVariable Long id) {
        return ResponseEntity.ok(complaintService.closeMyComplaint(email, id));
    }

    // OWNER: view all complaints in their PG (paginated)
    @GetMapping("/owner/complaints")
    @PreAuthorize("hasRole('OWNER')")
    @Operation(summary = "Get all complaints — Owner only")
    public ResponseEntity<Page<ComplaintResponse>> getAllComplaints(
            @AuthenticationPrincipal String email,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(complaintService.getAllComplaintsForPg(email, pageable));
    }

    // OWNER: filter by status
    @GetMapping("/owner/complaints/status/{status}")
    @PreAuthorize("hasRole('OWNER')")
    @Operation(summary = "Get complaints by status — Owner only")
    public ResponseEntity<List<ComplaintResponse>> getByStatus(
            @AuthenticationPrincipal String email,
            @PathVariable ComplaintStatus status) {
        return ResponseEntity.ok(complaintService.getComplaintsByStatus(email, status));
    }

    // OWNER: update complaint status (acknowledge → resolve)
    @PutMapping("/owner/complaints/{id}/status")
    @PreAuthorize("hasRole('OWNER')")
    @Operation(summary = "Update complaint status — Owner only")
    public ResponseEntity<ComplaintResponse> updateStatus(
            @AuthenticationPrincipal String email,
            @PathVariable Long id,
            @Valid @RequestBody ComplaintUpdateRequest request) {
        return ResponseEntity.ok(complaintService.updateComplaintStatus(email, id, request));
    }
}
