package com.pg.integration;

import com.pg.entity.*;
import com.pg.entity.enums.*;
import com.pg.repository.*;
import com.pg.security.JwtUtil;
import com.pg.service.RoomBookingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

/**
 * HTTP-level ownership tests: real requests, real JWT, real filter chain, real PostgreSQL.
 * Owner A (and tenant C) take the ids of records that belong to owner B and try to read or change
 * them. Every attempt must be refused (403 or 404) AND the record must be unchanged afterwards.
 *
 * Run with:  mvn verify      (Docker must be running; skipped automatically without Docker)
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
@AutoConfigureMockMvc
class AuthorizationIT {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        r.add("spring.datasource.username", POSTGRES::getUsername);
        r.add("spring.datasource.password", POSTGRES::getPassword);
        r.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
        r.add("jwt.secret", () -> "integration-test-secret-integration-test-secret");
    }

    @Autowired MockMvc mvc;
    @Autowired JwtUtil jwt;
    @Autowired UserRepository users;
    @Autowired PGRepository pgs;
    @Autowired RoomRepository rooms;
    @Autowired TenantRepository tenants;
    @Autowired RentRecordRepository rents;
    @Autowired ComplaintRepository complaints;
    @Autowired PgChargeRepository charges;
    @Autowired PgPhotoRepository photos;
    @Autowired ReviewRepository reviews;
    @Autowired VisitRequestRepository visits;
    @Autowired RoomBookingRepository bookings;
    @Autowired InspectionRepository inspections;
    @Autowired RoomBookingService bookingService;

    User ownerA, ownerB, tenantUserB, tenantUserC, requesterD;
    PG pgA, pgB;
    Room roomB, roomB2;
    Tenant stayB;
    RentRecord rentB;
    Complaint complaintB;
    PgCharge chargeB;
    PgPhoto photoB;
    Review reviewB;
    VisitRequest visitB;
    RoomBooking bookingB;
    Inspection inspectionB;

    @BeforeEach
    void data() {
        bookings.deleteAll(); inspections.deleteAll(); reviews.deleteAll(); photos.deleteAll(); charges.deleteAll();
        complaints.deleteAll(); rents.deleteAll(); visits.deleteAll(); tenants.deleteAll();
        rooms.deleteAll(); pgs.deleteAll(); users.deleteAll();

        ownerA = user("ownerA@x.com", Role.OWNER);
        ownerB = user("ownerB@x.com", Role.OWNER);
        tenantUserB = user("tenantB@x.com", Role.TENANT);
        tenantUserC = user("tenantC@x.com", Role.TENANT);   // a tenant of nobody: also an outsider
        requesterD = user("requesterD@x.com", Role.TENANT);

        pgA = pgs.save(PG.builder().name("A").address("1").city("Pune").baseMonthlyRent(new BigDecimal("5000")).owner(ownerA).build());
        pgB = pgs.save(PG.builder().name("B").address("2").city("Pune").baseMonthlyRent(new BigDecimal("5000"))
                .bookingMode(BookingMode.ON_REQUEST).owner(ownerB).build());
        roomB = rooms.save(Room.builder().roomNumber("B1").type(RoomType.SINGLE).capacity(2).currentOccupancy(1)
                .monthlyRent(new BigDecimal("5000")).pg(pgB).build());
        roomB2 = rooms.save(Room.builder().roomNumber("B2").type(RoomType.SINGLE).capacity(2).currentOccupancy(0)
                .monthlyRent(new BigDecimal("5000")).pg(pgB).build());
        stayB = tenants.save(Tenant.builder().user(tenantUserB).pg(pgB).room(roomB).joinDate(LocalDate.now().minusDays(30)).isActive(true).build());
        rentB = rents.save(RentRecord.builder().tenant(stayB).pg(pgB).month(1).year(2026).roomNumber("B1")
                .baseAmount(new BigDecimal("5000")).totalAmount(new BigDecimal("5000")).dueDate(LocalDate.of(2026, 1, 5)).build());
        complaintB = complaints.save(Complaint.builder().tenant(stayB).pg(pgB).roomNumber("B1")
                .category(ComplaintCategory.OTHER).title("Fan").description("broken").build());
        chargeB = charges.save(PgCharge.builder().pg(pgB).name("Wifi").amount(new BigDecimal("300")).kind(ChargeKind.OPTIONAL_MONTHLY).build());
        photoB = photos.save(PgPhoto.builder().pg(pgB).storedName("x.png").contentType("image/png").sizeBytes(10L).sortOrder(1).build());
        reviewB = reviews.save(Review.builder().tenant(stayB).pg(pgB).rating(4).comment("ok").build());
        visitB = visits.save(VisitRequest.builder().pg(pgB).user(requesterD).visitDate(LocalDate.now().plusDays(2))
                .visitTime(LocalTime.of(11, 0)).build());
        bookingB = bookings.findById(bookingService.request("requesterD@x.com", bookingFor(roomB2.getId())).getId()).orElseThrow();
        inspectionB = inspections.save(Inspection.builder().tenant(stayB).pg(pgB).type(InspectionType.CHECK_IN).roomNumber("B1").build());
    }

    private User user(String email, Role role) {
        return users.save(User.builder().name(email).email(email).password("x").role(role).build());
    }

    private com.pg.dto.request.BookingRequest bookingFor(Long roomId) {
        com.pg.dto.request.BookingRequest b = new com.pg.dto.request.BookingRequest();
        b.setRoomId(roomId);
        b.setMoveInDate(LocalDate.now());
        return b;
    }

    private String token(User u) { return "Bearer " + jwt.generateToken(u.getEmail(), u.getRole().name()); }

    /** Sends the request as `who` and asserts it is refused with 403 or 404. */
    private void refused(MockHttpServletRequestBuilder req, User who, String json) throws Exception {
        req.header("Authorization", token(who));
        if (json != null) req.contentType(MediaType.APPLICATION_JSON).content(json);
        int status = mvc.perform(req).andReturn().getResponse().getStatus();
        assertTrue(status == 403 || status == 404, "expected 403/404 but got " + status);
    }

    // ------------------------------------------------------------ owner A against owner B's records

    @Test
    void ownerACanNotChangeOwnerBsTenantOrRooms() throws Exception {
        refused(put("/api/owner/tenants/" + stayB.getId() + "/allocate-room/" + roomB2.getId()), ownerA, null);
        refused(put("/api/owner/tenants/" + stayB.getId() + "/checkout"), ownerA, null);
        refused(put("/api/owner/rooms/" + roomB.getId()), ownerA,
                "{\"roomNumber\":\"HACK\",\"type\":\"SINGLE\",\"capacity\":1,\"monthlyRent\":1}");
        refused(put("/api/owner/rooms/" + roomB.getId() + "/maintenance?underMaintenance=true"), ownerA, null);

        Tenant t = tenants.findById(stayB.getId()).orElseThrow();
        assertTrue(t.getIsActive());
        assertEquals(roomB.getId(), t.getRoom().getId());
        Room r = rooms.findById(roomB.getId()).orElseThrow();
        assertEquals("B1", r.getRoomNumber());
        assertEquals(1, r.getCurrentOccupancy());
        assertNotEquals(RoomStatus.UNDER_MAINTENANCE, r.getStatus());
    }

    @Test
    void ownerACanNotTouchOwnerBsMoneyComplaintsAndCharges() throws Exception {
        refused(put("/api/owner/rent/" + rentB.getId() + "/mark-paid"), ownerA, "{}");
        refused(put("/api/owner/complaints/" + complaintB.getId() + "/status"), ownerA,
                "{\"status\":\"RESOLVED\",\"ownerResponse\":\"hacked\"}");
        refused(put("/api/owner/charges/" + chargeB.getId()), ownerA,
                "{\"name\":\"Hacked\",\"amount\":1,\"kind\":\"ONE_TIME\"}");
        refused(delete("/api/owner/charges/" + chargeB.getId()), ownerA, null);

        assertEquals(RentStatus.PENDING, rents.findById(rentB.getId()).orElseThrow().getStatus());
        assertEquals(ComplaintStatus.OPEN, complaints.findById(complaintB.getId()).orElseThrow().getStatus());
        PgCharge c = charges.findById(chargeB.getId()).orElseThrow();
        assertEquals("Wifi", c.getName());
        assertEquals(0, new BigDecimal("300").compareTo(c.getAmount()));
    }

    @Test
    void ownerACanNotTouchOwnerBsPhotosReviewsVisitsAndBookings() throws Exception {
        refused(put("/api/owner/pg-photos/" + photoB.getId() + "/cover"), ownerA, null);
        refused(delete("/api/owner/pg-photos/" + photoB.getId()), ownerA, null);
        refused(put("/api/owner/reviews/" + reviewB.getId() + "/reply"), ownerA, "{\"reply\":\"hacked\"}");
        refused(put("/api/owner/visits/" + visitB.getId()), ownerA, "{\"status\":\"CONFIRMED\"}");
        refused(put("/api/owner/booking-requests/" + bookingB.getId()), ownerA, "{\"status\":\"APPROVED\"}");

        assertTrue(photos.findById(photoB.getId()).isPresent());
        assertNull(reviews.findById(reviewB.getId()).orElseThrow().getOwnerReply());
        assertEquals(VisitStatus.PENDING, visits.findById(visitB.getId()).orElseThrow().getStatus());
        assertEquals(BookingStatus.PENDING, bookings.findById(bookingB.getId()).orElseThrow().getStatus());
        assertEquals(0, tenants.findAll().stream().filter(t -> t.getUser().getId().equals(requesterD.getId())).count());
    }

    @Test
    void ownerACanNotReadOwnerBsInspections() throws Exception {
        refused(get("/api/inspections/" + inspectionB.getId()), ownerA, null);
        refused(post("/api/inspections/" + inspectionB.getId() + "/items"), ownerA,
                "{\"area\":\"Wall\",\"condition\":\"DAMAGED\",\"note\":\"x\"}");
        refused(post("/api/inspections/" + inspectionB.getId() + "/confirm"), ownerA, null);
    }

    // ------------------------------------------------------------ an outsider tenant

    @Test
    void anOutsiderTenantCanNotReadOrChangeSomeoneElsesRecords() throws Exception {
        refused(put("/api/tenant/complaints/" + complaintB.getId() + "/close"), tenantUserC, null);
        refused(put("/api/tenant/visits/" + visitB.getId() + "/cancel"), tenantUserC, null);
        refused(put("/api/tenant/booking-requests/" + bookingB.getId() + "/cancel"), tenantUserC, null);
        refused(get("/api/inspections/" + inspectionB.getId()), tenantUserC, null);
        refused(post("/api/inspections/" + inspectionB.getId() + "/confirm"), tenantUserC, null);

        assertEquals(ComplaintStatus.OPEN, complaints.findById(complaintB.getId()).orElseThrow().getStatus());
        assertEquals(VisitStatus.PENDING, visits.findById(visitB.getId()).orElseThrow().getStatus());
        assertEquals(BookingStatus.PENDING, bookings.findById(bookingB.getId()).orElseThrow().getStatus());
    }

    // ------------------------------------------------------------ roles and tokens

    @Test
    void rolesAreSeparatedAndAnonymousRequestsAreRefused() throws Exception {
        refused(get("/api/owner/tenants"), tenantUserC, null);       // tenant on an owner URL
        refused(get("/api/tenant/profile"), ownerA, null);           // owner on a tenant URL
        refused(get("/api/admin/pgs"), ownerA, null);                // owner on an admin URL
        refused(get("/api/admin/pgs"), tenantUserC, null);

        int anonymous = mvc.perform(get("/api/owner/tenants")).andReturn().getResponse().getStatus();
        assertTrue(anonymous == 401 || anonymous == 403, "anonymous got " + anonymous);
        int garbage = mvc.perform(get("/api/owner/tenants").header("Authorization", "Bearer not.a.token"))
                .andReturn().getResponse().getStatus();
        assertTrue(garbage == 401 || garbage == 403, "garbage token got " + garbage);
    }

    @Test
    void aDeactivatedAccountsTokenStopsWorkingImmediately() throws Exception {
        String t = token(ownerA);
        assertEquals(200, mvc.perform(get("/api/owner/tenants").header("Authorization", t)).andReturn().getResponse().getStatus());
        ownerA.setIsActive(false);
        users.save(ownerA);
        int status = mvc.perform(get("/api/owner/tenants").header("Authorization", t)).andReturn().getResponse().getStatus();
        assertTrue(status == 401 || status == 403, "deactivated owner got " + status);
    }
}
