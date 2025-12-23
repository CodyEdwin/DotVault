package com.dotvault.controller;

import com.dotvault.App;
import com.dotvault.model.BackupProfile;
import com.dotvault.model.ConfigEntry;
import com.dotvault.model.Settings;
import com.dotvault.service.BackupService;
import com.dotvault.service.ConfigScanner;
import com.dotvault.service.RestoreService;
import com.dotvault.util.PathUtils;
import javafx.application.Platform;
import javafx.beans.property.*;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.control.cell.CheckBoxTreeCell;
import javafx.scene.layout.*;
import javafx.stage.DirectoryChooser;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Main controller for the DotVault application.
 * Handles the primary UI interactions for scanning, selecting, backing up,
 * and restoring configuration files.
 */
public class MainController implements Initializable {
    
    private static final Logger logger = LoggerFactory.getLogger(MainController.class);
    private static final DateTimeFormatter DATE_FORMATTER = 
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    
    @FXML private BorderPane mainBorderPane;
    @FXML private TreeView<ConfigEntry> configTreeView;
    @FXML private ListView<String> categoryListView;
    @FXML private TextField backupPathField;
    @FXML private Button backupPathBrowseButton;
    @FXML private CheckBox compressCheckBox;
    @FXML private CheckBox preservePermissionsCheckBox;
    @FXML private Button selectAllButton;
    @FXML private Button deselectAllButton;
    @FXML private Button refreshButton;
    @FXML private Button backupButton;
    @FXML private ProgressBar backupProgressBar;
    @FXML private Label statusLabel;
    @FXML private Label selectedCountLabel;
    @FXML private Label totalSizeLabel;
    @FXML private TabPane tabPane;
    @FXML private Tab backupTab;
    @FXML private Tab restoreTab;
    @FXML private Tab profilesTab;
    @FXML private Tab settingsTab;
    @FXML private TextField restorePathField;
    @FXML private Button restorePathBrowseButton;
    @FXML private Button restoreButton;
    @FXML private TextArea logTextArea;
    @FXML private ProgressIndicator progressIndicator;
    
    private Stage stage;
    private ConfigScanner configScanner;
    private BackupService backupService;
    private RestoreService restoreService;
    private Settings settings;
    private ObservableList<ConfigEntry> allEntries;
    private TreeItem<ConfigEntry> rootTreeItem;
    private Map<String, TreeItem<ConfigEntry>> categoryTreeItems;
    
    @Override
    public void initialize(URL location, ResourceBundle resources) {
        this.configScanner = new ConfigScanner();
        this.backupService = new BackupService();
        this.restoreService = new RestoreService();
        this.settings = App.getSettings();
        this.allEntries = FXCollections.observableArrayList();
        this.categoryTreeItems = new HashMap<>();
        
        setupUI();
        setupEventHandlers();
        loadSettings();
        scanConfigurations();
    }
    
    /**
     * Initialize the controller with the primary stage.
     */
    public MainController(Stage stage) {
        this.stage = stage;
    }
    
    /**
     * Setup UI components.
     */
    private void setupUI() {
        // Setup category list
        categoryListView.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        categoryListView.getSelectionModel().selectedItemProperty().addListener(
                (obs, oldVal, newVal) -> filterByCategory());
        
        // Setup backup options
        compressCheckBox.setSelected(settings.isCompressBackups());
        preservePermissionsCheckBox.setSelected(settings.isPreservePermissions());
        
        // Setup progress bar
        backupProgressBar.setProgress(0);
        
        // Setup log text area
        logTextArea.setEditable(false);
        
        // Setup tab selection listener
        tabPane.getSelectionModel().selectedItemProperty().addListener(
                (obs, oldTab, newTab) -> updateUIForTab(newTab));
    }
    
    /**
     * Setup event handlers for UI interactions.
     */
    private void setupEventHandlers() {
        // Backup path browse
        backupPathBrowseButton.setOnAction(this::onBrowseBackupPath);
        
        // Restore path browse
        restorePathBrowseButton.setOnAction(this::onBrowseRestorePath);
        
        // Selection buttons
        selectAllButton.setOnAction(this::onSelectAll);
        deselectAllButton.setOnAction(this::onDeselectAll);
        refreshButton.setOnAction(this::onRefresh);
        
        // Backup button
        backupButton.setOnAction(this::onBackup);
        
        // Restore button
        restoreButton.setOnAction(this::onRestore);
        
        // Compress checkbox
        compressCheckBox.setOnAction(e -> {
            settings.setCompressBackups(compressCheckBox.isSelected());
            saveSettings();
        });
        
        // Preserve permissions checkbox
        preservePermissionsCheckBox.setOnAction(e -> {
            settings.setPreservePermissions(preservePermissionsCheckBox.isSelected());
            saveSettings();
        });
    }
    
