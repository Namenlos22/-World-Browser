package com.example.worldbrowser.compat;

import com.example.worldbrowser.WorldBrowser;
import com.example.worldbrowser.model.ProfileInfo;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.LinkOption;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class WorldBackupHelper {
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");

    public static Path createBackup(Path worldDir, ProfileInfo profile) throws IOException {
        if (worldDir == null || !Files.isDirectory(worldDir)) {
            throw new IOException("World directory does not exist: " + worldDir);
        }

        String worldName = worldDir.getFileName().toString();
        Path backupDir;

        if (profile != null && profile.getGameDir() != null) {
            backupDir = profile.getGameDir().resolve("backups");
        } else if (worldDir.getParent() != null && worldDir.getParent().getParent() != null) {
            backupDir = worldDir.getParent().getParent().resolve("backups");
        } else {
            backupDir = worldDir.resolveSibling("backups");
        }

        Files.createDirectories(backupDir);

        String timestamp = LocalDateTime.now().format(FORMATTER);
        if (backupDir.toRealPath().startsWith(worldDir.toRealPath())) {
            throw new IOException("Backup directory must be outside the world directory");
        }
        Path zipFile = Files.createTempFile(backupDir, sanitizeFileName(worldName) + "_" + timestamp + "_", ".zip");

        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(zipFile))) {
            Files.walkFileTree(worldDir, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    // Skip session.lock to avoid file-in-use sharing violations
                    if (file.getFileName().toString().equals("session.lock")) {
                        return FileVisitResult.CONTINUE;
                    }
                    if (!attrs.isRegularFile()) {
                        throw new IOException("Cannot safely back up non-regular file: " + file);
                    }

                    Path relative = worldDir.relativize(file);
                    ZipEntry entry = new ZipEntry(relative.toString().replace('\\', '/'));
                    entry.setTime(attrs.lastModifiedTime().toMillis());
                    zos.putNextEntry(entry);

                    try (InputStream is = Files.newInputStream(file, LinkOption.NOFOLLOW_LINKS)) {
                        is.transferTo(zos);
                    }
                    zos.closeEntry();
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException | RuntimeException e) {
            try {
                Files.deleteIfExists(zipFile);
            } catch (IOException cleanupFailure) {
                e.addSuppressed(cleanupFailure);
            }
            throw e;
        }

        WorldBrowser.LOGGER.info("World backup created: {}", zipFile);
        return zipFile;
    }

    private static String sanitizeFileName(String name) {
        return name.replaceAll("[^a-zA-Z0-9._-]", "_");
    }
}
