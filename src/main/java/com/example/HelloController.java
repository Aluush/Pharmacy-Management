package com.example;

import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;

public class HelloController {

    @FXML private TextField firstNameField;
    @FXML private TextField lastNameField;
    @FXML private TextField emailField;
    @FXML private TextField phoneField;
    @FXML private TextField addressField;
    @FXML private TextField cityField;

    @FXML private Label statusLabel;

    @FXML
    protected void onClearClick() {
        firstNameField.clear();
        lastNameField.clear();
        emailField.clear();
        phoneField.clear();
        addressField.clear();
        cityField.clear();
        statusLabel.setText("");
    }

    @FXML
    protected void onSubmitClick() {
        String first = safe(firstNameField.getText());
        String last = safe(lastNameField.getText());
        String email = safe(emailField.getText());
        String phone = safe(phoneField.getText());
        String address = safe(addressField.getText());
        String city = safe(cityField.getText());

        // Simple example "validation"
        if (first.isEmpty() || last.isEmpty()) {
            statusLabel.setText("First and Last name are required.");
            return;
        }

        statusLabel.setText(String.format(
            "Submitted: %s %s | %s | %s | %s, %s",
            first, last, email, phone, address, city
        ));
    }

    private String safe(String s) {
        return s == null ? "" : s.trim();
    }
}
