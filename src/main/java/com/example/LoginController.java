package com.example;

import com.example.repository.UserRepository;
import com.example.util.PasswordUtil;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;

public class LoginController {

    @FXML private TextField usernameField;
    @FXML private PasswordField passwordField;
    @FXML private Label statusLabel;

    private final UserRepository userRepo = new UserRepository();

    @FXML
    private void initialize() {
        // Prefill for demo
        if (usernameField != null && (usernameField.getText() == null || usernameField.getText().isBlank())) {
            usernameField.setText("admin");
        }
    }

    @FXML
    private void onLogin(ActionEvent e) {
        String username = usernameField == null ? "" : usernameField.getText().trim();
        String password = passwordField == null ? "" : passwordField.getText();

        if (username.isBlank() || password.isBlank()) {
            setStatus("Enter username and password");
            return;
        }

        setStatus("Signing in...");
        Platform.runLater(() -> {
            UserRepository.UserRecord user = userRepo.findByUsername(username);
            if (user == null) {
                setStatus("Invalid credentials");
                return;
            }
            if (!user.active) {
                setStatus("Account is disabled");
                return;
            }
            boolean ok = PasswordUtil.verifyPassword(password.toCharArray(), user.passwordHash);
            if (!ok) {
                setStatus("Invalid credentials");
                return;
            }

            // Success
            Session.setUser(user.username, user.displayName);
            setStatus("");
            try {
                App.loadMain();
            } catch (Exception ex) {
                setStatus("Failed to load main UI: " + ex.getMessage());
            }
        });
    }

    private void setStatus(String msg) {
        if (statusLabel != null) {
            statusLabel.setText(msg == null ? "" : msg);
        }
    }
}
