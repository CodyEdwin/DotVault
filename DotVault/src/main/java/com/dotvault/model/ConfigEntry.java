package com.dotvault.model;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Represents a configuration entry (file or directory) that can be backed up.
 * This class encapsulates information about a single dotfile or configuration
 * including its path, category, application name, and selection state.
 */
public class ConfigEntry {
    
    private final String path;
    private final String category;
    private final String application;
    private final String description;
    private final boolean isDirectory;
    private boolean selected;
    private boolean exists;
    private long fileSize;
    private LocalDateTime lastModified;
    
    private static final DateTimeFormatter DATE_FORMATTER = 
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    
    /**
     * Create a new configuration entry.
     *
     * @param path        The filesystem path to the configuration
     * @param category    The category this config belongs to
     * @param application The application that uses this config
     * @param description Human-readable description
     * @param isDirectory Whether this is a directory or single file
     */
    public ConfigEntry(String path, String category, String application, 
                       String description, boolean isDirectory) {
        this.path = path;
        this.category = category;
        this.application = application;
        this.description = description;
        this.isDirectory = isDirectory;
        this.selected = false;
        this.exists = false;
        this.fileSize = 0;
        this.lastModified = null;
    }
    
    /**
     * Get the full path to the configuration.
     */
    public String getPath() {
        return path;
    }
    
    /**
     * Get the category name.
     */
    public String getCategory() {
        return category;
    }
    
    /**
     * Get the application name.
     */
    public String getApplication() {
        return application;
    }
    
    /**
     * Get the description.
     */
    public String getDescription() {
        return description;
    }
    
    /**
     * Check if this entry represents a directory.
     */
    public boolean isDirectory() {
        return isDirectory;
    }
    
    /**
     * Check if this entry is currently selected for backup.
     */
    public boolean isSelected() {
        return selected;
    }
    
    /**
     * Set the selection state.
     */
    public void setSelected(boolean selected) {
        this.selected = selected;
    }
    
    /**
     * Toggle the selection state.
     */
    public void toggleSelection() {
        this.selected = !this.selected;
    }
    
    /**
     * Check if the configuration exists on the filesystem.
     */
    public boolean isExists() {
        return exists;
    }
    
    /**
     * Set the existence state.
     */
    public void setExists(boolean exists) {
        this.exists = exists;
    }
    
    /**
     * Get the file size in bytes.
     */
    public long getFileSize() {
        return fileSize;
    }
    
    /**
     * Set the file size.
     */
    public void setFileSize(long fileSize) {
        this.fileSize = fileSize;
    }
    
    /**
     * Get the last modified timestamp.
     */
    public LocalDateTime getLastModified() {
        return lastModified;
    }
    
    /**
     * Set the last modified timestamp.
     */
    public void setLastModified(LocalDateTime lastModified) {
        this.lastModified = lastModified;
    }
    
    /**
     * Get a formatted file size string.
     */
    public String getFormattedSize() {
        if (fileSize < 1024) {
            return fileSize + " B";
        } else if (fileSize < 1024 * 1024) {
            return String.format("%.1f KB", fileSize / 1024.0);
        } else if (fileSize < 1024 * 1024 * 1024) {
            return String.format("%.1f MB", fileSize / (1024.0 * 1024));
        } else {
            return String.format("%.1f GB", fileSize / (1024.0 * 1024 * 1024));
        }
    }
    
    /**
     * Get a formatted last modified date string.
     */
    public String getFormattedDate() {
        if (lastModified == null) {
            return "N/A";
        }
        return lastModified.format(DATE_FORMATTER);
    }
    
    /**
     * Get the display name for UI representation.
     */
    public String getDisplayName() {
        String name = path.substring(path.lastIndexOf('/') + 1);
        if (name.isEmpty()) {
            name = path;
        }
        return name;
    }
    
    /**
     * Get the parent directory path.
     */
    public String getParentPath() {
        int lastSlash = path.lastIndexOf('/');
        if (lastSlash > 0) {
            return path.substring(0, lastSlash);
        }
        return path;
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ConfigEntry that = (ConfigEntry) o;
        return Objects.equals(path, that.path);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(path);
    }
    
    @Override
    public String toString() {
        return String.format("%s (%s) - %s", path, application, 
                           exists ? (selected ? "Selected" : "Available") : "Not found");
    }
    
    /**
     * Builder class for creating ConfigEntry instances.
     */
    public static class Builder {
        private String path;
        private String category;
        private String application;
        private String description;
        private boolean isDirectory;
        
        public Builder path(String path) {
            this.path = path;
            return this;
        }
        
        public Builder category(String category) {
            this.category = category;
            return this;
        }
        
        public Builder application(String application) {
            this.application = application;
            return this;
        }
        
        public Builder description(String description) {
            this.description = description;
            return this;
        }
        
        public Builder isDirectory(boolean isDirectory) {
            this.isDirectory = isDirectory;
            return this;
        }
        
        public ConfigEntry build() {
            return new ConfigEntry(path, category, application, description, isDirectory);
        }
    }
    
    /**
     * Create a new builder instance.
     */
    public static Builder builder() {
        return new Builder();
    }
}
