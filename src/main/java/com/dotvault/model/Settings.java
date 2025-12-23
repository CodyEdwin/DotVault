package com.dotvault.model;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.dotvault.util.PathUtils;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.prefs.Preferences;

/**
 * Application settings that persist between sessions.
 * Stores user preferences including backup directories, UI settings,
 * default options, and recent history.
 */
public class Settings {
    
    private static final String SETTINGS_FILENAME = "settings.json";
    private static final DateTimeFormatter DATE_FORMATTER = 
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    
    // Backup settings
    private String defaultBackupDirectory;
    private boolean compressBackups;
    private String compressionFormat;
    private boolean preservePermissions;
    private boolean followSymlinks;
    private boolean skipHiddenFiles;
    private List<String> excludePatterns;
    private boolean createTimestampFolder;
    
    // UI settings
    private boolean darkMode;
    private double windowWidth;
    private double windowHeight;
    private boolean windowMaximized;
    private int selectedTab;
    private String theme;
    
    // History and recent
    private List<String> recentBackupDirs;
    private List<String> recentRestoreDirs;
    private int maxRecentDirs;
    private LocalDateTime lastBackupTime;
    private String lastBackupPath;
    
    // Default constructor with sensible defaults
    public Settings() {
        // Default backup settings
        this.defaultBackupDirectory = System.getProperty("user.home") + "/Backups";
        this.compressBackups = false;
        this.compressionFormat = "zip";
        this.preservePermissions = true;
        this.followSymlinks = false;
        this.skipHiddenFiles = false;
        this.createTimestampFolder = true;
        this.excludePatterns = new ArrayList<>();
        this.excludePatterns.add("*.log");
        this.excludePatterns.add("cache");
        this.excludePatterns.add(".cache");
        
        // Default UI settings
        this.darkMode = true;
        this.windowWidth = 1200;
        this.windowHeight = 800;
        this.windowMaximized = false;
        this.selectedTab = 0;
        this.theme = "dark";
        
        // History settings
        this.recentBackupDirs = new ArrayList<>();
        this.recentRestoreDirs = new ArrayList<>();
        this.maxRecentDirs = 10;
        this.lastBackupTime = null;
        this.lastBackupPath = "";
    }
    
    // Getters and Setters
    
    public String getDefaultBackupDirectory() {
        return defaultBackupDirectory;
    }
    
    public void setDefaultBackupDirectory(String defaultBackupDirectory) {
        this.defaultBackupDirectory = defaultBackupDirectory;
    }
    
    public boolean isCompressBackups() {
        return compressBackups;
    }
    
    public void setCompressBackups(boolean compressBackups) {
        this.compressBackups = compressBackups;
    }
    
    public String getCompressionFormat() {
        return compressionFormat;
    }
    
    public void setCompressionFormat(String compressionFormat) {
        this.compressionFormat = compressionFormat;
    }
    
    public boolean isPreservePermissions() {
        return preservePermissions;
    }
    
    public void setPreservePermissions(boolean preservePermissions) {
        this.preservePermissions = preservePermissions;
    }
    
    public boolean isFollowSymlinks() {
        return followSymlinks;
    }
    
    public void setFollowSymlinks(boolean followSymlinks) {
        this.followSymlinks = followSymlinks;
    }
    
    public boolean isSkipHiddenFiles() {
        return skipHiddenFiles;
    }
    
    public void setSkipHiddenFiles(boolean skipHiddenFiles) {
        this.skipHiddenFiles = skipHiddenFiles;
    }
    
    public List<String> getExcludePatterns() {
        return excludePatterns;
    }
    
    public void setExcludePatterns(List<String> excludePatterns) {
        this.excludePatterns = excludePatterns != null ? excludePatterns : new ArrayList<>();
    }
    
    public boolean isCreateTimestampFolder() {
        return createTimestampFolder;
    }
    
    public void setCreateTimestampFolder(boolean createTimestampFolder) {
        this.createTimestampFolder = createTimestampFolder;
    }
    
    public boolean isDarkMode() {
        return darkMode;
    }
    
    public void setDarkMode(boolean darkMode) {
        this.darkMode = darkMode;
    }
    
    public double getWindowWidth() {
        return windowWidth;
    }
    
    public void setWindowWidth(double windowWidth) {
        this.windowWidth = windowWidth;
    }
    
    public double getWindowHeight() {
        return windowHeight;
    }
    
    public void setWindowHeight(double windowHeight) {
        this.windowHeight = windowHeight;
    }
    
    public boolean isWindowMaximized() {
        return windowMaximized;
    }
    
    public void setWindowMaximized(boolean windowMaximized) {
        this.windowMaximized = windowMaximized;
    }
    
    public int getSelectedTab() {
        return selectedTab;
    }
    
    public void setSelectedTab(int selectedTab) {
        this.selectedTab = selectedTab;
    }
    
    public String getTheme() {
        return theme;
    }
    
    public void setTheme(String theme) {
        this.theme = theme;
    }
    
    public List<String> getRecentBackupDirs() {
        return recentBackupDirs;
    }
    
    public void setRecentBackupDirs(List<String> recentBackupDirs) {
        this.recentBackupDirs = recentBackupDirs != null ? recentBackupDirs : new ArrayList<>();
    }
    
    public void addRecentBackupDir(String path) {
        if (path == null || path.isEmpty()) return;
        
        recentBackupDirs.remove(path);
        recentBackupDirs.add(0, path);
        
        while (recentBackupDirs.size() > maxRecentDirs) {
            recentBackupDirs.remove(recentBackupDirs.size() - 1);
        }
    }
    
