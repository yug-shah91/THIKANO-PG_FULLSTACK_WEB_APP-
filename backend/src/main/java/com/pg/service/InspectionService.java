package com.pg.service;

import com.pg.dto.request.InspectionCreateRequest;
import com.pg.dto.request.InspectionItemRequest;
import com.pg.dto.response.InspectionComparisonResponse;
import com.pg.dto.response.InspectionComparisonResponse.Change;
import com.pg.dto.response.InspectionResponse;
import com.pg.entity.*;
import com.pg.entity.enums.InspectionType;
import com.pg.entity.enums.ItemCondition;
import com.pg.entity.enums.Party;
import com.pg.entity.enums.Role;
import com.pg.exception.BusinessException;
import com.pg.exception.ResourceNotFoundException;
import com.pg.exception.UnauthorizedAccessException;
import com.pg.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

/**
 * Move-in / move-out evidence.
 *
 * Rules (all enforced here, not in the screen):
 *  - only the owner starts an inspection, for an ACTIVE tenant of their own PG;
 *  - one CHECK_IN and one CHECK_OUT per tenant; CHECK_OUT needs a CHECK_IN first;
 *  - owner AND tenant add their own lines/photos; nobody edits the other's lines;
 *  - after you confirm you can not add or remove anything of yours;
 *  - when both have confirmed, the inspection is locked for ever;
 *  - photos can not be deleted (they are evidence) and get a server timestamp + SHA-256.
 *
 * Tip for owners: do the CHECK_OUT inspection BEFORE pressing "Check out" on the tenant,
 * because a checked-out tenant's login is switched off and they can no longer confirm.
 */
@Service
@RequiredArgsConstructor
public class InspectionService {

    static final int MAX_PHOTOS = 20;
    static final int MAX_ITEMS = 60;

    private final java.time.Clock clock; // injected so tests can set "now"
    private final InspectionRepository inspectionRepository;
    private final InspectionItemRepository itemRepository;
    private final InspectionPhotoRepository photoRepository;
    private final TenantRepository tenantRepository;
    private final PGRepository pgRepository;
    private final UserRepository userRepository;
    private final InspectionStorage storage;

    // ------------------------------------------------------------ who is calling

    private record Actor(User user, Party party, PG ownerPg) {}

    private Actor actor(String email) {
        User u = userRepository.findByEmail(email.toLowerCase())
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        if (u.getRole() == Role.OWNER) {
            PG pg = pgRepository.findByOwnerIdAndIsActiveTrue(u.getId())
                    .orElseThrow(() -> new ResourceNotFoundException("Set up your PG first."));
            return new Actor(u, Party.OWNER, pg);
        }
        if (u.getRole() == Role.TENANT) {
            return new Actor(u, Party.TENANT, null);
        }
        throw new UnauthorizedAccessException("Not allowed.");
    }

    /** Throws unless this actor is the owner of the inspection's PG or the inspected tenant. */
    private void checkAccess(Actor a, Inspection i) {
        boolean ok = a.party() == Party.OWNER
                ? i.getPg().getId().equals(a.ownerPg().getId())
                : i.getTenant().getUser().getId().equals(a.user().getId());
        if (!ok) throw new UnauthorizedAccessException("You can not open this inspection.");
    }

    // ------------------------------------------------------------ create / read

    @Transactional
    public InspectionResponse create(String ownerEmail, InspectionCreateRequest req) {
        Actor a = actor(ownerEmail);
        if (a.party() != Party.OWNER) throw new UnauthorizedAccessException("Only the owner starts an inspection.");

        Tenant tenant = tenantRepository.findByIdForUpdate(req.getTenantId())   // also stops a double click
                .orElseThrow(() -> new ResourceNotFoundException("Tenant not found"));
        if (!tenant.getPg().getId().equals(a.ownerPg().getId())) {
            throw new UnauthorizedAccessException("This tenant is not in your PG.");
        }
        if (!Boolean.TRUE.equals(tenant.getIsActive()) || tenant.getRoom() == null) {
            throw new BusinessException("The tenant needs to be active and have a room.");
        }
        if (inspectionRepository.findByTenantIdAndType(tenant.getId(), req.getType()).isPresent()) {
            throw new BusinessException("A " + label(req.getType()) + " inspection already exists for this tenant.");
        }
        if (req.getType() == InspectionType.CHECK_OUT
                && inspectionRepository.findByTenantIdAndType(tenant.getId(), InspectionType.CHECK_IN).isEmpty()) {
            throw new BusinessException("There is no move-in inspection to compare with. Record the move-in first.");
        }

        Inspection saved = inspectionRepository.save(Inspection.builder()
                .tenant(tenant).pg(tenant.getPg()).type(req.getType())
                .roomNumber(tenant.getRoom().getRoomNumber())
                .note(blankToNull(req.getNote())).build());
        return toResponse(saved, Party.OWNER);
    }

