package com.pg.service;

import com.pg.dto.request.RoomRequest;
import com.pg.dto.response.RoomResponse;
import com.pg.entity.PG;
import com.pg.entity.Room;
import com.pg.entity.User;
import com.pg.entity.enums.RoomStatus;
import com.pg.entity.enums.RoomType;
import com.pg.exception.BusinessException;
import com.pg.exception.ResourceNotFoundException;
import com.pg.exception.UnauthorizedAccessException;
import com.pg.repository.PGRepository;
import com.pg.repository.RoomRepository;
import com.pg.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;


@Service
@RequiredArgsConstructor
public class RoomService {

    private final RoomRepository roomRepository;
    private final PGRepository pgRepository;
    private final UserRepository userRepository;

    private PG getOwnerPg(String ownerEmail) {
        User owner = userRepository.findByEmail(ownerEmail)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        return pgRepository.findByOwnerIdAndIsActiveTrue(owner.getId())
                .orElseThrow(() -> new ResourceNotFoundException("No active PG found. Register your PG first."));
    }

    @Transactional
    public RoomResponse addRoom(String ownerEmail, RoomRequest request) {
        PG pg = getOwnerPg(ownerEmail);


        if (roomRepository.findByPgIdAndRoomNumber(pg.getId(), request.getRoomNumber().trim()).isPresent()) {
            throw new BusinessException("Room " + request.getRoomNumber() + " already exists in your PG.");
        }


        BigDecimal rent = request.getMonthlyRent() != null
                ? request.getMonthlyRent()
                : pg.getBaseMonthlyRent();

        Room room = Room.builder()
                .roomNumber(request.getRoomNumber().trim())
                .type(request.getType())
                .capacity(request.getCapacity())
                .monthlyRent(rent)
                .floorNumber(request.getFloorNumber())
                .amenities(request.getAmenities())
                .status(RoomStatus.AVAILABLE)
                .pg(pg)
                .build();

        return toResponse(roomRepository.save(room));
    }

    @Transactional(readOnly = true)
    public List<RoomResponse> getAllRooms(String ownerEmail) {
        PG pg = getOwnerPg(ownerEmail);
        return roomRepository.findByPgId(pg.getId())
                .stream().map(this::toResponse).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<RoomResponse> getAvailableRooms(String ownerEmail) {
        PG pg = getOwnerPg(ownerEmail);
        return roomRepository.findAvailableRoomsInPg(pg.getId())
                .stream().map(this::toResponse).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<RoomResponse> getRoomsByType(String ownerEmail, RoomType type) {
        PG pg = getOwnerPg(ownerEmail);
        return roomRepository.findByPgIdAndType(pg.getId(), type)
                .stream().map(this::toResponse).collect(Collectors.toList());
    }

    @Transactional
    public RoomResponse updateRoom(String ownerEmail, Long roomId, RoomRequest request) {
        PG pg = getOwnerPg(ownerEmail);

        Room room = roomRepository.findByIdForUpdate(roomId)
                .orElseThrow(() -> new ResourceNotFoundException("Room not found: " + roomId));

        if (!room.getPg().getId().equals(pg.getId())) {
            throw new UnauthorizedAccessException("Room does not belong to your PG.");
        }

        // Room number: must stay unique inside this PG
        String newNumber = request.getRoomNumber().trim();
        if (!newNumber.equals(room.getRoomNumber())) {
            boolean taken = roomRepository.findByPgIdAndRoomNumber(pg.getId(), newNumber)
                    .filter(other -> !other.getId().equals(roomId)).isPresent();
            if (taken) {
                throw new BusinessException("Room " + newNumber + " already exists in your PG.");
            }
            room.setRoomNumber(newNumber);
        }

        // Capacity can never drop below the tenants already living there
        if (request.getCapacity() < room.getCurrentOccupancy()) {
            throw new BusinessException("Capacity cannot be less than the current occupancy ("
                    + room.getCurrentOccupancy() + ").");
        }

        room.setType(request.getType());
        room.setCapacity(request.getCapacity());
        // PUT = the request describes the WHOLE editable room (same fields as "add room"):
        //   roomNumber, type, capacity, monthlyRent, floorNumber, amenities.
        // Same rule as creating a room: a blank rent means "use the PG's base rent".
        // Floor and amenities can be cleared by sending them empty.
        // (Rent records that were already generated keep their old amount.)
        room.setMonthlyRent(request.getMonthlyRent() != null
                ? request.getMonthlyRent()
                : pg.getBaseMonthlyRent());
        room.setFloorNumber(request.getFloorNumber());
        room.setAmenities(request.getAmenities() == null || request.getAmenities().isBlank()
                ? null : request.getAmenities().trim());

        // Capacity may have changed, so AVAILABLE / FULLY_OCCUPIED must be recomputed
        room.refreshStatus();

        return toResponse(roomRepository.save(room));
    }

    @Transactional
    public RoomResponse setMaintenanceStatus(String ownerEmail, Long roomId, boolean underMaintenance) {
        PG pg = getOwnerPg(ownerEmail);

        Room room = roomRepository.findByIdForUpdate(roomId)
                .orElseThrow(() -> new ResourceNotFoundException("Room not found: " + roomId));

        if (!room.getPg().getId().equals(pg.getId())) {
            throw new UnauthorizedAccessException("Room does not belong to your PG.");
        }

        if (underMaintenance) {
            if (room.getCurrentOccupancy() > 0) {
                throw new BusinessException("Cannot set room under maintenance while tenants are assigned. Remove tenants first.");
            }
            room.setStatus(RoomStatus.UNDER_MAINTENANCE);
        } else {
            // Leaving maintenance: the status must come from the real occupancy,
            // never blindly "AVAILABLE" (a full room would be wrong)
            room.setStatus(RoomStatus.AVAILABLE);
            room.refreshStatus();
        }
        return toResponse(roomRepository.save(room));
    }

    private RoomResponse toResponse(Room room) {
        return RoomResponse.builder()
                .id(room.getId())
                .roomNumber(room.getRoomNumber())
                .type(room.getType())
                .capacity(room.getCapacity())
                .currentOccupancy(room.getCurrentOccupancy())
                .availableBeds(room.getCapacity() - room.getCurrentOccupancy())
                .monthlyRent(room.getMonthlyRent())
                .floorNumber(room.getFloorNumber())
                .amenities(room.getAmenities())
                .status(room.getStatus())
                .pgId(room.getPg().getId())
                .pgName(room.getPg().getName())
                .build();
    }
}
