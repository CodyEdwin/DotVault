package com.dotvault.service;

import com.dotvault.model.ConfigEntry;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * Service responsible for scanning the filesystem and discovering
 * known configuration files and directories. Uses a JSON database
 * of known Linux applications and their config locations.
 */
public class ConfigScanner {
    
    private static final Logger logger = LoggerFactory.getLogger(ConfigScanner.class);
    private static final String KNOWN_CONFIGS_RESOURCE = "/known_configs.json";
    
    private Map<String, List<ConfigEntry>> entriesByCategory;
    private List<ConfigEntry> allEntries;
    private Map<String, ConfigEntry> entriesByPath;
    private JsonObject knownConfigsDatabase;
    
    /**
     * Create a new configuration scanner.
     */
    public ConfigScanner() {
        this.entriesByCategory = new LinkedHashMap<>();
        this.allEntries = new ArrayList<>();
        this.entriesByPath = new HashMap<>();
    }
    
    /**
     * Load the known configurations database from resources.
     */
    private void loadKnownConfigs() {
        try (InputStream is = getClass().getResourceAsStream(KNOWN_CONFIGS_RESOURCE)) {
            if (is == null) {
                logger.error("Known configs resource not found: {}", KNOWN_CONFIGS_RESOURCE);
                return;
            }
            
            String json = new InputStreamReader(is).lines().collect(Collectors.joining("\n"));
            knownConfigsDatabase = JsonParser.parseString(json).getAsJsonObject();
            
            logger.info("Loaded known configurations database");
        } catch (Exception e) {
            logger.error("Failed to load known configs database", e);
        }
    }
    
    /**
     * Scan for existing configuration files and directories.
     * This method checks which known configurations actually exist on the system.
     *
     * @return List of found configuration entries
     */
    public List<ConfigEntry> scan() {
        loadKnownConfigs();
        
        if (knownConfigsDatabase == null) {
            logger.warn("No configurations database available");
            return Collections.emptyList();
        }
        
        entriesByCategory.clear();
        allEntries.clear();
        entriesByPath.clear();
        
        String homeDir = System.getProperty("user.home");
        
        for (String category : knownConfigsDatabase.keySet()) {
            JsonObject categoryObj = knownConfigsDatabase.getAsJsonObject(category);
            entriesByCategory.put(category, new ArrayList<>());
            
            for (String appName : categoryObj.keySet()) {
                JsonObject appObj = categoryObj.getAsJsonObject(appName);
                JsonArray configs = appObj.getAsJsonArray("configs");
                String description = appObj.has("description") ? 
                        appObj.get("description").getAsString() : appName;
                
                for (int i = 0; i < configs.size(); i++) {
                    JsonObject configObj = configs.get(i).getAsJsonObject();
                    String path = configObj.get("path").getAsString();
                    boolean isDir = configObj.has("directory") && configObj.get("directory").getAsBoolean();
                    String configDesc = configObj.has("description") ? 
                            configObj.get("description").getAsString() : description;
                    
                    // Expand ~ to home directory
                    String expandedPath = path.replace("~", homeDir);
                    Path fullPath = Paths.get(expandedPath);
                    
                    // Check if the path exists
                    boolean exists = Files.exists(fullPath);
                    
                    ConfigEntry entry = new ConfigEntry(
                            path, category, appName, configDesc, isDir
                    );
                    entry.setExists(exists);
                    
                    if (exists) {
                        try {
                            long size = Files.isDirectory(fullPath) ? 
                                    calculateDirectorySize(fullPath) : Files.size(fullPath);
                            entry.setFileSize(size);
                            entry.setLastModified(
                                    Files.getLastModifiedTime(fullPath)
                                        .toInstant()
                                        .atZone(java.time.ZoneId.systemDefault())
                                        .toLocalDateTime()
                            );
                        } catch (IOException e) {
                            logger.debug("Could not get file info for: {}", expandedPath);
                        }
                    }
                    
                    entriesByCategory.get(category).add(entry);
                    allEntries.add(entry);
                    entriesByPath.put(path, entry);
                }
            }
        }
        
        logger.info("Scan complete. Found {} configuration entries across {} categories",
                allEntries.size(), entriesByCategory.size());
        
        return allEntries;
    }
    
