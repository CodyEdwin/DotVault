package com.dotvault.model;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Represents a backup profile that saves a collection of configuration entries
 * under a specific name. Profiles allow users to quickly select and backup
 * common configurations for different scenarios (e.g., "Minimal Server",
 * "Full Desktop", "Development Environment").
 */
public class BackupProfile {
    
    private String name;
    private String description;
    private LocalDateTime createdAt;
    private LocalDateTime lastUsed;
    private List<String> selectedPaths;
    private List<String> excludedPaths;
    private List<String> categories;
    
    /**
     * Create a new empty backup profile.
     */
    public BackupProfile() {
        this.selectedPaths = new ArrayList<>();
        this.excludedPaths = new ArrayList<>();
        this.categories = new ArrayList<>();
        this.createdAt = LocalDateTime.now();
        this.lastUsed = null;
    }
    
    /**
     * Create a named backup profile.
     *
     * @param name        The profile name
     * @param description Profile description
     */
    public BackupProfile(String name, String description) {
        this();
        this.name = name;
        this.description = description;
    }
    
    /**
     * Get the profile name.
     */
    public String getName() {
        return name;
    }
    
    /**
     * Set the profile name.
     */
    public void setName(String name) {
        this.name = name;
    }
    
    /**
     * Get the profile description.
     */
    public String getDescription() {
        return description;
    }
    
    /**
     * Set the profile description.
     */
    public void setDescription(String description) {
        this.description = description;
    }
    
    /**
     * Get the creation timestamp.
     */
    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
    
    /**
     * Set the creation timestamp.
     */
    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
    
    /**
     * Get the last used timestamp.
     */
    public LocalDateTime getLastUsed() {
        return lastUsed;
    }
    
    /**
     * Set the last used timestamp.
     */
    public void setLastUsed(LocalDateTime lastUsed) {
        this.lastUsed = lastUsed;
    }
    
    /**
     * Mark this profile as used now.
     */
    public void markAsUsed() {
        this.lastUsed = LocalDateTime.now();
    }
    
    /**
     * Get the list of selected paths.
     */
    public List<String> getSelectedPaths() {
        return selectedPaths;
    }
    
    /**
     * Set the list of selected paths.
     */
    public void setSelectedPaths(List<String> selectedPaths) {
        this.selectedPaths = selectedPaths != null ? selectedPaths : new ArrayList<>();
    }
    
    /**
     * Add a path to the selection.
     */
    public void addSelectedPath(String path) {
        if (!selectedPaths.contains(path)) {
            selectedPaths.add(path);
        }
    }
    
    /**
     * Remove a path from the selection.
     */
    public void removeSelectedPath(String path) {
        selectedPaths.remove(path);
    }
    
    /**
     * Check if a path is in the selection.
     */
    public boolean isPathSelected(String path) {
        return selectedPaths.contains(path);
    }
    
    /**
     * Get the list of excluded paths.
     */
    public List<String> getExcludedPaths() {
        return excludedPaths;
    }
    
    /**
     * Set the list of excluded paths.
     */
    public void setExcludedPaths(List<String> excludedPaths) {
        this.excludedPaths = excludedPaths != null ? excludedPaths : new ArrayList<>();
    }
    
    /**
     * Add a path to the exclusion list.
     */
    public void addExcludedPath(String path) {
        if (!excludedPaths.contains(path)) {
            excludedPaths.add(path);
        }
    }
    
    /**
     * Get the list of included categories.
     */
    public List<String> getCategories() {
        return categories;
    }
    
    /**
     * Set the list of included categories.
     */
    public void setCategories(List<String> categories) {
        this.categories = categories != null ? categories : new ArrayList<>();
    }
    
    /**
     * Add a category to the profile.
     */
    public void addCategory(String category) {
        if (!categories.contains(category)) {
            categories.add(category);
        }
    }
    
    /**
     * Get the number of selected paths.
     */
    public int getPathCount() {
        return selectedPaths.size();
    }
    
    /**
     * Get a formatted creation date string.
     */
    public String getFormattedCreatedDate() {
        if (createdAt == null) {
            return "Unknown";
        }
        return createdAt.toString().replace('T', ' ');
    }
    
    /**
     * Get a formatted last used date string.
     */
    public String getFormattedLastUsed() {
        if (lastUsed == null) {
            return "Never";
        }
        return lastUsed.toString().replace('T', ' ');
    }
    
    @Override
    public String toString() {
        return String.format("%s (%d items)", name, selectedPaths.size());
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        BackupProfile that = (BackupProfile) o;
        return name != null && name.equals(that.name);
    }
    
    @Override
    public int hashCode() {
        return name != null ? name.hashCode() : 0;
    }
}