    /**
     * Load saved settings into UI.
     */
    private void loadSettings() {
        backupPathField.setText(settings.getDefaultBackupDirectory());
        restorePathField.setText(System.getProperty("user.home"));
        
        // Restore window size
        if (settings.getWindowWidth() > 0 && settings.getWindowHeight() > 0) {
            stage.setWidth(settings.getWindowWidth());
            stage.setHeight(settings.getWindowHeight());
        }
        
        // Restore maximized state
        if (settings.isWindowMaximized()) {
            stage.setMaximized(true);
        }
        
        // Restore selected tab
        if (settings.getSelectedTab() < tabPane.getTabs().size()) {
            tabPane.getSelectionModel().select(settings.getSelectedTab());
        }
    }
    
    /**
     * Save current settings.
     */
    private void saveSettings() {
        settings.setWindowWidth(stage.getWidth());
        settings.setWindowHeight(stage.getHeight());
        settings.setWindowMaximized(stage.isMaximized());
        settings.setSelectedTab(tabPane.getSelectionModel().getSelectedIndex());
        settings.setDefaultBackupDirectory(backupPathField.getText());
        
        try {
            settings.save();
        } catch (IOException e) {
            logger.error("Failed to save settings", e);
        }
    }
    
    /**
     * Scan for configuration files.
     */
    private void scanConfigurations() {
        updateStatus("Scanning for configurations...");
        progressIndicator.setVisible(true);
        
        Task<Void> scanTask = new Task<>() {
            @Override
            protected Void call() {
                List<ConfigEntry> entries = configScanner.scan();
                Platform.runLater(() -> {
                    allEntries.setAll(entries);
                    buildTreeView();
                    buildCategoryList();
                    progressIndicator.setVisible(false);
                    updateStatus("Found " + configScanner.getExistingCount() + " configurations");
                    updateStats();
                });
                return null;
            }
        };
        
        new Thread(scanTask).start();
    }
    
    /**
     * Build the tree view of configurations.
     */
    private void buildTreeView() {
        rootTreeItem = new TreeItem<>(null);
        
        Map<String, Map<String, List<ConfigEntry>>> grouped = allEntries.stream()
                .filter(ConfigEntry::isExists)
                .collect(Collectors.groupingBy(
                        ConfigEntry::getCategory,
                        Collectors.groupingBy(ConfigEntry::getApplication)
                ));
        
        for (String category : grouped.keySet()) {
            TreeItem<ConfigEntry> categoryItem = new TreeItem<>(null);
            categoryItem.setExpanded(true);
            
            for (String app : grouped.get(category).keySet()) {
                TreeItem<ConfigEntry> appItem = new TreeItem<>(null);
                
                for (ConfigEntry entry : grouped.get(category).get(app)) {
                    TreeItem<ConfigEntry> entryItem = new TreeItem<>(entry);
                    entryItem.setExpanded(true);
                    appItem.getChildren().add(entryItem);
                }
                
                if (!appItem.getChildren().isEmpty()) {
                    categoryItem.getChildren().add(appItem);
                }
            }
            
            if (!categoryItem.getChildren().isEmpty()) {
                rootTreeItem.getChildren().add(categoryItem);
            }
        }
        
        configTreeView.setRoot(rootTreeItem);
        configTreeView.setCellFactory(CheckBoxTreeCell.<ConfigEntry>forTreeView(
                item -> {
                    BooleanProperty observable = new SimpleBooleanProperty(
                            item.getValue() != null && item.getValue().isSelected());
                    observable.addListener((obs, wasSelected, isNowSelected) -> {
                        if (item.getValue() != null) {
                            item.getValue().setSelected(isNowSelected);
                            updateStats();
                        }
                    });
                    return observable;
                },
                configTreeView.getSelectionModel()
        ));
    }
    
    /**
     * Build the category list.
     */
    private void buildCategoryList() {
        List<String> categories = configScanner.getCategories();
        categoryListView.setItems(FXCollections.observableArrayList(categories));
    }
    
    /**
     * Filter tree view by selected categories.
     */
    private void filterByCategory() {
        Set<String> selectedCategories = new HashSet<>(
                categoryListView.getSelectionModel().getSelectedItems());
        
        if (selectedCategories.isEmpty()) {
            configTreeView.setRoot(rootTreeItem);
        } else {
            TreeItem<ConfigEntry> filteredRoot = new TreeItem<>(null);
            
            for (TreeItem<ConfigEntry> categoryItem : rootTreeItem.getChildren()) {
                String categoryName = getCategoryName(categoryItem);
                if (selectedCategories.contains(categoryName)) {
                    filteredRoot.getChildren().add(categoryItem);
                }
            }
            
            configTreeView.setRoot(filteredRoot);
        }
    }
    
    /**
     * Get category name from tree item.
     */
    private String getCategoryName(TreeItem<ConfigEntry> item) {
        if (item.getChildren().isEmpty()) {
            return null;
        }
        ConfigEntry first = item.getChildren().get(0).getParent().getValue();
        return first != null ? first.getCategory() : null;
    }
    
