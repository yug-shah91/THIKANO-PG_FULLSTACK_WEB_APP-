package com.pg.service;

import com.pg.dto.request.VisitDecisionRequest;
import com.pg.dto.request.VisitRequestDto;
import com.pg.dto.response.VisitResponse;
import com.pg.entity.PG;
import com.pg.entity.User;
import com.pg.entity.VisitRequest;
import com.pg.entity.enums.VisitStatus;
import com.pg.exception.BusinessException;
import com.pg.exception.ResourceNotFoundException;
import com.pg.exception.UnauthorizedAccessException;
import com.pg.repository.PGRepository;
import com.pg.repository.UserRepository;
import com.pg.repository.VisitRequestRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Service
@RequiredArgsConstructor
public class VisitService {

    static final int FIRST_HOUR = 10;      // first slot starts at 10:00
    static final int LAST_HOUR = 17;       // last slot starts at 17:00
    static final int MAX_DAYS_AHEAD = 14;
    static final int MAX_ACTIVE_PER_USER = 3;
    static final Set<VisitStatus> ACTIVE = EnumSet.of(VisitStatus.PENDING, VisitStatus.CONFIRMED);

    private final java.time.Clock clock; // injected so tests can set "now"
    private final VisitRequestRepository visitRepository;
    private final PGRepository pgRepository;
    private final UserRepository userRepository;

    /** The hourly start times a visitor can choose from. The frontend asks for them too. */
    public static List<LocalTime> slots() {
        return IntStream.rangeClosed(FIRST_HOUR, LAST_HOUR).mapToObj(h -> LocalTime.of(h, 0)).collect(Collectors.toList());
    }

    @Transactional
    public VisitResponse request(String email, VisitRequestDto dto) {
        User user = userRepository.findByEmail(email.toLowerCase())
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        userRepository.findByIdForUpdate(user.getId()); // one person's requests run one at a time
        PG pg = pgRepository.findById(dto.getPgId())
                .filter(p -> Boolean.TRUE.equals(p.getIsActive()))
                .orElseThrow(() -> new ResourceNotFoundException("PG not found"));

        LocalDate today = LocalDate.now(clock);
        if (dto.getVisitDate().isBefore(today) || dto.getVisitDate().isAfter(today.plusDays(MAX_DAYS_AHEAD))) {
            throw new BusinessException("Pick a day within the next " + MAX_DAYS_AHEAD + " days.");
        }
        if (!slots().contains(dto.getVisitTime())) {
            throw new BusinessException("Pick one of the hourly slots between 10:00 and 17:00.");
        }
        if (dto.getVisitDate().atTime(dto.getVisitTime()).isBefore(LocalDateTime.now(clock).plusHours(1))) {
            throw new BusinessException("That time is too soon. Pick a slot at least one hour from now.");
        }
        if (visitRepository.existsByUserIdAndPgIdAndStatusInAndVisitDateGreaterThanEqual(user.getId(), pg.getId(), ACTIVE, today)) {
            throw new BusinessException("You already have a visit planned at this PG. Cancel it first to pick a new time.");
        }
        if (visitRepository.countByUserIdAndStatusInAndVisitDateGreaterThanEqual(user.getId(), ACTIVE, today) >= MAX_ACTIVE_PER_USER) {
            throw new BusinessException("You can have at most " + MAX_ACTIVE_PER_USER + " visits planned at once.");
        }

        VisitRequest saved = visitRepository.save(VisitRequest.builder()
                .pg(pg).user(user)
                .visitDate(dto.getVisitDate()).visitTime(dto.getVisitTime())
                .note(blankToNull(dto.getNote()))
                .status(VisitStatus.PENDING)
                .build());
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<VisitResponse> mine(String email) {
        User user = userRepository.findByEmail(email.toLowerCase())
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        return visitRepository.findMine(user.getId()).stream().map(this::toResponse).collect(Collectors.toList());
    }

    @Transactional
    public VisitResponse cancel(String email, Long id) {
        VisitRequest v = visitRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new ResourceNotFoundException("Visit not found"));
        if (!v.getUser().getEmail().equalsIgnoreCase(email)) {
            throw new UnauthorizedAccessException("This is not your visit.");
        }
        if (!ACTIVE.contains(v.getStatus())) {
            throw new BusinessException("This visit is already " + v.getStatus().name().toLowerCase() + ".");
        }
        v.setStatus(VisitStatus.CANCELLED);
        return toResponse(visitRepository.save(v));
    }

    @Transactional(readOnly = true)
    public List<VisitResponse> forOwner(String ownerEmail) {
        PG pg = ownerPg(ownerEmail);
        return visitRepository.findForPg(pg.getId()).stream().map(this::toResponse).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public long pendingCount(String ownerEmail) {
        PG pg = ownerPg(ownerEmail);
        return visitRepository.countByPgIdAndStatusAndVisitDateGreaterThanEqual(pg.getId(), VisitStatus.PENDING, LocalDate.now(clock));
    }

    @Transactional
    public VisitResponse decide(String ownerEmail, Long id, VisitDecisionRequest req) {
        PG pg = ownerPg(ownerEmail);
        VisitRequest v = visitRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new ResourceNotFoundException("Visit not found"));
        if (!v.getPg().getId().equals(pg.getId())) {
            throw new UnauthorizedAccessException("This visit is not for your PG.");
        }
        VisitStatus to = req.getStatus();
        if (to != VisitStatus.CONFIRMED && to != VisitStatus.DECLINED) {
            throw new BusinessException("You can only confirm or decline a visit.");
        }
        boolean allowed = (v.getStatus() == VisitStatus.PENDING)
                || (v.getStatus() == VisitStatus.CONFIRMED && to == VisitStatus.DECLINED);
        if (!allowed) {
            throw new BusinessException("This visit is already " + v.getStatus().name().toLowerCase() + ".");
        }
        if (v.getVisitDate().isBefore(LocalDate.now(clock))) {
            throw new BusinessException("This visit date has passed.");
        }
        v.setStatus(to);
        v.setOwnerNote(blankToNull(req.getOwnerNote()));
        return toResponse(visitRepository.save(v));
    }

    private PG ownerPg(String ownerEmail) {
        User owner = userRepository.findByEmail(ownerEmail.toLowerCase())
                .orElseThrow(() -> new ResourceNotFoundException("Owner not found"));
        return pgRepository.findByOwnerIdAndIsActiveTrue(owner.getId())
                .orElseThrow(() -> new ResourceNotFoundException("You have not set up your PG yet."));
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private VisitResponse toResponse(VisitRequest v) {
        boolean confirmed = v.getStatus() == VisitStatus.CONFIRMED;
        return VisitResponse.builder()
                .id(v.getId())
                .pgId(v.getPg().getId())
                .pgName(v.getPg().getName())
                .pgAddress(v.getPg().getAddress())
                .ownerPhone(confirmed ? v.getPg().getContactPhone() : null)
                .visitorName(v.getUser().getName())
                .visitorPhone(v.getUser().getPhone())
                .visitDate(v.getVisitDate())
                .visitTime(v.getVisitTime())
                .status(v.getStatus())
                .note(v.getNote())
                .ownerNote(v.getOwnerNote())
                .createdAt(v.getCreatedAt())
                .build();
    }
}
