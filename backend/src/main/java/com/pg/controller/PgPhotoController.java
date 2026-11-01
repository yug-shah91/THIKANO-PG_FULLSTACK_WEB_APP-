package com.pg.controller;

import com.pg.dto.response.PgPhotoResponse;
import com.pg.service.PgPhotoService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Duration;
import java.util.List;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Tag(name = "PG photos")
public class PgPhotoController {

    private final PgPhotoService service;

    // ---- owner
    @GetMapping("/owner/pg-photos")
    @PreAuthorize("hasRole('OWNER')")
    @SecurityRequirement(name = "bearerAuth")
    public List<PgPhotoResponse> list(@AuthenticationPrincipal String email) {
        return service.list(email);
    }

    @PostMapping(value = "/owner/pg-photos", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('OWNER')")
    @SecurityRequirement(name = "bearerAuth")
    public List<PgPhotoResponse> add(@AuthenticationPrincipal String email,
                                     @RequestParam("file") MultipartFile file,
                                     @RequestParam(required = false) String caption) throws IOException {
        return service.add(email, file.getBytes(), caption);
    }

    @PutMapping("/owner/pg-photos/{id}/cover")
    @PreAuthorize("hasRole('OWNER')")
    @SecurityRequirement(name = "bearerAuth")
    public List<PgPhotoResponse> cover(@AuthenticationPrincipal String email, @PathVariable Long id) {
        return service.makeCover(email, id);
    }

    @DeleteMapping("/owner/pg-photos/{id}")
    @PreAuthorize("hasRole('OWNER')")
    @SecurityRequirement(name = "bearerAuth")
    public List<PgPhotoResponse> remove(@AuthenticationPrincipal String email, @PathVariable Long id) {
        return service.remove(email, id);
    }

    // ---- public (see SecurityConfig: GET /api/public/** is open)
    @GetMapping("/public/pg-photos/{id}")
    public ResponseEntity<byte[]> publicPhoto(@PathVariable Long id) {
        PgPhotoService.PhotoFile f = service.publicPhoto(id);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(f.contentType()))
                .header("X-Content-Type-Options", "nosniff")
                .cacheControl(CacheControl.maxAge(Duration.ofHours(1)).cachePublic())
                .body(f.bytes());
    }
}