    @Transactional(readOnly = true)
    public List<InspectionResponse> list(String email, Long tenantId) {
        Actor a = actor(email);
        Tenant tenant = tenantFor(a, tenantId);
        return inspectionRepository.findByTenantIdOrderByCreatedAtAsc(tenant.getId()).stream()
                .map(i -> toResponse(i, a.party())).toList();
    }

    @Transactional(readOnly = true)
    public InspectionResponse get(String email, Long id) {
        Actor a = actor(email);
        Inspection i = inspectionRepository.findByIdWithParties(id)
                .orElseThrow(() -> new ResourceNotFoundException("Inspection not found"));
        checkAccess(a, i);
        return toResponse(i, a.party());
    }

    // ------------------------------------------------------------ add entries

    @Transactional
    public InspectionResponse addItem(String email, Long id, InspectionItemRequest req) {
        Actor a = actor(email);
        Inspection i = editable(a, id);
        if (itemRepository.countByInspectionId(id) >= MAX_ITEMS) {
            throw new BusinessException("An inspection can have at most " + MAX_ITEMS + " lines.");
        }
        itemRepository.save(InspectionItem.builder().inspection(i).area(req.getArea().trim())
                .condition(req.getCondition()).note(blankToNull(req.getNote())).party(a.party()).build());
        return toResponse(i, a.party());
    }

    @Transactional
    public InspectionResponse removeItem(String email, Long id, Long itemId) {
        Actor a = actor(email);
        Inspection i = editable(a, id);
        InspectionItem item = itemRepository.findByIdAndInspectionId(itemId, id)
                .orElseThrow(() -> new ResourceNotFoundException("Line not found"));
        if (item.getParty() != a.party()) {
            throw new UnauthorizedAccessException("You can only remove your own lines.");
        }
        if (photoRepository.existsByItemId(itemId)) {
            throw new BusinessException("This line has photos. Photos are evidence and can not be removed.");
        }
        itemRepository.delete(item);
        return toResponse(i, a.party());
    }

    @Transactional
    public InspectionResponse addPhoto(String email, Long id, byte[] bytes, String caption, Long itemId) {
        Actor a = actor(email);
        Inspection i = editable(a, id);
        if (photoRepository.countByInspectionId(id) >= MAX_PHOTOS) {
            throw new BusinessException("An inspection can have at most " + MAX_PHOTOS + " photos.");
        }
        InspectionItem item = null;
        if (itemId != null) {
            item = itemRepository.findByIdAndInspectionId(itemId, id)
                    .orElseThrow(() -> new ResourceNotFoundException("Line not found"));
        }
        InspectionStorage.Stored s = storage.save(bytes); // checks size and real file type
        photoRepository.save(InspectionPhoto.builder().inspection(i).item(item)
                .storedName(s.name()).contentType(s.contentType()).sizeBytes(s.size()).sha256(s.sha256())
                .caption(blankToNull(caption)).party(a.party()).build());
        return toResponse(i, a.party());
    }

    @Transactional
    public InspectionResponse confirm(String email, Long id) {
        Actor a = actor(email);
        Inspection i = editable(a, id);
        if (itemRepository.countByInspectionId(id) == 0) {
            throw new BusinessException("Add at least one line about the room before you confirm.");
        }
        LocalDateTime now = LocalDateTime.now(clock);
        if (a.party() == Party.OWNER) i.setOwnerConfirmedAt(now); else i.setTenantConfirmedAt(now);
        return toResponse(inspectionRepository.save(i), a.party());
    }

