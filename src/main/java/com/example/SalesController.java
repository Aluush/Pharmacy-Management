package com.example;

import javafx.beans.binding.Bindings;
import javafx.beans.property.*;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.HBox;

import java.time.LocalDate;
import java.util.Locale;
import java.util.*;
import java.sql.*;

public class SalesController {

    // Top controls
    @FXML private TextField customerField;
    @FXML private DatePicker datePicker;

    // Add item controls
    @FXML private TextField itemField;
    @FXML private TextField qtyField;
    @FXML private TextField priceField;
    @FXML private Label addStatus;

    // Table
    @FXML private TableView<CartItem> table;
    @FXML private TableColumn<CartItem, String> colItem;
    @FXML private TableColumn<CartItem, Integer> colQty;
    @FXML private TableColumn<CartItem, Double> colPrice;
    @FXML private TableColumn<CartItem, Double> colLineTotal;
    @FXML private TableColumn<CartItem, Void> colActions;

    // Totals and checkout
    @FXML private Label lblSubtotal;
    @FXML private TextField discountField; // percentage
    @FXML private TextField taxField;      // percentage
    @FXML private Label lblGrandTotal;
    @FXML private Label checkoutStatus;

    private final ObservableList<CartItem> cart = FXCollections.observableArrayList();

    @FXML
    private void initialize() {
        datePicker.setValue(LocalDate.now());

        // Table bindings
        colItem.setCellValueFactory(new PropertyValueFactory<>("item"));
        colQty.setCellValueFactory(new PropertyValueFactory<>("qty"));
        colPrice.setCellValueFactory(new PropertyValueFactory<>("price"));
        colLineTotal.setCellValueFactory(new PropertyValueFactory<>("lineTotal"));
        // Provide a no-op value factory so the Actions column is initializable
        if (colActions != null) {
            colActions.setCellValueFactory(param -> new ReadOnlyObjectWrapper<>(null));
        }

        // Format numeric columns
        colPrice.setCellFactory(col -> new TableCell<CartItem, Double>() {
            @Override
            protected void updateItem(Double value, boolean empty) {
                super.updateItem(value, empty);
                setText(empty || value == null ? null : money(value));
            }
        });
        colLineTotal.setCellFactory(col -> new TableCell<CartItem, Double>() {
            @Override
            protected void updateItem(Double value, boolean empty) {
                super.updateItem(value, empty);
                setText(empty || value == null ? null : money(value));
            }
        });

        // Actions column
        if (colActions != null) {
            colActions.setCellFactory(col -> new TableCell<CartItem, Void>() {
                private final Button btnRemove = new Button("Remove");
                {
                    btnRemove.getStyleClass().add("ghost-button");
                    btnRemove.setOnAction(e -> {
                        CartItem item = getTableView().getItems().get(getIndex());
                        cart.remove(item);
                        updateTotals();
                    });
                }
                @Override
                protected void updateItem(Void v, boolean empty) {
                    super.updateItem(v, empty);
                    if (empty) {
                        setGraphic(null);
                    } else {
                        HBox box = new HBox(btnRemove);
                        box.setAlignment(Pos.CENTER);
                        setGraphic(box);
                    }
                }
            });
        }

        table.setItems(cart);
        table.setPlaceholder(new Label("No items. Use Add Item to start."));

        // Numeric input formatters
        if (qtyField != null) qtyField.setTextFormatter(integerFormatter());
        if (priceField != null) priceField.setTextFormatter(decimalFormatter());
        if (discountField != null) discountField.setTextFormatter(decimalFormatter());
        if (taxField != null) taxField.setTextFormatter(decimalFormatter());

        // Totals update on changes
        cart.addListener((ListChangeListener<CartItem>) c -> updateTotals());
        discountField.textProperty().addListener((o, a, b) -> updateTotals());
        taxField.textProperty().addListener((o, a, b) -> updateTotals());

        // Defaults
        discountField.setText("0");
        taxField.setText("0");
        updateTotals();
    }

