package com.example;

import javafx.beans.binding.Bindings;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import javafx.fxml.FXML;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.cell.PropertyValueFactory;

import java.time.LocalDate;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;

public class InventoryController {

    // Top Bar
    @FXML private TextField filterField;

    // Form inputs
    @FXML private TextField nameField;
    @FXML private ComboBox<String> categoryBox;
    @FXML private TextField batchField;
    @FXML private TextField qtyField;
    @FXML private TextField priceField;
    @FXML private DatePicker expiryPicker;
    @FXML private TextField supplierField;
    @FXML private TextField reorderField;
    @FXML private Label formStatus;

    // Table + columns
    @FXML private TableView<InventoryItem> table;
    @FXML private TableColumn<InventoryItem, Integer> colId;
    @FXML private TableColumn<InventoryItem, String> colName;
    @FXML private TableColumn<InventoryItem, String> colCategory;
    @FXML private TableColumn<InventoryItem, String> colBatch;
    @FXML private TableColumn<InventoryItem, Integer> colQty;
    @FXML private TableColumn<InventoryItem, String> colPrice;
    @FXML private TableColumn<InventoryItem, String> colExpiry;
    @FXML private TableColumn<InventoryItem, String> colSupplier;
    @FXML private TableColumn<InventoryItem, Integer> colReorder;

    @FXML private Label totalItems;

    private final ObservableList<InventoryItem> masterData = FXCollections.observableArrayList();
    private FilteredList<InventoryItem> filtered;
    private SortedList<InventoryItem> sorted;

    private final AtomicInteger idSequence = new AtomicInteger(1000);

    @FXML
    private void initialize() {
        // Categories
        categoryBox.setItems(FXCollections.observableArrayList(
            "Analgesics", "Antibiotics", "Vitamins", "Dermatology", "Cardiology", "Antipyretics", "Others"
        ));

        // Table columns
        colId.setCellValueFactory(new PropertyValueFactory<>("id"));
        colName.setCellValueFactory(new PropertyValueFactory<>("name"));
        colCategory.setCellValueFactory(new PropertyValueFactory<>("category"));
        colBatch.setCellValueFactory(new PropertyValueFactory<>("batch"));
        colQty.setCellValueFactory(new PropertyValueFactory<>("quantity"));
        colPrice.setCellValueFactory(new PropertyValueFactory<>("priceFmt"));
        colExpiry.setCellValueFactory(new PropertyValueFactory<>("expiry"));
        colSupplier.setCellValueFactory(new PropertyValueFactory<>("supplier"));
        colReorder.setCellValueFactory(new PropertyValueFactory<>("reorderLevel"));

        // Sample data
        masterData.addAll(
            new InventoryItem(idSequence.getAndIncrement(), "Paracetamol 500mg", "Analgesics", "B-1022", 120, 3.20, "2026-02-01", "HealthCo", 20),
            new InventoryItem(idSequence.getAndIncrement(), "Amoxicillin 500mg", "Antibiotics", "AMX-221", 80, 6.90, "2026-06-15", "PharmaSupply", 15),
            new InventoryItem(idSequence.getAndIncrement(), "Vitamin C 1000mg", "Vitamins", "VC-019", 200, 4.50, "2027-01-30", "NutriLabs", 30),
            new InventoryItem(idSequence.getAndIncrement(), "Ibuprofen 200mg", "Analgesics", "IB-551", 60, 2.80, "2026-09-10", "MediPlus", 10),
            new InventoryItem(idSequence.getAndIncrement(), "Hydrocortisone Cream 1%", "Dermatology", "DERM-44", 35, 5.75, "2025-12-12", "SkinCare Ltd", 8)
        );

        // Filtering + sorting
        filtered = new FilteredList<>(masterData, item -> true);
        sorted = new SortedList<>(filtered);
        sorted.comparatorProperty().bind(table.comparatorProperty());
        table.setItems(sorted);

        // Filter binding
        filterField.textProperty().addListener((obs, old, val) -> {
            Predicate<InventoryItem> predicate = item -> {
                if (val == null || val.isBlank()) return true;
                String q = val.toLowerCase();
                return (item.getName() != null && item.getName().toLowerCase().contains(q))
                    || (item.getCategory() != null && item.getCategory().toLowerCase().contains(q))
                    || (item.getBatch() != null && item.getBatch().toLowerCase().contains(q))
                    || (item.getSupplier() != null && item.getSupplier().toLowerCase().contains(q));
            };
            filtered.setPredicate(predicate);
            updateTotals();
        });

        // Update totals when list changes
        totalItems.textProperty().bind(Bindings.size(filtered).asString());

        updateTotals();
    }

    // Actions

