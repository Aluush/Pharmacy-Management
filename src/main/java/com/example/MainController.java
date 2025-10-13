package com.example;

import java.io.IOException;
import java.net.URL;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.animation.KeyValue;
import javafx.animation.Interpolator;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.control.Tooltip;
import javafx.scene.control.ContentDisplay;
import javafx.util.Duration;

import java.sql.Connection;
import java.sql.SQLException;

public class MainController {

    @FXML private StackPane contentArea;
    @FXML private ToggleButton btnDashboard;
    @FXML private ToggleButton btnInventory;
    @FXML private ToggleButton btnSales;
    @FXML private ToggleButton btnSettings;
    @FXML private TextField searchField;

    // Topbar extras
    @FXML private ToggleButton themeToggle;

    // Status bar
    @FXML private Label dbStatusLabel;
    @FXML private Label userStatusLabel;
    @FXML private Label timeLabel;

    // Pro sidebar
    @FXML private VBox sidebar;
    @FXML private Label sidebarUserLabel;
    private boolean sidebarCollapsed = false;
    private boolean sidebarHoverExpanded = false;

    private final Map<String, Parent> viewCache = new HashMap<>();
    private final Map<String, Object> controllerCache = new HashMap<>();
    private ToggleGroup navGroup;

    @FXML
    private void initialize() {
        // Setup exclusive selection for sidebar buttons
        navGroup = new ToggleGroup();
        btnDashboard.setToggleGroup(navGroup);
        btnInventory.setToggleGroup(navGroup);
        btnSales.setToggleGroup(navGroup);
        btnSettings.setToggleGroup(navGroup);

        // Default view
        if (btnDashboard.isSelected()) {
            showDashboard(null);
        } else {
            btnDashboard.setSelected(true);
            showDashboard(null);
        }

        // Defer UI-dependent setup until the Scene is ready
        Platform.runLater(() -> {
            setupClock();
            updateUserStatus();
            checkDbStatusAsync();
            setupShortcuts();
            syncThemeToggle();
            setupSearchRouting();
            setupSidebarUX();
            updateSidebarUser();
        });
    }

    @FXML
    public void showDashboard(ActionEvent e) {
        setContent(loadView("dashboard-view.fxml"));
    }

    @FXML
    public void showInventory(ActionEvent e) {
        setContent(loadView("inventory-view.fxml"));
    }

    @FXML
    public void showSales(ActionEvent e) {
        setContent(loadView("sales-view.fxml"));
    }

    @FXML
    public void showSettings(ActionEvent e) {
        setContent(loadView("settings-view.fxml"));
    }

    private void setContent(Parent view) {
        if (view == null) return;
        contentArea.getChildren().setAll(view);
    }

    private Parent loadView(String fxmlName) {
        try {
            if (viewCache.containsKey(fxmlName)) {
                return viewCache.get(fxmlName);
            }
            URL url = App.class.getResource(fxmlName);
            FXMLLoader loader = new FXMLLoader(url);
            Parent root = loader.load();
            viewCache.put(fxmlName, root);
            controllerCache.put(fxmlName, loader.getController());
            return root;
        } catch (IOException ex) {
            // Fallback simple error view
            return simpleError("Failed to load " + fxmlName + ": " + ex.getMessage());
        }
    }

    private Parent simpleError(String message) {
        javafx.scene.control.Label label = new javafx.scene.control.Label(message);
        label.getStyleClass().add("error-text");
        javafx.scene.layout.StackPane pane = new javafx.scene.layout.StackPane(label);
        pane.getStyleClass().add("error-pane");
        return pane;
    }

    // Topbar actions
    @FXML
    private void onToggleTheme(ActionEvent e) {
        Scene scene = App.getPrimaryScene();
        if (scene == null || themeToggle == null) return;
        boolean dark = themeToggle.isSelected();
        if (dark) {
            if (!scene.getRoot().getStyleClass().contains("dark")) {
                scene.getRoot().getStyleClass().add("dark");
            }
            themeToggle.setText("Light");
        } else {
            scene.getRoot().getStyleClass().remove("dark");
            themeToggle.setText("Dark");
        }
    }

    @FXML
    private void onProfile(ActionEvent e) {
        String user = Session.isLoggedIn() ? Session.getDisplayName() : "Guest";
        javafx.scene.control.Alert a = new javafx.scene.control.Alert(javafx.scene.control.Alert.AlertType.INFORMATION);
        a.setTitle("Profile");
        a.setHeaderText("Current User");
        a.setContentText("Signed in as: " + user);
        a.showAndWait();
    }

    @FXML
    private void onLogout(ActionEvent e) {
        Session.clear();
        updateUserStatus();
        // Optionally navigate to dashboard
        btnDashboard.setSelected(true);
        showDashboard(null);
    }

    // Helpers

    private void setupClock() {
        if (timeLabel == null) return;
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("HH:mm:ss");
        timeLabel.setText(LocalTime.now().format(fmt));
        Timeline timeline = new Timeline(
            new KeyFrame(Duration.seconds(1), ev -> timeLabel.setText(LocalTime.now().format(fmt)))
        );
        timeline.setCycleCount(Timeline.INDEFINITE);
        timeline.play();
    }

    private void updateUserStatus() {
        if (userStatusLabel == null) return;
        String user = Session.isLoggedIn() ? Session.getDisplayName() : "Guest";
        userStatusLabel.setText("User: " + (user == null || user.isBlank() ? "Guest" : user));
    }

