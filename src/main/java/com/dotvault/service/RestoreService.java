package com.dotvault.service;

import com.dotvault.model.ConfigEntry;
import com.dotvault.model.Settings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.*;
import java.nio.file.attribute.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Service responsible for restoring backup files.
 * Handles extraction from archives and file restoration.
 */
public class RestoreService {
    
    private static final Logger logger = LoggerFactory.getLogger(RestoreService.class);
    
    private ExecutorService executor;
    private Future<?> currentTask;
    private AtomicBoolean cancelled;
    
    /**
     * Result of a restore operation.
     */
    public static class RestoreResult {
        private final boolean success;
        private final long totalFiles;
        private final long restoredFiles;
        private final long skippedFiles;
        private final long durationMs;
        private final List<String> errors;
        private final List<String> warnings;
        private final List<ConflictInfo> conflicts;
        
        public RestoreResult(boolean success, long totalFiles, long restoredFiles,
                           long skippedFiles, long durationMs, List<String> errors,
                           List<String> warnings, List<ConflictInfo> conflicts) {
            this.success = success;
            this.totalFiles = totalFiles;
            this.restoredFiles = restoredFiles;
            this.skippedFiles = skippedFiles;
            this.durationMs = durationMs;
            this.errors = errors;
            this.warnings = warnings;
            this.conflicts = conflicts;
        }
        
        public boolean isSuccess() { return success; }
        public long getTotalFiles() { return totalFiles; }
        public long getRestoredFiles() { return restoredFiles; }
        public long getSkippedFiles() { return skippedFiles; }
        public long getDurationMs() { return durationMs; }
        public List<String> getErrors() { return errors; }
        public List<String> getWarnings() { return warnings; }
        public List<ConflictInfo> getConflicts() { return conflicts; }
    }
    
    /**
     * Information about a file conflict.
     */
    public static class ConflictInfo {
        private final Path source;
        private final Path destination;
        private final String conflictType;
        
        public ConflictInfo(Path source, Path destination, String conflictType) {
            this.source = source;
            this.destination = destination;
            this.conflictType = conflictType;
        }
        
        public Path getSource() { return source; }
        public Path getDestination() { return destination; }
        public String getConflictType() { return conflictType; }
        
        @Override
        public String toString() {
            return String.format("%s: %s -> %s", conflictType, source, destination);
        }
    }
    
    /**
     * Progress update during restore.
     */
    public static class RestoreProgress {
        private final String currentFile;
        private final long filesProcessed;
        private final long totalFiles;
        private final String status;
        
        public RestoreProgress(String currentFile, long filesProcessed, long totalFiles, String status) {
            this.currentFile = currentFile;
            this.filesProcessed = filesProcessed;
            this.totalFiles = totalFiles;
            this.status = status;
        }
        
        public String getCurrentFile() { return currentFile; }
        public long getFilesProcessed() { return filesProcessed; }
        public long getTotalFiles() { return totalFiles; }
        public String getStatus() { return status; }
        
        public int getPercentage() {
            return totalFiles > 0 ? (int) ((filesProcessed * 100) / totalFiles) : 0;
        }
    }
    
    /**
     * Create a new restore service.
     */
    public RestoreService() {
        this.executor = Executors.newSingleThreadExecutor();
        this.cancelled = new AtomicBoolean(false);
    }
    
