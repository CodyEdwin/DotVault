package com.dotvault.controller;

import com.dotvault.App;
import com.dotvault.model.BackupProfile;
import com.dotvault.model.ConfigEntry;
import com.dotvault.model.Settings;
import com.dotvault.service.ConfigScanner;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.lang.reflect.Type;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.ResourceBundle;
import java.util.stream.Collectors;

/**
 * Controller for managing backup profiles.
 * Allows saving, loading, and deleting named profiles of configuration selections.
 */
public class ProfileController implements Initializable {
    
    private static final Logger logger = LoggerFactory.getLogger(ProfileController.class);
    private static final String PROFILES_FILENAME = "profiles.json";
    private static final DateTimeFormatter DATE_FORMATTER = 
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    
    @FXML private ListView<BackupProfile> profileListView;
    @FXML private TextField profileNameField;
    @FXML private TextArea profileDescriptionField;
    @FXML private Button saveProfileButton;
    @FXML private Button loadProfileButton;
    @FXML private Button deleteProfileButton;
    @FXML private Button selectAllProfileButton;
    @FXML private Button deselectAllProfileButton;
    @FXML private VBox profileDetailsPanel;
    @FXML private Label createdAtLabel;
    @FXML private Label lastUsedLabel;
    @FXML private Label itemCountLabel;
    
    private Settings settings;
    private ConfigScanner configScanner;
    private ObservableList<BackupProfile> profiles;
    private Path profilesFilePath;
    
    @Override
    public void initialize(URL location, ResourceBundle resources) {
        this.settings = App.getSettings();
        this.configScanner = new ConfigScanner();
        this.profiles = FXCollections.observableArrayList();
        
        setupUI();
        loadProfiles();
    }
    
    /**
     * Setup UI components.
     */
    private void setupUI() {
        // Setup profile list
        profileListView.setItems(profiles);
        profileListView.setCellFactory(listView -> new ProfileListCell());
        
        // Setup selection listener
        profileListView.getSelectionModel().selectedItemProperty().addListener(
                (obs, oldVal, newVal) -> displayProfileDetails(newVal));
        
        // Setup buttons
        saveProfileButton.setOnAction(this::onSaveProfile);
        loadProfileButton.setOnAction(this::onLoadProfile);
        deleteProfileButton.setOnAction(this::onDeleteProfile);
        selectAllProfileButton.setOnAction(this::onSelectAllProfile);
        deselectAllProfileButton.setOnAction(this::onDeselectAllProfile);
        
        // Initialize profiles file path
        profilesFilePath = Paths.get(System.getProperty("user.home"))
                .resolve(".config/dotvault/" + PROFILES_FILENAME);
    }
    
    /**
     * Load profiles from disk.
     */
    private void loadProfiles() {
        try {
            if (Files.exists(profilesFilePath)) {
                String json = Files.readString(profilesFilePath);
                Gson gson = new Gson();
                Type listType = new TypeToken<List<BackupProfile>>(){}.getType();
                List<BackupProfile> loadedProfiles = gson.fromJson(json, listType);
                
                if (loadedProfiles != null) {
                    profiles.setAll(loadedProfiles);
                }
            }
        } catch (IOException e) {
            logger.error("Failed to load profiles", e);
        }
    }
    
    /**
     * Save profiles to disk.
     */
    private void saveProfiles() {
        try {
            Gson gson = new GsonBuilder().setPrettyPrinting().create();
            String json = gson.toJson(profiles);
            Files.writeString(profilesFilePath, json);
        } catch (IOException e) {
            logger.error("Failed to save profiles", e);
        }
    }
    
    /**
     * Display profile details in the details panel.
     */
    private void displayProfileDetails(BackupProfile profile) {
        if (profile == null) {
            profileDetailsPanel.setVisible(false);
            return;
        }
        
        profileDetailsPanel.setVisible(true);
        profileNameField.setText(profile.getName());
        profileDescriptionField.setText(profile.getDescription());
        createdAtLabel.setText(profile.getFormattedCreatedDate());
        lastUsedLabel.setText(profile.getFormattedLastUsed());
        itemCountLabel.setText(String.valueOf(profile.getPathCount()));
    }
    
