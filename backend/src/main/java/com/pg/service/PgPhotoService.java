package com.pg.service;

import com.pg.dto.response.PgPhotoResponse;
import com.pg.entity.PG;
import com.pg.entity.PgPhoto;
import com.pg.exception.BusinessException;
import com.pg.exception.ResourceNotFoundException;
import com.pg.repository.PGRepository;
import com.pg.repository.PgPhotoRepository;
import com.pg.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** The public photo gallery of a PG (owner manages, everyone sees). */
@Service
@RequiredArgsConstructor
public class PgPhotoService {

    static final int MAX_PHOTOS = 8;

    private final PgPhotoRepository photoRepository;
    private final PGRepository pgRepository;
    private final UserRepository userRepository;
    private final InspectionStorage storage;

    public record PhotoFile(byte[] bytes, String contentType) {}

    @Transactional(readOnly = true)
    public List<PgPhotoResponse> list(String ownerEmail) {
        PG pg = ownerPg(ownerEmail);
        return toResponses(photoRepository.findByPgIdOrderBySortOrderAscIdAsc(pg.getId()));
    }

    @Transactional
    public List<PgPhotoResponse> add(String ownerEmail, byte[] bytes, String caption) {
        PG pg = lockedOwnerPg(ownerEmail); // locked: two uploads at once can not both pass the limit
        List<PgPhoto> existing = photoRepository.findByPgIdOrderBySortOrderAscIdAsc(pg.getId());
        if (existing.size() >= MAX_PHOTOS) {
            throw new BusinessException("You can add at most " + MAX_PHOTOS + " photos. Remove one first.");
        }
        InspectionStorage.Stored s = storage.save(bytes, InspectionStorage.PGS); // checks size and real type
        int next = existing.stream().mapToInt(PgPhoto::getSortOrder).max().orElse(-1) + 1;
        photoRepository.save(PgPhoto.builder().pg(pg).storedName(s.name()).contentType(s.contentType())
                .sizeBytes(s.size()).caption(blankToNull(caption)).sortOrder(next).build());
        return toResponses(photoRepository.findByPgIdOrderBySortOrderAscIdAsc(pg.getId()));
    }

    @Transactional
    public List<PgPhotoResponse> remove(String ownerEmail, Long id) {
        PG pg = ownerPg(ownerEmail);
        PgPhoto p = photoRepository.findByIdAndPgId(id, pg.getId())   // only THIS owner's photos
                .orElseThrow(() -> new ResourceNotFoundException("Photo not found"));
        photoRepository.delete(p);
        storage.delete(p.getStoredName(), InspectionStorage.PGS);
        return toResponses(photoRepository.findByPgIdOrderBySortOrderAscIdAsc(pg.getId()));
    }

    @Transactional
    public List<PgPhotoResponse> makeCover(String ownerEmail, Long id) {
        PG pg = ownerPg(ownerEmail);
        List<PgPhoto> all = photoRepository.findByPgIdOrderBySortOrderAscIdAsc(pg.getId());
        PgPhoto chosen = all.stream().filter(p -> p.getId().equals(id)).findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Photo not found"));
        chosen.setSortOrder(all.stream().mapToInt(PgPhoto::getSortOrder).min().orElse(0) - 1);
        photoRepository.save(chosen);
        return toResponses(photoRepository.findByPgIdOrderBySortOrderAscIdAsc(pg.getId()));
    }

    /** Public: only photos of ACTIVE PGs. */
    @Transactional(readOnly = true)
    public PhotoFile publicPhoto(Long id) {
        PgPhoto p = photoRepository.findById(id)
                .filter(x -> Boolean.TRUE.equals(x.getPg().getIsActive()))
                .orElseThrow(() -> new ResourceNotFoundException("Photo not found"));
        return new PhotoFile(storage.load(p.getStoredName(), InspectionStorage.PGS), p.getContentType());
    }

    private List<PgPhotoResponse> toResponses(List<PgPhoto> photos) {
        return java.util.stream.IntStream.range(0, photos.size()).mapToObj(i -> {
            PgPhoto p = photos.get(i);
            return PgPhotoResponse.builder().id(p.getId()).caption(p.getCaption())
                    .sortOrder(p.getSortOrder()).sizeBytes(p.getSizeBytes()).cover(i == 0).build();
        }).toList();
    }

    private PG ownerPg(String ownerEmail) {
        var owner = userRepository.findByEmail(ownerEmail.toLowerCase())
                .orElseThrow(() -> new ResourceNotFoundException("Owner not found"));
        return pgRepository.findByOwnerIdAndIsActiveTrue(owner.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Set up your PG first."));
    }

    private PG lockedOwnerPg(String ownerEmail) {
        PG pg = ownerPg(ownerEmail);
        return pgRepository.findByIdForUpdate(pg.getId()).orElse(pg);
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
