package com.example;

import java.io.IOException;
import java.net.URL;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

import javafx.animation.KeyFrame;
import javafx.animation.FadeTransition;
import javafx.animation.TranslateTransition;
import javafx.animation.ScaleTransition;
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
import javafx.scene.layout.Region;
import javafx.scene.control.Tooltip;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Button;
import javafx.util.Duration;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;
import javafx.scene.effect.GaussianBlur;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
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
    @FXML private Button notificationsButton;

    // Creative animated background
    @FXML private Canvas bgCanvas;
    private Timeline bgTimeline;
    private final List<Blob> blobs = new ArrayList<>();
    private final int BLOBS = 12;

    // Status bar
    @FXML private Label dbStatusLabel;
    @FXML private Label userStatusLabel;
    @FXML private Label timeLabel;

    // Pro sidebar
    @FXML private VBox sidebar;
    @FXML private Label sidebarUserLabel;
    private boolean sidebarCollapsed = false;
    private boolean sidebarHoverExpanded = false;

    // Background alerts updater
    private Timeline alertsTimeline;

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
            setupBackgroundFX();
            setupAlerts();
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
        if (view == null || contentArea == null) return;
        if (contentArea.getChildren().isEmpty()) {
            contentArea.getChildren().setAll(view);
            animateIn(view);
            return;
        }
        Node old = contentArea.getChildren().get(0);
        // Prevent duplicate add of the same node (already displayed or mid-transition)
        if (old == view) {
            return;
        }
        if (contentArea.getChildren().contains(view)) {
            // Incoming view already attached (from a prior transition); just remove the old one smoothly
            FadeTransition fadeOutOnly = new FadeTransition(Duration.millis(160), old);
            fadeOutOnly.setFromValue(1.0);
            fadeOutOnly.setToValue(0.0);
            fadeOutOnly.setOnFinished(ev -> contentArea.getChildren().remove(old));
            fadeOutOnly.play();
            return;
        }

        contentArea.getChildren().add(view);
        // Prepare incoming
        view.setOpacity(0);
        view.setTranslateY(12);

        FadeTransition fadeOut = new FadeTransition(Duration.millis(160), old);
        fadeOut.setFromValue(1.0);
        fadeOut.setToValue(0.0);

        FadeTransition fadeIn = new FadeTransition(Duration.millis(220), view);
        fadeIn.setFromValue(0.0);
        fadeIn.setToValue(1.0);

        TranslateTransition slideIn = new TranslateTransition(Duration.millis(220), view);
        slideIn.setFromY(12);
        slideIn.setToY(0);
        slideIn.setInterpolator(Interpolator.EASE_BOTH);

        fadeOut.setOnFinished(ev -> contentArea.getChildren().remove(old));
        fadeOut.play();
        fadeIn.play();
        slideIn.play();
    }

    private void animateIn(Node node) {
        if (node == null) return;
        node.setOpacity(0);
        node.setTranslateY(10);
        FadeTransition ft = new FadeTransition(Duration.millis(240), node);
        ft.setFromValue(0);
        ft.setToValue(1);
        TranslateTransition tt = new TranslateTransition(Duration.millis(240), node);
        tt.setFromY(10);
        tt.setToY(0);
        tt.setInterpolator(Interpolator.EASE_BOTH);
        ft.play();
        tt.play();
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
        // Persist choice
        com.example.util.AppConfig.get().setThemeMode(dark ? "dark" : "light");
        com.example.util.AppConfig.get().saveToUserConfig();
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
        try {
            App.loadLogin();
        } catch (Exception ex) {
            // Fallback if login view fails to load
            updateUserStatus();
            btnDashboard.setSelected(true);
            showDashboard(null);
        }
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

    // Alerts/Notifications (low stock + expiring soon)
    private void setupAlerts() {
        if (notificationsButton == null) return;
        notificationsButton.setOnAction(ev -> onNotifications());
        updateNotifications();
        // refresh every 60s
        alertsTimeline = new Timeline(
            new KeyFrame(Duration.seconds(60), ev -> updateNotifications())
        );
        alertsTimeline.setCycleCount(Timeline.INDEFINITE);
        alertsTimeline.play();
    }

    private void updateNotifications() {
        if (notificationsButton == null) return;
        int low = 0;
        int exp = 0;
        try (Connection c = Database.getConnection()) {
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT COUNT(*) FROM inventory_items WHERE reorder_level > 0 AND quantity <= reorder_level");
                 ResultSet rs = ps.executeQuery()) {
                if (rs.next()) low = rs.getInt(1);
            }
            // Prefer batches-based expiry window
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT COUNT(*) FROM item_batches WHERE expiry_date IS NOT NULL AND expiry_date BETWEEN CURDATE() AND DATE_ADD(CURDATE(), INTERVAL 30 DAY)");
                 ResultSet rs = ps.executeQuery()) {
                if (rs.next()) exp = rs.getInt(1);
            }
        } catch (Exception ignore) {
            // fallback to legacy per-item expiry if batches table not present
            try (Connection c2 = Database.getConnection();
                 PreparedStatement ps = c2.prepareStatement(
                     "SELECT COUNT(*) FROM inventory_items WHERE expiry IS NOT NULL AND expiry BETWEEN CURDATE() AND DATE_ADD(CURDATE(), INTERVAL 30 DAY)");
                 ResultSet rs = ps.executeQuery()) {
                if (rs.next()) exp = rs.getInt(1);
            } catch (Exception ignored) {}
        }
        int total = low + exp;
        notificationsButton.setText(total > 0 ? "Notifications (" + total + ")" : "Notifications");
        if (total > 0) {
            pulse(notificationsButton);
        }
    }

    private void pulse(Node node) {
        if (node == null) return;
        ScaleTransition st1 = new ScaleTransition(Duration.millis(110), node);
        st1.setFromX(1.0); st1.setFromY(1.0);
        st1.setToX(1.06); st1.setToY(1.06);
        ScaleTransition st2 = new ScaleTransition(Duration.millis(140), node);
        st2.setFromX(1.06); st2.setFromY(1.06);
        st2.setToX(1.0); st2.setToY(1.0);
        st1.setOnFinished(ev -> st2.play());
        st1.play();
    }

    @FXML
    private void onNotifications() {
        int low = 0;
        int exp = 0;
        StringBuilder msg = new StringBuilder();
        msg.append("Alerts summary:\n\n");

        try (Connection c = Database.getConnection()) {
            // Low stock list (top 10)
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT name, quantity, reorder_level FROM inventory_items " +
                    "WHERE reorder_level > 0 AND quantity <= reorder_level " +
                    "ORDER BY quantity ASC LIMIT 10");
                 ResultSet rs = ps.executeQuery()) {
                StringBuilder lowList = new StringBuilder();
                while (rs.next()) {
                    low++;
                    lowList.append("- ")
                           .append(rs.getString("name"))
                           .append(" | qty: ").append(rs.getInt("quantity"))
                           .append(" | reorder: ").append(rs.getInt("reorder_level"))
                           .append("\n");
                }
                msg.append("Low stock (").append(low).append(")\n");
                msg.append(lowList.length() == 0 ? "  None\n" : lowList.toString());
                msg.append("\n");
            }

            // Expiring batches (next 30 days, top 10)
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT i.name, b.batch_no, b.expiry_date, b.qty_on_hand " +
                    "FROM item_batches b JOIN items i ON b.item_id = i.id " +
                    "WHERE b.expiry_date IS NOT NULL AND b.expiry_date BETWEEN CURDATE() AND DATE_ADD(CURDATE(), INTERVAL 30 DAY) " +
                    "ORDER BY b.expiry_date ASC, b.qty_on_hand DESC LIMIT 10");
                 ResultSet rs = ps.executeQuery()) {
                StringBuilder expList = new StringBuilder();
                while (rs.next()) {
                    exp++;
                    expList.append("- ")
                           .append(rs.getString(1)) // item name
                           .append(" | batch: ").append(rs.getString(2))
                           .append(" | exp: ").append(rs.getDate(3))
                           .append(" | qty: ").append(rs.getInt(4))
                           .append("\n");
                }
                msg.append("Expiring soon (").append(exp).append(")\n");
                msg.append(expList.length() == 0 ? "  None\n" : expList.toString());
            }
        } catch (Exception ex) {
            msg.append("\nError while fetching alerts: ").append(ex.getMessage());
        }

        javafx.scene.control.Alert a = new javafx.scene.control.Alert(javafx.scene.control.Alert.AlertType.INFORMATION);
        a.setTitle("Notifications");
        a.setHeaderText("Low stock: " + low + " | Expiring soon: " + exp);
        a.setContentText(msg.toString());
        a.getDialogPane().setMinHeight(Region.USE_PREF_SIZE);
        a.showAndWait();
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

    // ---------- Animated background (bokeh blobs) ----------

    private void setupBackgroundFX() {
        if (bgCanvas == null || contentArea == null) return;

        // Size to content area
        bgCanvas.widthProperty().bind(contentArea.widthProperty());
        bgCanvas.heightProperty().bind(contentArea.heightProperty());
        bgCanvas.setMouseTransparent(true);
        bgCanvas.setOpacity(0.8);
        bgCanvas.setEffect(new GaussianBlur(18));

        // Create blobs once
        if (blobs.isEmpty()) {
            initBlobs();
        }

        // Redraw on size changes
        bgCanvas.widthProperty().addListener((obs, ov, nv) -> drawBackground());
        bgCanvas.heightProperty().addListener((obs, ov, nv) -> drawBackground());

        // Start animation timeline (60 FPS-ish)
        if (bgTimeline != null) {
            bgTimeline.stop();
        }
        bgTimeline = new Timeline(new KeyFrame(Duration.millis(16), ev -> {
            stepBlobs();
            drawBackground();
        }));
        bgTimeline.setCycleCount(Timeline.INDEFINITE);
        bgTimeline.play();
    }

    private void initBlobs() {
        Random rnd = new Random();
        double w = Math.max(1, bgCanvas.getWidth());
        double h = Math.max(1, bgCanvas.getHeight());

        boolean dark = isDarkTheme();
        Color[] palette = dark
                ? new Color[]{
                    Color.web("#60a5fa88"), // blue-400
                    Color.web("#22d3ee88"), // cyan-400
                    Color.web("#a78bfa88"), // violet-400
                    Color.web("#34d39988")  // emerald-400
                }
                : new Color[]{
                    Color.web("#6366f177"), // indigo-500
                    Color.web("#22c55e66"), // green-500
                    Color.web("#14b8a677"), // teal-500
                    Color.web("#f59e0b55")  // amber-500
                };

        blobs.clear();
        for (int i = 0; i < BLOBS; i++) {
            double r = 80 + rnd.nextDouble() * 160;
            double x = rnd.nextDouble() * (w + 2 * r) - r;
            double y = rnd.nextDouble() * (h + 2 * r) - r;
            double speed = 0.12 + rnd.nextDouble() * 0.28;
            double angle = rnd.nextDouble() * Math.PI * 2;
            double dx = Math.cos(angle) * speed;
            double dy = Math.sin(angle) * speed;
            Color c = palette[rnd.nextInt(palette.length)];
            blobs.add(new Blob(x, y, r, dx, dy, c));
        }
    }

    private void stepBlobs() {
        double w = Math.max(1, bgCanvas.getWidth());
        double h = Math.max(1, bgCanvas.getHeight());
        for (Blob b : blobs) {
            b.x += b.dx;
            b.y += b.dy;
            // Soft wrap (teleport) for a dreamy drift instead of bounce
            if (b.x < -b.r) b.x = w + b.r * 0.5;
            if (b.x > w + b.r) b.x = -b.r * 0.5;
            if (b.y < -b.r) b.y = h + b.r * 0.5;
            if (b.y > h + b.r) b.y = -b.r * 0.5;
        }
    }

    private void drawBackground() {
        if (bgCanvas == null) return;
        GraphicsContext g = bgCanvas.getGraphicsContext2D();
        double w = Math.max(1, bgCanvas.getWidth());
        double h = Math.max(1, bgCanvas.getHeight());

        // Clear
        g.clearRect(0, 0, w, h);

        boolean dark = isDarkTheme();
        // Soft vignette to add depth
        g.setGlobalAlpha(dark ? 0.12 : 0.08);
        g.setFill(dark ? Color.web("#0ea5e9") : Color.web("#6366f1")); // cyan-500 / indigo-500
        g.fillOval(-w * 0.2, -h * 0.2, w * 0.8, h * 0.8);
        g.setFill(dark ? Color.web("#a78bfa") : Color.web("#22c55e")); // violet-400 / green-500
        g.fillOval(w * 0.5, -h * 0.3, w * 0.8, h * 0.8);
        g.setFill(dark ? Color.web("#22d3ee") : Color.web("#f59e0b")); // cyan-400 / amber-500
        g.fillOval(w * 0.1, h * 0.4, w * 0.9, h * 0.9);

        // Floating blobs
        g.setGlobalAlpha(dark ? 0.18 : 0.14);
        for (Blob b : blobs) {
            g.setFill(b.color);
            g.fillOval(b.x - b.r, b.y - b.r, b.r * 2, b.r * 2);
        }

        // Reset alpha
        g.setGlobalAlpha(1.0);
    }

    private boolean isDarkTheme() {
        Scene scene = App.getPrimaryScene();
        return scene != null && scene.getRoot().getStyleClass().contains("dark");
    }

    private static class Blob {
        double x, y, r, dx, dy;
        Color color;
        Blob(double x, double y, double r, double dx, double dy, Color color) {
            this.x = x; this.y = y; this.r = r; this.dx = dx; this.dy = dy; this.color = color;
        }
    }
}
