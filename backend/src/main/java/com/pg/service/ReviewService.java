package com.pg.service;

import com.pg.dto.request.ReviewReplyRequest;
import com.pg.dto.request.ReviewRequest;
import com.pg.dto.response.ReviewResponse;
import com.pg.dto.response.ReviewStatusResponse;
import com.pg.dto.response.ReviewSummaryResponse;
import com.pg.entity.PG;
import com.pg.entity.Review;
import com.pg.entity.Tenant;
import com.pg.exception.BusinessException;
import com.pg.exception.ResourceNotFoundException;
import com.pg.exception.UnauthorizedAccessException;
import com.pg.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Reviews and ratings.
 *  - Only an ACTIVE tenant who has lived in the PG for at least 7 days can review it.
 *  - One review per stay (database unique on tenant). Writing again edits it.
 *  - The owner can reply once-or-edit, but can not change or delete the review.
 *  - Reviewer names are shortened ("Asha K.").
 */
@Service
@RequiredArgsConstructor
public class ReviewService {

    static final int MIN_STAY_DAYS = 7;

    private final java.time.Clock clock; // injected so tests can set "now"
    private final ReviewRepository reviewRepository;
    private final TenantRepository tenantRepository;
    private final PGRepository pgRepository;
    private final UserRepository userRepository;

    // ------------------------------------------------------------ tenant

    @Transactional(readOnly = true)
    public ReviewStatusResponse status(String tenantEmail) {
        Tenant t = tenantRepository.findCurrentByUserEmail(tenantEmail.toLowerCase()).orElse(null);
        if (t == null) {
            return ReviewStatusResponse.builder().eligible(false).reason("Book a room first. Only people who stayed can review.").build();
        }
        String why = whyNotEligible(t);
        Review mine = reviewRepository.findByTenantId(t.getId()).orElse(null);
        return ReviewStatusResponse.builder().eligible(why == null).reason(why)
                .pgId(t.getPg().getId()).pgName(t.getPg().getName())
                .review(mine == null ? null : toResponse(mine, t)).build();
    }

    @Transactional
    public ReviewResponse save(String tenantEmail, ReviewRequest req) {
        Tenant found = tenantRepository.findCurrentByUserEmail(tenantEmail.toLowerCase())
                .orElseThrow(() -> new BusinessException("Book a room first. Only people who stayed can review."));
        // lock the tenant row: a double click can not create two reviews
        Tenant t = tenantRepository.findByIdForUpdate(found.getId()).orElse(found);
        String why = whyNotEligible(t);
        if (why != null) throw new BusinessException(why);

        Review r = reviewRepository.findByTenantId(t.getId())
                .orElseGet(() -> Review.builder().tenant(t).pg(t.getPg()).build());
        r.setRating(req.getRating());
        r.setComment(blankToNull(req.getComment()));
        return toResponse(reviewRepository.save(r), t);
    }

    @Transactional
    public void delete(String tenantEmail) {
        Tenant t = tenantRepository.findCurrentByUserEmail(tenantEmail.toLowerCase())
                .orElseThrow(() -> new ResourceNotFoundException("You have no review."));
        Review r = reviewRepository.findByTenantId(t.getId())
                .orElseThrow(() -> new ResourceNotFoundException("You have no review."));
        reviewRepository.delete(r);
    }

    private String whyNotEligible(Tenant t) {
        // a former tenant may review too (that is when people have the most to say); they only need to have really stayed
        if (t.getJoinDate() == null || t.getJoinDate().plusDays(MIN_STAY_DAYS).isAfter(LocalDate.now(clock))) {
            return "You can review after " + MIN_STAY_DAYS + " days of staying.";
        }
        return null;
    }

    // ------------------------------------------------------------ public

    @Transactional(readOnly = true)
    public Page<ReviewResponse> forPg(Long pgId, int page, int size) {
        pgRepository.findById(pgId).filter(p -> Boolean.TRUE.equals(p.getIsActive()))
                .orElseThrow(() -> new ResourceNotFoundException("PG not found"));
        return reviewRepository.findByPg(pgId, PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 20)))
                .map(r -> toResponse(r, r.getTenant()));
    }

    @Transactional(readOnly = true)
    public ReviewSummaryResponse summary(Long pgId) {
        Map<Integer, Long> dist = new TreeMap<>();
        for (int i = 1; i <= 5; i++) dist.put(i, 0L);
        long count = 0, total = 0;
        for (Object[] row : reviewRepository.distribution(pgId)) {
            int stars = ((Number) row[0]).intValue();
            long n = ((Number) row[1]).longValue();
            dist.put(stars, n);
            count += n;
            total += stars * n;
        }
        Double avg = count == 0 ? null : round1((double) total / count);
        return ReviewSummaryResponse.builder().average(avg).count(count).distribution(dist).build();
    }

    // ------------------------------------------------------------ owner

    @Transactional(readOnly = true)
    public Page<ReviewResponse> forOwner(String ownerEmail, int page, int size) {
        PG pg = ownerPg(ownerEmail);
        return reviewRepository.findByPg(pg.getId(), PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 20)))
                .map(r -> toResponse(r, r.getTenant()));
    }

    @Transactional
    public ReviewResponse reply(String ownerEmail, Long reviewId, ReviewReplyRequest req) {
        PG pg = ownerPg(ownerEmail);
        Review r = reviewRepository.findWithTenant(reviewId)
                .orElseThrow(() -> new ResourceNotFoundException("Review not found"));
        if (!r.getPg().getId().equals(pg.getId())) {
            throw new UnauthorizedAccessException("This review is not for your PG.");
        }
        r.setOwnerReply(req.getReply().trim());
        r.setRepliedAt(LocalDateTime.now(clock));
        return toResponse(reviewRepository.save(r), r.getTenant());
    }

    // ------------------------------------------------------------ helpers

    /** For the search page: [pgId] -> {average, count}, one query for all PGs. */
    @Transactional(readOnly = true)
    public Map<Long, double[]> summaries(Collection<Long> pgIds) {
        Map<Long, double[]> map = new HashMap<>();
        if (pgIds.isEmpty()) return map;
        for (Object[] row : reviewRepository.summaries(pgIds)) {
            map.put((Long) row[0], new double[]{round1(((Number) row[1]).doubleValue()), ((Number) row[2]).doubleValue()});
        }
        return map;
    }

    static double round1(double v) {
        return BigDecimal.valueOf(v).setScale(1, RoundingMode.HALF_UP).doubleValue();
    }

    /** "Asha Kumar" -> "Asha K."; "Asha" -> "Asha"; blank -> "A tenant" */
    static String displayName(String fullName) {
        if (fullName == null || fullName.isBlank()) return "A tenant";
        String[] parts = fullName.trim().split("\\s+");
        if (parts.length == 1) return parts[0];
        return parts[0] + " " + Character.toUpperCase(parts[parts.length - 1].charAt(0)) + ".";
    }

    private ReviewResponse toResponse(Review r, Tenant t) {
        return ReviewResponse.builder().id(r.getId()).rating(r.getRating()).comment(r.getComment())
                .reviewerName(displayName(t.getUser().getName())).createdAt(r.getCreatedAt())
                .ownerReply(r.getOwnerReply()).repliedAt(r.getRepliedAt()).build();
    }

    private PG ownerPg(String ownerEmail) {
        var owner = userRepository.findByEmail(ownerEmail.toLowerCase())
                .orElseThrow(() -> new ResourceNotFoundException("Owner not found"));
        return pgRepository.findByOwnerIdAndIsActiveTrue(owner.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Set up your PG first."));
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