    public List<String> getRecentRestoreDirs() {
        return recentRestoreDirs;
    }
    
    public void setRecentRestoreDirs(List<String> recentRestoreDirs) {
        this.recentRestoreDirs = recentRestoreDirs != null ? recentRestoreDirs : new ArrayList<>();
    }
    
    public void addRecentRestoreDir(String path) {
        if (path == null || path.isEmpty()) return;
        
        recentRestoreDirs.remove(path);
        recentRestoreDirs.add(0, path);
        
        while (recentRestoreDirs.size() > maxRecentDirs) {
            recentRestoreDirs.remove(recentRestoreDirs.size() - 1);
        }
    }
    
    public LocalDateTime getLastBackupTime() {
        return lastBackupTime;
    }
    
    public void setLastBackupTime(LocalDateTime lastBackupTime) {
        this.lastBackupTime = lastBackupTime;
    }
    
    public void markBackupCompleted(String path) {
        this.lastBackupTime = LocalDateTime.now();
        this.lastBackupPath = path;
        addRecentBackupDir(path);
    }
    
    public String getLastBackupPath() {
        return lastBackupPath;
    }
    
    public void setLastBackupPath(String lastBackupPath) {
        this.lastBackupPath = lastBackupPath;
    }
    
    /**
     * Get the settings file path.
     */
    private static Path getSettingsFilePath() {
        return PathUtils.getConfigDir().resolve(SETTINGS_FILENAME);
    }
    
    /**
     * Load settings from disk.
     */
    public static Settings load() throws IOException {
        Path settingsPath = getSettingsFilePath();
        
        if (!Files.exists(settingsPath)) {
            return null;
        }
        
        String json = Files.readString(settingsPath.toUri());
        
        // Create custom Gson with LocalDateTime serialization
        Gson gson = new GsonBuilder()
                .registerTypeAdapter(LocalDateTime.class, new LocalDateTimeAdapter())
                .create();
        
        return gson.fromJson(json, Settings.class);
    }
    
    /**
     * Save settings to disk.
     */
    public void save() throws IOException {
        Path settingsPath = getSettingsFilePath();
        
        // Create custom Gson with LocalDateTime serialization
        Gson gson = new GsonBuilder()
                .registerTypeAdapter(LocalDateTime.class, new LocalDateTimeAdapter())
                .setPrettyPrinting()
                .create();
        
        String json = gson.toJson(this);
        Files.writeString(settingsPath, json);
    }
    
    /**
     * Custom Gson adapter for LocalDateTime serialization.
     */
    private static class LocalDateTimeAdapter implements com.google.gson.JsonSerializer<LocalDateTime>,
                                                      com.google.gson.JsonDeserializer<LocalDateTime> {
        
        @Override
        public com.google.gson.JsonElement serialize(LocalDateTime src, java.lang.reflect.Type typeOfSrc,
                                                     com.google.gson.JsonSerializationContext context) {
            return new com.google.gson.JsonPrimitive(src.format(DATE_FORMATTER));
        }
        
        @Override
        public LocalDateTime deserialize(com.google.gson.JsonElement json, java.lang.reflect.Type typeOfT,
                                         com.google.gson.JsonDeserializationContext context)
                throws com.google.gson.JsonParseException {
            if (json == null || json.isJsonNull()) {
                return null;
            }
            return LocalDateTime.parse(json.getAsString(), DATE_FORMATTER);
        }
    }
    
    /**
     * Reset settings to defaults.
     */
    public void resetToDefaults() {
        this.defaultBackupDirectory = System.getProperty("user.home") + "/Backups";
        this.compressBackups = false;
        this.compressionFormat = "zip";
        this.preservePermissions = true;
        this.followSymlinks = false;
        this.skipHiddenFiles = false;
        this.createTimestampFolder = true;
        this.excludePatterns = new ArrayList<>();
        this.excludePatterns.add("*.log");
        this.excludePatterns.add("cache");
        this.excludePatterns.add(".cache");
        this.darkMode = true;
        this.theme = "dark";
    }
    
    /**
     * Get a copy of current settings.
     */
    public Settings copy() {
        Settings copy = new Settings();
        copy.defaultBackupDirectory = this.defaultBackupDirectory;
        copy.compressBackups = this.compressBackups;
        copy.compressionFormat = this.compressionFormat;
        copy.preservePermissions = this.preservePermissions;
        copy.followSymlinks = this.followSymlinks;
        copy.skipHiddenFiles = this.skipHiddenFiles;
        copy.createTimestampFolder = this.createTimestampFolder;
        copy.excludePatterns = new ArrayList<>(this.excludePatterns);
        copy.darkMode = this.darkMode;
        copy.windowWidth = this.windowWidth;
        copy.windowHeight = this.windowHeight;
        copy.windowMaximized = this.windowMaximized;
        copy.selectedTab = this.selectedTab;
        copy.theme = this.theme;
        copy.recentBackupDirs = new ArrayList<>(this.recentBackupDirs);
        copy.recentRestoreDirs = new ArrayList<>(this.recentRestoreDirs);
        copy.maxRecentDirs = this.maxRecentDirs;
        copy.lastBackupTime = this.lastBackupTime;
        copy.lastBackupPath = this.lastBackupPath;
        return copy;
    }
}
