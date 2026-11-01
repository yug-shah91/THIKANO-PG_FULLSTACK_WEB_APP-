package com.pg.controller;

import com.pg.dto.request.InspectionCreateRequest;
import com.pg.dto.request.InspectionItemRequest;
import com.pg.dto.response.InspectionComparisonResponse;
import com.pg.dto.response.InspectionResponse;
import com.pg.service.InspectionService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

/** Owner and tenant share these endpoints; the service decides what each may do. */
@RestController
@RequestMapping("/api/inspections")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('OWNER','TENANT')")
@Tag(name = "Move-in / move-out inspections")
@SecurityRequirement(name = "bearerAuth")
public class InspectionController {

    private final InspectionService service;

    @PostMapping
    @PreAuthorize("hasRole('OWNER')")
    public ResponseEntity<InspectionResponse> create(@AuthenticationPrincipal String email,
                                                     @Valid @RequestBody InspectionCreateRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(email, req));
    }

    // owner: ?tenantId=..   tenant: tenantId is ignored, they only ever get their own
    @GetMapping
    public List<InspectionResponse> list(@AuthenticationPrincipal String email,
                                         @RequestParam(required = false) Long tenantId) {
        return service.list(email, tenantId);
    }

    @GetMapping("/comparison")
    public InspectionComparisonResponse comparison(@AuthenticationPrincipal String email,
                                                   @RequestParam(required = false) Long tenantId) {
        return service.compare(email, tenantId);
    }

    @GetMapping("/{id}")
    public InspectionResponse get(@AuthenticationPrincipal String email, @PathVariable Long id) {
        return service.get(email, id);
    }

    @PostMapping("/{id}/items")
    public InspectionResponse addItem(@AuthenticationPrincipal String email, @PathVariable Long id,
                                      @Valid @RequestBody InspectionItemRequest req) {
        return service.addItem(email, id, req);
    }

    @DeleteMapping("/{id}/items/{itemId}")
    public InspectionResponse removeItem(@AuthenticationPrincipal String email, @PathVariable Long id,
                                         @PathVariable Long itemId) {
        return service.removeItem(email, id, itemId);
    }

    @PostMapping(value = "/{id}/photos", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public InspectionResponse addPhoto(@AuthenticationPrincipal String email, @PathVariable Long id,
                                       @RequestParam("file") MultipartFile file,
                                       @RequestParam(required = false) String caption,
                                       @RequestParam(required = false) Long itemId) throws IOException {
        return service.addPhoto(email, id, file.getBytes(), caption, itemId);
    }

    @PostMapping("/{id}/confirm")
    public InspectionResponse confirm(@AuthenticationPrincipal String email, @PathVariable Long id) {
        return service.confirm(email, id);
    }

    @GetMapping("/photos/{photoId}")
    public ResponseEntity<byte[]> photo(@AuthenticationPrincipal String email, @PathVariable Long photoId) {
        InspectionService.PhotoFile f = service.photo(email, photoId);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(f.contentType()))
                .header("X-Content-Type-Options", "nosniff")
                .cacheControl(CacheControl.noStore())
                .body(f.bytes());
    }
}
