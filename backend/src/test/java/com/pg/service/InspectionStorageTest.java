package com.pg.service;

import com.pg.exception.ResourceNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class InspectionStorageTest {

    // smallest bytes that start like a PNG file (the storage checks the first bytes, not the name)
    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 1, 2, 3, 4};

    @TempDir Path dir;

    @Test
    void loadingAMissingFileIsACleanNotFoundNotAServerError() {
        InspectionStorage storage = new InspectionStorage(dir.toString());
        assertThrows(ResourceNotFoundException.class, () -> storage.load("does-not-exist.png"));
    }

    @Test
    void savedFileCanBeLoadedBack() {
        InspectionStorage storage = new InspectionStorage(dir.toString());
        InspectionStorage.Stored s = storage.save(PNG);
        assertArrayEquals(PNG, storage.load(s.name()));
    }

    @Test
    void fileIsRemovedWhenTheDatabaseTransactionRollsBack() throws Exception {
        InspectionStorage storage = new InspectionStorage(dir.toString());
        TransactionSynchronizationManager.initSynchronization();
        try {
            InspectionStorage.Stored s = storage.save(PNG);
            Path file = dir.resolve("inspections").resolve(s.name());
            assertTrue(Files.exists(file));

            for (TransactionSynchronization sync : List.copyOf(TransactionSynchronizationManager.getSynchronizations())) {
                sync.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK);
            }
            assertFalse(Files.exists(file), "orphan file must be deleted after a rollback");
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void fileIsKeptWhenTheTransactionCommits() {
        InspectionStorage storage = new InspectionStorage(dir.toString());
        TransactionSynchronizationManager.initSynchronization();
        try {
            InspectionStorage.Stored s = storage.save(PNG);
            Path file = dir.resolve("inspections").resolve(s.name());
            for (TransactionSynchronization sync : List.copyOf(TransactionSynchronizationManager.getSynchronizations())) {
                sync.afterCompletion(TransactionSynchronization.STATUS_COMMITTED);
            }
            assertTrue(Files.exists(file));
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }
}
