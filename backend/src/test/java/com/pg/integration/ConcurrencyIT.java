package com.pg.integration;

import com.pg.dto.request.BookingRequest;
import com.pg.dto.request.VisitRequestDto;
import com.pg.entity.*;
import com.pg.entity.enums.Role;
import com.pg.entity.enums.RoomType;
import com.pg.repository.*;
import com.pg.service.RentService;
import com.pg.service.RoomBookingService;
import com.pg.service.TenantService;
import com.pg.service.VisitService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Real simultaneous requests against a real PostgreSQL (started in Docker by Testcontainers).
 * Mockito tests cannot show race conditions, because nothing runs at the same time there.
 *
 * Run with:  mvn verify      (Docker must be running; skipped automatically without Docker)
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
class ConcurrencyIT {

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

    @Autowired UserRepository users;
    @Autowired PGRepository pgs;
    @Autowired RoomRepository rooms;
    @Autowired TenantRepository tenants;
    @Autowired RentRecordRepository rents;
    @Autowired VisitRequestRepository visits;
    @Autowired TenantService tenantService;
    @Autowired VisitService visitService;
    @Autowired RentService rentService;
    @Autowired RoomBookingService roomBookingService;
    @Autowired RoomBookingRepository roomBookings;

    PG pg;
    User owner;

    @BeforeEach
    void clean() {
        roomBookings.deleteAll();
        rents.deleteAll();
        visits.deleteAll();
        tenants.deleteAll();
        rooms.deleteAll();
        pgs.deleteAll();
        users.deleteAll();
        owner = users.save(User.builder().name("Owner").email("owner@x.com").password("x").role(Role.OWNER).build());
        pg = pgs.save(PG.builder().name("Test PG").address("1 Road").city("Pune")
                .baseMonthlyRent(new BigDecimal("5000")).owner(owner).build());
    }

    private Room room(String number, int capacity) {
        return rooms.save(Room.builder().roomNumber(number).type(RoomType.SINGLE).capacity(capacity)
                .monthlyRent(new BigDecimal("5000")).pg(pg).build());
    }

    private User tenantUser(String email) {
        return users.save(User.builder().name(email).email(email).password("x").role(Role.TENANT).build());
    }

