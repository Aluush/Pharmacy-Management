package com.example;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.Parent;
import javafx.scene.control.Alert;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import com.example.util.AppConfig;

public class App extends Application {
    private static Scene primaryScene;
    @Override
    public void start(Stage stage) throws Exception {
        Scene scene = new Scene(new StackPane(), 1200, 800);
        scene.getStylesheets().add(App.class.getResource("styles.css").toExternalForm());
        scene.getStylesheets().add(App.class.getResource("styles-modern.css").toExternalForm());
        scene.getStylesheets().add(App.class.getResource("theme.css").toExternalForm());
        primaryScene = scene;
        // Apply stored theme to initial root
        Parent initialRoot = scene.getRoot();
        if ("dark".equalsIgnoreCase(AppConfig.get().getThemeMode())) {
            if (!initialRoot.getStyleClass().contains("dark")) {
                initialRoot.getStyleClass().add("dark");
            }
        }

        try {
            Database.bootstrap();
        } catch (Exception ex) {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("Database Error");
            alert.setHeaderText("Failed to connect to MySQL");
            alert.setContentText(ex.getMessage());
            alert.showAndWait();
        }

        // Load login first
        loadLogin();

        stage.setTitle(AppConfig.get().getAppTitle());
        stage.setScene(scene);
        stage.show();
    }

    public static Scene getPrimaryScene() { 
        return primaryScene; 
    }

    public static void loadLogin() throws Exception {
        FXMLLoader loader = new FXMLLoader(App.class.getResource("login-view.fxml"));
        Parent root = loader.load();
        setRoot(root);
    }

    public static void loadMain() throws Exception {
        FXMLLoader loader = new FXMLLoader(App.class.getResource("main-view.fxml"));
        Parent root = loader.load();
        setRoot(root);
    }

    private static void setRoot(Parent root) {
        if (primaryScene == null || root == null) return;
        // Ensure base style class
        if (!root.getStyleClass().contains("root")) {
            root.getStyleClass().add("root");
        }
        // Preserve/apply theme
        boolean shouldDark = "dark".equalsIgnoreCase(AppConfig.get().getThemeMode());
        Parent current = primaryScene.getRoot();
        if (!shouldDark && current != null) {
            shouldDark = current.getStyleClass().contains("dark");
        }
        if (shouldDark && !root.getStyleClass().contains("dark")) {
            root.getStyleClass().add("dark");
        } else if (!shouldDark) {
            root.getStyleClass().remove("dark");
        }
        primaryScene.setRoot(root);
    }

    public static void main(String[] args) {
        launch();
    }
}