    // Buttons - Add item card
    @FXML
    private void onAddItem() {
        String name = safe(itemField.getText());
        Integer qty = parseInt(qtyField.getText());
        Double price = parseDouble(priceField.getText());
        if (name.isEmpty()) {
            setAddStatus("Item name required.", true);
            return;
        }
        if (qty == null || qty <= 0) {
            setAddStatus("Quantity must be a positive integer.", true);
            return;
        }

        // If price not provided, try to fetch from inventory
        InvLookup inv = lookupInventoryByName(name);
        // Determine available quantity preferring non-expired batch stock
        int available = availableQtyNonExpired(name);
        if (price == null) {
            if (inv != null) {
                price = inv.price;
                priceField.setText(String.format(Locale.US, "%.2f", price));
            }
        }
        if (price == null || price < 0) {
            setAddStatus("Enter a valid non-negative unit price.", true);
            return;
        }

        // Ensure stock sufficiency (non-expired FIFO-aware)
        if (qty > available) {
            setAddStatus("Only " + available + " available (non-expired) for " + name + ".", true);
            return;
        }

        // Merge with existing item of same name and price (capture effectively final vars for lambda)
        final String nameKey = name;
        final double priceKey = price;
        CartItem existing = cart.stream()
            .filter(ci -> ci.getItem().equalsIgnoreCase(nameKey) && Double.compare(ci.getPrice(), priceKey) == 0)
            .findFirst().orElse(null);
        if (existing != null) {
            existing.setQty(existing.getQty() + qty);
        } else {
            cart.add(new CartItem(name, qty, price));
        }
        setAddStatus("Item added.", false);
        onClearItem();
        updateTotals();
    }

    @FXML
    private void onClearItem() {
        itemField.clear();
        qtyField.clear();
        priceField.clear();
        addStatus.setText("");
    }

    // Top bar actions
    @FXML
    private void onNewSale() {
        customerField.clear();
        datePicker.setValue(LocalDate.now());
        cart.clear();
        discountField.setText("0");
        taxField.setText("0");
        checkoutStatus.setText("");
        updateTotals();
    }

    // Cart actions
    @FXML
    private void onRemoveSelected() {
        CartItem sel = table.getSelectionModel().getSelectedItem();
        if (sel == null) {
            setCheckoutStatus("Select a row to remove.", true);
            return;
        }
        cart.remove(sel);
        updateTotals();
    }

    @FXML
    private void onClearCart() {
        cart.clear();
        updateTotals();
    }

    @FXML
    private void onHold() {
        // Stub: In a real app, persist hold to DB or cache
        setCheckoutStatus("Sale put on hold (demo).", false);
    }

    @FXML
    private void onCheckout() {
        if (cart.isEmpty()) {
            setCheckoutStatus("Cart is empty.", true);
            return;
        }

        // Validate stock against non-expired batches (FIFO), fallback to legacy if no batches exist
        for (CartItem ci : cart) {
            int available = availableQtyNonExpired(ci.getItem());
            if (ci.getQty() > available) {
                setCheckoutStatus("Insufficient non-expired stock for " + ci.getItem() + " (available: " + available + ")", true);
                return;
            }
        }

        String customer = safe(customerField.getText());
        double subtotal = subtotal();
        double discountPct = pct(discountField.getText());
        double taxPct = pct(taxField.getText());
        double grand = grandTotal(subtotal, discountPct, taxPct);

        // Persist sale and sale items in a transaction and update inventory
        Connection c = null;
        try {
            c = Database.getConnection();
            c.setAutoCommit(false);

            // Insert sale
            int saleId;
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO sales (customer, sale_date, subtotal, discount_pct, tax_pct, grand_total) VALUES (?,?,?,?,?,?)",
                    Statement.RETURN_GENERATED_KEYS)) {
                ps.setString(1, customer.isEmpty() ? "Walk-in" : customer);
                ps.setTimestamp(2, Timestamp.valueOf(LocalDate.now().atStartOfDay())); // or LocalDateTime.now()
                ps.setDouble(3, subtotal);
                ps.setDouble(4, Math.max(0.0, discountPct));
                ps.setDouble(5, Math.max(0.0, taxPct));
                ps.setDouble(6, grand);
                ps.executeUpdate();
                try (ResultSet keys = ps.getGeneratedKeys()) {
                    if (!keys.next()) throw new SQLException("No sale id returned");
                    saleId = keys.getInt(1);
                }
            }

