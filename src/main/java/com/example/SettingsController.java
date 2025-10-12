package com.example;

import javafx.fxml.FXML;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.Region;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

public class SettingsController {

    // Appearance
    @FXML private Label appearanceStatus;

    // Profile
    @FXML private TextField fullNameField;
    @FXML private TextField emailField;
    @FXML private TextField phoneField;
    @FXML private ComboBox<String> roleBox;
    @FXML private PasswordField passwordField;
    @FXML private PasswordField confirmPasswordField;
    @FXML private Label profileStatus;

    // Backup
    @FXML private Label lastBackupLabel;
    @FXML private Label backupStatus;

    @FXML
    private void initialize() {
        // Seed roles
        if (roleBox != null) {
            roleBox.getItems().setAll("Admin", "Manager", "Pharmacist", "Cashier", "Viewer");
        }
    }

    // Appearance handlers
    @FXML
    private void onLightMode() {
        Scene scene = App.getPrimaryScene();
        if (scene == null) return;
        scene.getRoot().getStyleClass().remove("dark");
        setAppearanceStatus("Light mode enabled.", false);
    }

    @FXML
    private void onDarkMode() {
        Scene scene = App.getPrimaryScene();
        if (scene == null) return;
        if (!scene.getRoot().getStyleClass().contains("dark")) {
            scene.getRoot().getStyleClass().add("dark");
        }
        setAppearanceStatus("Dark mode enabled.", false);
    }

    @FXML
    private void onSaveAll() {
        // Stub to represent saving all settings
        setAppearanceStatus("Appearance saved.", false);
        setProfileStatus("Profile saved.", false);
        setBackupStatus("Settings saved (demo).", false);
    }

    // Profile handlers
    @FXML
    private void onResetProfile() {
        fullNameField.clear();
        emailField.clear();
        phoneField.clear();
        roleBox.setValue(null);
        passwordField.clear();
        confirmPasswordField.clear();
        setProfileStatus("", false);
    }

    @FXML
    private void onUpdateProfile() {
        String name = safe(fullNameField.getText());
        String email = safe(emailField.getText());
        String phone = safe(phoneField.getText());
        String role = roleBox.getValue();
        String pass = safe(passwordField.getText());
        String confirm = safe(confirmPasswordField.getText());

        if (name.isEmpty()) { setProfileStatus("Full name is required.", true); return; }
        if (email.isEmpty() || !email.contains("@")) { setProfileStatus("Valid email is required.", true); return; }
        if (role == null || role.isBlank()) { setProfileStatus("Role is required.", true); return; }
        if (!pass.isEmpty() || !confirm.isEmpty()) {
            if (!pass.equals(confirm)) { setProfileStatus("Passwords do not match.", true); return; }
            if (pass.length() < 6) { setProfileStatus("Password must be at least 6 characters.", true); return; }
        }

        // Stub: persist profile settings
        setProfileStatus("Profile updated successfully.", false);
    }

    // Backup handlers
    @FXML
    private void onBackup() {
        // Stub: perform backup
        String ts = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.US));
        lastBackupLabel.setText(ts);
        setBackupStatus("Backup completed at " + ts + ".", false);
    }

    @FXML
    private void onRestore() {
        // Stub: perform restore
        setBackupStatus("Restore completed (demo).", false);
    }

    // Helpers
    private static String safe(String s) { return s == null ? "" : s.trim(); }

    private void setAppearanceStatus(String msg, boolean error) {
        appearanceStatus.setText(msg);
        appearanceStatus.setStyle(error ? "-fx-text-fill: #ef4444;" : "-fx-text-fill: -color-text-muted;");
    }
    private void setProfileStatus(String msg, boolean error) {
        profileStatus.setText(msg);
        profileStatus.setStyle(error ? "-fx-text-fill: #ef4444;" : "-fx-text-fill: -color-text-muted;");
    }
    private void setBackupStatus(String msg, boolean error) {
        backupStatus.setText(msg);
        backupStatus.setStyle(error ? "-fx-text-fill: #ef4444;" : "-fx-text-fill: -color-text-muted;");
    }
}
