package com.example;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.PieChart;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.cell.PropertyValueFactory;

import java.sql.*;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class DashboardController {

    // KPIs
    @FXML private Label metricSalesToday;
    @FXML private Label metricSalesDelta;
    @FXML private Label metricInStock;
    @FXML private Label metricLowStock;
    @FXML private Label metricExpiring;

    // Charts
    @FXML private LineChart<String, Number> salesLineChart;
    @FXML private PieChart stockPieChart;

    // Table
    @FXML private TableView<SalesRow> recentSalesTable;
    @FXML private TableColumn<SalesRow, String> colSaleId;
    @FXML private TableColumn<SalesRow, String> colSaleItem;
    @FXML private TableColumn<SalesRow, Integer> colSaleQty;
    @FXML private TableColumn<SalesRow, String> colSaleTotal;
    @FXML private TableColumn<SalesRow, String> colSaleDate;

    @FXML
    private void initialize() {
        loadMetrics();
        setupCharts();
        setupTable();
    }

    private void setupCharts() {
        // Weekly sales from DB (last 7 days)
        LocalDate today = LocalDate.now();
        LocalDate start = today.minusDays(6);
        Map<LocalDate, Double> totals = new HashMap<>();
        DateTimeFormatter dayFmt = DateTimeFormatter.ofPattern("EEE", Locale.US);
        for (int i = 0; i < 7; i++) {
            totals.put(start.plusDays(i), 0.0);
        }
        String sql = "SELECT DATE(sale_date) d, SUM(grand_total) t FROM sales " +
                     "WHERE sale_date >= DATE_SUB(CURDATE(), INTERVAL 6 DAY) " +
                     "GROUP BY DATE(sale_date)";
        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                LocalDate d = rs.getDate("d").toLocalDate();
                double t = rs.getDouble("t");
                if (totals.containsKey(d)) totals.put(d, t);
            }
        } catch (Exception ignore) {}
        XYChart.Series<String, Number> series = new XYChart.Series<>();
        for (int i = 0; i < 7; i++) {
            LocalDate d = start.plusDays(i);
            series.getData().add(new XYChart.Data<>(d.format(dayFmt), totals.getOrDefault(d, 0.0)));
        }
        salesLineChart.getData().setAll(series);

        // Stock by category from DB
        ObservableList<PieChart.Data> pieData = FXCollections.observableArrayList();
        String sqlPie = "SELECT COALESCE(category,'Uncategorized') AS cat, SUM(quantity) AS qty FROM inventory_items GROUP BY COALESCE(category,'Uncategorized')";
        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement(sqlPie);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                String cat = rs.getString("cat");
                int qty = rs.getInt("qty");
                if (qty > 0) pieData.add(new PieChart.Data(cat, qty));
            }
        } catch (Exception ignore) {}
        stockPieChart.setData(pieData);
    }

    private void setupTable() {
        colSaleId.setCellValueFactory(new PropertyValueFactory<>("saleId"));
        colSaleItem.setCellValueFactory(new PropertyValueFactory<>("item"));
        colSaleQty.setCellValueFactory(new PropertyValueFactory<>("qty"));
        colSaleTotal.setCellValueFactory(new PropertyValueFactory<>("total"));
        colSaleDate.setCellValueFactory(new PropertyValueFactory<>("date"));

        recentSalesTable.setItems(sampleSales());
    }

    private ObservableList<SalesRow> sampleSales() {
        ObservableList<SalesRow> rows = FXCollections.observableArrayList();
        String sql = "SELECT s.id AS sale_id, si.item_name, si.qty, si.line_total, s.sale_date " +
                     "FROM sale_items si JOIN sales s ON si.sale_id = s.id " +
                     "ORDER BY s.sale_date DESC, si.id DESC LIMIT 10";
        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
            while (rs.next()) {
                String saleId = "#" + rs.getInt("sale_id");
                String item = rs.getString("item_name");
                int qty = rs.getInt("qty");
                String total = String.format(Locale.US, "$%.2f", rs.getDouble("line_total"));
                Timestamp ts = rs.getTimestamp("sale_date");
                String date = ts != null ? ts.toLocalDateTime().format(fmt) : "";
                rows.add(new SalesRow(saleId, item, qty, total, date));
            }
        } catch (Exception ignore) {}
        return rows;
    }

    public static class SalesRow {
        private final String saleId;
        private final String item;
        private final Integer qty;
        private final String total;
        private final String date;

        public SalesRow(String saleId, String item, Integer qty, String total, String date) {
            this.saleId = saleId;
            this.item = item;
            this.qty = qty;
            this.total = total;
            this.date = date;
        }

        public String getSaleId() { return saleId; }
        public String getItem() { return item; }
        public Integer getQty() { return qty; }
        public String getTotal() { return total; }
        public String getDate() { return date; }
    }

    @FXML
    private void onRefresh() {
        loadMetrics();
        setupCharts();
        recentSalesTable.setItems(sampleSales());
    }
    private void loadMetrics() {
        // Today's sales and delta vs yesterday
        double today = 0.0;
        double yesterday = 0.0;
        try (Connection c = Database.getConnection()) {
            try (PreparedStatement ps = c.prepareStatement("SELECT COALESCE(SUM(grand_total),0) FROM sales WHERE DATE(sale_date)=CURDATE()");
                 ResultSet rs = ps.executeQuery()) {
                if (rs.next()) today = rs.getDouble(1);
            }
            try (PreparedStatement ps = c.prepareStatement("SELECT COALESCE(SUM(grand_total),0) FROM sales WHERE DATE(sale_date)=DATE_SUB(CURDATE(), INTERVAL 1 DAY)");
                 ResultSet rs = ps.executeQuery()) {
                if (rs.next()) yesterday = rs.getDouble(1);
            }
        } catch (Exception ignore) {}
        metricSalesToday.setText(String.format(Locale.US, "$%.2f", today));
        double deltaPct = (yesterday <= 0.0) ? (today > 0 ? 100.0 : 0.0) : ((today - yesterday) / yesterday) * 100.0;
        String arrow = deltaPct >= 0 ? "+" : "";
        metricSalesDelta.setText(String.format(Locale.US, "%s%.1f%% from yesterday", arrow, deltaPct));

        // Inventory KPIs
        int inStock = 0;
        int lowStock = 0;
        int expiring = 0;
        try (Connection c = Database.getConnection()) {
            try (PreparedStatement ps = c.prepareStatement("SELECT COALESCE(SUM(quantity),0) FROM inventory_items");
                 ResultSet rs = ps.executeQuery()) {
                if (rs.next()) inStock = rs.getInt(1);
            }
            try (PreparedStatement ps = c.prepareStatement("SELECT COUNT(*) FROM inventory_items WHERE reorder_level > 0 AND quantity <= reorder_level");
                 ResultSet rs = ps.executeQuery()) {
                if (rs.next()) lowStock = rs.getInt(1);
            }
            try (PreparedStatement ps = c.prepareStatement("SELECT COUNT(*) FROM inventory_items WHERE expiry IS NOT NULL AND expiry BETWEEN CURDATE() AND DATE_ADD(CURDATE(), INTERVAL 30 DAY)");
                 ResultSet rs = ps.executeQuery()) {
                if (rs.next()) expiring = rs.getInt(1);
            }
        } catch (Exception ignore) {}
        metricInStock.setText(String.format(Locale.US, "%,d", inStock));
        metricLowStock.setText(String.format(Locale.US, "%,d", lowStock));
        metricExpiring.setText(String.format(Locale.US, "%,d", expiring));
    }
}
