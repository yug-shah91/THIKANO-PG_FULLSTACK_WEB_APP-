package com.pg.service;

import com.pg.dto.request.BookingDecisionRequest;
import com.pg.dto.request.BookingRequest;
import com.pg.dto.response.RoomBookingResponse;
import com.pg.entity.*;
import com.pg.entity.enums.*;
import com.pg.exception.BusinessException;
import com.pg.exception.ResourceNotFoundException;
import com.pg.exception.UnauthorizedAccessException;
import com.pg.repository.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Booking in ON_REQUEST mode: the tenant asks, the bed is HELD, the owner answers.
 *
 *  request  -> room.currentOccupancy + 1, status PENDING, expires in 48 hours
 *  approve  -> a Tenant record is created in that bed (occupancy unchanged: the hold becomes the bed)
 *  decline / cancel / expire -> room.currentOccupancy - 1 (hold released)
 *
 * Because a hold counts as occupancy, availability, search and the capacity checks stay correct
 * without any extra logic. Remember: occupancy = active tenants + pending holds.
 *
 * LOCK ORDER (the same in every workflow, so two requests can never wait for each other):
 *     user  ->  tenant  ->  rooms (lowest room id first)  ->  booking
 * A workflow may skip a step but must never go backwards.
 */
@Service
@Slf4j
public class RoomBookingService {

    static final Duration HOLD = Duration.ofHours(48);

    private final RoomBookingRepository bookingRepository;
    private final RoomRepository roomRepository;
    private final TenantRepository tenantRepository;
    private final UserRepository userRepository;
    private final PGRepository pgRepository;
    private final Clock clock; // injected so tests can set "now" and check expiry boundaries exactly

    @Autowired
    public RoomBookingService(RoomBookingRepository bookingRepository, RoomRepository roomRepository,
                              TenantRepository tenantRepository, UserRepository userRepository,
                              PGRepository pgRepository, Clock clock) {
        this.bookingRepository = bookingRepository;
        this.roomRepository = roomRepository;
        this.tenantRepository = tenantRepository;
        this.userRepository = userRepository;
        this.pgRepository = pgRepository;
        this.clock = clock;
    }

    // ------------------------------------------------------------ tenant side

