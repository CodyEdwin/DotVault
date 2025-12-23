package com.dotvault.service;

import com.dotvault.model.ConfigEntry;
import com.dotvault.model.Settings;
import org.apache.commons.compress.archivers.ArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream;
import org.apache.commons.compress.utils.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Service responsible for performing backup operations.
 * Handles copying files, compression, and progress tracking.
 */
public class BackupService {
    
    private static final Logger logger = LoggerFactory.getLogger(BackupService.class);
    private static final DateTimeFormatter TIMESTAMP_FORMAT = 
            DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");
    
    private ExecutorService executor;
    private Future<?> currentTask;
    private AtomicBoolean cancelled;
    private AtomicLong bytesProcessed;
    private AtomicLong totalBytes;
    
    /**
     * Result of a backup operation.
     */
    public static class BackupResult {
        private final boolean success;
        private final String outputPath;
        private final long totalFiles;
        private final long totalBytes;
        private final long durationMs;
        private final List<String> errors;
        private final List<String> skippedFiles;
        
        public BackupResult(boolean success, String outputPath, long totalFiles,
                          long totalBytes, long durationMs, List<String> errors,
                          List<String> skippedFiles) {
            this.success = success;
            this.outputPath = outputPath;
            this.totalFiles = totalFiles;
            this.totalBytes = totalBytes;
            this.durationMs = durationMs;
            this.errors = errors;
            this.skippedFiles = skippedFiles;
        }
        
        public boolean isSuccess() { return success; }
        public String getOutputPath() { return outputPath; }
        public long getTotalFiles() { return totalFiles; }
        public long getTotalBytes() { return totalBytes; }
        public long getDurationMs() { return durationMs; }
        public List<String> getErrors() { return errors; }
        public List<String> getSkippedFiles() { return skippedFiles; }
    }
    
    /**
     * Progress update during backup.
     */
    public static class BackupProgress {
        private final String currentFile;
        private final long filesProcessed;
        private final long totalFiles;
        private final long bytesProcessed;
        private final long totalBytes;
        private final int percentage;
        
        public BackupProgress(String currentFile, long filesProcessed, long totalFiles,
                             long bytesProcessed, long totalBytes) {
            this.currentFile = currentFile;
            this.filesProcessed = filesProcessed;
            this.totalFiles = totalFiles;
            this.bytesProcessed = bytesProcessed;
            this.totalBytes = totalBytes;
            this.percentage = totalBytes > 0 ? (int) ((bytesProcessed * 100) / totalBytes) : 0;
        }
        
        public String getCurrentFile() { return currentFile; }
        public long getFilesProcessed() { return filesProcessed; }
        public long getTotalFiles() { return totalFiles; }
        public long getBytesProcessed() { return bytesProcessed; }
        public long getTotalBytes() { return totalBytes; }
        public int getPercentage() { return percentage; }
    }
    
    /**
     * Create a new backup service.
     */
    public BackupService() {
        this.executor = Executors.newSingleThreadExecutor();
        this.cancelled = new AtomicBoolean(false);
        this.bytesProcessed = new AtomicLong(0);
        this.totalBytes = new AtomicLong(0);
    }
    
