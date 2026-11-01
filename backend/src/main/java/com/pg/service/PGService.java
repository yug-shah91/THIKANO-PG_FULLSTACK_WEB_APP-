package com.pg.service;

import com.pg.dto.request.PGRequest;
import com.pg.dto.response.DashboardResponse;
import com.pg.dto.response.PGResponse;
import com.pg.entity.PG;
import com.pg.entity.User;
import com.pg.entity.enums.RoomStatus;
import com.pg.entity.enums.RentStatus;
import com.pg.entity.enums.ComplaintStatus;
import com.pg.exception.BusinessException;
import com.pg.exception.ResourceNotFoundException;
import com.pg.exception.UnauthorizedAccessException;
import com.pg.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PGService {

    private final java.time.Clock clock;
    private final PGRepository pgRepository;
    private final UserRepository userRepository;
    private final RoomRepository roomRepository;
    private final TenantRepository tenantRepository;
    private final RentRecordRepository rentRecordRepository;
    private final ComplaintRepository complaintRepository;


    private PG getOwnerPg(String ownerEmail) {
        User owner = userRepository.findByEmail(ownerEmail)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        return pgRepository.findByOwnerIdAndIsActiveTrue(owner.getId())
                .orElseThrow(() -> new ResourceNotFoundException(
                    "No active PG found. Register your PG first."));
    }

    @Transactional
    public PGResponse registerPg(String ownerEmail, PGRequest request) {
        User owner = userRepository.findByEmail(ownerEmail)
                .orElseThrow(() -> new ResourceNotFoundException("Owner not found"));

        if (pgRepository.existsByOwnerId(owner.getId())) {
            throw new BusinessException("You already have a registered PG. Contact support to add more.");
        }

        requireBothOrNoCoordinates(request);

        PG pg = PG.builder()
                .name(request.getName())
                .address(request.getAddress())
                .city(request.getCity())
                .description(request.getDescription())
                .contactPhone(request.getContactPhone())
                .latitude(request.getLatitude())
                .bookingMode(request.getBookingMode() != null ? request.getBookingMode() : com.pg.entity.enums.BookingMode.INSTANT)
                .genderPolicy(request.getGenderPolicy() != null ? request.getGenderPolicy() : com.pg.entity.enums.GenderPolicy.ANY)
                .longitude(request.getLongitude())
                .baseMonthlyRent(request.getBaseMonthlyRent())
                .rentDueDay(request.getRentDueDay() != null ? request.getRentDueDay() : 5)
                .penaltyPercent(request.getPenaltyPercent() != null ? request.getPenaltyPercent() : 5)
                .owner(owner)
                .build();

        return toResponse(pgRepository.save(pg));
    }

    private void requireBothOrNoCoordinates(PGRequest request) {
        if ((request.getLatitude() == null) != (request.getLongitude() == null)) {
            throw new BusinessException("Send both latitude and longitude, or neither.");
        }
    }

    @Transactional(readOnly = true)
    public PGResponse getMyPg(String ownerEmail) {
        return toResponse(getOwnerPg(ownerEmail));
    }

    @Transactional
    public PGResponse updateMyPg(String ownerEmail, PGRequest request) {
        PG pg = getOwnerPg(ownerEmail);
        requireBothOrNoCoordinates(request);

        if (request.getName() != null)          pg.setName(request.getName());
        if (request.getAddress() != null)       pg.setAddress(request.getAddress());
        if (request.getCity() != null)          pg.setCity(request.getCity());
        if (request.getDescription() != null)   pg.setDescription(request.getDescription());
        if (request.getContactPhone() != null)  pg.setContactPhone(request.getContactPhone());
        if (Boolean.TRUE.equals(request.getClearCoordinates())) {
            if (request.getLatitude() != null) {
                throw new BusinessException("Send coordinates or clearCoordinates, not both.");
            }
            pg.setLatitude(null);
            pg.setLongitude(null);
        } else if (request.getLatitude() != null && request.getLongitude() != null) {
            pg.setLatitude(request.getLatitude());
            pg.setLongitude(request.getLongitude());
        }
        if (request.getBaseMonthlyRent() != null) pg.setBaseMonthlyRent(request.getBaseMonthlyRent());
        if (request.getRentDueDay() != null)    pg.setRentDueDay(request.getRentDueDay());
        if (request.getPenaltyPercent() != null) pg.setPenaltyPercent(request.getPenaltyPercent());
        // Changing the mode only affects NEW bookings; requests already waiting stay valid
        if (request.getBookingMode() != null)   pg.setBookingMode(request.getBookingMode());
        if (request.getGenderPolicy() != null)  pg.setGenderPolicy(request.getGenderPolicy());

        return toResponse(pgRepository.save(pg));
    }


    @Transactional(readOnly = true)
    public DashboardResponse getDashboard(String ownerEmail) {
        PG pg = getOwnerPg(ownerEmail);
        Long pgId = pg.getId();

        // All rent numbers below cover ALL unpaid months, so the counts and the
        // outstanding amount always describe the same set of records.
        java.time.YearMonth now = java.time.YearMonth.now(clock);
        long totalBeds = roomRepository.sumUsableBeds(pgId, RoomStatus.UNDER_MAINTENANCE);
        long occupied = tenantRepository.countByPgIdAndIsActiveTrue(pgId);
        List<DashboardResponse.MonthRevenue> trend = new java.util.ArrayList<>();
        java.util.Map<Integer, DashboardResponse.MonthRevenue> byIndex = new java.util.HashMap<>();
        for (Object[] row : rentRecordRepository.monthlyTotals(pgId, now.getYear() * 12 + now.getMonthValue() - 5, RentStatus.PAID)) {
            int y = ((Number) row[0]).intValue(), m = ((Number) row[1]).intValue();
            byIndex.put(y * 12 + m, DashboardResponse.MonthRevenue.builder().year(y).month(m)
                    .expected((java.math.BigDecimal) row[2]).collected((java.math.BigDecimal) row[3]).build());
        }
        for (int i = 5; i >= 0; i--) { // always 6 points, zero for months without rent
            java.time.YearMonth ym = now.minusMonths(i);
            trend.add(byIndex.getOrDefault(ym.getYear() * 12 + ym.getMonthValue(), DashboardResponse.MonthRevenue.builder()
                    .year(ym.getYear()).month(ym.getMonthValue()).expected(java.math.BigDecimal.ZERO).collected(java.math.BigDecimal.ZERO).build()));
        }
        DashboardResponse.MonthRevenue thisMonth = trend.get(trend.size() - 1);

        return DashboardResponse.builder()
                .totalBeds(totalBeds)
                .occupiedBeds(occupied)
                .availableBeds(roomRepository.sumFreeBeds(pgId, RoomStatus.UNDER_MAINTENANCE))
                .occupancyPercent(totalBeds == 0 ? 0 : (int) Math.min(100, Math.round(occupied * 100.0 / totalBeds)))
                .revenueThisMonth(thisMonth.getCollected())
                .expectedThisMonth(thisMonth.getExpected())
                .rentTrend(trend)
                .pgName(pg.getName())
                .totalRooms(roomRepository.countByPgId(pgId))
                .availableRooms(roomRepository.countWithFreeBeds(pgId, RoomStatus.UNDER_MAINTENANCE))
                .fullyOccupiedRooms(roomRepository.countFull(pgId, RoomStatus.UNDER_MAINTENANCE))
                .activeTenants(tenantRepository.countByPgIdAndIsActiveTrue(pgId))
                .openComplaints(complaintRepository.countByPgIdAndStatus(pgId, ComplaintStatus.OPEN))
                .pendingRentCount(rentRecordRepository.countByPgIdAndStatus(pgId, RentStatus.PENDING))
                .overdueRentCount(rentRecordRepository.countByPgIdAndStatus(pgId, RentStatus.OVERDUE))
                .totalOutstandingRent(rentRecordRepository.getTotalOutstandingForPg(pgId))
                .build();
    }

    // SUPER_ADMIN: see all PGs on the platform
    @Transactional(readOnly = true)
    public List<PGResponse> getAllPgs() {
        return pgRepository.findAll().stream().map(this::toResponse).collect(Collectors.toList());
    }

    private PGResponse toResponse(PG pg) {
        long availableRooms = roomRepository.countWithFreeBeds(pg.getId(), RoomStatus.UNDER_MAINTENANCE);
        long activeTenants  = tenantRepository.countByPgIdAndIsActiveTrue(pg.getId());

        return PGResponse.builder()
                .id(pg.getId())
                .name(pg.getName())
                .address(pg.getAddress())
                .city(pg.getCity())
                .description(pg.getDescription())
                .contactPhone(pg.getContactPhone())
                .bookingMode(PgBookingPolicy.modeOf(pg))
                .genderPolicy(pg.getGenderPolicy() != null ? pg.getGenderPolicy() : com.pg.entity.enums.GenderPolicy.ANY)
                .latitude(pg.getLatitude())
                .longitude(pg.getLongitude())
                .baseMonthlyRent(pg.getBaseMonthlyRent())
                .rentDueDay(pg.getRentDueDay())
                .penaltyPercent(pg.getPenaltyPercent())
                .isActive(pg.getIsActive())
                .ownerName(pg.getOwner().getName())
                .ownerEmail(pg.getOwner().getEmail())
                .totalRooms(pg.getRooms().size())
                .availableRooms((int) availableRooms)
                .activeTenants(activeTenants)
                .createdAt(pg.getCreatedAt())
                .build();
    }
}