    /**
     * Save the current selection as a new profile.
     */
    @FXML
    private void onSaveProfile(ActionEvent event) {
        String name = profileNameField.getText().trim();
        
        if (name.isEmpty()) {
            showError("Please enter a profile name");
            return;
        }
        
        // Check for duplicate names
        Optional<BackupProfile> existing = profiles.stream()
                .filter(p -> p.getName().equals(name))
                .findFirst();
        
        if (existing.isPresent()) {
            Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
            alert.setTitle("Profile Exists");
            alert.setHeaderText("Profile Already Exists");
            alert.setContentText("A profile with this name already exists. Overwrite?");
            
            if (alert.showAndWait().orElse(ButtonType.NO) == ButtonType.NO) {
                return;
            }
            
            profiles.remove(existing.get());
        }
        
        // Create new profile
        BackupProfile profile = new BackupProfile(name, profileDescriptionField.getText());
        profile.setCreatedAt(LocalDateTime.now());
        
        // Get currently selected paths
        List<String> selectedPaths = configScanner.getAllEntries().stream()
                .filter(ConfigEntry::isSelected)
                .map(ConfigEntry::getPath)
                .collect(Collectors.toList());
        
        profile.setSelectedPaths(selectedPaths);
        
        // Get included categories
        List<String> categories = configScanner.getAllEntries().stream()
                .filter(ConfigEntry::isSelected)
                .map(ConfigEntry::getCategory)
                .distinct()
                .collect(Collectors.toList());
        
        profile.setCategories(categories);
        
        profiles.add(profile);
        saveProfiles();
        
        profileListView.getSelectionModel().select(profile);
        logger.info("Profile saved: {}", name);
    }
    
    /**
     * Load selected profile and apply selections.
     */
    @FXML
    private void onLoadProfile(ActionEvent event) {
        BackupProfile selected = profileListView.getSelectionModel().getSelectedItem();
        
        if (selected == null) {
            showError("Please select a profile to load");
            return;
        }
        
        // Apply selections from profile
        for (ConfigEntry entry : configScanner.getAllEntries()) {
            if (selected.isPathSelected(entry.getPath())) {
                entry.setSelected(true);
            } else {
                entry.setSelected(false);
            }
        }
        
        // Mark profile as used
        selected.markAsUsed();
        saveProfiles();
        
        logger.info("Profile loaded: {}", selected.getName());
    }
    
    /**
     * Delete selected profile.
     */
    @FXML
    private void onDeleteProfile(ActionEvent event) {
        BackupProfile selected = profileListView.getSelectionModel().getSelectedItem();
        
        if (selected == null) {
            showError("Please select a profile to delete");
            return;
        }
        
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Delete Profile");
        alert.setHeaderText("Delete Profile");
        alert.setContentText("Are you sure you want to delete profile: " + selected.getName() + "?");
        
        if (alert.showAndWait().orElse(ButtonType.NO) == ButtonType.YES) {
            profiles.remove(selected);
            saveProfiles();
            profileDetailsPanel.setVisible(false);
            logger.info("Profile deleted: {}", selected.getName());
        }
    }
    
    /**
     * Select all items in the current profile.
     */
    @FXML
    private void onSelectAllProfile(ActionEvent event) {
        BackupProfile selected = profileListView.getSelectionModel().getSelectedItem();
        
        if (selected == null) {
            showError("Please select a profile first");
            return;
        }
        
        for (ConfigEntry entry : configScanner.getAllEntries()) {
            if (entry.isExists() && selected.getCategories().contains(entry.getCategory())) {
                entry.setSelected(true);
            }
        }
    }
    
    /**
     * Deselect all items.
     */
    @FXML
    private void onDeselectAllProfile(ActionEvent event) {
        configScanner.deselectAll();
    }
    
    /**
     * Show error dialog.
     */
    private void showError(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Error");
        alert.setContentText(message);
        alert.showAndWait();
    }
    
    /**
     * Custom list cell for displaying profiles.
     */
    private static class ProfileListCell extends ListCell<BackupProfile> {
        @Override
        protected void updateItem(BackupProfile profile, boolean empty) {
            super.updateItem(profile, empty);
            
            if (empty || profile == null) {
                setText(null);
                setGraphic(null);
            } else {
                VBox container = new VBox(5);
                container.setStyle("-fx-padding: 5;");
                
                Label nameLabel = new Label(profile.getName());
                nameLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 14px;");
                
                Label descLabel = new Label(profile.getDescription());
                descLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: #888;");
                
                Label infoLabel = new Label(String.format("%d items | Created: %s | Used: %s",
                        profile.getPathCount(),
                        profile.getFormattedCreatedDate(),
                        profile.getFormattedLastUsed()));
                infoLabel.setStyle("-fx-font-size: 10px; -fx-text-fill: #666;");
                
                container.getChildren().addAll(nameLabel, descLabel, infoLabel);
                setGraphic(container);
            }
        }
    }
}