    public record PhotoFile(byte[] bytes, String contentType) {}

    @Transactional(readOnly = true)
    public PhotoFile photo(String email, Long photoId) {
        Actor a = actor(email);
        InspectionPhoto p = photoRepository.findWithInspection(photoId)
                .orElseThrow(() -> new ResourceNotFoundException("Photo not found"));
        checkAccess(a, p.getInspection());
        return new PhotoFile(storage.load(p.getStoredName()), p.getContentType());
    }

    // ------------------------------------------------------------ comparison

    @Transactional(readOnly = true)
    public InspectionComparisonResponse compare(String email, Long tenantId) {
        Actor a = actor(email);
        Tenant tenant = tenantFor(a, tenantId);
        Inspection in = inspectionRepository.findByTenantIdAndType(tenant.getId(), InspectionType.CHECK_IN)
                .orElseThrow(() -> new BusinessException("No move-in inspection has been recorded yet."));
        Inspection out = inspectionRepository.findByTenantIdAndType(tenant.getId(), InspectionType.CHECK_OUT).orElse(null);

        Side inSide = side(in);
        Side outSide = out == null ? null : side(out);
        return compare(tenant.getUser().getName(), tenant.getId(), inSide, outSide);
    }

    // ------------------------------------------------------------ the comparison rules (pure, unit-tested)

    public record Side(Inspection inspection, List<InspectionItem> items, List<InspectionPhoto> photos) {}

    static InspectionComparisonResponse compare(String tenantName, Long tenantId, Side in, Side out) {
        Map<String, Area> areas = new LinkedHashMap<>();
        collect(areas, in, true);
        if (out != null) collect(areas, out, false);

        List<InspectionComparisonResponse.Row> rows = new ArrayList<>();
        int newDamage = 0;
        for (Area ar : areas.values()) {
            Change change;
            if (out == null) change = Change.UNCHANGED; // nothing to compare yet
            else if (ar.checkIn == null) change = Change.ONLY_AT_CHECKOUT;
            else if (ar.checkOut == null) change = Change.NOT_RECORDED_AT_CHECKOUT;
            else if (ar.checkOut.severity() > ar.checkIn.severity()) change = Change.WORSE;
            else if (ar.checkOut.severity() < ar.checkIn.severity()) change = Change.BETTER;
            else change = Change.UNCHANGED;

            boolean possibleNew = change == Change.WORSE
                    || (change == Change.ONLY_AT_CHECKOUT && ar.checkOut.severity() > 0);
            if (possibleNew) newDamage++;

            rows.add(InspectionComparisonResponse.Row.builder()
                    .area(ar.display).checkIn(ar.checkIn).checkOut(ar.checkOut).change(change)
                    .damageAlreadyAtMoveIn(ar.checkIn != null && ar.checkIn.severity() > 0)
                    .possibleNewDamage(possibleNew)
                    .notes(ar.notes).checkInPhotoIds(ar.inPhotos).checkOutPhotoIds(ar.outPhotos).build());
        }
        // the rows that need attention come first
        rows.sort(Comparator.comparing((InspectionComparisonResponse.Row r) -> !r.isPossibleNewDamage()));

        Inspection i = in.inspection();
        return InspectionComparisonResponse.builder()
                .tenantId(tenantId).tenantName(tenantName).roomNumber(i.getRoomNumber())
                .checkInId(i.getId()).checkOutId(out == null ? null : out.inspection().getId())
                .checkInComplete(i.isComplete()).checkOutComplete(out != null && out.inspection().isComplete())
                .checkOutMissing(out == null).possibleNewDamage(newDamage).rows(rows).build();
    }

    private static class Area {
        String display;
        ItemCondition checkIn, checkOut;   // the WORST condition anyone recorded for this area
        List<String> notes = new ArrayList<>();
        List<Long> inPhotos = new ArrayList<>(), outPhotos = new ArrayList<>();
    }

