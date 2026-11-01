package com.pg.controller;

import com.pg.dto.request.ChargeRequest;
import com.pg.dto.response.ChargeResponse;
import com.pg.service.ChargeService;
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
@RequestMapping("/api/owner/charges")
@RequiredArgsConstructor
@PreAuthorize("hasRole('OWNER')")
@Tag(name = "PG charges")
@SecurityRequirement(name = "bearerAuth")
public class ChargeController {

    private final ChargeService chargeService;

    @GetMapping
    public List<ChargeResponse> list(@AuthenticationPrincipal String email) {
        return chargeService.list(email);
    }

    @PostMapping
    public ResponseEntity<ChargeResponse> add(@AuthenticationPrincipal String email, @Valid @RequestBody ChargeRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(chargeService.add(email, req));
    }

    @PutMapping("/{id}")
    public ChargeResponse update(@AuthenticationPrincipal String email, @PathVariable Long id, @Valid @RequestBody ChargeRequest req) {
        return chargeService.update(email, id, req);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal String email, @PathVariable Long id) {
        chargeService.delete(email, id);
        return ResponseEntity.noContent().build();
    }
}
