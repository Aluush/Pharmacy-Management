package com.example;

import javafx.fxml.FXML;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.Region;
import javafx.stage.Stage;
import com.example.util.AppConfig;
import com.example.Database;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.HashMap;
import java.util.Map;

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

    // Configuration
    @FXML private ComboBox<String> profileBox;
    @FXML private TextField appTitleField;
    @FXML private ComboBox<String> dbTypeBox;
    @FXML private TextField dbHostField;
    @FXML private TextField dbPortField;
    @FXML private TextField dbNameField;
    @FXML private TextField dbUserField;
    @FXML private PasswordField dbPassField;
    @FXML private TextField dbParamsField;
    @FXML private TextField dbUrlField;
    @FXML private Label configStatus;

    // Backup
    @FXML private Label lastBackupLabel;
    @FXML private Label backupStatus;

    @FXML
    private void initialize() {
        // Seed roles
        if (roleBox != null) {
            roleBox.getItems().setAll("Admin", "Manager", "Pharmacist", "Cashier", "Viewer");
        }
        initConfigUi();
        loadFromConfig();
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

        // Persist to session and update app shell
        Session.setUser(email, name);
        Scene scene = App.getPrimaryScene();
        if (scene != null) {
            Label u = (Label) scene.lookup("#userStatusLabel");
            if (u != null) u.setText("User: " + Session.getDisplayName());
            Label s = (Label) scene.lookup("#sidebarUserLabel");
            if (s != null) s.setText(Session.getDisplayName());
        }
        setProfileStatus("Profile updated successfully.", false);
    }

    // Configuration helpers and handlers
    private void initConfigUi() {
        if (profileBox != null) {
            profileBox.getItems().setAll("base", "dev", "test", "prod");
            String p = AppConfig.get().getActiveProfile();
            profileBox.setValue(p == null ? "base" : p);
        }
        if (dbTypeBox != null) {
            dbTypeBox.getItems().setAll("mysql", "postgres", "sqlite");
        }
    }

    private void loadFromConfig() {
        AppConfig cfg = AppConfig.get();
        if (appTitleField != null) appTitleField.setText(cfg.getAppTitle());
        if (dbTypeBox != null) dbTypeBox.setValue(cfg.getDbType());
        if (dbHostField != null) dbHostField.setText(cfg.getDbHost());
        if (dbPortField != null) dbPortField.setText(String.valueOf(cfg.getDbPort()));
        if (dbNameField != null) dbNameField.setText(cfg.getDbName());
        if (dbUserField != null) dbUserField.setText(cfg.getDbUser());
        if (dbPassField != null) dbPassField.setText(cfg.getDbPass());
        if (dbParamsField != null) dbParamsField.setText(cfg.getDbParams());
        if (dbUrlField != null) {
            String url = cfg.getExplicitJdbcUrl();
            dbUrlField.setText(url == null ? "" : url);
        }
        setConfigStatus("Configuration loaded" + (cfg.getActiveProfile() == null ? " (base)" : " (" + cfg.getActiveProfile() + ")") + ".", false);
    }

    private Map<String, String> gatherFromFields() {
        Map<String, String> m = new HashMap<>();
        if (appTitleField != null) m.put("app.title", safe(appTitleField.getText()));
        if (dbTypeBox != null && dbTypeBox.getValue() != null) m.put("db.type", dbTypeBox.getValue().toLowerCase(Locale.ROOT));
        if (dbHostField != null) m.put("db.host", safe(dbHostField.getText()));
        if (dbPortField != null) m.put("db.port", safe(dbPortField.getText()));
        if (dbNameField != null) m.put("db.name", safe(dbNameField.getText()));
        if (dbUserField != null) m.put("db.user", safe(dbUserField.getText()));
        if (dbPassField != null) m.put("db.pass", safe(dbPassField.getText()));
        if (dbParamsField != null) m.put("db.params", safe(dbParamsField.getText()));
        if (dbUrlField != null) m.put("db.url", safe(dbUrlField.getText()));
        return m;
    }

    private void setConfigStatus(String msg, boolean error) {
        if (configStatus != null) {
            configStatus.setText(msg);
            configStatus.setStyle(error ? "-fx-text-fill: #ef4444;" : "-fx-text-fill: -color-text-muted;");
        }
    }

    private void updateStageTitleFromConfig() {
        Scene scene = App.getPrimaryScene();
        if (scene != null) {
            Stage st = (Stage) scene.getWindow();
            if (st != null) st.setTitle(AppConfig.get().getAppTitle());
        }
    }

    @FXML
    private void onLoadConfig() {
        AppConfig.get().reload();
        loadFromConfig();
        updateStageTitleFromConfig();
    }

    @FXML
    private void onSaveConfig() {
        AppConfig cfg = AppConfig.get();
        cfg.update(gatherFromFields());
        boolean ok = cfg.saveToUserConfig();
        setConfigStatus(ok ? "Configuration saved to ~/.pharmapro" : "Failed to save configuration", !ok);
        updateStageTitleFromConfig();
    }

    @FXML
    private void onTestDb() {
        try (var c = Database.getConnection()) {
            setConfigStatus("DB connection OK (" + c.getMetaData().getURL() + ")", false);
        } catch (Exception ex) {
            setConfigStatus("DB connection failed: " + ex.getMessage(), true);
        }
    }

    @FXML
    private void onSwitchProfile() {
        String sel = profileBox != null ? profileBox.getValue() : "base";
        String profile = (sel == null || sel.equalsIgnoreCase("base")) ? null : sel.trim().toLowerCase(Locale.ROOT);
        AppConfig.get().setActiveProfile(profile);
        // refresh UI from new profile
        if (profileBox != null) profileBox.setValue(AppConfig.get().getActiveProfile() == null ? "base" : AppConfig.get().getActiveProfile());
        loadFromConfig();
        updateStageTitleFromConfig();
    }

    @FXML
    private void onReloadConfig() {
        AppConfig.get().reload();
        loadFromConfig();
        updateStageTitleFromConfig();
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
