package com.pg.service;

import com.pg.dto.request.InspectionCreateRequest;
import com.pg.dto.request.InspectionItemRequest;
import com.pg.dto.response.InspectionComparisonResponse;
import com.pg.dto.response.InspectionComparisonResponse.Change;
import com.pg.dto.response.InspectionResponse;
import com.pg.entity.*;
import com.pg.entity.enums.*;
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

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class InspectionServiceTest {

    @Mock InspectionRepository inspectionRepository;
    @Mock InspectionItemRepository itemRepository;
    @Mock InspectionPhotoRepository photoRepository;
    @Mock TenantRepository tenantRepository;
    @Mock PGRepository pgRepository;
    @Mock UserRepository userRepository;
    @Mock InspectionStorage storage;
    @org.mockito.Spy java.time.Clock clock = java.time.Clock.systemDefaultZone();
    @InjectMocks InspectionService service;

    User ownerUser, tenantUser, otherOwnerUser;
    PG pg, otherPg;
    Tenant tenant;
    Inspection checkIn;

    @BeforeEach
    void setUp() {
        ownerUser = User.builder().id(1L).name("Owner").email("o@x.com").role(Role.OWNER).build();
        otherOwnerUser = User.builder().id(2L).name("Other").email("o2@x.com").role(Role.OWNER).build();
        tenantUser = User.builder().id(3L).name("Asha").email("t@x.com").role(Role.TENANT).build();
        pg = PG.builder().id(10L).owner(ownerUser).isActive(true).build();
        otherPg = PG.builder().id(11L).owner(otherOwnerUser).isActive(true).build();
        Room room = Room.builder().id(7L).roomNumber("101").pg(pg).build();
        tenant = Tenant.builder().id(20L).user(tenantUser).pg(pg).room(room).isActive(true).build();
        checkIn = Inspection.builder().id(100L).tenant(tenant).pg(pg).type(InspectionType.CHECK_IN).roomNumber("101").build();

        when(userRepository.findByEmail("o@x.com")).thenReturn(Optional.of(ownerUser));
        when(userRepository.findByEmail("o2@x.com")).thenReturn(Optional.of(otherOwnerUser));
        when(userRepository.findByEmail("t@x.com")).thenReturn(Optional.of(tenantUser));
        when(pgRepository.findByOwnerIdAndIsActiveTrue(1L)).thenReturn(Optional.of(pg));
        when(pgRepository.findByOwnerIdAndIsActiveTrue(2L)).thenReturn(Optional.of(otherPg));
        when(tenantRepository.findByIdForUpdate(20L)).thenReturn(Optional.of(tenant));
        when(inspectionRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(checkIn));
        when(inspectionRepository.findByIdWithParties(100L)).thenReturn(Optional.of(checkIn));
        when(inspectionRepository.save(any(Inspection.class))).thenAnswer(i -> i.getArgument(0));
        when(itemRepository.countByInspectionId(100L)).thenReturn(1L);
    }

    private InspectionCreateRequest create(InspectionType t) {
        InspectionCreateRequest r = new InspectionCreateRequest();
        r.setTenantId(20L); r.setType(t);
        return r;
    }

    private InspectionItemRequest line(String area, ItemCondition c) {
        InspectionItemRequest r = new InspectionItemRequest();
        r.setArea(area); r.setCondition(c);
        return r;
    }

    // ---------- starting an inspection ----------

    @Test
    void ownerStartsMoveInAndRoomNumberIsSnapshotted() {
        InspectionResponse r = service.create("o@x.com", create(InspectionType.CHECK_IN));
        assertEquals("101", r.getRoomNumber());
        assertFalse(r.isComplete());
    }

    @Test
    void tenantsAndOtherOwnersCanNotStartOne() {
        assertThrows(UnauthorizedAccessException.class, () -> service.create("t@x.com", create(InspectionType.CHECK_IN)));
        assertThrows(UnauthorizedAccessException.class, () -> service.create("o2@x.com", create(InspectionType.CHECK_IN)));
    }

    @Test
    void onlyOneOfEachTypeAndMoveOutNeedsMoveIn() {
        when(inspectionRepository.findByTenantIdAndType(20L, InspectionType.CHECK_IN)).thenReturn(Optional.of(checkIn));
        assertThrows(BusinessException.class, () -> service.create("o@x.com", create(InspectionType.CHECK_IN)));

        when(inspectionRepository.findByTenantIdAndType(20L, InspectionType.CHECK_IN)).thenReturn(Optional.empty());
        assertThrows(BusinessException.class, () -> service.create("o@x.com", create(InspectionType.CHECK_OUT)));
    }

    @Test
    void checkedOutTenantCanNotBeInspected() {
        tenant.setIsActive(false);
        assertThrows(BusinessException.class, () -> service.create("o@x.com", create(InspectionType.CHECK_IN)));
    }

    // ---------- who can see / change ----------

    @Test
    void anotherOwnerAndAnotherTenantCanNotOpenIt() {
        assertThrows(UnauthorizedAccessException.class, () -> service.get("o2@x.com", 100L));
        User stranger = User.builder().id(99L).email("s@x.com").role(Role.TENANT).build();
        when(userRepository.findByEmail("s@x.com")).thenReturn(Optional.of(stranger));
        assertThrows(UnauthorizedAccessException.class, () -> service.get("s@x.com", 100L));
        assertDoesNotThrow(() -> service.get("t@x.com", 100L));
        assertDoesNotThrow(() -> service.get("o@x.com", 100L));
    }

    @Test
    void afterYouConfirmYouCanNotAddMore() {
        service.addItem("o@x.com", 100L, line("Window", ItemCondition.GOOD));
        service.confirm("o@x.com", 100L);
        assertNotNull(checkIn.getOwnerConfirmedAt());
        assertThrows(BusinessException.class, () -> service.addItem("o@x.com", 100L, line("Wall", ItemCondition.GOOD)));
        // the tenant can still add their side
        assertDoesNotThrow(() -> service.addItem("t@x.com", 100L, line("Wall", ItemCondition.MINOR_DAMAGE)));
    }

    @Test
    void whenBothConfirmedItIsLockedForEveryone() {
        service.confirm("o@x.com", 100L);
        service.confirm("t@x.com", 100L);
        assertTrue(checkIn.isComplete());
        assertThrows(BusinessException.class, () -> service.addItem("t@x.com", 100L, line("Door", ItemCondition.GOOD)));
        assertThrows(BusinessException.class, () -> service.confirm("o@x.com", 100L));
    }

    @Test
    void confirmNeedsAtLeastOneLine() {
        when(itemRepository.countByInspectionId(100L)).thenReturn(0L);
        assertThrows(BusinessException.class, () -> service.confirm("o@x.com", 100L));
    }

    @Test
    void youCanOnlyRemoveYourOwnLinesAndNeverOnesWithPhotos() {
        InspectionItem ownersLine = InspectionItem.builder().id(5L).inspection(checkIn).area("Bed")
                .condition(ItemCondition.GOOD).party(Party.OWNER).build();
        when(itemRepository.findByIdAndInspectionId(5L, 100L)).thenReturn(Optional.of(ownersLine));

        assertThrows(UnauthorizedAccessException.class, () -> service.removeItem("t@x.com", 100L, 5L));

        when(photoRepository.existsByItemId(5L)).thenReturn(true);
        assertThrows(BusinessException.class, () -> service.removeItem("o@x.com", 100L, 5L));

        when(photoRepository.existsByItemId(5L)).thenReturn(false);
        assertDoesNotThrow(() -> service.removeItem("o@x.com", 100L, 5L));
        verify(itemRepository).delete(ownersLine);
    }

    @Test
    void photoIsCheckedByTheStorageBeforeItIsSaved() {
        when(storage.save(any())).thenThrow(new BusinessException("Only JPEG, PNG or WebP photos are accepted."));
        assertThrows(BusinessException.class, () -> service.addPhoto("o@x.com", 100L, new byte[]{1, 2, 3}, null, null));
        verify(photoRepository, never()).save(any());
    }

    // ---------- the comparison ----------

    private static InspectionItem item(long id, String area, ItemCondition c, Party p, String note) {
        return InspectionItem.builder().id(id).area(area).condition(c).party(p).note(note).build();
    }

    private InspectionService.Side side(Inspection i, List<InspectionItem> items, List<InspectionPhoto> photos) {
        return new InspectionService.Side(i, items, photos);
    }

    @Test
    void comparisonFindsNewDamageAndRespectsDamageThatWasAlreadyThere() {
        Inspection out = Inspection.builder().id(101L).tenant(tenant).pg(pg).type(InspectionType.CHECK_OUT).roomNumber("101").build();
        InspectionItem inWindow = item(1, "Window", ItemCondition.MINOR_DAMAGE, Party.OWNER, "small crack");
        InspectionItem inWall = item(2, "Wall", ItemCondition.GOOD, Party.OWNER, null);
        InspectionItem inFan = item(3, "Fan", ItemCondition.GOOD, Party.TENANT, null);
        InspectionItem outWindow = item(4, "window ", ItemCondition.MINOR_DAMAGE, Party.OWNER, null); // same area, other casing/space
        InspectionItem outWall = item(5, "Wall", ItemCondition.DAMAGED, Party.OWNER, "hole");
        InspectionItem outDoor = item(6, "Door", ItemCondition.MISSING, Party.OWNER, "handle gone");
        InspectionPhoto wallPhoto = InspectionPhoto.builder().id(50L).item(outWall).build();

        InspectionComparisonResponse r = InspectionService.compare("Asha", 20L,
                side(checkIn, List.of(inWindow, inWall, inFan), List.of()),
                side(out, List.of(outWindow, outWall, outDoor), List.of(wallPhoto)));

        assertEquals(2, r.getPossibleNewDamage()); // Wall got worse, Door is new and broken

        var byArea = r.getRows().stream().collect(java.util.stream.Collectors.toMap(x -> x.getArea().toLowerCase(), x -> x));
        assertEquals(Change.UNCHANGED, byArea.get("window").getChange());
        assertTrue(byArea.get("window").isDamageAlreadyAtMoveIn());
        assertFalse(byArea.get("window").isPossibleNewDamage());          // crack was there at move-in
        assertEquals(Change.WORSE, byArea.get("wall").getChange());
        assertTrue(byArea.get("wall").isPossibleNewDamage());
        assertEquals(List.of(50L), byArea.get("wall").getCheckOutPhotoIds());
        assertEquals(Change.ONLY_AT_CHECKOUT, byArea.get("door").getChange());
        assertTrue(byArea.get("door").isPossibleNewDamage());
        assertEquals(Change.NOT_RECORDED_AT_CHECKOUT, byArea.get("fan").getChange());
        assertFalse(r.getRows().get(r.getRows().size() - 1).isPossibleNewDamage()); // attention rows sort first
        assertTrue(r.getRows().get(0).isPossibleNewDamage());
    }

    @Test
    void worstConditionWinsWhenOwnerAndTenantDisagree() {
        Inspection out = Inspection.builder().id(101L).tenant(tenant).pg(pg).type(InspectionType.CHECK_OUT).roomNumber("101").build();
        InspectionComparisonResponse r = InspectionService.compare("Asha", 20L,
                side(checkIn, List.of(item(1, "Bed", ItemCondition.GOOD, Party.OWNER, null),
                                      item(2, "Bed", ItemCondition.MINOR_DAMAGE, Party.TENANT, "stain")), List.of()),
                side(out, List.of(item(3, "Bed", ItemCondition.MINOR_DAMAGE, Party.OWNER, null)), List.of()));
        // the tenant noted the stain at move-in, so it is NOT new damage at move-out
        assertEquals(0, r.getPossibleNewDamage());
        assertTrue(r.getRows().get(0).isDamageAlreadyAtMoveIn());
    }

    @Test
    void withoutAMoveOutInspectionNothingIsFlagged() {
        InspectionComparisonResponse r = InspectionService.compare("Asha", 20L,
                side(checkIn, List.of(item(1, "Bed", ItemCondition.DAMAGED, Party.OWNER, null)), List.of()), null);
        assertTrue(r.isCheckOutMissing());
        assertEquals(0, r.getPossibleNewDamage());
    }

    // ---------- photo storage ----------

    @Test
    void storageRecognisesRealImagesByTheirFirstBytes() {
        assertEquals("image/jpeg", InspectionStorage.detect(new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0})[0]);
        assertEquals("image/png", InspectionStorage.detect(new byte[]{(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A})[0]);
        assertEquals("image/webp", InspectionStorage.detect("RIFF0000WEBPxx".getBytes())[0]);
        assertNull(InspectionStorage.detect("<html><script>alert(1)</script>".getBytes())); // a renamed web page is refused
        assertNull(InspectionStorage.detect(new byte[]{1, 2}));
    }

    @Test
    void storageRefusesEmptyOversizeAndNonImageFiles(@org.junit.jupiter.api.io.TempDir java.nio.file.Path dir) {
        InspectionStorage s = new InspectionStorage(dir.toString());
        assertThrows(BusinessException.class, () -> s.save(new byte[0]));
        assertThrows(BusinessException.class, () -> s.save(new byte[(int) InspectionStorage.MAX_BYTES + 1]));
        assertThrows(BusinessException.class, () -> s.save("not an image".getBytes()));

        byte[] png = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 1, 2, 3};
        InspectionStorage.Stored st = s.save(png);
        assertTrue(st.name().endsWith(".png"));
        assertEquals(64, st.sha256().length());
        assertArrayEquals(png, s.load(st.name()));
        assertThrows(BusinessException.class, () -> s.load("../../etc/passwd")); // no escaping the folder
    }
}
