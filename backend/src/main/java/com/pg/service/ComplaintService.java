package com.pg.service;

import com.pg.dto.request.ComplaintRequest;
import com.pg.dto.request.ComplaintUpdateRequest;
import com.pg.dto.response.ComplaintResponse;
import com.pg.entity.*;
import com.pg.entity.enums.ComplaintStatus;
import com.pg.exception.BusinessException;
import com.pg.exception.ResourceNotFoundException;
import com.pg.exception.UnauthorizedAccessException;
import com.pg.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;


@Service
@RequiredArgsConstructor
@Slf4j
public class ComplaintService {

    private final java.time.Clock clock; // injected so tests can set "now"
    private final ComplaintRepository complaintRepository;
    private final TenantRepository tenantRepository;
    private final PGRepository pgRepository;
    private final UserRepository userRepository;


    @Transactional
    public ComplaintResponse raiseComplaint(String tenantEmail, ComplaintRequest request) {
        // only a CURRENT stay can raise complaints (a former tenant can still read old ones)
        Tenant tenant = tenantRepository.findActiveByUserEmail(tenantEmail)
                .orElseThrow(() -> new BusinessException("You have no current stay, so you cannot raise a complaint."));

        Complaint complaint = Complaint.builder()
                .tenant(tenant)
                .pg(tenant.getPg())
                .roomNumber(tenant.getRoom() != null ? tenant.getRoom().getRoomNumber() : null)
                .category(request.getCategory())
                .title(request.getTitle())
                .description(request.getDescription())
                .status(ComplaintStatus.OPEN)
                .build();

        return toResponse(complaintRepository.save(complaint));
    }

    @Transactional(readOnly = true)
    public List<ComplaintResponse> getMyComplaints(String tenantEmail) {
        Tenant tenant = tenantRepository.findCurrentByUserEmail(tenantEmail)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant not found"));
        return complaintRepository.findByTenantIdOrderByCreatedAtDesc(tenant.getId())
                .stream().map(this::toResponse).collect(Collectors.toList());
    }

    @Transactional
    public ComplaintResponse closeMyComplaint(String tenantEmail, Long complaintId) {
        Tenant tenant = tenantRepository.findCurrentByUserEmail(tenantEmail)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant not found"));


        if (!complaintRepository.existsByIdAndTenantId(complaintId, tenant.getId())) {
            throw new UnauthorizedAccessException("Complaint does not belong to you.");
        }

        Complaint complaint = complaintRepository.findById(complaintId)
                .orElseThrow(() -> new ResourceNotFoundException("Complaint not found"));

        if (complaint.getStatus() != ComplaintStatus.RESOLVED) {
            throw new BusinessException("You can only close RESOLVED complaints.");
        }

        complaint.setStatus(ComplaintStatus.CLOSED);
        return toResponse(complaintRepository.save(complaint));
    }


