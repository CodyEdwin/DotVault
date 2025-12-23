package com.dotvault;

import com.dotvault.controller.MainController;
import com.dotvault.controller.SettingsController;
import com.dotvault.model.Settings;
import com.dotvault.util.PathUtils;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.prefs.Preferences;

/**
 * Main application class for DotVault - Linux Dotfiles Backup Manager.
 * This application provides a GUI interface for backing up and restoring
 * Linux configuration files (dotfiles) with support for compression,
 * profiles, and version history.
 */
public class App extends Application {
    
    private static final Logger logger = LoggerFactory.getLogger(App.class);
    public static final String APP_NAME = "DotVault";
    public static final String APP_VERSION = "1.0.0";
    public static final String APP_CONFIG_DIR = ".config/dotvault";
    public static final String APP_CACHE_DIR = ".cache/dotvault";
    
    private Stage primaryStage;
    private static Settings settings;
    
    @Override
    public void start(Stage stage) throws IOException {
        this.primaryStage = stage;
        stage.setTitle(APP_NAME + " - Linux Dotfiles Backup Manager");
        stage.setMinWidth(1000);
        stage.setMinHeight(700);
        
        initializeAppDirectories();
        loadSettings();
        
        showMainWindow();
        
        // Setup close handler
        stage.setOnCloseRequest(event -> {
            saveSettings();
            logger.info("Application shutting down");
        });
        
        logger.info("DotVault application started successfully");
    }
    
    /**
     * Initialize application directories for settings, logs, and backups.
     */
    private void initializeAppDirectories() throws IOException {
        Path configDir = PathUtils.getConfigDir();
        Path cacheDir = PathUtils.getCacheDir();
        
        Files.createDirectories(configDir);
        Files.createDirectories(cacheDir);
        
        // Initialize log directory
        Path logDir = cacheDir.resolve("logs");
        Files.createDirectories(logDir);
        
        logger.info("Application directories initialized");
    }
    
    /**
     * Load application settings or create defaults if not exist.
     */
    private void loadSettings() {
        try {
            settings = Settings.load();
            if (settings == null) {
                settings = new Settings();
                settings.save();
            }
            logger.info("Settings loaded successfully");
        } catch (Exception e) {
            logger.error("Failed to load settings, using defaults", e);
            settings = new Settings();
        }
    }
    
    /**
     * Save current settings to disk.
     */
    private void saveSettings() {
        if (settings != null) {
            try {
                settings.save();
                logger.info("Settings saved successfully");
            } catch (Exception e) {
                logger.error("Failed to save settings", e);
            }
        }
    }
    
    /**
     * Display the main application window.
     */
    private void showMainWindow() throws IOException {
        FXMLLoader loader = new FXMLLoader();
        loader.setLocation(getClass().getResource("/fxml/main.fxml"));
        loader.setControllerFactory(c -> new MainController(primaryStage));
        
        Parent root = loader.load();
        
        Scene scene = new Scene(root);
        scene.getStylesheets().add(getClass().getResource("/css/styles.css").toExternalForm());
        
        primaryStage.setScene(scene);
        primaryStage.show();
    }
    
    /**
     * Get the current application settings.
     */
    public static Settings getSettings() {
        return settings;
    }
    
    /**
     * Update application settings.
     */
    public static void setSettings(Settings newSettings) {
        settings = newSettings;
    }
    
    /**
     * Get the primary stage.
     */
    public Stage getPrimaryStage() {
        return primaryStage;
    }
    
    /**
     * Application entry point.
     */
    public static void main(String[] args) {
        launch(args);
    }
}
