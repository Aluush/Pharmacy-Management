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
        if (price == null || price < 0) {
            setAddStatus("Unit price must be a non-negative number.", true);
            return;
        }
        // Merge with existing item of same name and price
        CartItem existing = cart.stream()
            .filter(ci -> ci.getItem().equalsIgnoreCase(name) && Double.compare(ci.getPrice(), price) == 0)
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
        String customer = safe(customerField.getText());
        double subtotal = subtotal();
        double discountPct = pct(discountField.getText());
        double taxPct = pct(taxField.getText());
        double grand = grandTotal(subtotal, discountPct, taxPct);

        // Stub: In real app, persist sale and maybe print receipt
        cart.clear();
        updateTotals();
        setCheckoutStatus(String.format(Locale.US,
            "Checked out for %s | Total: %s", customer.isEmpty() ? "Walk-in" : customer, money(grand)), false);
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
