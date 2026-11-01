package com.pg.service;

import com.pg.exception.BusinessException;
import com.pg.exception.ResourceNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Set;
import java.util.UUID;

/**
 * Saves uploaded photos on disk, OUTSIDE anything the web server serves.
 * Files are only reachable through our own endpoints (which decide who may see them).
 * Two folders: "inspections" (private evidence) and "pgs" (public PG gallery).
 * The real file type is checked from the file's first bytes, not from its name or header.
 */
@Component
public class InspectionStorage {

    public static final long MAX_BYTES = 5L * 1024 * 1024;
    public static final String INSPECTIONS = "inspections";
    public static final String PGS = "pgs";
    private static final Set<String> FOLDERS = Set.of(INSPECTIONS, PGS);

    public record Stored(String name, String contentType, long size, String sha256) {}

    private static final Logger log = LoggerFactory.getLogger(InspectionStorage.class);

    private final Path base;

    public InspectionStorage(@Value("${app.upload-dir:./uploads}") String dir) {
        this.base = Path.of(dir).toAbsolutePath().normalize();
    }

    public Stored save(byte[] bytes) {
        return save(bytes, INSPECTIONS);
    }

    public Stored save(byte[] bytes, String folder) {
        if (bytes == null || bytes.length == 0) {
            throw new BusinessException("The photo is empty.");
        }
        if (bytes.length > MAX_BYTES) {
            throw new BusinessException("The photo is too big. The limit is 5 MB.");
        }
        String[] type = detect(bytes);
        if (type == null) {
            throw new BusinessException("Only JPEG, PNG or WebP photos are accepted.");
        }
        String name = UUID.randomUUID() + type[1]; // we choose the name; the uploader's name is never used
        Path root = root(folder);
        try {
            Files.createDirectories(root);
            Path file = root.resolve(name);
            Files.write(file, bytes);
            deleteFileIfTransactionFails(file);
            return new Stored(name, type[0], bytes.length, sha256(bytes));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public byte[] load(String storedName) {
        return load(storedName, INSPECTIONS);
    }

    public byte[] load(String storedName, String folder) {
        Path root = root(folder);
        Path p = root.resolve(storedName).normalize();
        if (!p.startsWith(root)) {
            throw new BusinessException("Invalid file.");
        }
        try {
            return Files.readAllBytes(p);
        } catch (NoSuchFileException e) {
            // the row exists but the file is gone: a clean 404, not a server error
            log.warn("Photo file missing on disk: {}/{}", folder, storedName);
            throw new ResourceNotFoundException("Photo not found.");
        } catch (IOException e) {
            // a real disk problem (permissions, hardware): this IS a server error
            throw new UncheckedIOException(e);
        }
    }

    /**
     * The file is written BEFORE the database row is committed. If the transaction then fails and
     * rolls back, nobody points at the file any more, so remove it instead of leaving an orphan.
     */
    private void deleteFileIfTransactionFails(Path file) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) return;
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status != STATUS_COMMITTED) {
                    try {
                        Files.deleteIfExists(file);
                    } catch (IOException e) {
                        log.warn("Could not delete orphan file {}", file, e);
                    }
                }
            }
        });
    }

    public void delete(String storedName, String folder) {
        Path root = root(folder);
        Path p = root.resolve(storedName).normalize();
        if (!p.startsWith(root)) return;
        try {
            Files.deleteIfExists(p);
        } catch (IOException ignored) {
            // the database row is already gone; a leftover file is harmless
        }
    }

    private Path root(String folder) {
        if (!FOLDERS.contains(folder)) throw new IllegalArgumentException("Unknown folder");
        return base.resolve(folder);
    }

    /** returns {contentType, extension} or null when it is not an accepted image */
    static String[] detect(byte[] b) {
        if (b.length >= 3 && (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8 && (b[2] & 0xFF) == 0xFF) {
            return new String[]{"image/jpeg", ".jpg"};
        }
        if (b.length >= 8 && (b[0] & 0xFF) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G'
                && b[4] == 0x0D && b[5] == 0x0A && b[6] == 0x1A && b[7] == 0x0A) {
            return new String[]{"image/png", ".png"};
        }
        if (b.length >= 12 && b[0] == 'R' && b[1] == 'I' && b[2] == 'F' && b[3] == 'F'
                && b[8] == 'W' && b[9] == 'E' && b[10] == 'B' && b[11] == 'P') {
            return new String[]{"image/webp", ".webp"};
        }
        return null;
    }

    static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
