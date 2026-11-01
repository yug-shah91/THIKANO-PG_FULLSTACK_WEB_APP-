package com.pg.service;

import com.pg.dto.request.ReviewReplyRequest;
import com.pg.dto.request.ReviewRequest;
import com.pg.dto.response.ReviewResponse;
import com.pg.dto.response.ReviewStatusResponse;
import com.pg.dto.response.ReviewSummaryResponse;
import com.pg.entity.*;
import com.pg.entity.enums.Role;
import com.pg.exception.BusinessException;
import com.pg.exception.UnauthorizedAccessException;
import com.pg.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ReviewServiceTest {

    @Mock ReviewRepository reviewRepository;
    @Mock TenantRepository tenantRepository;
    @Mock PGRepository pgRepository;
    @Mock UserRepository userRepository;
    @org.mockito.Spy java.time.Clock clock = java.time.Clock.systemDefaultZone();
    @InjectMocks ReviewService service;

    User ownerUser, otherOwner, tenantUser;
    PG pg, otherPg;
    Tenant tenant;

    @BeforeEach
    void setUp() {
        ownerUser = User.builder().id(1L).email("o@x.com").role(Role.OWNER).build();
        otherOwner = User.builder().id(2L).email("o2@x.com").role(Role.OWNER).build();
        tenantUser = User.builder().id(3L).name("Asha Kumar").email("t@x.com").role(Role.TENANT).build();
        pg = PG.builder().id(10L).name("Sunrise").owner(ownerUser).isActive(true).build();
        otherPg = PG.builder().id(11L).owner(otherOwner).isActive(true).build();
        tenant = Tenant.builder().id(20L).user(tenantUser).pg(pg).isActive(true)
                .joinDate(LocalDate.now().minusDays(30)).build();

        when(userRepository.findByEmail("o@x.com")).thenReturn(Optional.of(ownerUser));
        when(userRepository.findByEmail("o2@x.com")).thenReturn(Optional.of(otherOwner));
        when(pgRepository.findByOwnerIdAndIsActiveTrue(1L)).thenReturn(Optional.of(pg));
        when(pgRepository.findByOwnerIdAndIsActiveTrue(2L)).thenReturn(Optional.of(otherPg));
        when(tenantRepository.findCurrentByUserEmail("t@x.com")).thenReturn(Optional.of(tenant));
        when(tenantRepository.findByIdForUpdate(20L)).thenReturn(Optional.of(tenant));
        when(reviewRepository.save(any(Review.class))).thenAnswer(i -> i.getArgument(0));
    }

    private ReviewRequest req(int stars, String comment) {
        ReviewRequest r = new ReviewRequest();
        r.setRating(stars); r.setComment(comment);
        return r;
    }

    @Test
    void aFormerTenantWhoReallyStayedCanStillReview() {
        tenant.setIsActive(false); // checked out
        assertTrue(service.status("t@x.com").isEligible());
        assertEquals(4, service.save("t@x.com", req(4, "Good")).getRating());
    }

    @Test
    void aTenantWhoStayedAWeekCanReviewAndTheNameIsShortened() {
        ReviewResponse r = service.save("t@x.com", req(5, "Great food"));
        assertEquals(5, r.getRating());
        assertEquals("Asha K.", r.getReviewerName());
    }

    @Test
    void tooNewCheckedOutOrWithoutStayCanNotReview() {
        tenant.setJoinDate(LocalDate.now().minusDays(6));
        assertThrows(BusinessException.class, () -> service.save("t@x.com", req(4, null)));
        tenant.setJoinDate(LocalDate.now().minusDays(7));           // exactly 7 days is enough
        assertDoesNotThrow(() -> service.save("t@x.com", req(4, null)));

        tenant.setIsActive(false);
        assertThrows(BusinessException.class, () -> service.save("t@x.com", req(4, null)));

        when(tenantRepository.findCurrentByUserEmail("nobody@x.com")).thenReturn(Optional.empty());
        assertThrows(BusinessException.class, () -> service.save("nobody@x.com", req(4, null)));
    }

    @Test
    void writingAgainEditsTheSameReviewInsteadOfCreatingASecond() {
        Review existing = Review.builder().id(5L).tenant(tenant).pg(pg).rating(3).comment("ok").build();
        when(reviewRepository.findByTenantId(20L)).thenReturn(Optional.of(existing));

        service.save("t@x.com", req(5, "better now"));

        assertEquals(5, existing.getRating());
        assertEquals("better now", existing.getComment());
        verify(reviewRepository).save(existing);
    }

    @Test
    void statusExplainsWhyAndShowsMyReview() {
        tenant.setJoinDate(LocalDate.now().minusDays(2));
        ReviewStatusResponse s = service.status("t@x.com");
        assertFalse(s.isEligible());
        assertNotNull(s.getReason());
        assertEquals(10L, s.getPgId());
    }

    @Test
    void onlyTheOwnerOfThatPgCanReplyAndCanNotEditTheRating() {
        Review r = Review.builder().id(5L).tenant(tenant).pg(pg).rating(2).comment("noisy").build();
        when(reviewRepository.findWithTenant(5L)).thenReturn(Optional.of(r));
        ReviewReplyRequest reply = new ReviewReplyRequest();
        reply.setReply("  Sorry, we fixed it.  ");

        assertThrows(UnauthorizedAccessException.class, () -> service.reply("o2@x.com", 5L, reply));

        ReviewResponse out = service.reply("o@x.com", 5L, reply);
        assertEquals("Sorry, we fixed it.", out.getOwnerReply());
        assertNotNull(out.getRepliedAt());
        assertEquals(2, out.getRating()); // untouched
    }

    @Test
    void summaryCountsAverageAndFillsAllFiveBars() {
        when(reviewRepository.distribution(10L)).thenReturn(List.of(new Object[]{5, 3L}, new Object[]{4, 1L}));
        ReviewSummaryResponse s = service.summary(10L);
        assertEquals(4, s.getCount());
        assertEquals(4.8, s.getAverage());                 // (15 + 4) / 4 = 4.75 -> 4.8
        assertEquals(0L, s.getDistribution().get(1));
        assertEquals(3L, s.getDistribution().get(5));
        assertEquals(5, s.getDistribution().size());
    }

    @Test
    void noReviewsMeansNoAverageNotZero() {
        when(reviewRepository.distribution(10L)).thenReturn(List.of());
        assertNull(service.summary(10L).getAverage());
    }

    @Test
    void displayNames() {
        assertEquals("Asha K.", ReviewService.displayName("Asha Kumar"));
        assertEquals("Asha K.", ReviewService.displayName("  asha   kumar "  .replace("asha", "Asha")));
        assertEquals("Asha", ReviewService.displayName("Asha"));
        assertEquals("A tenant", ReviewService.displayName(" "));
        assertEquals("A tenant", ReviewService.displayName(null));
    }
}
