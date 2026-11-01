package com.pg.service;

import com.pg.dto.request.BookingRequest;
import com.pg.dto.request.TenantRequest;
import com.pg.dto.response.TenantResponse;
import com.pg.entity.*;
import com.pg.entity.enums.Role;
import com.pg.entity.enums.RoomStatus;
import com.pg.exception.BusinessException;
import com.pg.exception.ResourceNotFoundException;
import com.pg.exception.UnauthorizedAccessException;
import com.pg.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Objects;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class TenantService {

    private final TenantRepository tenantRepository;
    private final UserRepository userRepository;
    private final PGRepository pgRepository;
    private final RoomRepository roomRepository;
    private final PasswordEncoder passwordEncoder;
    private final RoomBookingService roomBookingService;
    private final Clock clock; // injected so tests can set "today"

    private PG getOwnerPg(String ownerEmail) {
        User owner = userRepository.findByEmail(ownerEmail)
                .orElseThrow(() -> new ResourceNotFoundException("Owner not found"));
        return pgRepository.findByOwnerIdAndIsActiveTrue(owner.getId())
                .orElseThrow(() -> new ResourceNotFoundException("No active PG found."));
    }


    @Transactional
    public TenantResponse registerTenant(String ownerEmail, TenantRequest request) {
        PG pg = getOwnerPg(ownerEmail);
        String email = request.getEmail().trim().toLowerCase();

        if (userRepository.existsByEmail(email)) {
            throw new BusinessException("Email already registered: " + email);
        }

        User user = User.builder()
                .name(request.getName())
                .email(email)
                .password(passwordEncoder.encode(request.getPassword()))
                .phone(request.getPhone())
                .role(Role.TENANT)
                .build();
        userRepository.save(user);


        Tenant tenant = Tenant.builder()
                .user(user)
                .pg(pg)
                .joinDate(request.getJoinDate() != null ? request.getJoinDate() : LocalDate.now(clock))
                .expectedLeaveDate(request.getExpectedLeaveDate())
                .emergencyContactName(request.getEmergencyContactName())
                .emergencyContactPhone(request.getEmergencyContactPhone())
                .idProofType(request.getIdProofType())
                .idProofNumber(request.getIdProofNumber())
                .build();


        // If anything below throws, the whole transaction (including the new user) is rolled back
        if (request.getRoomId() != null) {
            assignRoomToTenant(tenant, request.getRoomId(), pg);
        }

        return toResponse(tenantRepository.save(tenant));
    }


    /**
     * INSTANT mode (the default): a tenant books a room by themselves. No owner approval:
     * if the room has a free bed the booking succeeds immediately, otherwise it is refused.
     * PGs in ON_REQUEST mode use RoomBookingService instead.
     * The room row is locked, so two people can not take the same last bed.
     */
    @Transactional
    public TenantResponse bookRoom(String tenantEmail, BookingRequest request) {
        User user = userRepository.findByEmail(tenantEmail)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        if (user.getRole() != Role.TENANT) {
            throw new UnauthorizedAccessException("Only tenant accounts can book a room.");
        }
        // Booking POLICY: instant. A free bed is taken immediately, no owner approval
        // (decided on purpose; see README). Lock this person first so two simultaneous
        // clicks can not create two bookings, then lock the room so two people can not
        // take the last bed.
        userRepository.findByIdForUpdate(user.getId());
        if (tenantRepository.findActiveByUserEmail(tenantEmail).isPresent()) {
            throw new BusinessException("You already have a booking. Contact your PG owner to change it.");
        }

        LocalDate today = LocalDate.now(clock);
        LocalDate moveIn = request.getMoveInDate() != null ? request.getMoveInDate() : today;
        if (moveIn.isBefore(today) || moveIn.isAfter(today.plusDays(60))) {
            throw new BusinessException("Move-in date must be within the next 60 days.");
        }

        Room room = roomRepository.findByIdForUpdate(request.getRoomId())
                .orElseThrow(() -> new ResourceNotFoundException("Room not found: " + request.getRoomId()));
        PG pg = room.getPg();
        if (!Boolean.TRUE.equals(pg.getIsActive())) {
            throw new BusinessException("This PG is not accepting bookings.");
        }
        if (PgBookingPolicy.needsApproval(pg)) {
            throw new BusinessException("This PG needs the owner's approval. Send a booking request instead.");
        }

        Tenant tenant = Tenant.builder()
                .user(user)
                .pg(pg)
                .joinDate(moveIn)
                .emergencyContactName(request.getEmergencyContactName())
                .emergencyContactPhone(request.getEmergencyContactPhone())
                .build();

        // Same rules as the owner's allocation: not in maintenance, must have a free bed
        assignRoomToTenant(tenant, room.getId(), pg);

        return toResponse(tenantRepository.save(tenant));
    }

    @Transactional
    public TenantResponse allocateRoom(String ownerEmail, Long tenantId, Long roomId) {
        PG pg = getOwnerPg(ownerEmail);

        // Lock the tenant row: two clicks / two requests can not allocate the same tenant twice
        Tenant tenant = tenantRepository.findByIdForUpdate(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant not found: " + tenantId));

        if (!tenant.getPg().getId().equals(pg.getId())) {
            throw new UnauthorizedAccessException("Tenant does not belong to your PG.");
        }

        // A checked-out tenant must never take a bed again
        if (!Boolean.TRUE.equals(tenant.getIsActive())) {
            throw new BusinessException("This tenant has checked out and cannot be allocated a room.");
        }

        Long oldRoomId = tenant.getRoom() != null ? tenant.getRoom().getId() : null;
        if (roomId.equals(oldRoomId)) {
            throw new BusinessException("Tenant is already in this room.");
        }

        // LOCK ORDER: user -> tenant -> rooms (lowest id first) -> booking. Two moves between the
        // same pair of rooms (A to B and B to A) now lock in the same order, so they can not deadlock.
        lockRoomsInOrder(oldRoomId, roomId);

        // Take the new bed first; only free the old one once that has succeeded
        assignRoomToTenant(tenant, roomId, pg);
        if (oldRoomId != null) {
            vacateRoom(oldRoomId);
        }
        return toResponse(tenantRepository.save(tenant));
    }


    private void lockRoomsInOrder(Long first, Long second) {
        java.util.stream.Stream.of(first, second).filter(Objects::nonNull).distinct().sorted()
                .forEach(roomRepository::findByIdForUpdate);
    }

    private void assignRoomToTenant(Tenant tenant, Long roomId, PG pg) {

        // Lock the room row so concurrent allocations can not both read the same occupancy
        Room room = roomRepository.findByIdForUpdate(roomId)
                .orElseThrow(() -> new ResourceNotFoundException("Room not found: " + roomId));

        if (!room.getPg().getId().equals(pg.getId())) {
            throw new UnauthorizedAccessException("Room does not belong to your PG.");
        }

        if (room.getStatus() == RoomStatus.UNDER_MAINTENANCE) {
            throw new BusinessException("Room " + room.getRoomNumber() + " is under maintenance.");
        }

        // a booking request nobody answered in 48 hours must not keep this bed blocked
        roomBookingService.releaseExpiredHolds(room);

        if (room.getCurrentOccupancy() >= room.getCapacity()) {
            throw new BusinessException("Room " + room.getRoomNumber() + " is fully occupied.");
        }

        room.setCurrentOccupancy(room.getCurrentOccupancy() + 1);
        room.refreshStatus();
        roomRepository.save(room);
        tenant.setRoom(room);
    }

    private void vacateRoom(Long roomId) {
        Room room = roomRepository.findByIdForUpdate(roomId)
                .orElseThrow(() -> new ResourceNotFoundException("Room not found: " + roomId));
        if (room.getCurrentOccupancy() <= 0) {
            // An active tenant occupies a bed, so this is inconsistent data. Roll back instead of hiding it.
            log.error("Data integrity problem: vacating room {} but its occupancy is already {}", room.getId(), room.getCurrentOccupancy());
            throw new IllegalStateException("Room " + room.getId() + " has occupancy " + room.getCurrentOccupancy()
                    + " but a tenant is leaving it");
        }
        room.setCurrentOccupancy(room.getCurrentOccupancy() - 1);
        room.refreshStatus(); // keeps UNDER_MAINTENANCE, otherwise AVAILABLE / FULLY_OCCUPIED
        roomRepository.save(room);
    }


    @Transactional
    public TenantResponse checkoutTenant(String ownerEmail, Long tenantId) {
        PG pg = getOwnerPg(ownerEmail);

        // Lock: a double-click can not free the same bed twice
        Tenant tenant = tenantRepository.findByIdForUpdate(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant not found: " + tenantId));

        if (!tenant.getPg().getId().equals(pg.getId())) {
            throw new UnauthorizedAccessException("Tenant does not belong to your PG.");
        }

        if (!Boolean.TRUE.equals(tenant.getIsActive())) {
            throw new BusinessException("This tenant has already checked out.");
        }

        if (tenant.getRoom() != null) {
            vacateRoom(tenant.getRoom().getId());
            tenant.setRoom(null);
        }

        // BUSINESS RULE: checkout does not cancel or settle rent. Unpaid rent records stay on
        // file (and keep their penalty), still show in the owner's Pending / Overdue lists with a
        // "checked out" marker, and still count in the dashboard's outstanding amount until the
        // owner marks them paid.
        // Only the STAY ends. The user ACCOUNT stays active on purpose: the person can still log in
        // to see their rent history, confirm a move-out report, and book a new room later.
        // (A new stay is a new Tenant row; only one active stay per user is allowed.)
        tenant.setIsActive(false);

        return toResponse(tenantRepository.save(tenant));
    }

    @Transactional(readOnly = true)
    public List<TenantResponse> getAllTenants(String ownerEmail) {
        PG pg = getOwnerPg(ownerEmail);
        return tenantRepository.findByPgIdAndIsActiveTrue(pg.getId())
                .stream().map(this::toResponse).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public TenantResponse getMyProfile(String tenantEmail) {
        // current stay, or the last finished one (a former tenant still sees their history)
        Tenant tenant = tenantRepository.findCurrentByUserEmail(tenantEmail)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant profile not found"));
        return toResponse(tenant);
    }

    @Transactional(readOnly = true)
    public List<TenantResponse> searchTenants(String ownerEmail, String name) {
        PG pg = getOwnerPg(ownerEmail);
        return tenantRepository.searchByNameInPg(pg.getId(), name)
                .stream().map(this::toResponse).collect(Collectors.toList());
    }

    private TenantResponse toResponse(Tenant t) {
        return TenantResponse.builder()
                .id(t.getId())
                .name(t.getUser().getName())
                .email(t.getUser().getEmail())
                .phone(t.getUser().getPhone())
                .roomId(t.getRoom() != null ? t.getRoom().getId() : null)
                .roomNumber(t.getRoom() != null ? t.getRoom().getRoomNumber() : null)
                .pgId(t.getPg().getId())
                .pgName(t.getPg().getName())
                .joinDate(t.getJoinDate())
                .expectedLeaveDate(t.getExpectedLeaveDate())
                .emergencyContactName(t.getEmergencyContactName())
                .emergencyContactPhone(t.getEmergencyContactPhone())
                .idProofType(t.getIdProofType())
                .isActive(t.getIsActive())
                .registeredAt(t.getRegisteredAt())
                .build();
    }
}
