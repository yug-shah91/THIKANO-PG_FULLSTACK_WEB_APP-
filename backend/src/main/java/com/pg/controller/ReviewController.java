package com.pg.controller;

import com.pg.dto.request.ReviewReplyRequest;
import com.pg.dto.request.ReviewRequest;
import com.pg.dto.response.ReviewResponse;
import com.pg.dto.response.ReviewStatusResponse;
import com.pg.dto.response.ReviewSummaryResponse;
import com.pg.service.ReviewService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Tag(name = "Reviews")
public class ReviewController {

    private final ReviewService service;

    // ---- public
    @GetMapping("/public/pgs/{pgId}/reviews")
    public Page<ReviewResponse> reviews(@PathVariable Long pgId,
                                        @RequestParam(defaultValue = "0") int page,
                                        @RequestParam(defaultValue = "10") int size) {
        return service.forPg(pgId, page, size);
    }

    @GetMapping("/public/pgs/{pgId}/review-summary")
    public ReviewSummaryResponse summary(@PathVariable Long pgId) {
        return service.summary(pgId);
    }

    // ---- tenant
    @GetMapping("/tenant/review")
    @PreAuthorize("hasRole('TENANT')")
    @SecurityRequirement(name = "bearerAuth")
    public ReviewStatusResponse status(@AuthenticationPrincipal String email) {
        return service.status(email);
    }

    @PutMapping("/tenant/review")
    @PreAuthorize("hasRole('TENANT')")
    @SecurityRequirement(name = "bearerAuth")
    public ReviewResponse save(@AuthenticationPrincipal String email, @Valid @RequestBody ReviewRequest req) {
        return service.save(email, req);
    }

    @DeleteMapping("/tenant/review")
    @PreAuthorize("hasRole('TENANT')")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal String email) {
        service.delete(email);
        return ResponseEntity.noContent().build();
    }

    // ---- owner
    @GetMapping("/owner/reviews")
    @PreAuthorize("hasRole('OWNER')")
    @SecurityRequirement(name = "bearerAuth")
    public Page<ReviewResponse> forOwner(@AuthenticationPrincipal String email,
                                         @RequestParam(defaultValue = "0") int page,
                                         @RequestParam(defaultValue = "10") int size) {
        return service.forOwner(email, page, size);
    }

    @PutMapping("/owner/reviews/{id}/reply")
    @PreAuthorize("hasRole('OWNER')")
    @SecurityRequirement(name = "bearerAuth")
    public ReviewResponse reply(@AuthenticationPrincipal String email, @PathVariable Long id,
                                @Valid @RequestBody ReviewReplyRequest req) {
        return service.reply(email, id, req);
    }
}
