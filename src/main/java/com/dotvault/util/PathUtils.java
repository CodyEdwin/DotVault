package com.dotvault.util;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.*;
import java.util.List;

/**
 * Utility class for path manipulation and filesystem operations.
 * Provides helper methods for handling Linux home directory paths,
 * configuration directories, and other common path operations.
 */
public final class PathUtils {
    
    private static final String LINUX_HOME_VAR = "~";
    private static final String LINUX_CONFIG_DIR = ".config";
    private static final String LINUX_CACHE_DIR = ".cache";
    
    private PathUtils() {
        // Utility class - prevent instantiation
    }
    
    /**
     * Get the user's home directory.
     */
    public static Path getHomeDir() {
        String home = System.getProperty("user.home");
        return Paths.get(home);
    }
    
    /**
     * Get the application configuration directory.
     * Creates the directory if it doesn't exist.
     */
    public static Path getConfigDir() {
        String appConfigDir = System.getProperty("user.home") + "/.config/dotvault";
        Path configPath = Paths.get(appConfigDir);
        return configPath;
    }
    
    /**
     * Get the application cache directory.
     * Creates the directory if it doesn't exist.
     */
    public static Path getCacheDir() {
        String appCacheDir = System.getProperty("user.home") + "/.cache/dotvault";
        Path cachePath = Paths.get(appCacheDir);
        return cachePath;
    }
    
    /**
     * Get the application log directory.
     * Creates the directory if it doesn't exist.
     */
    public static Path getLogDir() {
        Path logDir = getCacheDir().resolve("logs");
        return logDir;
    }
    
    /**
     * Expand ~ to the user's home directory.
     */
    public static String expandHome(String path) {
        if (path == null) {
            return null;
        }
        if (path.startsWith(LINUX_HOME_VAR)) {
            String home = System.getProperty("user.home");
            return home + path.substring(1);
        }
        return path;
    }
    
    /**
     * Contract path to use ~ for user's home directory.
     */
    public static String contractHome(String path) {
        if (path == null) {
            return null;
        }
        String home = System.getProperty("user.home");
        if (path.startsWith(home)) {
            return LINUX_HOME_VAR + path.substring(home.length());
        }
        return path;
    }
    
    /**
     * Check if a path is inside the user's home directory.
     */
    public static boolean isInsideHome(String path) {
        Path home = getHomeDir();
        Path targetPath = Paths.get(expandHome(path));
        return targetPath.startsWith(home) || targetPath.normalize().startsWith(home.normalize());
    }
    
    /**
     * Get the relative path from home directory.
     */
    public static String getRelativeFromHome(String absolutePath) {
        Path home = getHomeDir();
        Path target = Paths.get(absolutePath);
        
        if (target.startsWith(home)) {
            return target.subpath(home.getNameCount(), target.getNameCount()).toString();
        }
        return absolutePath;
    }
    
    /**
     * Check if a path exists on the filesystem.
     */
    public static boolean exists(String path) {
        if (path == null || path.isEmpty()) {
            return false;
        }
        return Files.exists(Paths.get(expandHome(path)));
    }
    
    /**
     * Check if a path is a directory.
     */
    public static boolean isDirectory(String path) {
        if (path == null || path.isEmpty()) {
            return false;
        }
        Path expanded = Paths.get(expandHome(path));
        return Files.isDirectory(expanded);
    }
    
    /**
     * Check if a path is a regular file.
     */
    public static boolean isFile(String path) {
        if (path == null || path.isEmpty()) {
            return false;
        }
        Path expanded = Paths.get(expandHome(path));
        return Files.isRegularFile(expanded);
    }
    
    /**
     * Get the file size in bytes.
     */
    public static long getFileSize(String path) {
        if (path == null || path.isEmpty()) {
            return 0;
        }
        try {
            Path expanded = Paths.get(expandHome(path));
            if (Files.isDirectory(expanded)) {
                return calculateDirectorySize(expanded);
            }
            return Files.size(expanded);
        } catch (IOException e) {
            return 0;
        }
    }
    
    /**
     * Calculate the total size of a directory recursively.
     */
    public static long calculateDirectorySize(Path dir) {
        try {
            return Files.walk(dir)
                    .filter(Files::isRegularFile)
                    .mapToLong(path -> {
                        try {
                            return Files.size(path);
                        } catch (IOException e) {
                            return 0;
                        }
                    })
                    .sum();
        } catch (IOException e) {
            return 0;
        }
    }
    
    /**
     * Get the last modified time of a path.
     */
    public static java.time.LocalDateTime getLastModified(String path) {
        if (path == null || path.isEmpty()) {
            return null;
        }
        try {
            Path expanded = Paths.get(expandHome(path));
            return Files.getLastModifiedTime(expanded)
                    .toInstant()
                    .atZone(java.time.ZoneId.systemDefault())
                    .toLocalDateTime();
        } catch (IOException e) {
            return null;
        }
    }
    