    @Transactional
    public RoomBookingResponse request(String email, BookingRequest req) {
        User user = userRepository.findByEmail(email.toLowerCase())
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        if (user.getRole() != Role.TENANT) {
            throw new UnauthorizedAccessException("Only tenant accounts can book a room.");
        }
        userRepository.findByIdForUpdate(user.getId()); // one person's clicks run one at a time

        if (tenantRepository.findActiveByUserEmail(user.getEmail()).isPresent()) {
            throw new BusinessException("You already have a booking. Contact your PG owner to change it.");
        }
        if (bookingRepository.existsByUserIdAndStatus(user.getId(), BookingStatus.PENDING)) {
            throw new BusinessException("You already have a request waiting for an answer. Cancel it to send another.");
        }

        LocalDate today = LocalDate.now(clock);
        LocalDate moveIn = req.getMoveInDate() != null ? req.getMoveInDate() : today;
        if (moveIn.isBefore(today) || moveIn.isAfter(today.plusDays(60))) {
            throw new BusinessException("Move-in date must be within the next 60 days.");
        }

        Room room = roomRepository.findByIdForUpdate(req.getRoomId())
                .orElseThrow(() -> new ResourceNotFoundException("Room not found: " + req.getRoomId()));
        releaseExpiredHolds(room); // a hold nobody answered must not block this bed
        PG pg = room.getPg();
        if (!Boolean.TRUE.equals(pg.getIsActive())) {
            throw new BusinessException("This PG is not accepting bookings.");
        }
        if (!PgBookingPolicy.needsApproval(pg)) {
            throw new BusinessException("This PG books instantly. Use the normal booking.");
        }
        if (room.getStatus() == RoomStatus.UNDER_MAINTENANCE) {
            throw new BusinessException("Room " + room.getRoomNumber() + " is under maintenance.");
        }
        if (room.getCurrentOccupancy() >= room.getCapacity()) {
            throw new BusinessException("Room " + room.getRoomNumber() + " is fully occupied.");
        }

        holdBed(room);
        RoomBooking saved = bookingRepository.save(RoomBooking.builder()
                .user(user).pg(pg).room(room).moveInDate(moveIn)
                .emergencyContactName(req.getEmergencyContactName())
                .emergencyContactPhone(req.getEmergencyContactPhone())
                .status(BookingStatus.PENDING)
                .expiresAt(LocalDateTime.now(clock).plus(HOLD)).build());
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<RoomBookingResponse> mine(String email) {
        User user = userRepository.findByEmail(email.toLowerCase())
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        return bookingRepository.findMine(user.getId(), PageRequest.of(0, 50)).stream().map(this::toResponse).toList();
    }

    @Transactional
    public RoomBookingResponse cancel(String email, Long id) {
        RoomBooking b = lockBooking(id);
        if (!b.getUser().getEmail().equalsIgnoreCase(email)) {
            throw new UnauthorizedAccessException("This is not your request.");
        }
        requirePending(b);
        release(b, BookingStatus.CANCELLED, null);
        return toResponse(b);
    }

    // ------------------------------------------------------------ owner side

    @Transactional(readOnly = true)
    public Page<RoomBookingResponse> forOwner(String ownerEmail, boolean pending, int page, int size) {
        PG pg = ownerPg(ownerEmail);
        int safeSize = Math.min(Math.max(size, 1), 50); // never an unbounded page
        return bookingRepository.findForPg(pg.getId(), pending, PageRequest.of(Math.max(page, 0), safeSize))
                .map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public long pendingCount(String ownerEmail) {
        return bookingRepository.countByPgIdAndStatusAndExpiresAtAfter(
                ownerPg(ownerEmail).getId(), BookingStatus.PENDING, LocalDateTime.now(clock));
    }

    @Transactional
    public RoomBookingResponse decide(String ownerEmail, Long id, BookingDecisionRequest req) {
        PG pg = ownerPg(ownerEmail);
        RoomBooking b = lockBooking(id);
        if (!b.getPg().getId().equals(pg.getId())) {
            throw new UnauthorizedAccessException("This request is not for your PG.");
        }
        requirePending(b);

        // Too late: give the bed back and RETURN the expired request. We must not throw here:
        // a BusinessException would roll this transaction back and undo the release.
        if (isExpired(b)) {
            release(b, BookingStatus.EXPIRED, null);
            return toResponse(b);
        }

        if (req.getStatus() == BookingStatus.DECLINED) {
            release(b, BookingStatus.DECLINED, req.getOwnerNote());
            return toResponse(b);
        }
        if (req.getStatus() != BookingStatus.APPROVED) {
            throw new BusinessException("You can only approve or decline a request.");
        }

        User user = b.getUser(); // already locked by lockBooking
        if (tenantRepository.findActiveByUserEmail(user.getEmail()).isPresent()) {
            throw new BusinessException("This person already has a stay. Decline the request.");
        }
        // The bed is already held, so occupancy does not change: the hold becomes the tenant's bed.
        Room room = b.getRoom();
        tenantRepository.save(Tenant.builder()
                .user(user).pg(pg).room(room).joinDate(b.getMoveInDate())
                .emergencyContactName(b.getEmergencyContactName())
                .emergencyContactPhone(b.getEmergencyContactPhone()).build());
        b.setStatus(BookingStatus.APPROVED);
        b.setOwnerNote(blankToNull(req.getOwnerNote()));
        b.setDecidedAt(LocalDateTime.now(clock));
        bookingRepository.save(b);
        return toResponse(b);
    }

    // ------------------------------------------------------------ expiry

    /**
     * Ids of requests whose 48 hours are over. The scheduler (BookingExpiryJob) calls expireOne()
     * for each id, one transaction per request, so a transaction never holds locks on several
     * rooms at once (that would break the lock order).
     */
    @Transactional(readOnly = true)
    public List<Long> expiredIds() {
        return bookingRepository.findIdsByStatusAndExpiresAtBefore(BookingStatus.PENDING, LocalDateTime.now(clock));
    }

    /** Expires one request. Returns false if someone answered it first. */
    @Transactional
    public boolean expireOne(Long id) {
        RoomBooking b = lockBooking(id);
        // re-check after the locks: the owner or the tenant may have just answered it
        if (b.getStatus() != BookingStatus.PENDING || !isExpired(b)) {
            return false;
        }
        release(b, BookingStatus.EXPIRED, null);
        return true;
    }

    /**
     * Gives back every expired hold on this (already locked) room right now, without waiting for
     * the scheduler. Called before a bed is taken so a stale hold can never block or double-book it.
     */
    public void releaseExpiredHolds(Room room) {
        for (RoomBooking b : bookingRepository.findByRoomIdAndStatusAndExpiresAtBefore(
                room.getId(), BookingStatus.PENDING, LocalDateTime.now(clock))) {
            release(b, BookingStatus.EXPIRED, null);
        }
    }

    // ------------------------------------------------------------ helpers

    // expired = strictly AFTER the expiry moment (at the exact moment it is still valid)
    private boolean isExpired(RoomBooking b) {
        return b.getExpiresAt().isBefore(LocalDateTime.now(clock));
    }

    /**
     * Locks everything one booking touches, in the allowed order (user, room, booking), then returns
     * the booking loaded AFTER the locks, so its status is the real, current one.
     */
    private RoomBooking lockBooking(Long id) {
        Long userId = bookingRepository.findUserId(id)
                .orElseThrow(() -> new ResourceNotFoundException("Request not found"));
        Long roomId = bookingRepository.findRoomId(id)
                .orElseThrow(() -> new ResourceNotFoundException("Request not found"));
        userRepository.findByIdForUpdate(userId);
        roomRepository.findByIdForUpdate(roomId);
        return bookingRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new ResourceNotFoundException("Request not found"));
    }

    private void requirePending(RoomBooking b) {
        if (b.getStatus() != BookingStatus.PENDING) {
            throw new BusinessException("This request is already " + b.getStatus().name().toLowerCase() + ".");
        }
    }

    private void holdBed(Room room) {
        room.setCurrentOccupancy(room.getCurrentOccupancy() + 1);
        room.refreshStatus();
        roomRepository.save(room);
    }

    /** Gives the held bed back and closes the request. */
    private void release(RoomBooking b, BookingStatus to, String note) {
        // Exactly once: only a PENDING request holds a bed, and the caller holds its row lock.
        if (b.getStatus() != BookingStatus.PENDING) {
            throw new IllegalStateException("Booking " + b.getId() + " is " + b.getStatus() + ": its hold was already released");
        }
        Room room = roomRepository.findByIdForUpdate(b.getRoom().getId())
                .orElseThrow(() -> new ResourceNotFoundException("Room not found"));
        if (room.getCurrentOccupancy() <= 0) {
            // A pending request holds a bed, so occupancy can not be 0 here: the data is inconsistent.
            // Do NOT carry on (that would mark the request closed while the room count is wrong).
            // Throwing rolls the whole transaction back, so request and room stay exactly as they were
            // and someone can look at it. (An IllegalStateException is not a BusinessException on purpose.)
            log.error("Data integrity problem: booking {} holds a bed but room {} has occupancy {}",
                    b.getId(), room.getId(), room.getCurrentOccupancy());
            throw new IllegalStateException("Room " + room.getId() + " has occupancy "
                    + room.getCurrentOccupancy() + " but booking " + b.getId() + " holds a bed");
        }
        room.setCurrentOccupancy(room.getCurrentOccupancy() - 1);
        room.refreshStatus();
        roomRepository.save(room);
        b.setStatus(to);
        b.setOwnerNote(blankToNull(note));
        b.setDecidedAt(LocalDateTime.now(clock));
        bookingRepository.save(b);
    }

    private PG ownerPg(String ownerEmail) {
        User owner = userRepository.findByEmail(ownerEmail.toLowerCase())
                .orElseThrow(() -> new ResourceNotFoundException("Owner not found"));
        return pgRepository.findByOwnerIdAndIsActiveTrue(owner.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Set up your PG first."));
    }

    private RoomBookingResponse toResponse(RoomBooking b) {
        return RoomBookingResponse.builder()
                .id(b.getId()).pgId(b.getPg().getId()).pgName(b.getPg().getName())
                .roomNumber(b.getRoom().getRoomNumber()).monthlyRent(b.getRoom().getMonthlyRent())
                .requesterName(b.getUser().getName()).requesterPhone(b.getUser().getPhone())
                .moveInDate(b.getMoveInDate()).status(b.getStatus()).expiresAt(b.getExpiresAt())
                .ownerNote(b.getOwnerNote())
                .ownerPhone(b.getStatus() == BookingStatus.APPROVED ? b.getPg().getContactPhone() : null)
                .createdAt(b.getCreatedAt()).build();
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