    /**
     * Perform a backup operation.
     *
     * @param entries       The configuration entries to backup
     * @param destination   The destination directory or archive path
     * @param settings      Backup settings
     * @param progressCallback Callback for progress updates
     * @return The backup result
     */
    public BackupResult backup(List<ConfigEntry> entries, Path destination,
                              Settings settings, Consumer<BackupProgress> progressCallback) {
        cancelled.set(false);
        bytesProcessed.set(0);
        
        if (entries == null || entries.isEmpty()) {
            return new BackupResult(false, null, 0, 0, 0, 
                    Collections.singletonList("No entries selected for backup"), 
                    Collections.emptyList());
        }
        
        long startTime = System.currentTimeMillis();
        List<String> errors = new ArrayList<>();
        List<String> skippedFiles = new ArrayList<>();
        
        try {
            // Filter entries that exist
            List<ConfigEntry> validEntries = entries.stream()
                    .filter(ConfigEntry::isExists)
                    .toList();
            
            if (validEntries.isEmpty()) {
                return new BackupResult(false, null, 0, 0, 0,
                        Collections.singletonList("No existing files found to backup"),
                        Collections.emptyList());
            }
            
            // Calculate total bytes
            long totalBytesCalc = validEntries.stream()
                    .mapToLong(ConfigEntry::getFileSize)
                    .sum();
            totalBytes.set(totalBytesCalc);
            
            // Determine output path
            Path outputPath = determineOutputPath(destination, settings);
            
            // Perform the backup
            if (settings.isCompressBackups()) {
                compressBackup(validEntries, outputPath, settings, progressCallback, errors, skippedFiles);
            } else {
                copyBackup(validEntries, outputPath, settings, progressCallback, errors, skippedFiles);
            }
            
            long duration = System.currentTimeMillis() - startTime;
            
            boolean success = errors.isEmpty() || 
                    errors.size() < validEntries.size(); // Success if at least some files backed up
            
            return new BackupResult(success, outputPath.toString(), 
                    validEntries.size(), bytesProcessed.get(), duration,
                    errors, skippedFiles);
            
        } catch (Exception e) {
            logger.error("Backup failed", e);
            errors.add("Backup failed: " + e.getMessage());
            return new BackupResult(false, null, 0, 0, 
                    System.currentTimeMillis() - startTime, errors, Collections.emptyList());
        }
    }
    
    /**
     * Determine the output path for the backup.
     */
    private Path determineOutputPath(Path destination, Settings settings) {
        try {
            Path basePath = destination;
            
            if (!Files.exists(basePath)) {
                Files.createDirectories(basePath);
            }
            
            if (settings.isCreateTimestampFolder() && !settings.isCompressBackups()) {
                String timestamp = LocalDateTime.now().format(TIMESTAMP_FORMAT);
                basePath = basePath.resolve("backup_" + timestamp);
                Files.createDirectories(basePath);
            }
            
            return basePath;
        } catch (IOException e) {
            throw new RuntimeException("Failed to create backup directory", e);
        }
    }
    
    /**
     * Perform a plain file copy backup.
     */
    private void copyBackup(List<ConfigEntry> entries, Path destination,
                           Settings settings, Consumer<BackupProgress> progressCallback,
                           List<String> errors, List<String> skippedFiles) {
        String homeDir = System.getProperty("user.home");
        AtomicLong filesProcessed = new AtomicLong(0);
        
        for (ConfigEntry entry : entries) {
            if (cancelled.get()) {
                break;
            }
            
            String sourcePath = entry.getPath().replace("~", homeDir);
            Path source = Paths.get(sourcePath);
            Path dest = destination.resolve(entry.getPath().replace("~", homeDir)
                    .substring(homeDir.length() + 1));
            
            try {
                if (Files.isDirectory(source)) {
                    Files.createDirectories(dest);
                    copyDirectory(source, dest, settings, progressCallback, 
                                 filesProcessed, errors, skippedFiles);
                } else {
                    Files.createDirectories(dest.getParent());
                    copyFile(source, dest, settings, progressCallback, 
                            filesProcessed, errors, skippedFiles);
                }
            } catch (IOException e) {
                logger.error("Failed to backup: {}", sourcePath, e);
                errors.add("Failed to backup " + entry.getPath() + ": " + e.getMessage());
            }
        }
    }
    
