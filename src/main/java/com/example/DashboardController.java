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
        // Metrics (sample data)
        metricSalesToday.setText("$2,340.50");
        metricSalesDelta.setText("+8.4% from yesterday");
        metricInStock.setText("12,384");
        metricLowStock.setText("27");
        metricExpiring.setText("14");

        setupCharts();
        setupTable();
    }

    private void setupCharts() {
        // Line chart - weekly sales (sample)
        if (salesLineChart.getXAxis() instanceof CategoryAxis) {
            // ok
        }
        if (salesLineChart.getYAxis() instanceof NumberAxis) {
            // ok
        }
        XYChart.Series<String, Number> series = new XYChart.Series<>();
        series.getData().add(new XYChart.Data<>("Mon", 280));
        series.getData().add(new XYChart.Data<>("Tue", 320));
        series.getData().add(new XYChart.Data<>("Wed", 450));
        series.getData().add(new XYChart.Data<>("Thu", 380));
        series.getData().add(new XYChart.Data<>("Fri", 520));
        series.getData().add(new XYChart.Data<>("Sat", 610));
        series.getData().add(new XYChart.Data<>("Sun", 420));
        salesLineChart.getData().setAll(series);

        // Pie chart - stock by category (sample)
        ObservableList<PieChart.Data> pieData = FXCollections.observableArrayList(
            new PieChart.Data("Analgesics", 35),
            new PieChart.Data("Antibiotics", 22),
            new PieChart.Data("Vitamins", 18),
            new PieChart.Data("Dermatology", 12),
            new PieChart.Data("Other", 13)
        );
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
        return FXCollections.observableArrayList(
            new SalesRow("#10021", "Paracetamol 500mg", 2, "$6.00", "2025-10-12 10:23"),
            new SalesRow("#10022", "Vitamin C 1000mg", 1, "$9.50", "2025-10-12 11:05"),
            new SalesRow("#10023", "Amoxicillin 500mg", 1, "$12.20", "2025-10-12 11:17"),
            new SalesRow("#10024", "Ibuprofen 200mg", 3, "$7.80", "2025-10-12 12:02"),
            new SalesRow("#10025", "Cough Syrup 100ml", 1, "$4.90", "2025-10-12 12:40")
        );
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
        // In a real app, reload from services/DB. For now, just update with minor changes.
        setupCharts();
        recentSalesTable.setItems(sampleSales());
    }
}
