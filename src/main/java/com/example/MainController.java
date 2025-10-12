package com.example;

import java.io.IOException;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.StackPane;

public class MainController {

    @FXML private StackPane contentArea;
    @FXML private ToggleButton btnDashboard;
    @FXML private ToggleButton btnInventory;
    @FXML private ToggleButton btnSales;
    @FXML private ToggleButton btnSettings;
    @FXML private TextField searchField;

    private final Map<String, Parent> viewCache = new HashMap<>();
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
}