    /**
     * Calculate the total size of a directory recursively.
     */
    private long calculateDirectorySize(Path dir) {
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
            logger.debug("Could not calculate directory size: {}", dir);
            return 0;
        }
    }
    
    /**
     * Get all scanned entries.
     */
    public List<ConfigEntry> getAllEntries() {
        return new ArrayList<>(allEntries);
    }
    
    /**
     * Get entries grouped by category.
     */
    public Map<String, List<ConfigEntry>> getEntriesByCategory() {
        return new LinkedHashMap<>(entriesByCategory);
    }
    
    /**
     * Get entries for a specific category.
     */
    public List<ConfigEntry> getEntriesForCategory(String category) {
        return entriesByCategory.getOrDefault(category, Collections.emptyList());
    }
    
    /**
     * Get entry by its original path.
     */
    public ConfigEntry getEntryByPath(String path) {
        return entriesByPath.get(path);
    }
    
    /**
     * Get all existing entries.
     */
    public List<ConfigEntry> getExistingEntries() {
        return allEntries.stream()
                .filter(ConfigEntry::isExists)
                .collect(Collectors.toList());
    }
    
    /**
     * Get all selected entries.
     */
    public List<ConfigEntry> getSelectedEntries() {
        return allEntries.stream()
                .filter(ConfigEntry::isSelected)
                .collect(Collectors.toList());
    }
    
    /**
     * Select all existing entries.
     */
    public void selectAll() {
        allEntries.forEach(entry -> {
            if (entry.isExists()) {
                entry.setSelected(true);
            }
        });
    }
    
    /**
     * Deselect all entries.
     */
    public void deselectAll() {
        allEntries.forEach(entry -> entry.setSelected(false));
    }
    
    /**
     * Select entries by category.
     */
    public void selectByCategory(String category) {
        List<ConfigEntry> categoryEntries = entriesByCategory.get(category);
        if (categoryEntries != null) {
            categoryEntries.stream()
                    .filter(ConfigEntry::isExists)
                    .forEach(entry -> entry.setSelected(true));
        }
    }
    
    /**
     * Deselect entries by category.
     */
    public void deselectByCategory(String category) {
        List<ConfigEntry> categoryEntries = entriesByCategory.get(category);
        if (categoryEntries != null) {
            categoryEntries.forEach(entry -> entry.setSelected(false));
        }
    }
    
    /**
     * Select entries by application.
     */
    public void selectByApplication(String application) {
        allEntries.stream()
                .filter(e -> e.getApplication().equals(application))
                .filter(ConfigEntry::isExists)
                .forEach(ConfigEntry::setSelected);
    }
    
    /**
     * Get all unique categories.
     */
    public List<String> getCategories() {
        return new ArrayList<>(entriesByCategory.keySet());
    }
    
    /**
     * Get all unique applications.
     */
    public List<String> getApplications() {
        return allEntries.stream()
                .map(ConfigEntry::getApplication)
                .distinct()
                .sorted()
                .collect(Collectors.toList());
    }
    
    /**
     * Get the total size of selected entries.
     */
    public long getSelectedTotalSize() {
        return allEntries.stream()
                .filter(ConfigEntry::isSelected)
                .mapToLong(ConfigEntry::getFileSize)
                .sum();
    }
    
    /**
     * Get the count of selected entries.
     */
    public int getSelectedCount() {
        return (int) allEntries.stream()
                .filter(ConfigEntry::isSelected)
                .count();
    }
    
    /**
     * Get the count of existing entries.
     */
    public int getExistingCount() {
        return (int) allEntries.stream()
                .filter(ConfigEntry::isExists)
                .count();
    }
    
    /**
     * Refresh the scan and check for changes.
     */
    public void refresh() {
        scan();
    }
}