            // Insert sale items and update inventory
            try (PreparedStatement psItem = c.prepareStatement(
                     "INSERT INTO sale_items (sale_id, item_name, qty, unit_price, line_total) VALUES (?,?,?,?,?)");
                 PreparedStatement psUpd = c.prepareStatement(
                     "UPDATE inventory_items SET quantity = quantity - ? WHERE id = ?")) {
                for (CartItem ci : cart) {
                    double line = ci.getLineTotal();
                    psItem.setInt(1, saleId);
                    psItem.setString(2, ci.getItem());
                    psItem.setInt(3, ci.getQty());
                    psItem.setDouble(4, ci.getPrice());
                    psItem.setDouble(5, line);
                    psItem.addBatch();

                    InvLookup inv = lookupInventoryByName(ci.getItem());
                    if (inv != null) {
                        psUpd.setInt(1, ci.getQty());
                        psUpd.setInt(2, inv.id);
                        psUpd.addBatch();
                    }
                }
                psItem.executeBatch();
                psUpd.executeBatch();

                // FIFO per-batch deduction and inventory movements (SALE), skipping if no batches tracked
                try (PreparedStatement psUpdateBatch = c.prepareStatement(
                         "UPDATE item_batches SET qty_on_hand = qty_on_hand - ? WHERE id = ? AND qty_on_hand >= ?");
                     PreparedStatement psInsertMov = c.prepareStatement(
                         "INSERT INTO inventory_movements (item_batch_id, qty, movement_type, ref_type, ref_id, created_at, user_id) VALUES (?, ?, 'SALE', 'SALE', ?, CURRENT_TIMESTAMP, NULL)")) {
                    for (CartItem ci : cart) {
                        java.util.List<BatchAllocation> allocs = fifoPlan(c, ci.getItem(), ci.getQty());
                        for (BatchAllocation al : allocs) {
                            psUpdateBatch.setInt(1, al.qty);
                            psUpdateBatch.setInt(2, al.batchId);
                            psUpdateBatch.setInt(3, al.qty);
                            int updated = psUpdateBatch.executeUpdate();
                            if (updated == 0) throw new SQLException("Insufficient or modified stock for batch " + al.batchId);
                            psInsertMov.setInt(1, al.batchId);
                            psInsertMov.setInt(2, -al.qty);
                            psInsertMov.setInt(3, saleId);
                            psInsertMov.addBatch();
                        }
                    }
                    psInsertMov.executeBatch();
                }
            }

            c.commit();