    private static void collect(Map<String, Area> areas, Side side, boolean isCheckIn) {
        Map<Long, String> keyByItem = new HashMap<>();
        for (InspectionItem it : side.items()) {
            String key = it.getArea().trim().toLowerCase();
            keyByItem.put(it.getId(), key);
            Area a = areas.computeIfAbsent(key, k -> { Area n = new Area(); n.display = it.getArea().trim(); return n; });
            ItemCondition current = isCheckIn ? a.checkIn : a.checkOut;
            if (current == null || it.getCondition().severity() > current.severity()) {
                if (isCheckIn) a.checkIn = it.getCondition(); else a.checkOut = it.getCondition();
            }
            if (it.getNote() != null) {
                a.notes.add((it.getParty() == Party.OWNER ? "Owner" : "Tenant")
                        + (isCheckIn ? " (move-in): " : " (move-out): ") + it.getNote());
            }
        }
        for (InspectionPhoto p : side.photos()) {
            if (p.getItem() == null) continue; // not tied to a line, so not tied to an area
            String key = keyByItem.get(p.getItem().getId());
            Area a = key == null ? null : areas.get(key);
            if (a != null) (isCheckIn ? a.inPhotos : a.outPhotos).add(p.getId());
        }
    }

    // ------------------------------------------------------------ helpers

    private Side side(Inspection i) {
        return new Side(i, itemRepository.findByInspectionIdOrderByIdAsc(i.getId()), photoRepository.findByInspection(i.getId()));
    }

    /** Loads (and locks) an inspection this actor may still add to. */
    private Inspection editable(Actor a, Long id) {
        Inspection i = inspectionRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new ResourceNotFoundException("Inspection not found"));
        checkAccess(a, i);
        if (i.isComplete()) {
            throw new BusinessException("Both of you confirmed this inspection, so it is locked.");
        }
        boolean mineConfirmed = a.party() == Party.OWNER ? i.getOwnerConfirmedAt() != null : i.getTenantConfirmedAt() != null;
        if (mineConfirmed) {
            throw new BusinessException("You already confirmed this inspection.");
        }
        return i;
    }

    private Tenant tenantFor(Actor a, Long tenantId) {
        if (a.party() == Party.OWNER) {
            if (tenantId == null) throw new BusinessException("tenantId is required.");
            Tenant t = tenantRepository.findById(tenantId)
                    .orElseThrow(() -> new ResourceNotFoundException("Tenant not found"));
            if (!t.getPg().getId().equals(a.ownerPg().getId())) {
                throw new UnauthorizedAccessException("This tenant is not in your PG.");
            }
            return t;
        }
        // a tenant can only ever see their own, whatever tenantId they send
        return tenantRepository.findCurrentByUserEmail(a.user().getEmail())
                .orElseThrow(() -> new ResourceNotFoundException("You have no stay yet."));
    }

    private InspectionResponse toResponse(Inspection i, Party viewer) {
        List<InspectionResponse.Item> items = itemRepository.findByInspectionIdOrderByIdAsc(i.getId()).stream()
                .map(it -> InspectionResponse.Item.builder().id(it.getId()).area(it.getArea())
                        .condition(it.getCondition()).note(it.getNote()).party(it.getParty())
                        .createdAt(it.getCreatedAt()).build()).toList();
        List<InspectionResponse.Photo> photos = photoRepository.findByInspection(i.getId()).stream()
                .map(p -> InspectionResponse.Photo.builder().id(p.getId())
                        .itemId(p.getItem() == null ? null : p.getItem().getId()).caption(p.getCaption())
                        .party(p.getParty()).uploadedAt(p.getUploadedAt()).sha256(p.getSha256())
                        .sizeBytes(p.getSizeBytes()).build()).toList();
        return InspectionResponse.builder().id(i.getId()).tenantId(i.getTenant().getId())
                .tenantName(i.getTenant().getUser().getName()).roomNumber(i.getRoomNumber()).type(i.getType())
                .note(i.getNote()).createdAt(i.getCreatedAt()).ownerConfirmedAt(i.getOwnerConfirmedAt())
                .tenantConfirmedAt(i.getTenantConfirmedAt()).complete(i.isComplete()).viewer(viewer)
                .items(items).photos(photos).build();
    }

    private static String label(InspectionType t) {
        return t == InspectionType.CHECK_IN ? "move-in" : "move-out";
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
