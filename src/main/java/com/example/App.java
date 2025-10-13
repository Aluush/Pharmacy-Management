package com.example;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.stage.Stage;

public class App extends Application {
    private static Scene primaryScene;
    @Override
    public void start(Stage stage) throws Exception {
        FXMLLoader fxmlLoader = new FXMLLoader(App.class.getResource("main-view.fxml"));
        Scene scene = new Scene(fxmlLoader.load(), 1200, 800);
        scene.getStylesheets().add(App.class.getResource("styles.css").toExternalForm());
        // Ensure root class for looked-up colors used by sidebar/theme
        if (!scene.getRoot().getStyleClass().contains("root")) {
            scene.getRoot().getStyleClass().add("root");
        }
        primaryScene = scene;
        try {
            Database.bootstrap();
        } catch (Exception ex) {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("Database Error");
            alert.setHeaderText("Failed to connect to MySQL");
            alert.setContentText(ex.getMessage());
            alert.showAndWait();
        }
        stage.setTitle("PharmaPro - Pharmacy Management");
        stage.setScene(scene);
        stage.show();
    }

    public static Scene getPrimaryScene() { 
        return primaryScene; 
    }

    public static void main(String[] args) {
        launch();
    }
}
