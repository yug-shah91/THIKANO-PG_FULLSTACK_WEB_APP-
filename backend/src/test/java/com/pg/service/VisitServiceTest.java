package com.pg.service;

import com.pg.dto.request.VisitDecisionRequest;
import com.pg.dto.request.VisitRequestDto;
import com.pg.dto.response.VisitResponse;
import com.pg.entity.PG;
import com.pg.entity.User;
import com.pg.entity.VisitRequest;
import com.pg.entity.enums.VisitStatus;
import com.pg.exception.BusinessException;
import com.pg.exception.UnauthorizedAccessException;
import com.pg.repository.PGRepository;
import com.pg.repository.UserRepository;
import com.pg.repository.VisitRequestRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class VisitServiceTest {

    @Mock VisitRequestRepository visitRepository;
    @Mock PGRepository pgRepository;
    @Mock UserRepository userRepository;
    @org.mockito.Spy java.time.Clock clock = java.time.Clock.systemDefaultZone();
    @InjectMocks VisitService service;

    private User tenant, owner;
    private PG pg;

    @BeforeEach
    void setUp() {
        tenant = User.builder().id(1L).name("Asha").email("asha@x.com").phone("999").build();
        owner = User.builder().id(2L).name("Ravi").email("ravi@x.com").build();
        pg = PG.builder().id(10L).name("Sunrise").address("1 Main St").contactPhone("888").isActive(true).owner(owner).build();
        when(userRepository.findByEmail("asha@x.com")).thenReturn(Optional.of(tenant));
        when(userRepository.findByEmail("ravi@x.com")).thenReturn(Optional.of(owner));
        when(pgRepository.findById(10L)).thenReturn(Optional.of(pg));
        when(pgRepository.findByOwnerIdAndIsActiveTrue(2L)).thenReturn(Optional.of(pg));
        when(visitRepository.save(any(VisitRequest.class))).thenAnswer(i -> i.getArgument(0));
    }

    private VisitRequestDto dto(LocalDate d, LocalTime t) {
        VisitRequestDto dto = new VisitRequestDto();
        dto.setPgId(10L); dto.setVisitDate(d); dto.setVisitTime(t);
        return dto;
    }

    @Test
    void requestsAFutureSlot() {
        VisitResponse r = service.request("asha@x.com", dto(LocalDate.now().plusDays(2), LocalTime.of(11, 0)));
        assertEquals(VisitStatus.PENDING, r.getStatus());
        assertNull(r.getOwnerPhone()); // owner phone stays hidden until confirmed
    }

    @Test
    void rejectsPastDayFarDayAndOddTime() {
        assertThrows(BusinessException.class, () -> service.request("asha@x.com", dto(LocalDate.now().minusDays(1), LocalTime.of(11, 0))));
        assertThrows(BusinessException.class, () -> service.request("asha@x.com", dto(LocalDate.now().plusDays(30), LocalTime.of(11, 0))));
        assertThrows(BusinessException.class, () -> service.request("asha@x.com", dto(LocalDate.now().plusDays(2), LocalTime.of(11, 30))));
        assertThrows(BusinessException.class, () -> service.request("asha@x.com", dto(LocalDate.now().plusDays(2), LocalTime.of(22, 0))));
    }

    @Test
    void rejectsSecondActiveVisitAtSamePg() {
        when(visitRepository.existsByUserIdAndPgIdAndStatusInAndVisitDateGreaterThanEqual(any(), any(), any(), any())).thenReturn(true);
        assertThrows(BusinessException.class, () -> service.request("asha@x.com", dto(LocalDate.now().plusDays(2), LocalTime.of(11, 0))));
    }

    @Test
    void ownerConfirmShowsPhoneAndOtherOwnerIsBlocked() {
        VisitRequest v = VisitRequest.builder().id(5L).pg(pg).user(tenant)
                .visitDate(LocalDate.now().plusDays(1)).visitTime(LocalTime.of(10, 0)).status(VisitStatus.PENDING).build();
        when(visitRepository.findByIdForUpdate(5L)).thenReturn(Optional.of(v));
        VisitDecisionRequest req = new VisitDecisionRequest();
        req.setStatus(VisitStatus.CONFIRMED);
        VisitResponse r = service.decide("ravi@x.com", 5L, req);
        assertEquals(VisitStatus.CONFIRMED, r.getStatus());
        assertEquals("888", r.getOwnerPhone());

        User other = User.builder().id(3L).email("other@x.com").build();
        PG otherPg = PG.builder().id(11L).isActive(true).owner(other).build();
        when(userRepository.findByEmail("other@x.com")).thenReturn(Optional.of(other));
        when(pgRepository.findByOwnerIdAndIsActiveTrue(3L)).thenReturn(Optional.of(otherPg));
        assertThrows(UnauthorizedAccessException.class, () -> service.decide("other@x.com", 5L, req));
    }

    @Test
    void tenantCanOnlyCancelOwnActiveVisit() {
        VisitRequest v = VisitRequest.builder().id(6L).pg(pg).user(tenant)
                .visitDate(LocalDate.now().plusDays(1)).visitTime(LocalTime.of(10, 0)).status(VisitStatus.PENDING).build();
        when(visitRepository.findByIdForUpdate(6L)).thenReturn(Optional.of(v));
        assertThrows(UnauthorizedAccessException.class, () -> service.cancel("someone@x.com", 6L));
        assertEquals(VisitStatus.CANCELLED, service.cancel("asha@x.com", 6L).getStatus());
        assertThrows(BusinessException.class, () -> service.cancel("asha@x.com", 6L));
    }
}
