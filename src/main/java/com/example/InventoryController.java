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
import javafx.scene.control.TableCell;
import javafx.scene.control.TableRow;
import javafx.scene.control.TextFormatter;
import javafx.fxml.FXMLLoader;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.DialogPane;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.sql.*;
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
        // Categories loaded on-demand in the popup dialog

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

        // Load data from database
        loadInventoryFromDb();

        // Filtering + sorting
        filtered = new FilteredList<>(masterData, item -> true);
        sorted = new SortedList<>(filtered);
        sorted.comparatorProperty().bind(table.comparatorProperty());
        table.setItems(sorted);

        // Table UX: placeholder, row highlighting, quantity badge
        table.setPlaceholder(new Label("No inventory items."));
        table.setRowFactory(tv -> new TableRow<InventoryItem>() {
            @Override
            protected void updateItem(InventoryItem item, boolean empty) {
                super.updateItem(item, empty);
                getStyleClass().removeAll("row-warning", "row-danger");
                if (empty || item == null) return;
                boolean low = item.getReorderLevel() > 0 && item.getQuantity() <= item.getReorderLevel();
                boolean expSoon = false;
                if (item.getExpiry() != null && !item.getExpiry().isBlank()) {
                    try {
                        LocalDate exp = LocalDate.parse(item.getExpiry());
                        long days = ChronoUnit.DAYS.between(LocalDate.now(), exp);
                        expSoon = days >= 0 && days <= 30;
                    } catch (Exception ignore) { /* ignore */ }
                }
                if (low) {
                    getStyleClass().add("row-danger");
                } else if (expSoon) {
                    getStyleClass().add("row-warning");
                }
            }
        });
        colQty.setCellFactory(col -> new TableCell<InventoryItem, Integer>() {
            private final Label badge = new Label();
            @Override
            protected void updateItem(Integer val, boolean empty) {
                super.updateItem(val, empty);
                if (empty || val == null) {
                    setText(null);
                    setGraphic(null);
                    return;
                }
                InventoryItem item = getTableView().getItems().get(getIndex());
                boolean low = item != null && item.getReorderLevel() > 0 && item.getQuantity() <= item.getReorderLevel();
                badge.setText(val.toString());
                badge.getStyleClass().clear();
                badge.getStyleClass().add("badge");
                if (low) badge.getStyleClass().add("badge-danger");
                setText(null);
                setGraphic(badge);
            }
        });

        // Numeric input formatters
        if (qtyField != null) qtyField.setTextFormatter(integerFormatter());
        if (reorderField != null) reorderField.setTextFormatter(integerFormatter());
        if (priceField != null) priceField.setTextFormatter(decimalFormatter());

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
            int reorderVal = reorder != null ? reorder : 0;
            String batchStr = batch != null ? batch : "";
            String supplierStr = supplier != null ? supplier : "";
            if (selected != null) {
                // Update existing using DB
                updateInventoryItem(selected.getId(), name, category, batchStr, qty, price, expiryStr, supplierStr, reorderVal);
                selected.setName(name);
                selected.setBatch(batchStr);
                selected.setCategory(category);
                selected.setQuantity(qty);
                selected.setPrice(price);
                selected.setExpiry(expiryStr);
                selected.setSupplier(supplierStr);
                selected.setReorderLevel(reorderVal);
                table.refresh();
                setStatus("Item updated.", false);
                ensureCategoryInBox(category);
            } else {
                int newId = insertInventoryItem(name, category, batchStr, qty, price, expiryStr, supplierStr, reorderVal);
                InventoryItem item = new InventoryItem(
                    newId,
                    name,
                    category,
                    batchStr,
                    qty,
                    price,
                    expiryStr,
                    supplierStr,
                    reorderVal
                );
                masterData.add(item);
                setStatus("Item saved.", false);
                ensureCategoryInBox(category);
            }

            onReset(); // clear form
            updateTotals();
        } catch (Exception ex) {
            setStatus("Save failed: " + ex.getMessage(), true);
        }
    }

    @FXML
    private void onAddNew() {
        openItemDialog(null);
    }

    @FXML
    private void onEdit() {
        InventoryItem sel = table.getSelectionModel().getSelectedItem();
        if (sel == null) {
            setStatus("Select an item to edit.", true);
            return;
        }
        openItemDialog(sel);
    }

    private void openItemDialog(InventoryItem existing) {
        try {
            FXMLLoader loader = new FXMLLoader(App.class.getResource("inventory-item-dialog.fxml"));
            DialogPane pane = loader.load();
            InventoryItemDialogController dc = loader.getController();
            dc.setCategories(loadCategoriesFromDb());
            if (existing != null) {
                dc.setItem(existing);
                pane.setHeaderText("Edit Item");
            } else {
                pane.setHeaderText("Add Item");
            }
            Dialog<ButtonType> dialog = new Dialog<>();
            dialog.setDialogPane(pane);
            dialog.setTitle(existing != null ? "Edit Item" : "Add Item");
            if (App.getPrimaryScene() != null && App.getPrimaryScene().getWindow() != null) {
                dialog.initOwner(App.getPrimaryScene().getWindow());
            }

            var result = dialog.showAndWait();
            if (result.isPresent() && result.get().getButtonData() == ButtonBar.ButtonData.OK_DONE) {
                String name = trimOrNull(dc.getNameValue());
                String category = dc.getCategoryValue();
                String batch = trimOrNull(dc.getBatchValue());
                Integer qty = dc.getQtyValue();
                Double price = dc.getPriceValue();
                LocalDate expiry = dc.getExpiryValue();
                String supplier = trimOrNull(dc.getSupplierValue());
                Integer reorder = dc.getReorderValue();

                if (name == null || name.isBlank()) { dc.setStatus("Name is required.", true); return; }
                if (category == null || category.isBlank()) { dc.setStatus("Category is required.", true); return; }
                if (qty == null || qty < 0) { dc.setStatus("Quantity must be non-negative.", true); return; }
                if (price == null || price < 0) { dc.setStatus("Price must be non-negative.", true); return; }

                String expiryStr = (expiry != null) ? expiry.toString() : "";
                int reorderVal = reorder != null ? reorder : 0;
                String batchStr = batch != null ? batch : "";
                String supplierStr = supplier != null ? supplier : "";

                if (existing != null) {
                    updateInventoryItem(existing.getId(), name, category, batchStr, qty, price, expiryStr, supplierStr, reorderVal);
                    existing.setName(name);
                    existing.setBatch(batchStr);
                    existing.setCategory(category);
                    existing.setQuantity(qty);
                    existing.setPrice(price);
                    existing.setExpiry(expiryStr);
                    existing.setSupplier(supplierStr);
                    existing.setReorderLevel(reorderVal);
                    table.refresh();
                    setStatus("Item updated.", false);
                } else {
                    int newId = insertInventoryItem(name, category, batchStr, qty, price, expiryStr, supplierStr, reorderVal);
                    InventoryItem item = new InventoryItem(newId, name, category, batchStr, qty, price, expiryStr, supplierStr, reorderVal);
                    masterData.add(0, item);
                    setStatus("Item saved.", false);
                }
            }
        } catch (Exception ex) {
            setStatus("Operation failed: " + ex.getMessage(), true);
        }
    }

    @FXML
    private void onDelete() {
        InventoryItem sel = table.getSelectionModel().getSelectedItem();
        if (sel == null) {
            setStatus("Select an item to delete.", true);
            return;
        }
        deleteInventoryItem(sel.getId());
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

    // Database operations
    private void loadInventoryFromDb() {
        masterData.clear();
        String sql = "SELECT id, name, category, batch, quantity, price, expiry, supplier, reorder_level FROM inventory_items ORDER BY id DESC";
        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                int id = rs.getInt("id");
                String name = rs.getString("name");
                String category = rs.getString("category");
                String batch = rs.getString("batch");
                int qty = rs.getInt("quantity");
                double price = rs.getDouble("price");
                Date exp = rs.getDate("expiry");
                String expiry = exp != null ? exp.toString() : "";
                String supplier = rs.getString("supplier");
                int reorder = rs.getInt("reorder_level");
                masterData.add(new InventoryItem(id, name, category, batch != null ? batch : "", qty, price, expiry, supplier != null ? supplier : "", reorder));
            }
        } catch (Exception ex) {
            setStatus("Failed to load inventory: " + ex.getMessage(), true);
        }
    }

    private int insertInventoryItem(String name, String category, String batch, int qty, double price, String expiry, String supplier, int reorder) throws SQLException {
        String sql = "INSERT INTO inventory_items (name, category, batch, quantity, price, expiry, supplier, reorder_level) VALUES (?,?,?,?,?,?,?,?)";
        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, name);
            ps.setString(2, category);
            ps.setString(3, batch);
            ps.setInt(4, qty);
            ps.setDouble(5, price);
            if (expiry != null && !expiry.isBlank()) {
                ps.setDate(6, Date.valueOf(expiry));
            } else {
                ps.setNull(6, Types.DATE);
            }
            ps.setString(7, supplier);
            ps.setInt(8, reorder);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) return keys.getInt(1);
            }
        }
        throw new SQLException("Failed to obtain generated key for inventory item");
    }

    private void updateInventoryItem(int id, String name, String category, String batch, int qty, double price, String expiry, String supplier, int reorder) throws SQLException {
        String sql = "UPDATE inventory_items SET name=?, category=?, batch=?, quantity=?, price=?, expiry=?, supplier=?, reorder_level=? WHERE id=?";
        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, name);
            ps.setString(2, category);
            ps.setString(3, batch);
            ps.setInt(4, qty);
            ps.setDouble(5, price);
            if (expiry != null && !expiry.isBlank()) {
                ps.setDate(6, Date.valueOf(expiry));
            } else {
                ps.setNull(6, Types.DATE);
            }
            ps.setString(7, supplier);
            ps.setInt(8, reorder);
            ps.setInt(9, id);
            ps.executeUpdate();
        }
    }

    private void deleteInventoryItem(int id) {
        String sql = "DELETE FROM inventory_items WHERE id=?";
        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, id);
            ps.executeUpdate();
        } catch (Exception ex) {
            setStatus("Delete failed: " + ex.getMessage(), true);
        }
    }

    // Categories from DB
    private ObservableList<String> loadCategoriesFromDb() {
        ObservableList<String> items = FXCollections.observableArrayList();
        String sql = "SELECT DISTINCT COALESCE(category,'Uncategorized') AS cat " +
                     "FROM inventory_items ORDER BY cat";
        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                items.add(rs.getString("cat"));
            }
        } catch (Exception ex) {
            // fallback to empty list if failure
        }
        return items;
    }

    private void ensureCategoryInBox(String category) {
        if (category == null || category.isBlank() || categoryBox == null) return;
        if (!categoryBox.getItems().contains(category)) {
            categoryBox.getItems().add(category);
        }
    }

    // Text formatters
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

    // Focus helpers for global search routing
    public void focusFilterWith(String q) {
        if (filterField == null) return;
        if (q != null) {
            filterField.setText(q);
        }
        filterField.requestFocus();
        filterField.positionCaret(filterField.getText() != null ? filterField.getText().length() : 0);
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
        public void setName(String name) { this.name = name; }
        public void setBatch(String batch) { this.batch = batch; }

        public String getPriceFmt() {
            return String.format("$%.2f", price);
        }
    }
}
