package com.pg.controller;

import com.pg.dto.request.RoomRequest;
import com.pg.dto.response.RoomResponse;
import com.pg.entity.enums.RoomType;
import com.pg.service.RoomService;
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
@RequestMapping("/api/owner/rooms")
@RequiredArgsConstructor
@PreAuthorize("hasRole('OWNER')")
@Tag(name = "Room Management")
@SecurityRequirement(name = "bearerAuth")
public class RoomController {

    private final RoomService roomService;

    @PostMapping
    @Operation(summary = "Add a new room to your PG")
    public ResponseEntity<RoomResponse> addRoom(
            @AuthenticationPrincipal String email,
            @Valid @RequestBody RoomRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(roomService.addRoom(email, request));
    }

    @GetMapping
    @Operation(summary = "Get all rooms in your PG")
    public ResponseEntity<List<RoomResponse>> getAllRooms(@AuthenticationPrincipal String email) {
        return ResponseEntity.ok(roomService.getAllRooms(email));
    }

    @GetMapping("/available")
    @Operation(summary = "Get available rooms only")
    public ResponseEntity<List<RoomResponse>> getAvailableRooms(@AuthenticationPrincipal String email) {
        return ResponseEntity.ok(roomService.getAvailableRooms(email));
    }

    @GetMapping("/type/{type}")
    @Operation(summary = "Get rooms by type (SINGLE, DOUBLE, TRIPLE, DORMITORY)")
    public ResponseEntity<List<RoomResponse>> getRoomsByType(
            @AuthenticationPrincipal String email,
            @PathVariable RoomType type) {
        return ResponseEntity.ok(roomService.getRoomsByType(email, type));
    }

    @PutMapping("/{roomId}")
    @Operation(summary = "Update room details")
    public ResponseEntity<RoomResponse> updateRoom(
            @AuthenticationPrincipal String email,
            @PathVariable Long roomId,
            @Valid @RequestBody RoomRequest request) {
        return ResponseEntity.ok(roomService.updateRoom(email, roomId, request));
    }

    @PutMapping("/{roomId}/maintenance")
    @Operation(summary = "Toggle room maintenance status")
    public ResponseEntity<RoomResponse> setMaintenance(
            @AuthenticationPrincipal String email,
            @PathVariable Long roomId,
            @RequestParam boolean underMaintenance) {
        return ResponseEntity.ok(roomService.setMaintenanceStatus(email, roomId, underMaintenance));
    }
}