            cart.clear();
            updateTotals();
            setCheckoutStatus(String.format(Locale.US,
                "Checked out for %s | Total: %s (Sale saved)", customer.isEmpty() ? "Walk-in" : customer, money(grand)), false);
        } catch (Exception ex) {
            try { if (c != null) c.rollback(); } catch (Exception ignore) {}
            setCheckoutStatus("Checkout failed: " + ex.getMessage(), true);
        } finally {
            try { if (c != null) c.close(); } catch (Exception ignore) {}
        }
    }

    // Totals
    private void updateTotals() {
        double sub = subtotal();
        lblSubtotal.setText(money(sub));
        double discountPct = pct(discountField.getText());
        double taxPct = pct(taxField.getText());
        lblGrandTotal.setText(money(grandTotal(sub, discountPct, taxPct)));
    }

    private double subtotal() {
        return cart.stream().mapToDouble(CartItem::getLineTotal).sum();
    }

    private static double pct(String s) {
        try {
            if (s == null || s.isBlank()) return 0.0;
            return Math.max(0.0, Double.parseDouble(s.trim()));
        } catch (Exception e) {
            return 0.0;
        }
    }

    private double grandTotal(double subtotal, double discountPct, double taxPct) {
        double afterDiscount = subtotal * (1.0 - (Math.max(0.0, discountPct) / 100.0));
        double afterTax = afterDiscount * (1.0 + (Math.max(0.0, taxPct) / 100.0));
        return Math.max(0.0, afterTax);
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
    private static String money(double v) {
        return String.format(Locale.US, "$%.2f", v);
    }
    private void setAddStatus(String msg, boolean error) {
        addStatus.setText(msg);
        addStatus.setStyle(error ? "-fx-text-fill: #ef4444;" : "-fx-text-fill: -color-text-muted;");
    }
    private void setCheckoutStatus(String msg, boolean error) {
        checkoutStatus.setText(msg);
        checkoutStatus.setStyle(error ? "-fx-text-fill: #ef4444;" : "-fx-text-fill: -color-text-muted;");
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

    // Database helpers
    private InvLookup lookupInventoryByName(String name) {
        if (name == null || name.isBlank()) return null;
        String sql = "SELECT id, quantity, price FROM inventory_items WHERE LOWER(name)=LOWER(?) ORDER BY id LIMIT 1";
        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, name.trim());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    InvLookup i = new InvLookup();
                    i.id = rs.getInt("id");
                    i.quantity = rs.getInt("quantity");
                    i.price = rs.getDouble("price");
                    return i;
                }
            }
        } catch (Exception ignore) { }
        return null;
    }

    private static class InvLookup {
        int id;
        int quantity;
        double price;
    }

    // Allocation for FIFO
    private static class BatchAllocation {
        final int batchId;
        final int qty;
        BatchAllocation(int batchId, int qty) { this.batchId = batchId; this.qty = qty; }
    }

    // Available quantity preferring non-expired batches; falls back to legacy flat inventory if no batches tracked
    private int availableQtyNonExpired(String name) {
        if (name == null || name.isBlank()) return 0;
        String sqlCount = "SELECT COUNT(*) FROM item_batches b JOIN items i ON b.item_id = i.id WHERE LOWER(i.name) = LOWER(?)";
        String sqlAvail = "SELECT COALESCE(SUM(b.qty_on_hand),0) FROM item_batches b JOIN items i ON b.item_id = i.id " +
                          "WHERE LOWER(i.name) = LOWER(?) AND (b.expiry_date IS NULL OR b.expiry_date >= CURDATE())";
        try (Connection c = Database.getConnection()) {
            boolean hasBatches = false;
            try (PreparedStatement ps = c.prepareStatement(sqlCount)) {
                ps.setString(1, name.trim());
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) hasBatches = rs.getInt(1) > 0;
                }
            }
            if (hasBatches) {
                try (PreparedStatement ps = c.prepareStatement(sqlAvail)) {
                    ps.setString(1, name.trim());
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) return rs.getInt(1);
                    }
                }
                return 0;
            } else {
                try (PreparedStatement ps = c.prepareStatement("SELECT COALESCE(SUM(quantity),0) FROM inventory_items WHERE LOWER(name)=LOWER(?)")) {
                    ps.setString(1, name.trim());
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) return rs.getInt(1);
                    }
                }
            }
        } catch (Exception ignore) { }
        return 0;
    }

    // Build FIFO allocation plan from non-expired batches within an existing transaction
    private java.util.List<BatchAllocation> fifoPlan(Connection c, String name, int needed) throws SQLException {
        java.util.List<BatchAllocation> plan = new java.util.ArrayList<>();
        if (name == null || name.isBlank() || needed <= 0) return plan;
        String sql = "SELECT b.id, b.qty_on_hand, b.expiry_date " +
                     "FROM item_batches b JOIN items i ON b.item_id = i.id " +
                     "WHERE LOWER(i.name) = LOWER(?) AND (b.expiry_date IS NULL OR b.expiry_date >= CURDATE()) AND b.qty_on_hand > 0 " +
                     "ORDER BY (CASE WHEN b.expiry_date IS NULL THEN 1 ELSE 0 END), b.expiry_date, b.id";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, name.trim());
            try (ResultSet rs = ps.executeQuery()) {
                int remain = needed;
                while (rs.next() && remain > 0) {
                    int batchId = rs.getInt(1);
                    int onHand = rs.getInt(2);
                    int take = Math.min(remain, onHand);
                    if (take > 0) {
                        plan.add(new BatchAllocation(batchId, take));
                        remain -= take;
                    }
                }
                if (remain > 0) {
                    // Not enough non-expired batches; return empty to signal fallback/no-op
                    plan.clear();
                }
            }
        }
        return plan;
    }

    // Focus helpers for global search routing
    public void focusItemWith(String q) {
        if (itemField == null) return;
        itemField.setText(q == null ? "" : q);
        itemField.requestFocus();
        itemField.positionCaret(itemField.getText() != null ? itemField.getText().length() : 0);
    }

    // Data model
    public static class CartItem {
        private final StringProperty item = new SimpleStringProperty();
        private final IntegerProperty qty = new SimpleIntegerProperty();
        private final DoubleProperty price = new SimpleDoubleProperty();

        public CartItem(String item, int qty, double price) {
            this.item.set(item);
            this.qty.set(qty);
            this.price.set(price);
        }

        public String getItem() { return item.get(); }
        public void setItem(String v) { item.set(v); }
        public int getQty() { return qty.get(); }
        public void setQty(int v) { qty.set(v); }
        public double getPrice() { return price.get(); }
        public void setPrice(double v) { price.set(v); }

        public double getLineTotal() { return getQty() * getPrice(); }

        // JavaFX properties (optional if ever needed)
        public StringProperty itemProperty() { return item; }
        public IntegerProperty qtyProperty() { return qty; }
        public DoubleProperty priceProperty() { return price; }
        public ReadOnlyDoubleWrapper lineTotalProperty() {
            ReadOnlyDoubleWrapper w = new ReadOnlyDoubleWrapper();
            w.bind(Bindings.multiply(qty, price));
            return w;
        }
    }
}
