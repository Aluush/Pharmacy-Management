package com.example;

import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.TextFormatter;

import java.time.LocalDate;

public class InventoryItemDialogController {

    @FXML private TextField nameField;
    @FXML private ComboBox<String> categoryBox;
    @FXML private TextField batchField;
    @FXML private TextField qtyField;
    @FXML private TextField priceField;
    @FXML private DatePicker expiryPicker;
    @FXML private TextField supplierField;
    @FXML private TextField reorderField;
    @FXML private Label statusLabel;

    @FXML
    private void initialize() {
        if (qtyField != null) qtyField.setTextFormatter(integerFormatter());
        if (reorderField != null) reorderField.setTextFormatter(integerFormatter());
        if (priceField != null) priceField.setTextFormatter(decimalFormatter());
    }

    public void setCategories(ObservableList<String> categories) {
        if (categoryBox != null) {
            categoryBox.setItems(categories);
        }
    }

    public void setItem(InventoryController.InventoryItem item) {
        if (item == null) return;
        if (nameField != null) nameField.setText(item.getName());
        if (categoryBox != null) categoryBox.setValue(item.getCategory());
        if (batchField != null) batchField.setText(item.getBatch());
        if (qtyField != null) qtyField.setText(String.valueOf(item.getQuantity()));
        if (priceField != null) priceField.setText(String.valueOf(item.getPrice()));
        if (supplierField != null) supplierField.setText(item.getSupplier());
        if (reorderField != null) reorderField.setText(String.valueOf(item.getReorderLevel()));
        if (expiryPicker != null) {
            try {
                if (item.getExpiry() != null && !item.getExpiry().isBlank()) {
                    expiryPicker.setValue(LocalDate.parse(item.getExpiry()));
                } else {
                    expiryPicker.setValue(null);
                }
            } catch (Exception ignore) {
                expiryPicker.setValue(null);
            }
        }
    }

    // Getters for dialog result consumption
    public String getNameValue() { return safe(nameField != null ? nameField.getText() : null); }
    public String getCategoryValue() { return categoryBox != null ? categoryBox.getValue() : null; }
    public String getBatchValue() { return safe(batchField != null ? batchField.getText() : null); }
    public Integer getQtyValue() { return parseInt(qtyField != null ? qtyField.getText() : null); }
    public Double getPriceValue() { return parseDouble(priceField != null ? priceField.getText() : null); }
    public LocalDate getExpiryValue() { return expiryPicker != null ? expiryPicker.getValue() : null; }
    public String getSupplierValue() { return safe(supplierField != null ? supplierField.getText() : null); }
    public Integer getReorderValue() { return parseInt(reorderField != null ? reorderField.getText() : null); }

    public void setStatus(String msg, boolean error) {
        if (statusLabel != null) {
            statusLabel.setText(msg == null ? "" : msg);
            statusLabel.setStyle(error ? "-fx-text-fill: #ef4444;" : "-fx-text-fill: -color-text-muted;");
        }
    }

    // Helpers
    private static String safe(String s) { return s == null ? "" : s.trim(); }
    private static Integer parseInt(String s) {
        try {
            if (s == null || s.isBlank()) return null;
            return Integer.parseInt(s.trim());
        } catch (Exception e) { return null; }
    }
    private static Double parseDouble(String s) {
        try {
            if (s == null || s.isBlank()) return null;
            return Double.parseDouble(s.trim());
        } catch (Exception e) { return null; }
    }

    private static TextFormatter<String> integerFormatter() {
        return new TextFormatter<>(change ->
            change.getControlNewText().matches("\\d*") ? change : null
        );
    }
    private static TextFormatter<String> decimalFormatter() {
        return new TextFormatter<>(change ->
            change.getControlNewText().matches("\\d*(\\.?\\d{0,2})?") ? change : null
        );
    }
}
