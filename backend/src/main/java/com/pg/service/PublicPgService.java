package com.pg.service;

import com.pg.dto.response.PublicPgResponse;
import com.pg.dto.response.PublicRoomResponse;
import com.pg.entity.PG;
import com.pg.entity.Room;
import com.pg.entity.enums.RoomStatus;
import com.pg.entity.enums.RoomType;
import com.pg.exception.BusinessException;
import com.pg.exception.ResourceNotFoundException;
import com.pg.repository.PGRepository;
import com.pg.repository.RoomRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;

/** Read-only data for the public "find a PG" pages. */
@Service
@RequiredArgsConstructor
public class PublicPgService {

    private final java.time.Clock clock; // injected so tests can set "now"
    private final PGRepository pgRepository;
    private final RoomRepository roomRepository;
    private final com.pg.repository.PgChargeRepository chargeRepository;
    private final com.pg.repository.RoomBookingRepository bookingRepository;
    private final com.pg.repository.PgPhotoRepository photoRepository;
    private final ReviewService reviewService;

    // ------------------------------------------------------------------ search

    public List<PublicPgResponse> search(String text, BigDecimal maxRent, RoomType roomType,
                                         boolean onlyAvailable,
                                         Double lat, Double lng, Double radiusKm) {
        return search(text, maxRent, roomType, onlyAvailable, lat, lng, radiusKm, null, null);
    }

    /**
     * @param gender    MEN or WOMEN: keep PGs that accept that group (ANY, or MEN_ONLY / WOMEN_ONLY to match). null = no filter
     * @param amenities facilities the PG must offer (every one of them), e.g. ["wifi", "ac"]. null/empty = no filter
     */
    @Transactional(readOnly = true)
    public List<PublicPgResponse> search(String text, BigDecimal maxRent, RoomType roomType,
                                         boolean onlyAvailable,
                                         Double lat, Double lng, Double radiusKm,
                                         String gender, List<String> amenities) {
        if ((lat == null) != (lng == null)) {
            throw new BusinessException("Send both lat and lng, or neither.");
        }
        boolean hasLocation = lat != null;
        if (hasLocation) {
            validateCoordinates(lat, lng);
        }

        // 1) cheap text filter first, 2) ONE query for the rooms of all remaining PGs
        // the text filter runs in the database, so only matching PGs are loaded into memory
        boolean hasText = text != null && !text.isBlank();
        List<PG> candidates = (hasText ? pgRepository.findActiveMatching(likePattern(text)) : pgRepository.findByIsActiveTrue())
                .stream().filter(pg -> matchesText(pg, text)).toList(); // same check again: cheap, and keeps this method safe on its own
        Map<Long, List<Room>> roomsByPg = candidates.isEmpty() ? Map.of()
                : roomRepository.findByPgIds(candidates.stream().map(PG::getId).toList()).stream()
                        .collect(java.util.stream.Collectors.groupingBy(r -> r.getPg().getId()));

        Map<Long, Integer> expired = expiredHolds(roomsByPg.values().stream().flatMap(List::stream).toList());

        // cover photo and rating of every PG: one query each, not one per PG
        List<Long> ids = candidates.stream().map(PG::getId).toList();
        Map<Long, Long> covers = new HashMap<>();
        if (!ids.isEmpty()) {
            for (var ph : photoRepository.findByPgIdInOrderBySortOrderAscIdAsc(ids)) {
                covers.putIfAbsent(ph.getPg().getId(), ph.getId()); // list is ordered: first one wins
            }
        }
        Map<Long, double[]> ratings = reviewService.summaries(ids);

        List<PublicPgResponse> result = new ArrayList<>();
        for (PG pg : candidates) {
            PublicPgResponse summary = toSummary(pg, roomsByPg.getOrDefault(pg.getId(), List.of()), expired);
            summary.setCoverPhotoId(covers.get(pg.getId()));
            double[] rating = ratings.get(pg.getId());
            if (rating != null) {
                summary.setRatingAvg(rating[0]);
                summary.setReviewCount((int) rating[1]);
            } else {
                summary.setReviewCount(0);
            }

            if (hasLocation && pg.getLatitude() != null && pg.getLongitude() != null) {
                double km = distanceKm(lat, lng, pg.getLatitude(), pg.getLongitude());
                summary.setDistanceKm(Math.round(km * 10.0) / 10.0);
            }

            if (onlyAvailable && summary.getFreeBeds() == 0) continue;
            if (maxRent != null && (summary.getStartingRent() == null
                    || summary.getStartingRent().compareTo(maxRent) > 0)) continue;
            if (roomType != null && !summary.getRoomTypes().contains(roomType)) continue;
            if (!acceptsGender(summary.getGenderPolicy(), gender)) continue;
            if (amenities != null && !amenities.isEmpty() && !hasAllAmenities(summary.getAmenities(), amenities)) continue;
            if (hasLocation && radiusKm != null
                    && (summary.getDistanceKm() == null || summary.getDistanceKm() > radiusKm)) continue;

            result.add(summary);
        }

        Comparator<PublicPgResponse> order;
        if (hasLocation) {
            // nearest first; PGs without coordinates go to the end
            order = Comparator.comparing(PublicPgResponse::getDistanceKm,
                    Comparator.nullsLast(Comparator.<Double>naturalOrder()));
        } else {
            // PGs with free beds first, then cheapest
            order = Comparator.<PublicPgResponse, Integer>comparing(p -> p.getFreeBeds() > 0 ? 0 : 1)
                    .thenComparing(PublicPgResponse::getStartingRent,
                            Comparator.nullsLast(Comparator.<BigDecimal>naturalOrder()));
        }
        result.sort(order);
        // never send an unbounded list: the page shows the best matches first
        return result.size() > MAX_RESULTS ? new ArrayList<>(result.subList(0, MAX_RESULTS)) : result;
    }