    /**
     * Get the file or directory name from a path.
     */
    public static String getFileName(String path) {
        if (path == null || path.isEmpty()) {
            return path;
        }
        String expanded = expandHome(path);
        String name = Paths.get(expanded).getFileName().toString();
        if (path.startsWith(LINUX_HOME_VAR)) {
            return LINUX_HOME_VAR + "/" + name;
        }
        return name;
    }
    
    /**
     * Get the parent directory of a path.
     */
    public static String getParent(String path) {
        if (path == null || path.isEmpty()) {
            return null;
        }
        String expanded = expandHome(path);
        Path parent = Paths.get(expanded).getParent();
        if (parent == null) {
            return null;
        }
        String parentPath = parent.toString();
        if (parentPath.startsWith(System.getProperty("user.home"))) {
            return LINUX_HOME_VAR + parentPath.substring(System.getProperty("user.home").length());
        }
        return parentPath;
    }
    
    /**
     * Check if a path contains another path (is a parent).
     */
    public static boolean containsPath(String parentPath, String childPath) {
        if (parentPath == null || childPath == null) {
            return false;
        }
        Path parent = Paths.get(expandHome(parentPath));
        Path child = Paths.get(expandHome(childPath));
        Path normalizedParent = parent.normalize();
        Path normalizedChild = child.normalize();
        return normalizedChild.startsWith(normalizedParent) ||
               normalizedChild.startsWith(normalizedParent) ||
               (normalizedChild.toString() + "/").startsWith(normalizedParent.toString() + "/");
    }
    
    /**
     * Get common parent directory of multiple paths.
     */
    public static String getCommonParent(List<String> paths) {
        if (paths == null || paths.isEmpty()) {
            return null;
        }
        
        Path common = Paths.get(expandHome(paths.get(0)));
        for (String path : paths) {
            Path current = Paths.get(expandHome(path));
            while (!current.startsWith(common) && common.getNameCount() > 0) {
                common = common.getParent();
            }
        }
        return contractHome(common.toString());
    }
    
    /**
     * Validate that a path is safe (prevent path traversal attacks).
     */
    public static boolean isValidPath(String path) {
        if (path == null || path.isEmpty()) {
            return false;
        }
        
        String expanded = expandHome(path);
        
        // Check for path traversal attempts
        if (expanded.contains("..") || expanded.contains("//")) {
            return false;
        }
        
        // Ensure path is absolute and normalized
        Path normalized = Paths.get(expanded).normalize();
        return normalized.isAbsolute();
    }
    
    /**
     * Create a timestamped folder name for backups.
     */
    public static String createTimestampedFolderName(String prefix) {
        java.time.LocalDateTime now = java.time.LocalDateTime.now();
        java.time.format.DateTimeFormatter formatter = 
                java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");
        return prefix + "_" + now.format(formatter);
    }
    
    /**
     * Parse a glob pattern to regex.
     */
    public static String globToRegex(String glob) {
        if (glob == null || glob.isEmpty()) {
            return ".*";
        }
        
        StringBuilder regex = new StringBuilder();
        regex.append('^');
        
        for (int i = 0; i < glob.length(); i++) {
            char c = glob.charAt(i);
            switch (c) {
                case '*':
                    regex.append(".*");
                    break;
                case '?':
                    regex.append('.');
                    break;
                case '.':
                case '{':
                case '}':
                case '(':
                case ')':
                case '+':
                case '^':
                case '$':
                case '|':
                case '\\':
                    regex.append('\\').append(c);
                    break;
                default:
                    regex.append(c);
                    break;
            }
        }
        
        regex.append('$');
        return regex.toString();
    }
    
    /**
     * Check if a path matches a glob pattern.
     */
    public static boolean matchesGlob(String path, String pattern) {
        if (path == null || pattern == null) {
            return false;
        }
        
        String regex = globToRegex(pattern);
        String fileName = Paths.get(path).getFileName().toString();
        return fileName.matches(regex);
    }
    
    /**
     * Get a path from resources.
     */
    public static Path getResourcePath(String resource) {
        try {
            URI resourceURI = PathUtils.class.getResource(resource).toURI();
            return Paths.get(resourceURI);
        } catch (URISyntaxException | NullPointerException e) {
            return null;
        }
    }
    
    /**
     * Read file content as string.
     */
    public static String readFile(String path) throws IOException {
        Path expanded = Paths.get(expandHome(path));
        return Files.readString(expanded);
    }
    
    /**
     * Write content to a file.
     */
    public static void writeFile(String path, String content) throws IOException {
        Path expanded = Paths.get(expandHome(path));
        Files.createDirectories(expanded.getParent());
        Files.writeString(expanded, content);
    }
    
    /**
     * Safely join path components.
     */
    public static Path join(String base, String... components) {
        Path basePath = Paths.get(expandHome(base));
        for (String component : components) {
            basePath = basePath.resolve(expandHome(component));
        }
        return basePath;
    }
}
