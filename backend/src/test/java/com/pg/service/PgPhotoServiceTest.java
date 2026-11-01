package com.pg.service;

import com.pg.dto.response.PgPhotoResponse;
import com.pg.entity.*;
import com.pg.entity.enums.Role;
import com.pg.exception.BusinessException;
import com.pg.exception.ResourceNotFoundException;
import com.pg.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PgPhotoServiceTest {

    @Mock PgPhotoRepository photoRepository;
    @Mock PGRepository pgRepository;
    @Mock UserRepository userRepository;
    @Mock InspectionStorage storage;
    @InjectMocks PgPhotoService service;

    User owner, other;
    PG pg, otherPg;

    @BeforeEach
    void setUp() {
        owner = User.builder().id(1L).email("o@x.com").role(Role.OWNER).build();
        other = User.builder().id(2L).email("o2@x.com").role(Role.OWNER).build();
        pg = PG.builder().id(10L).owner(owner).isActive(true).build();
        otherPg = PG.builder().id(11L).owner(other).isActive(true).build();
        when(userRepository.findByEmail("o@x.com")).thenReturn(Optional.of(owner));
        when(userRepository.findByEmail("o2@x.com")).thenReturn(Optional.of(other));
        when(pgRepository.findByOwnerIdAndIsActiveTrue(1L)).thenReturn(Optional.of(pg));
        when(pgRepository.findByOwnerIdAndIsActiveTrue(2L)).thenReturn(Optional.of(otherPg));
        when(pgRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(pg));
        when(storage.save(any(), eq(InspectionStorage.PGS)))
                .thenReturn(new InspectionStorage.Stored("abc.jpg", "image/jpeg", 10, "x"));
    }

    private PgPhoto photo(long id, int order) {
        return PgPhoto.builder().id(id).pg(pg).storedName("f" + id + ".jpg").contentType("image/jpeg").sizeBytes(1L).sortOrder(order).build();
    }

    @Test
    void ninthPhotoIsRefusedAndNothingIsStored() {
        List<PgPhoto> eight = new ArrayList<>();
        for (int i = 0; i < 8; i++) eight.add(photo(i + 1, i));
        when(photoRepository.findByPgIdOrderBySortOrderAscIdAsc(10L)).thenReturn(eight);

        assertThrows(BusinessException.class, () -> service.add("o@x.com", new byte[]{1}, null));
        verify(storage, never()).save(any(), any());
    }

    @Test
    void newPhotosGoToTheEndAndTheFirstIsTheCover() {
        when(photoRepository.findByPgIdOrderBySortOrderAscIdAsc(10L)).thenReturn(List.of(photo(1, 0), photo(2, 1)));
        service.add("o@x.com", new byte[]{1}, " front gate ");
        verify(photoRepository).save(argThat(p -> p.getSortOrder() == 2 && "front gate".equals(p.getCaption())));
    }

    @Test
    void makeCoverMovesItToTheFront() {
        PgPhoto first = photo(1, 0), second = photo(2, 1);
        when(photoRepository.findByPgIdOrderBySortOrderAscIdAsc(10L)).thenReturn(List.of(first, second));
        service.makeCover("o@x.com", 2L);
        assertTrue(second.getSortOrder() < first.getSortOrder());
    }

    @Test
    void anotherOwnerCanNotDeleteMyPhoto() {
        when(photoRepository.findByIdAndPgId(1L, 11L)).thenReturn(Optional.empty()); // scoped to THEIR pg
        assertThrows(ResourceNotFoundException.class, () -> service.remove("o2@x.com", 1L));
        verify(photoRepository, never()).delete(any());
    }

    @Test
    void deleteRemovesTheRowAndTheFile() {
        PgPhoto p = photo(1, 0);
        when(photoRepository.findByIdAndPgId(1L, 10L)).thenReturn(Optional.of(p));
        service.remove("o@x.com", 1L);
        verify(photoRepository).delete(p);
        verify(storage).delete("f1.jpg", InspectionStorage.PGS);
    }

    @Test
    void publicCanNotSeePhotosOfAnInactivePg() {
        PgPhoto p = photo(1, 0);
        pg.setIsActive(false);
        when(photoRepository.findById(1L)).thenReturn(Optional.of(p));
        assertThrows(ResourceNotFoundException.class, () -> service.publicPhoto(1L));
    }
}