    private void checkDbStatusAsync() {
        if (dbStatusLabel == null) return;
        // Default state while checking
        dbStatusLabel.getStyleClass().removeAll("badge", "status-ok", "status-bad");
        dbStatusLabel.setText("DB: Checking...");
        new Thread(() -> {
            boolean ok;
            try (Connection c = Database.getConnection()) {
                ok = (c != null && !c.isClosed());
            } catch (SQLException ex) {
                ok = false;
            }
            final boolean connected = ok;
            Platform.runLater(() -> {
                dbStatusLabel.getStyleClass().removeAll("badge", "status-ok", "status-bad");
                if (connected) {
                    dbStatusLabel.getStyleClass().add("status-ok");
                    dbStatusLabel.setText("DB: Connected");
                } else {
                    dbStatusLabel.getStyleClass().add("status-bad");
                    dbStatusLabel.setText("DB: Offline");
                }
            });
        }, "db-status-check").start();
    }

    private void setupShortcuts() {
        if (contentArea == null) return;
        Scene scene = contentArea.getScene();
        if (scene == null) return;
        scene.getAccelerators().put(
            new KeyCodeCombination(KeyCode.K, KeyCombination.SHORTCUT_DOWN),
            () -> { if (searchField != null) searchField.requestFocus(); }
        );
    }

    private void syncThemeToggle() {
        if (themeToggle == null) return;
        Scene scene = App.getPrimaryScene();
        if (scene == null) return;
        boolean isDark = scene.getRoot().getStyleClass().contains("dark");
        themeToggle.setSelected(isDark);
        themeToggle.setText(isDark ? "Light" : "Dark");
    }

    private void setupSearchRouting() {
        if (searchField == null) return;
        searchField.setOnAction(ev -> {
            String q = searchField.getText();
            routeSearch(q);
        });
    }

    private void routeSearch(String q) {
        String query = (q == null ? "" : q.trim().toLowerCase());
        if (query.isEmpty()) return;
        try {
            if (query.contains("inventory") || query.contains("stock") || query.startsWith("inv")) {
                btnInventory.setSelected(true);
                showInventory(null);
                Object ctrl = controllerCache.get("inventory-view.fxml");
                if (ctrl instanceof InventoryController ic) {
                    ic.focusFilterWith(q);
                }
            } else if (query.contains("sale") || query.startsWith("sal") || query.contains("cart")) {
                btnSales.setSelected(true);
                showSales(null);
                Object ctrl = controllerCache.get("sales-view.fxml");
                if (ctrl instanceof SalesController sc) {
                    sc.focusItemWith("");
                }
            } else if (query.contains("setting") || query.startsWith("set")) {
                btnSettings.setSelected(true);
                showSettings(null);
            } else {
                btnDashboard.setSelected(true);
                showDashboard(null);
            }
        } catch (Exception ignore) {
            // no-op
        }
    }

    @FXML
    private void toggleSidebar(ActionEvent e) {
        setSidebarCollapsed(!sidebarCollapsed);
    }

    private void setSidebarCollapsed(boolean collapsed) {
        double from = sidebar == null ? 0 : sidebar.getPrefWidth();
        sidebarCollapsed = collapsed;
        if (sidebar == null) return;
        if (collapsed) {
            if (!sidebar.getStyleClass().contains("collapsed")) {
                sidebar.getStyleClass().add("collapsed");
            }
            animateSidebarWidth(from <= 0 ? 240 : from, 72, true);
        } else {
            sidebar.getStyleClass().remove("collapsed");
            animateSidebarWidth(from <= 0 ? 72 : from, 240, false);
        }
    }

    private void setButtonsContentDisplay(ContentDisplay display) {
        if (btnDashboard != null) btnDashboard.setContentDisplay(display);
        if (btnInventory != null) btnInventory.setContentDisplay(display);
        if (btnSales != null) btnSales.setContentDisplay(display);
        if (btnSettings != null) btnSettings.setContentDisplay(display);
    }

    private void animateSidebarWidth(double from, double to, boolean collapsing) {
        if (sidebar == null) return;
        sidebar.setPrefWidth(from);
        Timeline tl = new Timeline(
            new KeyFrame(Duration.ZERO, new KeyValue(sidebar.prefWidthProperty(), from, Interpolator.EASE_BOTH)),
            new KeyFrame(Duration.millis(220), new KeyValue(sidebar.prefWidthProperty(), to, Interpolator.EASE_BOTH))
        );
        tl.setOnFinished(ev -> {
            setButtonsContentDisplay(collapsing ? ContentDisplay.GRAPHIC_ONLY : ContentDisplay.LEFT);
        });
        tl.play();
    }

    private void setupSidebarUX() {
        // Tooltips for quick access (useful when collapsed)
        if (btnDashboard != null) btnDashboard.setTooltip(new Tooltip("Dashboard"));
        if (btnInventory != null) btnInventory.setTooltip(new Tooltip("Inventory"));
        if (btnSales != null) btnSales.setTooltip(new Tooltip("Sales"));
        if (btnSettings != null) btnSettings.setTooltip(new Tooltip("Settings"));
        if (sidebar != null) {
            sidebar.setPrefWidth(240);
            sidebar.setOnMouseEntered(ev -> {
                if (sidebarCollapsed) {
                    sidebarHoverExpanded = true;
                    setSidebarCollapsed(false);
                }
            });
            sidebar.setOnMouseExited(ev -> {
                if (sidebarHoverExpanded) {
                    setSidebarCollapsed(true);
                    sidebarHoverExpanded = false;
                }
            });
        }
    }

    private void updateSidebarUser() {
        if (sidebarUserLabel == null) return;
        String user = Session.isLoggedIn() ? Session.getDisplayName() : "Guest";
        sidebarUserLabel.setText(user == null || user.isBlank() ? "Guest" : user);
    }
}
