package com.dotvault.util;

import java.io.*;
import java.nio.file.*;
import java.nio.file.attribute.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Utility class for file operations and checksum calculations.
 * Provides helper methods for copying, comparing, and manipulating files.
 */
public final class FileUtils {
    
    private static final int BUFFER_SIZE = 8192;
    
    private FileUtils() {
        // Utility class - prevent instantiation
    }
    
    /**
     * Copy a file preserving attributes.
     */
    public static void copyFile(Path source, Path target) throws IOException {
        Files.copy(source, target, StandardCopyOption.COPY_ATTRIBUTES, 
                  StandardCopyOption.REPLACE_EXISTING);
    }
    
    /**
     * Copy a file with custom options.
     */
    public static void copyFile(Path source, Path target, CopyOption... options) throws IOException {
        Files.copy(source, target, options);
    }
    
    /**
     * Copy a directory recursively.
     */
    public static void copyDirectory(Path source, Path target) throws IOException {
        Files.walkFileTree(source, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) 
                    throws IOException {
                Path targetDir = target.resolve(source.relativize(dir));
                Files.createDirectories(targetDir);
                return FileVisitResult.CONTINUE;
            }
            
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) 
                    throws IOException {
                Path targetFile = target.resolve(source.relativize(file));
                copyFile(file, targetFile);
                return FileVisitResult.CONTINUE;
            }
        });
    }
    
    /**
     * Delete a file or directory recursively.
     */
    public static void delete(Path path) throws IOException {
        if (Files.exists(path)) {
            if (Files.isDirectory(path)) {
                Files.walk(path)
                     .sorted(Comparator.reverseOrder())
                     .forEach(p -> {
                         try {
                             Files.delete(p);
                         } catch (IOException e) {
                             // Log and continue
                         }
                     });
            } else {
                Files.delete(path);
            }
        }
    }
    
    /**
     * Safely delete a file, ignoring errors.
     */
    public static void deleteQuietly(Path path) {
        try {
            delete(path);
        } catch (IOException e) {
            // Ignore
        }
    }
    
    /**
     * Check if two files are identical by comparing content.
     */
    public static boolean compareFiles(Path file1, Path file2) throws IOException {
        if (!Files.isRegularFile(file1) || !Files.isRegularFile(file2)) {
            return false;
        }
        
        if (Files.size(file1) != Files.size(file2)) {
            return false;
        }
        
        String hash1 = calculateChecksum(file1);
        String hash2 = calculateChecksum(file2);
        return hash1.equals(hash2);
    }
    
    /**
     * Calculate MD5 checksum of a file.
     */
    public static String calculateChecksum(Path file) throws IOException {
        return calculateChecksum(file, "MD5");
    }
    
    /**
     * Calculate checksum of a file using specified algorithm.
     */
    public static String calculateChecksum(Path file, String algorithm) throws IOException {
        if (!Files.isRegularFile(file)) {
            return null;
        }
        
        try (InputStream is = Files.newInputStream(file)) {
            MessageDigest digest = MessageDigest.getInstance(algorithm);
            byte[] buffer = new byte[BUFFER_SIZE];
            int bytesRead;
            
            while ((bytesRead = is.read(buffer)) != -1) {
                digest.update(buffer, 0, bytesRead);
            }
            
            byte[] hash = digest.digest();
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IOException("Algorithm not available: " + algorithm, e);
        }
    }
    
    /**
     * Calculate checksum of a string.
     */
    public static String calculateStringChecksum(String content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("MD5");
            byte[] hash = digest.digest(content.getBytes());
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            return null;
        }
    }
    
    /**
     * Get file permissions as string (e.g., "rwxr-xr-x").
     */
    public static String getPermissionsString(Path path) {
        try {
            Set<PosixFilePermission> permissions = 
                    Files.getPosixFilePermissions(path);
            return PosixFilePermissions.toString(permissions);
        } catch (UnsupportedOperationException | IOException e) {
            // Not on POSIX system
            return "Unknown";
        }
    }
    
    /**
     * Set file permissions from string.
     */
    public static void setPermissionsFromString(Path path, String permissions) {
        try {
            Set<PosixFilePermission> perms = 
                    PosixFilePermissions.fromString(permissions);
            Files.setPosixFilePermissions(path, perms);
        } catch (UnsupportedOperationException | IOException e) {
            // Not on POSIX system or permission denied
        }
    }
    
    /**
     * Check if a file is a symbolic link.
     */
    public static boolean isSymlink(Path path) {
        try {
            return Files.isSymbolicLink(path);
        } catch (IOException e) {
            return false;
        }
    }
    
    /**
     * Read target of symbolic link.
     */
    public static String readSymlinkTarget(Path link) {
        try {
            return Files.readSymbolicLink(link).toString();
        } catch (IOException e) {
            return null;
        }
    }
    
    /**
     * Create symbolic link.
     */
    public static void createSymlink(Path target, Path link) throws IOException {
        Files.createSymbolicLink(link, target);
    }
    
    /**
     * Get file extension.
     */
    public static String getExtension(Path path) {
        String name = path.getFileName().toString();
        int lastDot = name.lastIndexOf('.');
        if (lastDot > 0 && lastDot < name.length() - 1) {
            return name.substring(lastDot + 1);
        }
        return "";
    }
    
    /**
     * Remove file extension.
     */
    public static String removeExtension(Path path) {
        String name = path.getFileName().toString();
        int lastDot = name.lastIndexOf('.');
        if (lastDot > 0) {
            return name.substring(0, lastDot);
        }
        return name;
    }
    
    /**
     * Format file size for display.
     */
    public static String formatFileSize(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        } else if (bytes < 1024 * 1024) {
            return String.format("%.1f KB", bytes / 1024.0);
        } else if (bytes < 1024 * 1024 * 1024) {
            return String.format("%.1f MB", bytes / (1024.0 * 1024));
        } else {
            return String.format("%.1f GB", bytes / (1024.0 * 1024 * 1024));
        }
    }
    
    /**
     * Count files in directory recursively.
     */
    public static int countFiles(Path directory) {
        try {
            return (int) Files.walk(directory)
                    .filter(Files::isRegularFile)
                    .count();
        } catch (IOException e) {
            return 0;
        }
    }
    
    /**
     * Count directories in directory recursively.
     */
    public static int countDirectories(Path directory) {
        try {
            return (int) Files.walk(directory)
                    .filter(Files::isDirectory)
                    .count() - 1; // Subtract the root directory
        } catch (IOException e) {
            return 0;
        }
    }
    
    /**
     * List files in directory matching a pattern.
     */
    public static List<Path> listFiles(Path directory, String pattern) throws IOException {
        if (!Files.isDirectory(directory)) {
            return Collections.emptyList();
        }
        
        String regex = PathUtils.globToRegex(pattern);
        final java.util.regex.Pattern p = java.util.regex.Pattern.compile(regex);
        
        return Files.walk(directory)
                .filter(Files::isRegularFile)
                .filter(path -> p.matcher(path.getFileName().toString()).matches())
                .collect(Collectors.toList());
    }
    
    /**
     * Find files by name.
     */
    public static List<Path> findFiles(Path directory, String fileName) throws IOException {
        if (!Files.isDirectory(directory)) {
            return Collections.emptyList();
        }
        
        final String targetName = fileName.toLowerCase();
        
        return Files.walk(directory)
                .filter(Files::isRegularFile)
                .filter(path -> path.getFileName().toString().equalsIgnoreCase(targetName))
                .collect(Collectors.toList());
    }
    
    /**
     * Extract a ZIP archive.
     */
    public static void extractZip(Path zipPath, Path targetDir) throws IOException {
        byte[] buffer = new byte[BUFFER_SIZE];
        
        try (ZipInputStream zis = new ZipInputStream(Files.newInputStream(zipPath))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                Path newFile = targetDir.resolve(entry.getName());
                
                if (entry.isDirectory()) {
                    Files.createDirectories(newFile);
                } else {
                    // Create parent directories
                    Files.createDirectories(newFile.getParent());
                    
                    // Write file
                    try (FileOutputStream fos = new FileOutputStream(newFile.toFile())) {
                        int len;
                        while ((len = zis.read(buffer)) > 0) {
                            fos.write(buffer, 0, len);
                        }
                    }
                }
                zis.closeEntry();
            }
        }
    }
    
    /**
     * Create a ZIP archive.
     */
    public static void createZip(Path sourceDir, Path zipPath) throws IOException {
        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(zipPath))) {
            Files.walk(sourceDir)
                    .filter(Files::isRegularFile)
                    .forEach(path -> {
                        try {
                            String entryName = sourceDir.relativize(path).toString();
                            ZipEntry entry = new ZipEntry(entryName);
                            zos.putNextEntry(entry);
                            Files.copy(path, zos);
                            zos.closeEntry();
                        } catch (IOException e) {
                            // Log and continue
                        }
                    });
        }
    }
    
    /**
     * Check if a file is hidden (starts with . on Unix).
     */
    public static boolean isHidden(Path path) {
        String name = path.getFileName().toString();
        return name.startsWith(".") && name.length() > 1;
    }
    
    /**
     * Check if file is readable.
     */
    public static boolean isReadable(Path path) {
        try {
            return Files.isReadable(path);
        } catch (IOException e) {
            return false;
        }
    }
    
    /**
     * Check if file is writable.
     */
    public static boolean isWritable(Path path) {
        try {
            return Files.isWritable(path);
        } catch (IOException e) {
            return false;
        }
    }
    
    /**
     * Read file lines as a list.
     */
    public static List<String> readLines(Path file) throws IOException {
        return Files.readAllLines(file);
    }
    
    /**
     * Write lines to a file.
     */
    public static void writeLines(Path file, List<String> lines) throws IOException {
        Files.write(file, lines);
    }
    
    /**
     * Append content to a file.
     */
    public static void append(Path file, String content) throws IOException {
        Files.writeString(file, content, StandardOpenOption.CREATE, 
                         StandardOpenOption.APPEND);
    }
    
    /**
     * Get file content as stream.
     */
    public static InputStream getInputStream(Path file) throws IOException {
        return Files.newInputStream(file);
    }
    
    /**
     * Get file content as reader.
     */
    public static BufferedReader getReader(Path file) throws IOException {
        return Files.newBufferedReader(file);
    }
    
    /**
     * Get writer for file.
     */
    public static BufferedWriter getWriter(Path file) throws IOException {
        return Files.newBufferedWriter(file, StandardOpenOption.CREATE, 
                                      StandardOpenOption.TRUNCATE_EXISTING);
    }
}