    // ------------------------------------------------------------------ detail

    @Transactional(readOnly = true)
    public PublicPgResponse getDetail(Long pgId, boolean loggedIn) {
        PG pg = pgRepository.findById(pgId)
                .filter(p -> Boolean.TRUE.equals(p.getIsActive()))
                .orElseThrow(() -> new ResourceNotFoundException("PG not found"));

        List<Room> rooms = roomRepository.findByPgId(pgId);
        Map<Long, Integer> expired = expiredHolds(rooms);
        PublicPgResponse detail = toSummary(pg, rooms, expired);

        List<PublicRoomResponse> roomList = new ArrayList<>();
        rooms.stream()
                .filter(r -> r.getStatus() != RoomStatus.UNDER_MAINTENANCE)
                .sorted(Comparator.comparing(Room::getRoomNumber))
                .forEach(r -> roomList.add(PublicRoomResponse.builder()
                        .id(r.getId())
                        .roomNumber(r.getRoomNumber())
                        .type(r.getType())
                        .floorNumber(r.getFloorNumber())
                        .capacity(r.getCapacity())
                        .availableBeds(Math.max(0, r.getCapacity() - occupancy(r, expired)))
                        .monthlyRent(r.getMonthlyRent())
                        .amenities(r.getAmenities())
                        .bookable(isBookable(r, expired))
                        .build()));

        detail.setRooms(roomList);
        List<Long> photoIds = photoRepository.findByPgIdOrderBySortOrderAscIdAsc(pgId).stream().map(x -> x.getId()).toList();
        detail.setPhotoIds(photoIds);
        detail.setCoverPhotoId(photoIds.isEmpty() ? null : photoIds.get(0));
        double[] rating = reviewService.summaries(List.of(pgId)).get(pgId);
        detail.setRatingAvg(rating == null ? null : rating[0]);
        detail.setReviewCount(rating == null ? 0 : (int) rating[1]);
        detail.setCharges(chargeRepository.findByPgIdOrderByIdAsc(pgId).stream()
                .map(CostEstimateService::toResponse).toList());
        detail.setOwnerName(pg.getOwner().getName());
        detail.setRentDueDay(pg.getRentDueDay());
        detail.setPenaltyPercent(pg.getPenaltyPercent());

        // The phone number is only for people who have an account (keeps scrapers out)
        if (loggedIn) {
            String phone = pg.getContactPhone();
            if (phone == null || phone.isBlank()) {
                phone = pg.getOwner().getPhone();
            }
            detail.setContactPhone(phone);
        }
        return detail;
    }

    // ------------------------------------------------------------------ helpers

    PublicPgResponse toSummary(PG pg, List<Room> allRooms, Map<Long, Integer> expired) {
        List<Room> visible = allRooms.stream()
                .filter(r -> r.getStatus() != RoomStatus.UNDER_MAINTENANCE).toList();
        List<Room> bookable = visible.stream().filter(r -> isBookable(r, expired)).toList();

        int freeBeds = bookable.stream().mapToInt(r -> r.getCapacity() - occupancy(r, expired)).sum();

        List<Room> priceBase = bookable.isEmpty() ? visible : bookable;
        BigDecimal startingRent = priceBase.stream()
                .map(Room::getMonthlyRent)
                .filter(Objects::nonNull)
                .min(Comparator.<BigDecimal>naturalOrder())
                .orElse(pg.getBaseMonthlyRent());

        EnumSet<RoomType> types = EnumSet.noneOf(RoomType.class);
        bookable.forEach(r -> types.add(r.getType()));

        return PublicPgResponse.builder()
                .id(pg.getId())
                .name(pg.getName())
                .address(pg.getAddress())
                .city(pg.getCity())
                .description(pg.getDescription())
                .startingRent(startingRent)
                .freeBeds(freeBeds)
                .roomTypes(new ArrayList<>(types))
                .bookingMode(PgBookingPolicy.modeOf(pg))
                .genderPolicy(pg.getGenderPolicy() != null ? pg.getGenderPolicy() : com.pg.entity.enums.GenderPolicy.ANY)
                .amenities(amenitiesOf(visible))
                .latitude(pg.getLatitude())
                .longitude(pg.getLongitude())
                .build();
    }

