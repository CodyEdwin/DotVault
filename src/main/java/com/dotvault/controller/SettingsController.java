package com.dotvault.controller;

import com.dotvault.App;
import com.dotvault.model.Settings;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.stage.DirectoryChooser;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ResourceBundle;

/**
 * Controller for the settings dialog.
 * Handles configuration of application preferences including backup options,
 * UI settings, and paths.
 */
public class SettingsController implements Initializable {
    
    private static final Logger logger = LoggerFactory.getLogger(SettingsController.class);
    
    @FXML private TextField defaultBackupDirField;
    @FXML private Button defaultBackupDirBrowseButton;
    @FXML private CheckBox compressCheckBox;
    @FXML private ChoiceBox<String> compressionFormatChoice;
    @FXML private CheckBox preservePermissionsCheckBox;
    @FXML private CheckBox followSymlinksCheckBox;
    @FXML private CheckBox createTimestampFolderCheckBox;
    @FXML private CheckBox skipHiddenCheckBox;
    @FXML private TextArea excludePatternsField;
    @FXML private CheckBox darkModeCheckBox;
    @FXML private CheckBox autoScanCheckBox;
    @FXML private Spinner<Integer> maxRecentDirsSpinner;
    @FXML private Button saveButton;
    @FXML private Button cancelButton;
    @FXML private Button resetDefaultsButton;
    
    private Settings settings;
    private Stage dialogStage;
    
    @Override
    public void initialize(URL location, ResourceBundle resources) {
        this.settings = App.getSettings();
        setupUI();
    }
    
    /**
     * Initialize the controller with the dialog stage.
     */
    public SettingsController(Stage dialogStage) {
        this.dialogStage = dialogStage;
    }
    
    /**
     * Setup UI components with current settings.
     */
    private void setupUI() {
        // Backup settings
        defaultBackupDirField.setText(settings.getDefaultBackupDirectory());
        compressCheckBox.setSelected(settings.isCompressBackups());
        compressionFormatChoice.setItems(
                javafx.collections.FXCollections.observableArrayList("zip", "tar.gz")
        );
        compressionFormatChoice.setValue(settings.getCompressionFormat());
        preservePermissionsCheckBox.setSelected(settings.isPreservePermissions());
        followSymlinksCheckBox.setSelected(settings.isFollowSymlinks());
        createTimestampFolderCheckBox.setSelected(settings.isCreateTimestampFolder());
        skipHiddenCheckBox.setSelected(settings.isSkipHiddenFiles());
        
        // Exclude patterns
        String patterns = String.join(", ", settings.getExcludePatterns());
        excludePatternsField.setText(patterns);
        
        // UI settings
        darkModeCheckBox.setSelected(settings.isDarkMode());
        autoScanCheckBox.setSelected(false); // TODO: Add to Settings
        
        // Recent directories spinner
        SpinnerValueFactory<Integer> valueFactory = 
                new SpinnerValueFactory.IntegerSpinnerValueFactory(1, 20, settings.getMaxRecentDirs());
        maxRecentDirsSpinner.setValueFactory(valueFactory);
        
        // Compression format visibility
        compressCheckBox.selectedProperty().addListener((obs, old, newVal) -> {
            compressionFormatChoice.setDisable(!newVal);
        });
        compressionFormatChoice.setDisable(!compressCheckBox.isSelected());
    }
    
    /**
     * Save current settings.
     */
    @FXML
    private void onSave(ActionEvent event) {
        try {
            // Backup settings
            settings.setDefaultBackupDirectory(defaultBackupDirField.getText());
            settings.setCompressBackups(compressCheckBox.isSelected());
            settings.setCompressionFormat(compressionFormatChoice.getValue());
            settings.setPreservePermissions(preservePermissionsCheckBox.isSelected());
            settings.setFollowSymlinks(followSymlinksCheckBox.isSelected());
            settings.setCreateTimestampFolder(createTimestampFolderCheckBox.isSelected());
            settings.setSkipHiddenFiles(skipHiddenCheckBox.isSelected());
            
            // Exclude patterns
            String patternsText = excludePatternsField.getText();
            if (!patternsText.isEmpty()) {
                String[] patterns = patternsText.split(",");
                settings.setExcludePatterns(
                        java.util.Arrays.stream(patterns)
                                .map(String::trim)
                                .filter(s -> !s.isEmpty())
                                .collect(java.util.stream.Collectors.toList())
                );
            }
            
            // UI settings
            settings.setDarkMode(darkModeCheckBox.isSelected());
            settings.setMaxRecentDirs(maxRecentDirsSpinner.getValue());
            
            settings.save();
            dialogStage.close();
        } catch (IOException e) {
            logger.error("Failed to save settings", e);
            showError("Failed to save settings: " + e.getMessage());
        }
    }
    
    /**
     * Cancel settings changes.
     */
    @FXML
    private void onCancel(ActionEvent event) {
        dialogStage.close();
    }
    
    /**
     * Reset settings to defaults.
     */
    @FXML
    private void onResetDefaults(ActionEvent event) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Reset Settings");
        alert.setHeaderText("Reset to Defaults");
        alert.setContentText("Are you sure you want to reset all settings to their default values?");
        
        if (alert.showAndWait().orElse(ButtonType.NO) == ButtonType.YES) {
            settings.resetToDefaults();
            setupUI();
        }
    }
    
    /**
     * Browse for default backup directory.
     */
    @FXML
    private void onBrowseBackupDir(ActionEvent event) {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("Select Default Backup Directory");
        chooser.setInitialDirectory(Paths.get(defaultBackupDirField.getText()));
        
        Path selected = chooser.showDialog(dialogStage);
        if (selected != null) {
            defaultBackupDirField.setText(selected.toString());
        }
    }
    
    /**
     * Show error dialog.
     */
    private void showError(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Error");
        alert.setHeaderText("Settings Error");
        alert.setContentText(message);
        alert.showAndWait();
    }
}
