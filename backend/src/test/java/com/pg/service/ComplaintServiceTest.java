package com.pg.service;

import com.pg.dto.request.ComplaintUpdateRequest;
import com.pg.entity.*;
import com.pg.entity.enums.ComplaintStatus;
import com.pg.entity.enums.Role;
import com.pg.exception.BusinessException;
import com.pg.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ComplaintServiceTest {

    @Mock ComplaintRepository complaintRepository;
    @Mock TenantRepository tenantRepository;
    @Mock PGRepository pgRepository;
    @Mock UserRepository userRepository;
    @org.mockito.Spy java.time.Clock clock = java.time.Clock.systemDefaultZone();
    @InjectMocks ComplaintService service;

    private static final String OWNER_EMAIL = "owner@example.com";
    private PG pg;
    private Tenant tenant;

    @BeforeEach
    void setUp() {
        User owner = User.builder().id(1L).email(OWNER_EMAIL).role(Role.OWNER).build();
        pg = PG.builder().id(10L).name("PG").owner(owner).build();
        User tenantUser = User.builder().id(2L).name("Tenant").email("t@example.com").role(Role.TENANT).build();
        tenant = Tenant.builder().id(100L).user(tenantUser).pg(pg).build();
        when(userRepository.findByEmail(OWNER_EMAIL)).thenReturn(Optional.of(owner));
        when(pgRepository.findByOwnerIdAndIsActiveTrue(1L)).thenReturn(Optional.of(pg));
        when(complaintRepository.existsByIdAndPgId(5L, 10L)).thenReturn(true);
        when(complaintRepository.save(any(Complaint.class))).thenAnswer(i -> i.getArgument(0));
    }

    @Test
    void aFormerTenantWithNoCurrentStayCanNotRaiseAComplaint() {
        when(tenantRepository.findActiveByUserEmail("t@example.com")).thenReturn(Optional.empty());
        assertThrows(com.pg.exception.BusinessException.class,
                () -> service.raiseComplaint("t@example.com", new com.pg.dto.request.ComplaintRequest()));
    }

    private Complaint complaint(ComplaintStatus status) {
        Complaint c = Complaint.builder().id(5L).tenant(tenant).pg(pg).title("Fan").description("broken")
                .status(status).build();
        when(complaintRepository.findById(5L)).thenReturn(Optional.of(c));
        return c;
    }

    private ComplaintUpdateRequest update(ComplaintStatus status) {
        ComplaintUpdateRequest r = new ComplaintUpdateRequest();
        r.setStatus(status);
        return r;
    }

    @Test
    void ownerCanNotCloseOrReopenDirectly() {
        complaint(ComplaintStatus.OPEN);
        assertThrows(BusinessException.class, () -> service.updateComplaintStatus(OWNER_EMAIL, 5L, update(ComplaintStatus.CLOSED)));
        assertThrows(BusinessException.class, () -> service.updateComplaintStatus(OWNER_EMAIL, 5L, update(ComplaintStatus.OPEN)));
        verify(complaintRepository, never()).save(any());
    }

    @Test
    void closedComplaintCanNotChange() {
        complaint(ComplaintStatus.CLOSED);
        assertThrows(BusinessException.class, () -> service.updateComplaintStatus(OWNER_EMAIL, 5L, update(ComplaintStatus.IN_PROGRESS)));
    }

    @Test
    void openToResolvedSetsResolvedAt() {
        Complaint c = complaint(ComplaintStatus.OPEN);
        service.updateComplaintStatus(OWNER_EMAIL, 5L, update(ComplaintStatus.RESOLVED));
        assertEquals(ComplaintStatus.RESOLVED, c.getStatus());
        assertNotNull(c.getResolvedAt());
    }

    @Test
    void sendingTheSameStatusAgainKeepsTheOriginalResolvedAt() {
        Complaint c = complaint(ComplaintStatus.RESOLVED);
        LocalDateTime original = LocalDateTime.now().minusDays(2);
        c.setResolvedAt(original);
        ComplaintUpdateRequest request = update(ComplaintStatus.RESOLVED);
        request.setOwnerResponse("note only");

        service.updateComplaintStatus(OWNER_EMAIL, 5L, request);

        assertEquals(original, c.getResolvedAt());
        assertEquals("note only", c.getOwnerResponse());
    }

    @Test
    void resolvedBackToInProgressClearsResolvedAt() {
        Complaint c = complaint(ComplaintStatus.RESOLVED);
        c.setResolvedAt(LocalDateTime.now().minusDays(1));
        service.updateComplaintStatus(OWNER_EMAIL, 5L, update(ComplaintStatus.IN_PROGRESS));
        assertNull(c.getResolvedAt());
    }

    @Test
    void autoCloseUsesOneBulkUpdateAndNeverLoadsAllComplaints() {
        service.autoCloseOldResolvedComplaints();

        verify(complaintRepository).autoCloseResolvedBefore(any(LocalDateTime.class), any(LocalDateTime.class),
                eq("Auto-closed after 7 days"), eq(ComplaintStatus.RESOLVED), eq(ComplaintStatus.CLOSED));
        verify(complaintRepository, never()).findAll();
    }
}