    /**
     * Copy a directory recursively.
     */
    private void copyDirectory(Path source, Path dest, Settings settings,
                              Consumer<BackupProgress> progressCallback,
                              AtomicLong filesProcessed, List<String> errors,
                              List<String> skippedFiles) throws IOException {
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(source)) {
            for (Path entry : stream) {
                if (cancelled.get()) {
                    break;
                }
                
                Path destEntry = dest.resolve(entry.getFileName().toString());
                
                if (Files.isDirectory(entry)) {
                    Files.createDirectories(destEntry);
                    copyDirectory(entry, destEntry, settings, progressCallback,
                                 filesProcessed, errors, skippedFiles);
                } else if (Files.isRegularFile(entry)) {
                    if (shouldSkipFile(entry, settings)) {
                        skippedFiles.add(entry.toString());
                        continue;
                    }
                    copyFile(entry, destEntry, settings, progressCallback,
                            filesProcessed, errors, skippedFiles);
                }
            }
        }
    }
    
    /**
     * Copy a single file.
     */
    private void copyFile(Path source, Path dest, Settings settings,
                         Consumer<BackupProgress> progressCallback,
                         AtomicLong filesProcessed, List<String> errors,
                         List<String> skippedFiles) {
        try {
            // Preserve permissions if on Linux
            if (settings.isPreservePermissions()) {
                Files.copy(source, dest, StandardCopyOption.COPY_ATTRIBUTES, 
                          StandardCopyOption.REPLACE_EXISTING);
            } else {
                Files.copy(source, dest, StandardCopyOption.REPLACE_EXISTING);
            }
            
            long fileSize = Files.size(source);
            bytesProcessed.addAndGet(fileSize);
            filesProcessed.incrementAndGet();
            
            if (progressCallback != null) {
                progressCallback.accept(new BackupProgress(
                        source.toString(), filesProcessed.get(), 0,
                        bytesProcessed.get(), totalBytes.get()
                ));
            }
        } catch (IOException e) {
            logger.error("Failed to copy file: {}", source, e);
            errors.add("Failed to copy " + source + ": " + e.getMessage());
        }
    }
    
    /**
     * Perform a compressed backup.
     */
    private void compressBackup(List<ConfigEntry> entries, Path destination,
                               Settings settings, Consumer<BackupProgress> progressCallback,
                               List<String> errors, List<String> skippedFiles) 
            throws IOException {
        
        String homeDir = System.getProperty("user.home");
        String extension = settings.getCompressionFormat().equals("tar.gz") ? ".tar.gz" : ".zip";
        Path archivePath = destination.getParent().resolve(destination.getFileName() + extension);
        
        AtomicLong filesProcessed = new AtomicLong(0);
        
        try (OutputStream os = Files.newOutputStream(archivePath)) {
            if (settings.getCompressionFormat().equals("tar.gz")) {
                try (TarArchiveOutputStream tarOut = new TarArchiveOutputStream(
                        new GzipCompressorOutputStream(os))) {
                    tarOut.setLongFileMode(TarArchiveOutputStream.LONGFILE_GNU);
                    for (ConfigEntry entry : entries) {
                        if (cancelled.get()) {
                            break;
                        }
                        String sourcePath = entry.getPath().replace("~", homeDir);
                        Path source = Paths.get(sourcePath);
                        
                        if (Files.isDirectory(source)) {
                            addDirectoryToTar(tarOut, source, homeDir, settings,
                                            filesProcessed, errors, skippedFiles);
                        } else {
                            addFileToTar(tarOut, source, homeDir, settings,
                                        filesProcessed, errors, skippedFiles);
                        }
                    }
                }
            } else {
                try (ZipOutputStream zipOut = new ZipOutputStream(os)) {
                    for (ConfigEntry entry : entries) {
                        if (cancelled.get()) {
                            break;
                        }
                        String sourcePath = entry.getPath().replace("~", homeDir);
                        Path source = Paths.get(sourcePath);
                        
                        if (Files.isDirectory(source)) {
                            addDirectoryToZip(zipOut, source, homeDir, settings,
                                            filesProcessed, errors, skippedFiles);
                        } else {
                            addFileToZip(zipOut, source, homeDir, settings,
                                        filesProcessed, errors, skippedFiles);
                        }
                    }
                }
            }
        }
    }
    
    /**
     * Add a file to tar archive.
     */
    private void addFileToTar(TarArchiveOutputStream tarOut, Path file, String homeDir,
                             Settings settings, AtomicLong filesProcessed,
                             List<String> errors, List<String> skippedFiles) {
        if (shouldSkipFile(file, settings)) {
            skippedFiles.add(file.toString());
            return;
        }
        
        try {
            String entryName = file.toString().substring(homeDir.length() + 1);
            TarArchiveEntry tarEntry = new TarArchiveEntry(file.toFile(), entryName);
            
            // Preserve permissions
            if (settings.isPreservePermissions()) {
                tarEntry.setMode(Files.getPosixFilePermissions(file)
                        .stream().mapToInt(p -> p.toOctal().charAt(0)).sum());
            }
            
            tarOut.putArchiveEntry(tarEntry);
            byte[] content = Files.readAllBytes(file);
            tarOut.write(content);
            tarOut.closeArchiveEntry();
            
            bytesProcessed.addAndGet(content.length);
            filesProcessed.incrementAndGet();
            
            if (filesProcessed.get() % 10 == 0 && progressCallback != null) {
                progressCallback.accept(new BackupProgress(
                        file.toString(), filesProcessed.get(), 0,
                        bytesProcessed.get(), totalBytes.get()
                ));
            }
        } catch (IOException e) {
            logger.error("Failed to add file to tar: {}", file, e);
            errors.add("Failed to add " + file + " to archive: " + e.getMessage());
        }
    }
    
    /**
     * Add a directory to tar archive.
     */
    private void addDirectoryToTar(TarArchiveOutputStream tarOut, Path dir, String homeDir,
                                   Settings settings, AtomicLong filesProcessed,
                                   List<String> errors, List<String> skippedFiles) 
            throws IOException {
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir)) {
            for (Path entry : stream) {
                if (cancelled.get()) {
                    break;
                }
                
                if (Files.isDirectory(entry)) {
                    addDirectoryToTar(tarOut, entry, homeDir, settings,
                                    filesProcessed, errors, skippedFiles);
                } else {
                    addFileToTar(tarOut, entry, homeDir, settings,
                                filesProcessed, errors, skippedFiles);
                }
            }
        }
    }
    
    /**
     * Add a file to zip archive.
     */
    private void addFileToZip(ZipOutputStream zipOut, Path file, String homeDir,
                             Settings settings, AtomicLong filesProcessed,
                             List<String> errors, List<String> skippedFiles) {
        if (shouldSkipFile(file, settings)) {
            skippedFiles.add(file.toString());
            return;
        }
        
        try {
            String entryName = file.toString().substring(homeDir.length() + 1);
            ZipEntry zipEntry = new ZipEntry(entryName);
            
            // Set compression method
            zipEntry.setMethod(ZipEntry.DEFLATED);
            
            zipOut.putNextEntry(zipEntry);
            byte[] content = Files.readAllBytes(file);
            zipOut.write(content);
            zipOut.closeEntry();
            
            bytesProcessed.addAndGet(content.length);
            filesProcessed.incrementAndGet();
            
            if (filesProcessed.get() % 10 == 0 && progressCallback != null) {
                progressCallback.accept(new BackupProgress(
                        file.toString(), filesProcessed.get(), 0,
                        bytesProcessed.get(), totalBytes.get()
                ));
            }
        } catch (IOException e) {
            logger.error("Failed to add file to zip: {}", file, e);
            errors.add("Failed to add " + file + " to archive: " + e.getMessage());
        }
    }
    
    /**
     * Add a directory to zip archive.
     */
    private void addDirectoryToZip(ZipOutputStream zipOut, Path dir, String homeDir,
                                   Settings settings, AtomicLong filesProcessed,
                                   List<String> errors, List<String> skippedFiles) 
            throws IOException {
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir)) {
            for (Path entry : stream) {
                if (cancelled.get()) {
                    break;
                }
                
                if (Files.isDirectory(entry)) {
                    addDirectoryToZip(zipOut, entry, homeDir, settings,
                                    filesProcessed, errors, skippedFiles);
                } else {
                    addFileToZip(zipOut, entry, homeDir, settings,
                                filesProcessed, errors, skippedFiles);
                }
            }
        }
    }
    
    /**
     * Check if a file should be skipped based on exclude patterns.
     */
    private boolean shouldSkipFile(Path file, Settings settings) {
        String filename = file.getFileName().toString();
        
        for (String pattern : settings.getExcludePatterns()) {
            if (pattern.startsWith("*")) {
                // Suffix match
                if (filename.endsWith(pattern.substring(1))) {
                    return true;
                }
            } else if (pattern.endsWith("/")) {
                // Directory exclusion
                if (file.getParent() != null && 
                    file.getParent().toString().contains(pattern.substring(0, pattern.length() - 1))) {
                    return true;
                }
            } else if (filename.equals(pattern)) {
                return true;
            }
        }
        
        return false;
    }
    
    /**
     * Cancel the current backup operation.
     */
    public void cancel() {
        cancelled.set(true);
    }
    
    /**
     * Check if backup is currently running.
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
}