    /**
     * Restore from a directory backup.
     */
    public RestoreResult restoreFromDirectory(Path backupDir, Path restoreDir,
                                             Settings settings, Consumer<RestoreProgress> progressCallback,
                                             ConflictResolver conflictResolver) {
        cancelled.set(false);
        long startTime = System.currentTimeMillis();
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        List<ConflictInfo> conflicts = new ArrayList<>();
        AtomicLong restoredFiles = new AtomicLong(0);
        AtomicLong skippedFiles = new AtomicLong(0);
        
        try {
            // List all files in the backup directory
            List<Path> files = new ArrayList<>();
            Files.walk(backupDir)
                 .filter(Files::isRegularFile)
                 .forEach(files::add);
            
            long totalFiles = files.size();
            AtomicLong processedFiles = new AtomicLong(0);
            
            for (Path backupFile : files) {
                if (cancelled.get()) {
                    break;
                }
                
                // Calculate relative path from backup directory
                Path relativePath = backupDir.relativize(backupFile);
                Path targetFile = restoreDir.resolve(relativePath);
                
                // Check for conflicts
                if (Files.exists(targetFile)) {
                    ConflictInfo conflict = new ConflictInfo(backupFile, targetFile, "EXISTS");
                    conflicts.add(conflict);
                    
                    ConflictResolver.Resolution resolution = conflictResolver.resolve(conflict);
                    
                    if (resolution == ConflictResolver.Resolution.SKIP) {
                        skippedFiles.incrementAndGet();
                        processedFiles.incrementAndGet();
                        if (progressCallback != null) {
                            progressCallback.accept(new RestoreProgress(
                                    relativePath.toString(), processedFiles.get(), totalFiles, "Skipped"));
                        }
                        continue;
                    } else if (resolution == ConflictResolver.Resolution.RENAME) {
                        // Rename existing file
                        Path renamed = targetFile.resolveSibling(targetFile.getFileName() + ".bak");
                        Files.move(targetFile, renamed);
                        warnings.add("Renamed existing file: " + targetFile + " -> " + renamed);
                    }
                    // RESOLUTION.OVERWRITE is default behavior
                }
                
                try {
                    // Create parent directories
                    Files.createDirectories(targetFile.getParent());
                    
                    // Copy file preserving attributes
                    if (settings.isPreservePermissions()) {
                        Files.copy(backupFile, targetFile, StandardCopyOption.COPY_ATTRIBUTES,
                                  StandardCopyOption.REPLACE_EXISTING);
                    } else {
                        Files.copy(backupFile, targetFile, StandardCopyOption.REPLACE_EXISTING);
                    }
                    
                    restoredFiles.incrementAndGet();
                    
                } catch (IOException e) {
                    logger.error("Failed to restore file: {}", targetFile, e);
                    errors.add("Failed to restore " + targetFile + ": " + e.getMessage());
                }
                
                processedFiles.incrementAndGet();
                if (progressCallback != null) {
                    progressCallback.accept(new RestoreProgress(
                            relativePath.toString(), processedFiles.get(), totalFiles, "Restored"));
                }
            }
            
            long duration = System.currentTimeMillis() - startTime;
            return new RestoreResult(errors.isEmpty(), totalFiles, 
                    restoredFiles.get(), skippedFiles.get(), duration,
                    errors, warnings, conflicts);
            
        } catch (IOException e) {
            logger.error("Restore failed", e);
            errors.add("Restore failed: " + e.getMessage());
            return new RestoreResult(false, 0, 0, 0,
                    System.currentTimeMillis() - startTime, errors, 
                    Collections.emptyList(), Collections.emptyList());
        }
    }
    
    /**
     * Restore from an archive file.
     */
    public RestoreResult restoreFromArchive(Path archivePath, Path restoreDir,
                                           Settings settings, Consumer<RestoreProgress> progressCallback,
                                           ConflictResolver conflictResolver) {
        cancelled.set(false);
        long startTime = System.currentTimeMillis();
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        List<ConflictInfo> conflicts = new ArrayList<>();
        AtomicLong restoredFiles = new AtomicLong(0);
        AtomicLong skippedFiles = new AtomicLong(0);
        
        String archiveName = archivePath.getFileName().toString().toLowerCase();
        boolean isZip = archiveName.endsWith(".zip");
        boolean isTarGz = archiveName.endsWith(".tar.gz") || archiveName.endsWith(".tgz");
        
        try {
            if (isZip) {
                restoreFromZip(archivePath, restoreDir, settings, progressCallback,
                              conflictResolver, errors, warnings, conflicts, 
                              restoredFiles, skippedFiles);
            } else if (isTarGz) {
                restoreFromTarGz(archivePath, restoreDir, settings, progressCallback,
                                conflictResolver, errors, warnings, conflicts,
                                restoredFiles, skippedFiles);
            } else {
                errors.add("Unsupported archive format: " + archivePath);
                return new RestoreResult(false, 0, 0, 0,
                        System.currentTimeMillis() - startTime, errors,
                        Collections.emptyList(), Collections.emptyList());
            }
            
            long duration = System.currentTimeMillis() - startTime;
            return new RestoreResult(errors.isEmpty(), 0,
                    restoredFiles.get(), skippedFiles.get(), duration,
                    errors, warnings, conflicts);
            
        } catch (Exception e) {
            logger.error("Restore failed", e);
            errors.add("Restore failed: " + e.getMessage());
            return new RestoreResult(false, 0, 0, 0,
                    System.currentTimeMillis() - startTime, errors,
                    Collections.emptyList(), Collections.emptyList());
        }
    }
    