    @Transactional(readOnly = true)
    public Page<ComplaintResponse> getAllComplaintsForPg(String ownerEmail, Pageable pageable) {
        PG pg = getOwnerPg(ownerEmail);
        return complaintRepository.findByPgIdOrderByCreatedAtDesc(pg.getId(), pageable)
                .map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public List<ComplaintResponse> getComplaintsByStatus(String ownerEmail, ComplaintStatus status) {
        PG pg = getOwnerPg(ownerEmail);
        return complaintRepository.findByPgIdAndStatusOrderByCreatedAtDesc(pg.getId(), status)
                .stream().map(this::toResponse).collect(Collectors.toList());
    }

    @Transactional
    public ComplaintResponse updateComplaintStatus(String ownerEmail, Long complaintId,
                                                    ComplaintUpdateRequest request) {
        PG pg = getOwnerPg(ownerEmail);


        if (!complaintRepository.existsByIdAndPgId(complaintId, pg.getId())) {
            throw new UnauthorizedAccessException("Complaint does not belong to your PG.");
        }

        Complaint complaint = complaintRepository.findById(complaintId)
                .orElseThrow(() -> new ResourceNotFoundException("Complaint not found"));


        ComplaintStatus current = complaint.getStatus();
        ComplaintStatus next = request.getStatus();

        validateStatusTransition(current, next);

        if (next != current) {
            complaint.setStatus(next);
            if (next == ComplaintStatus.RESOLVED) {
                complaint.setResolvedAt(LocalDateTime.now(clock));
            } else if (current == ComplaintStatus.RESOLVED) {
                complaint.setResolvedAt(null); // sent back to IN_PROGRESS: no longer resolved
            }
        }
        // (same status = only the owner's note is updated; resolvedAt is left untouched)
        if (request.getOwnerResponse() != null) complaint.setOwnerResponse(request.getOwnerResponse());

        return toResponse(complaintRepository.save(complaint));
    }


    // What the OWNER may change a complaint to. CLOSED is deliberately not here:
    // it happens only when the tenant confirms, or by the 7-day auto-close.
    // OPEN is not here either: a complaint can not go back to OPEN.
    private static final Map<ComplaintStatus, Set<ComplaintStatus>> OWNER_TRANSITIONS = Map.of(
            ComplaintStatus.OPEN,        EnumSet.of(ComplaintStatus.IN_PROGRESS, ComplaintStatus.RESOLVED),
            ComplaintStatus.IN_PROGRESS, EnumSet.of(ComplaintStatus.RESOLVED),
            ComplaintStatus.RESOLVED,    EnumSet.of(ComplaintStatus.IN_PROGRESS),
            ComplaintStatus.CLOSED,      EnumSet.noneOf(ComplaintStatus.class)
    );

    private void validateStatusTransition(ComplaintStatus current, ComplaintStatus next) {
        if (current == ComplaintStatus.CLOSED) {
            throw new BusinessException("Cannot change status of a CLOSED complaint.");
        }
        if (next == current) {
            if (current == ComplaintStatus.OPEN) {
                throw new BusinessException("Complaint is already OPEN.");
            }
            return; // same status again = just updating the note
        }
        if (!OWNER_TRANSITIONS.get(current).contains(next)) {
            throw new BusinessException("Invalid status change: " + current + " -> " + next
                    + ". Allowed: " + OWNER_TRANSITIONS.get(current) + ".");
        }
    }

    // One SQL UPDATE - no complaint is loaded into memory
    @Scheduled(cron = "0 0 2 * * *")
    @Transactional
    public void autoCloseOldResolvedComplaints() {
        LocalDateTime now = LocalDateTime.now(clock);
        int closed = complaintRepository.autoCloseResolvedBefore(
                now.minusDays(7), now, "Auto-closed after 7 days",
                ComplaintStatus.RESOLVED, ComplaintStatus.CLOSED);
        log.info("Auto-closed {} resolved complaints", closed);
    }

    private PG getOwnerPg(String ownerEmail) {
        User owner = userRepository.findByEmail(ownerEmail)
                .orElseThrow(() -> new ResourceNotFoundException("Owner not found"));
        return pgRepository.findByOwnerIdAndIsActiveTrue(owner.getId())
                .orElseThrow(() -> new ResourceNotFoundException("No active PG found."));
    }

    private ComplaintResponse toResponse(Complaint c) {
        return ComplaintResponse.builder()
                .id(c.getId())
                .tenantId(c.getTenant().getId())
                .tenantName(c.getTenant().getUser().getName())
                .roomNumber(c.getRoomNumber() != null ? c.getRoomNumber()
                        : (c.getTenant().getRoom() != null ? c.getTenant().getRoom().getRoomNumber() : "N/A"))
                .pgId(c.getPg().getId())
                .category(c.getCategory())
                .title(c.getTitle())
                .description(c.getDescription())
                .status(c.getStatus())
                .ownerResponse(c.getOwnerResponse())
                .resolvedAt(c.getResolvedAt())
                .createdAt(c.getCreatedAt())
                .updatedAt(c.getUpdatedAt())
                .build();
    }
}