    /**
     * Update statistics display.
     */
    private void updateStats() {
        int selectedCount = configScanner.getSelectedCount();
        long totalSize = configScanner.getSelectedTotalSize();
        
        selectedCountLabel.setText(selectedCount + " selected");
        totalSizeLabel.setText(com.dotvault.util.FileUtils.formatFileSize(totalSize));
    }
    
    /**
     * Update status label.
     */
    private void updateStatus(String message) {
        statusLabel.setText(message);
        appendLog(message);
    }
    
    /**
     * Append message to log area.
     */
    private void appendLog(String message) {
        String timestamp = LocalDateTime.now().format(DATE_FORMATTER);
        logTextArea.appendText("[" + timestamp + "] " + message + "\n");
    }
    
    /**
     * Update UI based on selected tab.
     */
    private void updateUIForTab(Tab tab) {
        if (tab == backupTab) {
            // Backup tab active
        } else if (tab == restoreTab) {
            // Restore tab active
        } else if (tab == profilesTab) {
            // Profiles tab active
        } else if (tab == settingsTab) {
            // Settings tab active
        }
    }
    
    // Event handlers
    
    @FXML
    private void onBrowseBackupPath(ActionEvent event) {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("Select Backup Destination");
        chooser.setInitialDirectory(Paths.get(backupPathField.getText()));
        
        Path selected = chooser.showDialog(stage);
        if (selected != null) {
            backupPathField.setText(selected.toString());
            settings.setDefaultBackupDirectory(selected.toString());
            saveSettings();
        }
    }
    
    @FXML
    private void onBrowseRestorePath(ActionEvent event) {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("Select Restore Location");
        chooser.setInitialDirectory(Paths.get(restorePathField.getText()));
        
        Path selected = chooser.showDialog(stage);
        if (selected != null) {
            restorePathField.setText(selected.toString());
        }
    }
    
    @FXML
    private void onSelectAll(ActionEvent event) {
        configScanner.selectAll();
        refreshTreeView();
        updateStats();
        updateStatus("All configurations selected");
    }
    
    @FXML
    private void onDeselectAll(ActionEvent event) {
        configScanner.deselectAll();
        refreshTreeView();
        updateStats();
        updateStatus("All configurations deselected");
    }
    
    @FXML
    private void onRefresh(ActionEvent event) {
        scanConfigurations();
    }
    
    @FXML
    private void onBackup(ActionEvent event) {
        List<ConfigEntry> selectedEntries = configScanner.getSelectedEntries();
        
        if (selectedEntries.isEmpty()) {
            updateStatus("No configurations selected for backup");
            return;
        }
        
        Path backupPath = Paths.get(backupPathField.getText());
        
        updateStatus("Starting backup...");
        backupButton.setDisable(true);
        backupProgressBar.setProgress(0);
        
        Task<BackupService.BackupResult> backupTask = new Task<>() {
            @Override
            protected BackupService.BackupResult call() {
                return backupService.backup(
                        selectedEntries,
                        backupPath,
                        settings,
                        progress -> {
                            Platform.runLater(() -> {
                                backupProgressBar.setProgress(progress.getPercentage() / 100.0);
                                updateStatus("Backing up: " + progress.getCurrentFile());
                            });
                        }
                );
            }
            
            @Override
            protected void succeeded() {
                BackupService.BackupResult result = getValue();
                backupButton.setDisable(false);
                backupProgressBar.setProgress(1.0);
                
                if (result.isSuccess()) {
                    settings.markBackupCompleted(result.getOutputPath());
                    saveSettings();
                    updateStatus("Backup completed: " + result.getTotalFiles() + " files, " +
                            com.dotvault.util.FileUtils.formatFileSize(result.getTotalBytes()));
                    appendLog("Backup completed successfully");
                    appendLog("Output: " + result.getOutputPath());
                } else {
                    updateStatus("Backup completed with errors");
                    appendLog("Backup completed with " + result.getErrors().size() + " errors");
                }
                
                for (String error : result.getErrors()) {
                    appendLog("ERROR: " + error);
                }
            }
            
            @Override
            protected void failed() {
                backupButton.setDisable(false);
                updateStatus("Backup failed");
                appendLog("Backup failed: " + getException().getMessage());
            }
        };
        
        new Thread(backupTask).start();
    }
    
    @FXML
    private void onRestore(ActionEvent event) {
        // TODO: Implement restore functionality
        updateStatus("Restore functionality coming soon");
    }
    
    /**
     * Refresh tree view selection state.
     */
    private void refreshTreeView() {
        configTreeView.refresh();
    }
    
    /**
     * Cleanup resources when closing.
     */
    public void shutdown() {
        backupService.shutdown();
        restoreService.shutdown();
        saveSettings();
    }
}