    /** Runs all tasks at the same instant on separate threads; returns how many succeeded. */
    private int runTogether(List<Callable<Object>> tasks) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(tasks.size());
        CountDownLatch ready = new CountDownLatch(tasks.size());
        CountDownLatch go = new CountDownLatch(1);
        AtomicInteger ok = new AtomicInteger();
        List<Future<?>> futures = new ArrayList<>();
        for (Callable<Object> t : tasks) {
            futures.add(pool.submit(() -> {
                ready.countDown();
                go.await();
                try { t.call(); ok.incrementAndGet(); } catch (Exception expectedFailure) { /* losers fail */ }
                return null;
            }));
        }
        ready.await();
        go.countDown();
        for (Future<?> f : futures) f.get(60, TimeUnit.SECONDS);
        pool.shutdown();
        return ok.get();
    }

    private BookingRequest booking(Long roomId) {
        BookingRequest b = new BookingRequest();
        b.setRoomId(roomId);
        b.setMoveInDate(LocalDate.now());
        return b;
    }

    @Test
    void tenPeopleRaceForTheLastBedExactlyOneWins() throws Exception {
        Room r = room("101", 1);
        List<Callable<Object>> tasks = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            String email = "t" + i + "@x.com";
            tenantUser(email);
            tasks.add(() -> tenantService.bookRoom(email, booking(r.getId())));
        }

        assertEquals(1, runTogether(tasks));
        assertEquals(1, rooms.findById(r.getId()).orElseThrow().getCurrentOccupancy());
        assertEquals(1, tenants.count());
    }

    @Test
    void sameTenantDoubleClickingTwoRoomsGetsOnlyOneBooking() throws Exception {
        Room a = room("101", 2), b = room("102", 2);
        tenantUser("one@x.com");
        List<Callable<Object>> tasks = List.of(
                () -> tenantService.bookRoom("one@x.com", booking(a.getId())),
                () -> tenantService.bookRoom("one@x.com", booking(b.getId())),
                () -> tenantService.bookRoom("one@x.com", booking(a.getId())));

        assertEquals(1, runTogether(tasks));
        assertEquals(1, tenants.count());
        int occupied = rooms.findById(a.getId()).orElseThrow().getCurrentOccupancy()
                + rooms.findById(b.getId()).orElseThrow().getCurrentOccupancy();
        assertEquals(1, occupied); // no bed is taken without a tenant
    }

    @Test
    void sameVisitorSendingTheSameVisitRequestManyTimesGetsOne() throws Exception {
        tenantUser("v@x.com");
        List<Callable<Object>> tasks = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            VisitRequestDto dto = new VisitRequestDto();
            dto.setPgId(pg.getId());
            dto.setVisitDate(LocalDate.now().plusDays(2));
            dto.setVisitTime(LocalTime.of(11, 0));
            tasks.add(() -> visitService.request("v@x.com", dto));
        }

        assertEquals(1, runTogether(tasks));
        assertEquals(1, visits.count());
    }

    @Test
    void rentGeneratedByTwoOwnersAtTheSameTimeHasNoDuplicatesAndNoFailures() throws Exception {
        Room r = room("101", 4);
        for (int i = 0; i < 3; i++) {
            String email = "t" + i + "@x.com";
            tenantUser(email);
            tenantService.bookRoom(email, booking(r.getId()));
        }
        List<Callable<Object>> tasks = new ArrayList<>();
        for (int i = 0; i < 4; i++) tasks.add(() -> rentService.generateRentForOwnerPg("owner@x.com"));

        // every run must SUCCEED (the losers just see "already existed"), not fail
        assertEquals(4, runTogether(tasks));
        assertEquals(3, rents.count());
    }

    @Test
    void onRequestPgHoldsTheLastBedForExactlyOneRequester() throws Exception {
        pg.setBookingMode(com.pg.entity.enums.BookingMode.ON_REQUEST);
        pgs.save(pg);
        Room r = room("101", 1);
        List<Callable<Object>> tasks = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            String email = "r" + i + "@x.com";
            tenantUser(email);
            tasks.add(() -> roomBookingService.request(email, booking(r.getId())));
        }

        assertEquals(1, runTogether(tasks));
        assertEquals(1, rooms.findById(r.getId()).orElseThrow().getCurrentOccupancy()); // one held bed
        assertEquals(1, roomBookings.count());
    }

    @Test
    void approvingAnExpiredRequestReallyReleasesTheBedAfterCommit() {
        pg.setBookingMode(com.pg.entity.enums.BookingMode.ON_REQUEST);
        pgs.save(pg);
        Room r = room("101", 1);
        User tu = tenantUser("late@x.com");
        roomBookingService.request("late@x.com", booking(r.getId()));
        RoomBooking b = roomBookings.findAll().get(0);
        b.setExpiresAt(java.time.LocalDateTime.now().minusMinutes(5)); // pretend 48h passed
        roomBookings.save(b);

        com.pg.dto.request.BookingDecisionRequest d = new com.pg.dto.request.BookingDecisionRequest();
        d.setStatus(com.pg.entity.enums.BookingStatus.APPROVED);
        var response = roomBookingService.decide("owner@x.com", b.getId(), d);

        // the whole point: this is what the DATABASE holds after the call, not just the returned object
        assertEquals(com.pg.entity.enums.BookingStatus.EXPIRED, response.getStatus());
        assertEquals(com.pg.entity.enums.BookingStatus.EXPIRED, roomBookings.findById(b.getId()).orElseThrow().getStatus());
        assertEquals(0, rooms.findById(r.getId()).orElseThrow().getCurrentOccupancy());
        assertEquals(0, tenants.count());
    }

    @Test
    void cancelAndDeclineAtTheSameTimeReleaseTheBedOnlyOnce() throws Exception {
        pg.setBookingMode(com.pg.entity.enums.BookingMode.ON_REQUEST);
        pgs.save(pg);
        Room r = room("101", 3);
        tenantUser("c@x.com");
        roomBookingService.request("c@x.com", booking(r.getId()));
        Long id = roomBookings.findAll().get(0).getId();
        com.pg.dto.request.BookingDecisionRequest d = new com.pg.dto.request.BookingDecisionRequest();
        d.setStatus(com.pg.entity.enums.BookingStatus.DECLINED);

        int ok = runTogether(List.of(
                () -> roomBookingService.cancel("c@x.com", id),
                () -> roomBookingService.decide("owner@x.com", id, d)));

        assertEquals(1, ok); // the loser is refused
        assertEquals(0, rooms.findById(r.getId()).orElseThrow().getCurrentOccupancy()); // 1 hold, released once
    }

    @Test
    void duplicateRoomNumberInOnePgIsRejectedByTheDatabase() {
        room("101", 2);
        assertThrows(Exception.class, () -> room("101", 2));
    }

    // ------------------------------------------------------------------ lock order / deadlocks

    /** Like runTogether, but returns every failure so a test can prove none of them was a deadlock. */
    private List<Throwable> failuresOfTogether(List<Callable<Object>> tasks) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(tasks.size());
        CountDownLatch ready = new CountDownLatch(tasks.size());
        CountDownLatch go = new CountDownLatch(1);
        List<Throwable> failures = new CopyOnWriteArrayList<>();
        List<Future<?>> futures = new ArrayList<>();
        for (Callable<Object> t : tasks) {
            futures.add(pool.submit(() -> {
                ready.countDown();
                go.await();
                try { t.call(); } catch (Throwable e) { failures.add(e); }
                return null;
            }));
        }
        ready.await();
        go.countDown();
        for (Future<?> f : futures) f.get(60, TimeUnit.SECONDS);
        pool.shutdown();
        return failures;
    }

    private static boolean isDeadlock(Throwable t) {
        for (Throwable c = t; c != null; c = c.getCause()) {
            if (c.getMessage() != null && c.getMessage().toLowerCase().contains("deadlock")) return true;
        }
        return false;
    }

    private Tenant stay(User u, Room r) {
        r.setCurrentOccupancy(r.getCurrentOccupancy() + 1);
        rooms.save(r);
        return tenants.save(Tenant.builder().user(u).pg(pg).room(r).joinDate(LocalDate.now()).isActive(true).build());
    }

    @Test
    void twoTenantsSwappingRoomsAtTheSameTimeDoNotDeadlock() throws Exception {
        Room r1 = room("101", 2);
        Room r2 = room("102", 2);
        Tenant a = stay(tenantUser("a@x.com"), r1);
        Tenant b = stay(tenantUser("b@x.com"), r2);

        List<Throwable> failures = failuresOfTogether(List.of(
                () -> tenantService.allocateRoom("owner@x.com", a.getId(), r2.getId()),
                () -> tenantService.allocateRoom("owner@x.com", b.getId(), r1.getId())));

        assertTrue(failures.stream().noneMatch(ConcurrencyIT::isDeadlock), "deadlock: " + failures);
        assertTrue(failures.isEmpty(), "both moves are valid: " + failures);
        assertEquals(1, rooms.findById(r1.getId()).orElseThrow().getCurrentOccupancy());
        assertEquals(1, rooms.findById(r2.getId()).orElseThrow().getCurrentOccupancy());
    }

    @Test
    void cancelDeclineApproveAndExpiryTogetherEndWithExactlyOneOutcomeAndCorrectOccupancy() throws Exception {
        pg.setBookingMode(com.pg.entity.enums.BookingMode.ON_REQUEST);
        pgs.save(pg);
        Room r = room("101", 3);
        tenantUser("c@x.com");
        roomBookingService.request("c@x.com", booking(r.getId()));
        Long id = roomBookings.findAll().get(0).getId();
        com.pg.dto.request.BookingDecisionRequest approve = new com.pg.dto.request.BookingDecisionRequest();
        approve.setStatus(com.pg.entity.enums.BookingStatus.APPROVED);
        com.pg.dto.request.BookingDecisionRequest decline = new com.pg.dto.request.BookingDecisionRequest();
        decline.setStatus(com.pg.entity.enums.BookingStatus.DECLINED);

        List<Throwable> failures = failuresOfTogether(List.of(
                () -> roomBookingService.cancel("c@x.com", id),
                () -> roomBookingService.decide("owner@x.com", id, approve),
                () -> roomBookingService.decide("owner@x.com", id, decline),
                () -> roomBookingService.expireOne(id)));

        assertTrue(failures.stream().noneMatch(ConcurrencyIT::isDeadlock), "deadlock: " + failures);
        var finalStatus = roomBookings.findById(id).orElseThrow().getStatus();
        int occupancy = rooms.findById(r.getId()).orElseThrow().getCurrentOccupancy();
        if (finalStatus == com.pg.entity.enums.BookingStatus.APPROVED) {
            assertEquals(1, occupancy);          // the hold became the tenant's bed, counted once
            assertEquals(1, tenants.count());
        } else {
            assertEquals(0, occupancy);          // released exactly once
            assertEquals(0, tenants.count());
        }
    }

    @Test
    void expiryAndApprovalOfAnExpiredRequestEndExpiredWithTheBedReleasedOnce() throws Exception {
        pg.setBookingMode(com.pg.entity.enums.BookingMode.ON_REQUEST);
        pgs.save(pg);
        Room r = room("101", 2);
        tenantUser("e@x.com");
        roomBookingService.request("e@x.com", booking(r.getId()));
        RoomBooking b = roomBookings.findAll().get(0);
        b.setExpiresAt(java.time.LocalDateTime.now().minusMinutes(5));
        roomBookings.save(b);
        com.pg.dto.request.BookingDecisionRequest approve = new com.pg.dto.request.BookingDecisionRequest();
        approve.setStatus(com.pg.entity.enums.BookingStatus.APPROVED);

        List<Throwable> failures = failuresOfTogether(List.of(
                () -> roomBookingService.expireOne(b.getId()),
                () -> roomBookingService.decide("owner@x.com", b.getId(), approve)));

        assertTrue(failures.isEmpty(), "nothing should fail: " + failures);
        assertEquals(com.pg.entity.enums.BookingStatus.EXPIRED, roomBookings.findById(b.getId()).orElseThrow().getStatus());
        assertEquals(0, rooms.findById(r.getId()).orElseThrow().getCurrentOccupancy());
        assertEquals(0, tenants.count());
    }

    // ------------------------------------------------------------------ stays and queries

    @Test
    void afterCheckoutTheAccountStaysActiveAndThePersonCanBookAgain() {
        Room r1 = room("101", 2);
        Room r2 = room("102", 2);
        User u = tenantUser("again@x.com");
        Tenant first = stay(u, r1);

        tenantService.checkoutTenant("owner@x.com", first.getId());

        assertTrue(users.findById(u.getId()).orElseThrow().getIsActive());
        assertEquals(0, rooms.findById(r1.getId()).orElseThrow().getCurrentOccupancy());
        tenantService.bookRoom("again@x.com", booking(r2.getId()));
        assertEquals(2, tenants.count());                                   // old stay + new stay
        assertEquals(1, tenants.findAll().stream().filter(Tenant::getIsActive).count());
        assertEquals(r2.getId(), tenantService.getMyProfile("again@x.com").getRoomId()); // current stay wins
    }

    @Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;

    @Test
    void startupVerifiedTheOneActiveStayIndexExistsAndIsValid() {
        Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM pg_index i JOIN pg_class c ON c.oid = i.indexrelid "
                + "WHERE c.relname = 'uq_tenant_one_active_stay' AND i.indisunique AND i.indisvalid", Integer.class);
        assertEquals(1, n);
        String def = jdbc.queryForObject("SELECT pg_get_indexdef(c.oid) FROM pg_class c WHERE c.relname = 'uq_tenant_one_active_stay'", String.class);
        assertTrue(def.toLowerCase().contains("unique index"), def);
        assertTrue(def.contains("(user_id)"), def);
        assertTrue(def.toLowerCase().contains("where"), def);   // partial: only rows with is_active
    }

    @Test
    void theDatabaseRefusesTwoActiveStaysForOnePerson() {
        Room r = room("101", 3);
        User u = tenantUser("dup@x.com");
        stay(u, r);
        assertThrows(Exception.class, () -> stay(u, r));
    }

    @Test
    void enumQueriesRunAgainstRealPostgres() {
        Room r = room("101", 2);
        assertEquals(1, rooms.findAvailableRoomsInPg(pg.getId()).size());
        assertTrue(rents.findOverdueRecords(LocalDate.now()).isEmpty());
        assertEquals(0, rents.getTotalOutstandingForPg(pg.getId()).signum());
    }

}