    /**
     * Restore from a ZIP archive.
     */
    private void restoreFromZip(Path archivePath, Path restoreDir, Settings settings,
                               Consumer<RestoreProgress> progressCallback,
                               ConflictResolver conflictResolver, List<String> errors,
                               List<String> warnings, List<ConflictInfo> conflicts,
                               AtomicLong restoredFiles, AtomicLong skippedFiles) 
            throws IOException {
        
        byte[] buffer = new byte[8192];
        AtomicLong totalFiles = new AtomicLong(0);
        
        try (ZipInputStream zis = new ZipInputStream(Files.newInputStream(archivePath))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (cancelled.get()) {
                    break;
                }
                
                totalFiles.incrementAndGet();
                Path targetPath = restoreDir.resolve(entry.getName());
                
                // Skip directories
                if (entry.isDirectory()) {
                    Files.createDirectories(targetPath);
                    continue;
                }
                
                // Check for conflicts
                if (Files.exists(targetPath)) {
                    ConflictInfo conflict = new ConflictInfo(
                            archivePath.resolve(entry.getName()), targetPath, "EXISTS");
                    conflicts.add(conflict);
                    
                    ConflictResolver.Resolution resolution = conflictResolver.resolve(conflict);
                    
                    if (resolution == ConflictResolver.Resolution.SKIP) {
                        skippedFiles.incrementAndGet();
                        if (progressCallback != null) {
                            progressCallback.accept(new RestoreProgress(
                                    entry.getName(), totalFiles.get(), 0, "Skipped"));
                        }
                        zis.closeEntry();
                        continue;
                    } else if (resolution == ConflictResolver.Resolution.RENAME) {
                        Path renamed = targetPath.resolveSibling(
                                targetPath.getFileName() + ".bak");
                        Files.move(targetPath, renamed);
                        warnings.add("Renamed: " + targetPath + " -> " + renamed);
                    }
                }
                
                // Create parent directories
                Files.createDirectories(targetPath.getParent());
                
                // Write file
                try (FileOutputStream fos = new FileOutputStream(targetPath.toFile())) {
                    int len;
                    while ((len = zis.read(buffer)) > 0) {
                        fos.write(buffer, 0, len);
                    }
                }
                
                // Restore permissions if available
                if (settings.isPreservePermissions() && entry.getUnixMode() != 0) {
                    Set<PosixFilePermission> permissions = 
                            PosixFilePermissions.fromUnixMode(entry.getUnixMode());
                    Files.setPosixFilePermissions(targetPath, permissions);
                }
                
                restoredFiles.incrementAndGet();
                if (progressCallback != null) {
                    progressCallback.accept(new RestoreProgress(
                            entry.getName(), totalFiles.get(), 0, "Restored"));
                }
                
                zis.closeEntry();
            }
        }
    }
    
    /**
     * Restore from a tar.gz archive.
     */
    private void restoreFromTarGz(Path archivePath, Path restoreDir, Settings settings,
                                  Consumer<RestoreProgress> progressCallback,
                                  ConflictResolver conflictResolver, List<String> errors,
                                  List<String> warnings, List<ConflictInfo> conflicts,
                                  AtomicLong restoredFiles, AtomicLong skippedFiles)
            throws IOException {
        
        org.apache.commons.compress.archivers.tar.TarArchiveInputStream tis =
                new org.apache.commons.compress.archivers.tar.TarArchiveInputStream(
                        new org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream(
                                Files.newInputStream(archivePath)));
        
        byte[] buffer = new byte[8192];
        AtomicLong totalFiles = new AtomicLong(0);
        
        try {
            org.apache.commons.compress.archivers.ArchiveEntry entry;
            while ((entry = tis.getNextEntry()) != null) {
                if (cancelled.get()) {
                    break;
                }
                
                totalFiles.incrementAndGet();
                Path targetPath = restoreDir.resolve(entry.getName());
                
                // Skip directories (will be created with files)
                if (entry.isDirectory()) {
                    Files.createDirectories(targetPath);
                    continue;
                }
                
                // Check for conflicts
                if (Files.exists(targetPath)) {
                    ConflictInfo conflict = new ConflictInfo(
                            archivePath.resolve(entry.getName()), targetPath, "EXISTS");
                    conflicts.add(conflict);
                    
                    ConflictResolver.Resolution resolution = conflictResolver.resolve(conflict);
                    
                    if (resolution == ConflictResolver.Resolution.SKIP) {
                        skippedFiles.incrementAndGet();
                        if (progressCallback != null) {
                            progressCallback.accept(new RestoreProgress(
                                    entry.getName(), totalFiles.get(), 0, "Skipped"));
                        }
                        continue;
                    } else if (resolution == ConflictResolver.Resolution.RENAME) {
                        Path renamed = targetPath.resolveSibling(
                                targetPath.getFileName() + ".bak");
                        Files.move(targetPath, renamed);
                        warnings.add("Renamed: " + targetPath + " -> " + renamed);
                    }
                }
                
                // Create parent directories
                Files.createDirectories(targetPath.getParent());
                
                // Write file
                try (FileOutputStream fos = new FileOutputStream(targetPath.toFile())) {
                    int len;
                    while ((len = tis.read(buffer)) > 0) {
                        fos.write(buffer, 0, len);
                    }
                }
                
                // Restore permissions
                if (settings.isPreservePermissions() && entry instanceof 
                        org.apache.commons.compress.archivers.tar.TarArchiveEntry tarEntry) {
                    int mode = tarEntry.getMode();
                    if (mode > 0) {
                        Set<PosixFilePermission> permissions = 
                                PosixFilePermissions.fromUnixMode(mode);
                        Files.setPosixFilePermissions(targetPath, permissions);
                    }
                }
                
                restoredFiles.incrementAndGet();
                if (progressCallback != null) {
                    progressCallback.accept(new RestoreProgress(
                            entry.getName(), totalFiles.get(), 0, "Restored"));
                }
            }
        } finally {
            tis.close();
        }
    }
    
    /**
     * Cancel the current restore operation.
     */
    public void cancel() {
        cancelled.set(true);
    }
    
    /**
     * Check if restore is currently running.
     */
    public boolean isRunning() {
        return currentTask != null && !currentTask.isDone();
    }
    
    /**
     * Shutdown the service.
     */
    public void shutdown() {
        if (executor != null) {
            executor.shutdown();
            try {
                if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                    executor.shutdownNow();
                }
            } catch (InterruptedException e) {
                executor.shutdownNow();
            }
        }
    }
    
    /**
     * Interface for resolving file conflicts during restore.
     */
    public interface ConflictResolver {
        enum Resolution { OVERWRITE, SKIP, RENAME }
        
        Resolution resolve(RestoreService.ConflictInfo conflict);
        
        /**
         * Default conflict resolver that always overwrites.
         */
        static ConflictResolver alwaysOverwrite() {
            return conflict -> Resolution.OVERWRITE;
        }
        
        /**
         * Default conflict resolver that always skips.
         */
        static ConflictResolver alwaysSkip() {
            return conflict -> Resolution.SKIP;
        }
        
        /**
         * Interactive conflict resolver (to be implemented with UI).
         */
        static ConflictResolver interactive() {
            // For non-interactive use, default to overwrite
            return alwaysOverwrite();
        }
    }
}
