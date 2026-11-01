package com.pg.service;

import com.pg.dto.response.PublicPgResponse;
import com.pg.entity.PG;
import com.pg.entity.Room;
import com.pg.entity.User;
import com.pg.entity.enums.Role;
import com.pg.entity.enums.RoomStatus;
import com.pg.entity.enums.RoomType;
import com.pg.exception.BusinessException;
import com.pg.exception.ResourceNotFoundException;
import com.pg.repository.PGRepository;
import com.pg.repository.PgChargeRepository;
import com.pg.repository.PgPhotoRepository;
import com.pg.repository.RoomBookingRepository;
import com.pg.repository.RoomRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PublicPgServiceTest {

    @Mock PGRepository pgRepository;
    @Mock RoomRepository roomRepository;
    @Mock PgChargeRepository chargeRepository;
    @Mock RoomBookingRepository bookingRepository;
    @Mock PgPhotoRepository photoRepository;
    @Mock ReviewService reviewService;
    @org.mockito.Spy java.time.Clock clock = java.time.Clock.systemDefaultZone();
    @InjectMocks PublicPgService service;

    private User owner;
    private PG pune;

    @BeforeEach
    void setUp() {
        owner = User.builder().id(1L).name("Owner").phone("9990001111").role(Role.OWNER).build();
        pune = PG.builder().id(10L).name("Sunrise PG").city("Pune").address("MG Road").owner(owner)
                .baseMonthlyRent(new BigDecimal("4000")).latitude(18.52).longitude(73.85)
                .rentDueDay(5).penaltyPercent(5).build();
        when(pgRepository.findByIsActiveTrue()).thenReturn(List.of(pune));
        when(pgRepository.findById(10L)).thenReturn(Optional.of(pune));
    }

    private Room room(long id, String number, int capacity, int occupancy, String rent) {
        return Room.builder().id(id).roomNumber(number).type(RoomType.DOUBLE).capacity(capacity)
                .currentOccupancy(occupancy).monthlyRent(new BigDecimal(rent)).pg(pune).build();
    }

    // search() asks for the rooms of all PGs in ONE query
    private void rooms(PG pg, Room... rooms) {
        when(roomRepository.findByPgIds(anyCollection())).thenReturn(List.of(rooms));
        when(roomRepository.findByPgId(pg.getId())).thenReturn(List.of(rooms)); // used by getDetail
    }

    private List<PublicPgResponse> searchAll(boolean onlyAvailable) {
        return service.search(null, null, null, onlyAvailable, null, null, null);
    }

    @Test
    void amenitiesAreCollectedAcrossRoomsAsDistinctLowerCaseWords() {
        Room a = room(1, "101", 2, 0, "5000"); a.setAmenities("WiFi, AC ; attached bath");
        Room b = room(2, "102", 2, 0, "5000"); b.setAmenities("wifi,Geyser");
        assertEquals(List.of("wifi", "ac", "attached bath", "geyser"), PublicPgService.amenitiesOf(List.of(a, b)));
    }

    @Test
    void facilityFilterNeedsEveryWantedFacility() {
        List<String> have = List.of("wifi", "ac room", "geyser");
        assertTrue(PublicPgService.hasAllAmenities(have, List.of("wifi", "ac")));      // "ac room" matches "ac"
        assertFalse(PublicPgService.hasAllAmenities(have, List.of("wifi", "laundry")));
        assertFalse(PublicPgService.hasAllAmenities(List.of("spacious", "access control"), List.of("ac"))); // not inside other words
        assertTrue(PublicPgService.hasAllAmenities(List.of("meals included"), List.of("meal")));
        assertFalse(PublicPgService.hasAllAmenities(null, List.of("wifi")));
    }

    @Test
    void genderFilterKeepsAnyAndMatchingPgsOnly() {
        var any = com.pg.entity.enums.GenderPolicy.ANY;
        var men = com.pg.entity.enums.GenderPolicy.MEN_ONLY;
        var women = com.pg.entity.enums.GenderPolicy.WOMEN_ONLY;
        assertTrue(PublicPgService.acceptsGender(any, "WOMEN"));
        assertTrue(PublicPgService.acceptsGender(women, "women"));
        assertFalse(PublicPgService.acceptsGender(men, "WOMEN"));
        assertFalse(PublicPgService.acceptsGender(women, "MEN"));
        assertTrue(PublicPgService.acceptsGender(men, null));          // no filter
        assertThrows(BusinessException.class, () -> PublicPgService.acceptsGender(men, "robots"));
    }

    @Test
    void likePatternEscapesWildcardsSoVisitorsCanNotInjectThem() {
        assertEquals("%pune%", PublicPgService.likePattern("  Pune "));
        assertEquals("%100\\%%", PublicPgService.likePattern("100%"));
        assertEquals("%a\\_b%", PublicPgService.likePattern("a_b"));
    }

    @Test
    void searchNeverReturnsMoreThanTheCap() {
        List<PG> many = new ArrayList<>();
        List<Room> manyRooms = new ArrayList<>();
        for (long i = 100; i < 100 + PublicPgService.MAX_RESULTS + 20; i++) {
            PG p = PG.builder().id(i).name("PG" + i).city("Pune").isActive(true).build();
            many.add(p);
            manyRooms.add(Room.builder().id(i).roomNumber("1").type(RoomType.DOUBLE).capacity(2).currentOccupancy(0)
                    .monthlyRent(new BigDecimal("5000")).pg(p).build());
        }
        when(pgRepository.findByIsActiveTrue()).thenReturn(many);
        when(roomRepository.findByPgIds(anyCollection())).thenReturn(manyRooms);
        assertEquals(PublicPgService.MAX_RESULTS, searchAll(true).size());
    }

    // ---------- availability ----------

    @Test
    void freeBedsAndStartingRentCountOnlyBookableRooms() {
        Room full = room(1, "101", 1, 1, "3000");          // cheapest, but full
        Room free = room(2, "102", 2, 1, "5000");          // 1 bed free
        Room repair = room(3, "103", 2, 0, "2000");
        repair.setStatus(RoomStatus.UNDER_MAINTENANCE);    // not for rent
        rooms(pune, full, free, repair);

        List<PublicPgResponse> result = searchAll(true);

        assertEquals(1, result.size());
        assertEquals(1, result.get(0).getFreeBeds());
        assertEquals(0, new BigDecimal("5000").compareTo(result.get(0).getStartingRent()));
        assertEquals(List.of(RoomType.DOUBLE), result.get(0).getRoomTypes());
    }

    @Test
    void pgWithoutFreeBedsIsHiddenOnlyWhenOnlyAvailableIsOn() {
        rooms(pune, room(1, "101", 1, 1, "3000"));

        assertTrue(searchAll(true).isEmpty());
        List<PublicPgResponse> all = searchAll(false);
        assertEquals(1, all.size());
        assertEquals(0, all.get(0).getFreeBeds());
    }

    @Test
    void maxRentFiltersOnTheCheapestFreeRoom() {
        rooms(pune, room(1, "101", 2, 0, "5000"));

        assertTrue(service.search(null, new BigDecimal("4000"), null, true, null, null, null).isEmpty());
        assertEquals(1, service.search(null, new BigDecimal("6000"), null, true, null, null, null).size());
    }

    @Test
    void textSearchMatchesCityNameOrAddress() {
        rooms(pune, room(1, "101", 2, 0, "5000"));
        when(pgRepository.findActiveMatching(anyString())).thenReturn(List.of(pune));

        assertEquals(1, service.search("pune", null, null, true, null, null, null).size());
        assertEquals(1, service.search("sunrise", null, null, true, null, null, null).size());
        assertTrue(service.search("mumbai", null, null, true, null, null, null).isEmpty());
    }

    // ---------- near me ----------

    @Test
    void nearbySearchSortsNearestFirstAndAppliesTheRadius() {
        PG delhi = PG.builder().id(11L).name("Delhi PG").city("Delhi").address("CP").owner(owner)
                .baseMonthlyRent(new BigDecimal("4000")).latitude(28.61).longitude(77.21).build();
        PG noLocation = PG.builder().id(12L).name("Unknown PG").city("Pune").address("?").owner(owner)
                .baseMonthlyRent(new BigDecimal("4000")).build();
        when(pgRepository.findByIsActiveTrue()).thenReturn(List.of(delhi, noLocation, pune));
        Room r1 = room(1, "101", 2, 0, "5000");
        Room r2 = room(2, "101", 2, 0, "5000"); r2.setPg(delhi);
        Room r3 = room(3, "101", 2, 0, "5000"); r3.setPg(noLocation);
        when(roomRepository.findByPgIds(anyCollection())).thenReturn(List.of(r1, r2, r3));

        // user is standing next to the Pune PG
        List<PublicPgResponse> noRadius = service.search(null, null, null, true, 18.53, 73.86, null);
        assertEquals("Sunrise PG", noRadius.get(0).getName());        // nearest first
        assertEquals("Unknown PG", noRadius.get(2).getName());        // no coordinates: last
        assertNotNull(noRadius.get(0).getDistanceKm());
        assertTrue(noRadius.get(0).getDistanceKm() < 5);

        List<PublicPgResponse> within10 = service.search(null, null, null, true, 18.53, 73.86, 10.0);
        assertEquals(1, within10.size());
        assertEquals("Sunrise PG", within10.get(0).getName());
    }

    @Test
    void invalidOrHalfLocationIsRejected() {
        assertThrows(BusinessException.class, () -> service.search(null, null, null, true, 18.5, null, null));
        assertThrows(BusinessException.class, () -> service.search(null, null, null, true, 95.0, 10.0, null));
        assertThrows(BusinessException.class, () -> service.search(null, null, null, true, 10.0, 200.0, null));
    }

    @Test
    void haversineGivesAboutOneHundredElevenKmPerDegreeOfLatitude() {
        assertEquals(111.19, PublicPgService.distanceKm(0, 0, 1, 0), 0.5);
        assertEquals(0.0, PublicPgService.distanceKm(18.5, 73.8, 18.5, 73.8), 0.0001);
    }

    // ---------- detail page ----------

    @Test
    void phoneNumberIsOnlyShownToLoggedInUsers() {
        rooms(pune, room(1, "101", 2, 0, "5000"));

        assertNull(service.getDetail(10L, false).getContactPhone());
        assertEquals("9990001111", service.getDetail(10L, true).getContactPhone()); // falls back to owner phone
    }

    @Test
    void pgContactNumberWinsOverOwnerPhone() {
        pune.setContactPhone("8880002222");
        rooms(pune, room(1, "101", 2, 0, "5000"));

        assertEquals("8880002222", service.getDetail(10L, true).getContactPhone());
    }

    @Test
    void detailListsRoomsMarksFullOnesAndHidesMaintenanceRooms() {
        Room free = room(1, "101", 2, 1, "5000");
        Room full = room(2, "102", 1, 1, "4000");
        Room repair = room(3, "103", 2, 0, "4500");
        repair.setStatus(RoomStatus.UNDER_MAINTENANCE);
        rooms(pune, repair, full, free);

        PublicPgResponse detail = service.getDetail(10L, false);

        assertEquals(2, detail.getRooms().size());
        assertEquals("101", detail.getRooms().get(0).getRoomNumber());   // sorted by number
        assertTrue(detail.getRooms().get(0).getBookable());
        assertFalse(detail.getRooms().get(1).getBookable());
        assertEquals("Owner", detail.getOwnerName());
        assertEquals(5, detail.getRentDueDay());
    }

    @Test
    void inactivePgIsNotFound() {
        pune.setIsActive(false);
        assertThrows(ResourceNotFoundException.class, () -> service.getDetail(10L, false));
    }

    @Test
    void searchLoadsRoomsWithOneQueryNoMatterHowManyPgs() {
        PG a = PG.builder().id(11L).name("A").city("Pune").address("x").owner(owner).baseMonthlyRent(new BigDecimal("4000")).build();
        PG b = PG.builder().id(12L).name("B").city("Pune").address("y").owner(owner).baseMonthlyRent(new BigDecimal("4000")).build();
        when(pgRepository.findByIsActiveTrue()).thenReturn(List.of(pune, a, b));
        when(roomRepository.findByPgIds(anyCollection())).thenReturn(List.of());

        searchAll(false);

        verify(roomRepository, times(1)).findByPgIds(anyCollection());
        verify(roomRepository, never()).findByPgId(anyLong());
    }

    @Test
    void textFilterRunsBeforeRoomsAreLoaded() {
        service.search("nowhere-city", null, null, false, null, null, null);
        verify(roomRepository, never()).findByPgIds(anyCollection());
    }

    @Test
    void bedsHeldByExpiredRequestsCountAsFreeBeforeTheSchedulerRuns() {
        Room held = room(1, "101", 1, 1, "5000"); // 1 of 1 bed taken, but that "taken" is an expired hold
        rooms(pune, held);
        when(bookingRepository.countExpiredHolds(anyCollection(), any())).thenReturn(
                java.util.Collections.singletonList(new Object[]{1L, 1L}));

        List<PublicPgResponse> result = searchAll(true);

        assertEquals(1, result.size());
        assertEquals(1, result.get(0).getFreeBeds());
    }
}