    @FXML
    private void onSave() {
        try {
            String name = trimOrNull(nameField.getText());
            String category = categoryBox.getValue();
            String batch = trimOrNull(batchField.getText());
            Integer qty = parseIntSafe(qtyField.getText());
            Double price = parseDoubleSafe(priceField.getText());
            LocalDate expiry = expiryPicker.getValue();
            String supplier = trimOrNull(supplierField.getText());
            Integer reorder = parseIntSafe(reorderField.getText());

            if (name == null || name.isBlank()) {
                setStatus("Name is required.", true);
                return;
            }
            if (category == null || category.isBlank()) {
                setStatus("Category is required.", true);
                return;
            }
            if (qty == null || qty < 0) {
                setStatus("Quantity must be a non-negative integer.", true);
                return;
            }
            if (price == null || price < 0) {
                setStatus("Price must be a non-negative number.", true);
                return;
            }
            String expiryStr = expiry != null ? expiry.toString() : "";

            InventoryItem selected = table.getSelectionModel().getSelectedItem();
            if (selected != null && Objects.equals(selected.getName(), name) && Objects.equals(selected.getBatch(), batch)) {
                // Update existing (simple heuristic)
                selected.setCategory(category);
                selected.setQuantity(qty);
                selected.setPrice(price);
                selected.setExpiry(expiryStr);
                selected.setSupplier(supplier);
                selected.setReorderLevel(reorder != null ? reorder : 0);
                table.refresh();
                setStatus("Item updated.", false);
            } else {
                InventoryItem item = new InventoryItem(
                    idSequence.getAndIncrement(),
                    name,
                    category,
                    batch != null ? batch : "",
                    qty,
                    price,
                    expiryStr,
                    supplier != null ? supplier : "",
                    reorder != null ? reorder : 0
                );
                masterData.add(item);
                setStatus("Item saved.", false);
            }

            onReset(); // clear form
            updateTotals();
        } catch (Exception ex) {
            setStatus("Save failed: " + ex.getMessage(), true);
        }
    }

    @FXML
    private void onEdit() {
        InventoryItem sel = table.getSelectionModel().getSelectedItem();
        if (sel == null) {
            setStatus("Select an item to edit.", true);
            return;
        }
        nameField.setText(sel.getName());
        categoryBox.setValue(sel.getCategory());
        batchField.setText(sel.getBatch());
        qtyField.setText(String.valueOf(sel.getQuantity()));
        priceField.setText(String.valueOf(sel.getPrice()));
        supplierField.setText(sel.getSupplier());
        reorderField.setText(String.valueOf(sel.getReorderLevel()));
        if (sel.getExpiry() != null && !sel.getExpiry().isBlank()) {
            try {
                expiryPicker.setValue(LocalDate.parse(sel.getExpiry()));
            } catch (Exception ignore) {
                expiryPicker.setValue(null);
            }
        } else {
            expiryPicker.setValue(null);
        }
        setStatus("Loaded item for editing. Saving will update.", false);
    }

    @FXML
    private void onDelete() {
        InventoryItem sel = table.getSelectionModel().getSelectedItem();
        if (sel == null) {
            setStatus("Select an item to delete.", true);
            return;
        }
        masterData.remove(sel);
        setStatus("Item deleted.", false);
        updateTotals();
    }

    @FXML
    private void onReset() {
        nameField.clear();
        categoryBox.setValue(null);
        batchField.clear();
        qtyField.clear();
        priceField.clear();
        expiryPicker.setValue(null);
        supplierField.clear();
        reorderField.clear();
    }

    @FXML
    private void onExport() {
        // Stub for demo: integrate CSV/Excel later
        setStatus("Exported " + masterData.size() + " items (demo).", false);
    }

    @FXML
    private void onImport() {
        // Stub for demo: integrate file chooser + parser later
        setStatus("Import complete (demo).", false);
    }

    private void updateTotals() {
        // totalItems is already bound to filtered size; nothing else needed here for now.
    }

    private void setStatus(String msg, boolean error) {
        formStatus.setText(msg);
        formStatus.setStyle(error ? "-fx-text-fill: #ef4444;" : "-fx-text-fill: -color-text-muted;");
    }

    private static String trimOrNull(String s) {
        return s == null ? null : s.trim();
    }

    private static Integer parseIntSafe(String s) {
        try {
            if (s == null || s.isBlank()) return null;
            return Integer.parseInt(s.trim());
        } catch (Exception e) {
            return null;
        }
    }

    private static Double parseDoubleSafe(String s) {
        try {
            if (s == null || s.isBlank()) return null;
            return Double.parseDouble(s.trim());
        } catch (Exception e) {
            return null;
        }
    }

    // Data model
    public static class InventoryItem {
        private int id;
        private String name;
        private String category;
        private String batch;
        private int quantity;
        private double price;
        private String expiry; // ISO yyyy-MM-dd
        private String supplier;
        private int reorderLevel;

        public InventoryItem(int id, String name, String category, String batch, int quantity,
                             double price, String expiry, String supplier, int reorderLevel) {
            this.id = id;
            this.name = name;
            this.category = category;
            this.batch = batch;
            this.quantity = quantity;
            this.price = price;
            this.expiry = expiry;
            this.supplier = supplier;
            this.reorderLevel = reorderLevel;
        }

        public int getId() { return id; }
        public String getName() { return name; }
        public String getCategory() { return category; }
        public String getBatch() { return batch; }
        public int getQuantity() { return quantity; }
        public double getPrice() { return price; }
        public String getExpiry() { return expiry; }
        public String getSupplier() { return supplier; }
        public int getReorderLevel() { return reorderLevel; }

        public void setCategory(String category) { this.category = category; }
        public void setQuantity(int quantity) { this.quantity = quantity; }
        public void setPrice(double price) { this.price = price; }
        public void setExpiry(String expiry) { this.expiry = expiry; }
        public void setSupplier(String supplier) { this.supplier = supplier; }
        public void setReorderLevel(int reorderLevel) { this.reorderLevel = reorderLevel; }

        public String getPriceFmt() {
            return String.format("$%.2f", price);
        }
    }
}