    /** "WiFi, AC , attached bath" in room.amenities -> ["wifi", "ac", "attached bath"], distinct, in order. */
    static List<String> amenitiesOf(List<Room> rooms) {
        java.util.LinkedHashSet<String> all = new java.util.LinkedHashSet<>();
        for (Room r : rooms) {
            if (r.getAmenities() == null) continue;
            for (String part : r.getAmenities().split("[,;/\\n]")) {
                String a = part.trim().toLowerCase();
                if (!a.isEmpty() && a.length() <= 40) all.add(a);
            }
        }
        return new ArrayList<>(all);
    }

    static boolean isBookable(Room r, Map<Long, Integer> expired) {
        return r.getStatus() != RoomStatus.UNDER_MAINTENANCE && occupancy(r, expired) < r.getCapacity();
    }

    // Beds held by booking requests that already ran out of time are free again,
    // even if the scheduler has not released them yet (it runs every 10 minutes).
    private static int occupancy(Room r, Map<Long, Integer> expired) {
        int stored = r.getCurrentOccupancy() == null ? 0 : r.getCurrentOccupancy();
        return Math.max(0, stored - expired.getOrDefault(r.getId(), 0));
    }

    private Map<Long, Integer> expiredHolds(List<Room> rooms) {
        if (rooms.isEmpty()) return Map.of();
        Map<Long, Integer> result = new HashMap<>();
        for (Object[] row : bookingRepository.countExpiredHolds(
                rooms.stream().map(Room::getId).toList(), java.time.LocalDateTime.now(clock))) {
            result.put((Long) row[0], ((Number) row[1]).intValue());
        }
        return result;
    }

    static final int MAX_RESULTS = 100;

    static boolean acceptsGender(com.pg.entity.enums.GenderPolicy policy, String wanted) {
        if (wanted == null || wanted.isBlank() || policy == null || policy == com.pg.entity.enums.GenderPolicy.ANY) return true;
        return switch (wanted.trim().toUpperCase()) {
            case "MEN" -> policy == com.pg.entity.enums.GenderPolicy.MEN_ONLY;
            case "WOMEN" -> policy == com.pg.entity.enums.GenderPolicy.WOMEN_ONLY;
            default -> throw new BusinessException("gender must be MEN or WOMEN.");
        };
    }

    /** every wanted facility must appear in the PG's list (a room that says "AC room" matches "ac"). */
    static boolean hasAllAmenities(List<String> have, List<String> wanted) {
        for (String w : wanted) {
            String needle = w.trim().toLowerCase();
            if (needle.isEmpty()) continue;
            // short words ("ac", "tv") must be whole words, so "ac" does not match "spacious" or "access";
            // longer ones match the start of a word, so "meal" matches "meals"
            java.util.regex.Pattern p = java.util.regex.Pattern.compile(
                    "\\b" + java.util.regex.Pattern.quote(needle) + (needle.length() <= 3 ? "\\b" : ""));
            boolean found = have != null && have.stream().anyMatch(h -> p.matcher(h).find());
            if (!found) return false;
        }
        return true;
    }

    /** "%text%" in lower case, with % _ and \ escaped so a visitor can not inject wildcards. */
    static String likePattern(String text) {
        String q = text.trim().toLowerCase().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
        return "%" + q + "%";
    }

    private boolean matchesText(PG pg, String text) {
        if (text == null || text.isBlank()) return true;
        String q = text.trim().toLowerCase();
        return contains(pg.getCity(), q) || contains(pg.getName(), q) || contains(pg.getAddress(), q);
    }

    private boolean contains(String value, String lowerQuery) {
        return value != null && value.toLowerCase().contains(lowerQuery);
    }

    private void validateCoordinates(double lat, double lng) {
        if (lat < -90 || lat > 90 || lng < -180 || lng > 180) {
            throw new BusinessException("Location is not valid.");
        }
    }

    /** Distance between two points on Earth in km (Haversine formula). */
    static double distanceKm(double lat1, double lon1, double lat2, double lon2) {
        final double earthRadiusKm = 6371.0;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return earthRadiusKm * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }
}
