package com.pg.service;

import com.pg.dto.request.RentPaymentRequest;
import com.pg.dto.response.RentRecordResponse;
import com.pg.entity.*;
import com.pg.entity.enums.RentStatus;
import com.pg.exception.BusinessException;
import com.pg.exception.ResourceNotFoundException;
import com.pg.exception.UnauthorizedAccessException;
import com.pg.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class RentService {

    private final java.time.Clock clock; // injected so tests can set "now"
    private final RentRecordRepository rentRecordRepository;
    private final TenantRepository tenantRepository;
    private final UserRepository userRepository;
    private final PGRepository pgRepository;


    // ------------------------------------------------------------------
    //  RENT GENERATION
    // ------------------------------------------------------------------

    /** Runs on the 1st of every month at 06:00 - generates rent for EVERY PG. */
    @Scheduled(cron = "0 0 6 1 * *")
    @Transactional
    public void generateMonthlyRent() {
        GenerationResult result = generateFor(tenantRepository.findAllActiveTenatsWithRoom());
        log.info("Scheduled rent generation finished: {}", result);
    }

    /** Owner pressed "Generate rent": only THEIR PG is processed. */
    @Transactional
    public String generateRentForOwnerPg(String ownerEmail) {
        PG pg = getOwnerPg(ownerEmail);
        GenerationResult result = generateFor(tenantRepository.findActiveTenantsWithRoomByPgId(pg.getId()));
        return result.message();
    }

    private record GenerationResult(int month, int year, int generated, int alreadyExisted, int skipped) {
        String message() {
            return "Rent for " + month + "/" + year + ": " + generated + " created, "
                    + alreadyExisted + " already existed, " + skipped + " skipped (no valid rent amount).";
        }
    }

    private static final long RENT_GENERATION_LOCK = 8_100_001L;

    private GenerationResult generateFor(List<Tenant> tenants) {
        // Wait for any other generation run to finish, then re-check what already exists.
        // (The unique constraint is the last safety net, but without this lock the loser of a
        // race would fail the whole batch instead of just seeing "already existed".)
        rentRecordRepository.lockRentGeneration(RENT_GENERATION_LOCK);
        YearMonth period = YearMonth.now(clock);
        int month = period.getMonthValue();
        int year = period.getYear();
        int generated = 0, alreadyExisted = 0, skipped = 0;

        for (Tenant tenant : tenants) {
            if (rentRecordRepository.existsByTenantIdAndMonthAndYear(tenant.getId(), month, year)) {
                alreadyExisted++;
                continue;
            }

            // Booked online with a future move-in date: no rent until the month they arrive
            if (tenant.getJoinDate() != null && tenant.getJoinDate().isAfter(period.atEndOfMonth())) {
                continue;
            }

            PG pg = tenant.getPg();
            Room room = tenant.getRoom();

            // Room rent wins; otherwise fall back to the PG's base rent
            BigDecimal baseAmount = room.getMonthlyRent() != null
                    ? room.getMonthlyRent()
                    : pg.getBaseMonthlyRent();

            // Never create a record without a usable amount
            if (baseAmount == null || baseAmount.signum() <= 0) {
                log.warn("Skipping rent for tenant {} (room {}): no valid rent amount configured",
                        tenant.getId(), room.getRoomNumber());
                skipped++;
                continue;
            }

            RentRecord record = RentRecord.builder()
                    .tenant(tenant)
                    .pg(pg)
                    .month(month)
                    .year(year)
                    .roomNumber(room.getRoomNumber())
                    .baseAmount(baseAmount)
                    .penaltyAmount(BigDecimal.ZERO)
                    .totalAmount(baseAmount) // starts equal to base, penalty added later if overdue
                    .dueDate(safeDueDate(period, pg.getRentDueDay()))
                    .status(RentStatus.PENDING)
                    .build();

            rentRecordRepository.save(record);
            generated++;
        }

        return new GenerationResult(month, year, generated, alreadyExisted, skipped);
    }

    /** Due day 31 in February (or a null / 0 value) must not crash generation. */
    private LocalDate safeDueDate(YearMonth period, Integer configuredDay) {
        int day = (configuredDay == null || configuredDay < 1) ? 5 : configuredDay;
        return period.atDay(Math.min(day, period.lengthOfMonth()));
    }

    // ------------------------------------------------------------------
    //  PENALTY (runs every night)
    //
    //  Rule: when rent is not paid by its due date, ONE flat penalty of
    //  (pg.penaltyPercent)% of the BASE rent is added, once. It is not
    //  repeated or compounded on later days.
    // ------------------------------------------------------------------

    @Scheduled(cron = "0 0 0 * * *")
    @Transactional
    public void applyPenaltyToOverdueRent() {
        List<RentRecord> overdueRecords = rentRecordRepository.findOverdueRecords(LocalDate.now(clock));
        log.info("Applying penalties to {} overdue rent records", overdueRecords.size());

        for (RentRecord record : overdueRecords) {
            BigDecimal penalty = calculatePenalty(record);
            BigDecimal total = record.getBaseAmount().add(penalty);
            // Guarded update: does nothing if the owner marked it PAID in the meantime
            rentRecordRepository.markOverdue(record.getId(), penalty, total,
                    RentStatus.OVERDUE, RentStatus.PENDING);
        }
        log.info("Penalty application complete");
    }

    private BigDecimal calculatePenalty(RentRecord record) {
        Integer percent = record.getPg().getPenaltyPercent();
        if (percent == null || percent <= 0) {
            return BigDecimal.ZERO.setScale(2);
        }
        return record.getBaseAmount()
                .multiply(BigDecimal.valueOf(percent))
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
    }

    // ------------------------------------------------------------------
    //  MARK PAID
    // ------------------------------------------------------------------

    @Transactional
    public RentRecordResponse markAsPaid(String ownerEmail, Long rentRecordId, RentPaymentRequest request) {
        PG pg = getOwnerPg(ownerEmail);

        RentRecord record = rentRecordRepository.findById(rentRecordId)
                .orElseThrow(() -> new ResourceNotFoundException("Rent record not found: " + rentRecordId));

        // Security: this rent record must belong to owner's PG
        if (!record.getPg().getId().equals(pg.getId())) {
            throw new UnauthorizedAccessException("Rent record does not belong to your PG.");
        }

        if (record.getStatus() == RentStatus.PAID) {
            throw new BusinessException("Rent is already marked as paid.");
        }

        // If it is already past the due date but the nightly job has not run yet, the penalty
        // still applies - so the amount does not depend on the job's timing.
        BigDecimal penalty = record.getPenaltyAmount() != null ? record.getPenaltyAmount() : BigDecimal.ZERO;
        if (record.getStatus() == RentStatus.PENDING && record.getDueDate().isBefore(LocalDate.now(clock))) {
            penalty = calculatePenalty(record);
        }
        BigDecimal total = record.getBaseAmount().add(penalty);

        String notes = (request != null && request.getNotes() != null && !request.getNotes().isBlank())
                ? request.getNotes().trim()
                : record.getNotes();

        // status + paidAt are written together, in one atomic statement
        int changed = rentRecordRepository.markPaid(
                rentRecordId, LocalDateTime.now(clock), penalty, total, notes, RentStatus.PAID);
        if (changed == 0) {
            throw new BusinessException("Rent is already marked as paid.");
        }

        return toResponse(rentRecordRepository.findById(rentRecordId)
                .orElseThrow(() -> new ResourceNotFoundException("Rent record not found: " + rentRecordId)));
    }

    @Transactional(readOnly = true)
    public List<RentRecordResponse> getPendingRentForPg(String ownerEmail) {
        PG pg = getOwnerPg(ownerEmail);
        return rentRecordRepository.findByPgIdAndStatusOrderByDueDateAsc(pg.getId(), RentStatus.PENDING)
                .stream().map(this::toResponse).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<RentRecordResponse> getOverdueRentForPg(String ownerEmail) {
        PG pg = getOwnerPg(ownerEmail);
        return rentRecordRepository.findByPgIdAndStatusOrderByDueDateAsc(pg.getId(), RentStatus.OVERDUE)
                .stream().map(this::toResponse).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<RentRecordResponse> getMonthlyRentReport(String ownerEmail, int month, int year) {
        if (month < 1 || month > 12) {
            throw new BusinessException("Month must be between 1 and 12.");
        }
        if (year < 2000 || year > 2100) {
            throw new BusinessException("Year must be between 2000 and 2100.");
        }
        PG pg = getOwnerPg(ownerEmail);
        return rentRecordRepository.findByPgIdAndMonthAndYear(pg.getId(), month, year)
                .stream().map(this::toResponse).collect(Collectors.toList());
    }


    @Transactional(readOnly = true)
    public List<RentRecordResponse> getMyRentHistory(String tenantEmail) {
        Tenant tenant = tenantRepository.findCurrentByUserEmail(tenantEmail)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant profile not found"));
        return rentRecordRepository.findByTenantIdOrderByYearDescMonthDesc(tenant.getId())
                .stream().map(this::toResponse).collect(Collectors.toList());
    }


    private PG getOwnerPg(String ownerEmail) {
        User owner = userRepository.findByEmail(ownerEmail)
                .orElseThrow(() -> new ResourceNotFoundException("Owner not found"));
        return pgRepository.findByOwnerIdAndIsActiveTrue(owner.getId())
                .orElseThrow(() -> new ResourceNotFoundException("No active PG found."));
    }

    private RentRecordResponse toResponse(RentRecord r) {
        // Prefer the room saved on the record. Older records (before this field existed)
        // fall back to the tenant's current room.
        String roomNumber = r.getRoomNumber();
        if (roomNumber == null && r.getTenant().getRoom() != null) {
            roomNumber = r.getTenant().getRoom().getRoomNumber();
        }

        return RentRecordResponse.builder()
                .id(r.getId())
                .tenantId(r.getTenant().getId())
                .tenantName(r.getTenant().getUser().getName())
                // Unpaid rent of a checked-out tenant stays on record and stays visible
                .tenantActive(r.getTenant().getIsActive())
                .roomNumber(roomNumber != null ? roomNumber : "N/A")
                .month(r.getMonth())
                .year(r.getYear())
                .baseAmount(r.getBaseAmount())
                .penaltyAmount(r.getPenaltyAmount())
                .totalAmount(r.getTotalAmount())
                .dueDate(r.getDueDate())
                .status(r.getStatus())
                .paidAt(r.getPaidAt())
                .notes(r.getNotes())
                .createdAt(r.getCreatedAt())
                .build();
    }
}
