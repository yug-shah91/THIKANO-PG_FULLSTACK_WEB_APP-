package com.pg.controller;

import com.pg.dto.request.LoginRequest;
import com.pg.dto.request.RegisterRequest;
import com.pg.dto.response.AuthResponse;
import com.pg.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication")
public class AuthController {

    private final AuthService authService;
    private final com.pg.security.LoginRateLimiter loginRateLimiter;

    @PostMapping("/register")
    @Operation(summary = "Owner self-registration")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.register(req));
    }

    // Requires a valid token (see SecurityConfig). Returns the CURRENT name/role from the database.
    @GetMapping("/me")
    @Operation(summary = "Current user, checked against the database")
    public ResponseEntity<AuthResponse> me(@AuthenticationPrincipal String email) {
        return ResponseEntity.ok(authService.me(email));
    }

    @PostMapping("/login")
    @Operation(summary = "Login — all roles")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest req,
                                              jakarta.servlet.http.HttpServletRequest http) {
        String ip = http.getRemoteAddr();
        // Check AND count this attempt in one atomic step, before the password is verified.
        // (Check-then-count as two steps would let simultaneous requests all pass the check.)
        loginRateLimiter.acquire(req.getEmail(), ip);
        AuthResponse res = authService.login(req);
        loginRateLimiter.recordSuccess(req.getEmail(), ip); // right password: attempts are given back
        return ResponseEntity.ok(res);
    }
}
