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
import javafx.scene.Group;
import javafx.scene.SubScene;
import javafx.scene.SceneAntialiasing;
import javafx.scene.PerspectiveCamera;
import javafx.scene.paint.Color;
import javafx.scene.paint.PhongMaterial;
import javafx.scene.shape.Box;
import javafx.scene.shape.Cylinder;
import javafx.scene.shape.Sphere;
import javafx.scene.transform.Rotate;
import javafx.animation.Timeline;
import javafx.animation.KeyFrame;
import javafx.animation.FadeTransition;
import javafx.animation.Interpolator;
import javafx.util.Duration;
import javafx.scene.Node;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.scene.layout.StackPane;
import javafx.geometry.Point3D;

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
    @FXML private StackPane threeDPane;

    // Table
    @FXML private TableView<SalesRow> recentSalesTable;
    @FXML private TableColumn<SalesRow, String> colSaleId;
    @FXML private TableColumn<SalesRow, String> colSaleItem;
    @FXML private TableColumn<SalesRow, Integer> colSaleQty;
    @FXML private TableColumn<SalesRow, String> colSaleTotal;
    @FXML private TableColumn<SalesRow, String> colSaleDate;

    // Top sellers
    @FXML private TableView<TopItemRow> topItemsTable;
    @FXML private TableColumn<TopItemRow, String> colTopItemName;
    @FXML private TableColumn<TopItemRow, Integer> colTopItemQty;
    @FXML private TableColumn<TopItemRow, String> colTopItemRevenue;

    @FXML
    private void initialize() {
        loadMetrics();
        setupCharts();
        setup3D();
        setupTable();
        setupTopItems();
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
        fadeIn(salesLineChart);

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
        fadeIn(stockPieChart);
    }

    private void setupTable() {
        colSaleId.setCellValueFactory(new PropertyValueFactory<>("saleId"));
        colSaleItem.setCellValueFactory(new PropertyValueFactory<>("item"));
        colSaleQty.setCellValueFactory(new PropertyValueFactory<>("qty"));
        colSaleTotal.setCellValueFactory(new PropertyValueFactory<>("total"));
        colSaleDate.setCellValueFactory(new PropertyValueFactory<>("date"));

        recentSalesTable.setItems(sampleSales());
    }

    private void setupTopItems() {
        if (colTopItemName != null) colTopItemName.setCellValueFactory(new PropertyValueFactory<>("name"));
        if (colTopItemQty != null) colTopItemQty.setCellValueFactory(new PropertyValueFactory<>("qty"));
        if (colTopItemRevenue != null) colTopItemRevenue.setCellValueFactory(new PropertyValueFactory<>("revenue"));
        if (topItemsTable != null) {
            topItemsTable.setItems(loadTopItems());
        }
    }

    private ObservableList<TopItemRow> loadTopItems() {
        ObservableList<TopItemRow> rows = FXCollections.observableArrayList();
        String sql = "SELECT item_name, SUM(qty) AS total_qty, SUM(line_total) AS revenue " +
                     "FROM sale_items GROUP BY item_name ORDER BY total_qty DESC LIMIT 10";
        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                String name = rs.getString("item_name");
                int totalQty = rs.getInt("total_qty");
                String revStr = String.format(Locale.US, "$%.2f", rs.getDouble("revenue"));
                rows.add(new TopItemRow(name, totalQty, revStr));
            }
        } catch (Exception ignore) {}
        return rows;
    }

    // UX helpers (micro-interactions)
    private void fadeIn(Node node) {
        if (node == null) return;
        node.setOpacity(0);
        FadeTransition ft = new FadeTransition(Duration.millis(260), node);
        ft.setToValue(1.0);
        ft.play();
    }

    private double currentMoney(Label lbl) {
        try {
            String s = lbl == null ? "" : lbl.getText();
            if (s == null) return 0.0;
            s = s.replaceAll("[^0-9.\\-]", "");
            return s.isBlank() ? 0.0 : Double.parseDouble(s);
        } catch (Exception ignore) { return 0.0; }
    }

    private int currentInt(Label lbl) {
        try {
            String s = lbl == null ? "" : lbl.getText();
            if (s == null) return 0;
            s = s.replaceAll("[^0-9\\-]", "");
            return s.isBlank() ? 0 : Integer.parseInt(s);
        } catch (Exception ignore) { return 0; }
    }

    private void bump(Node node) {
        if (node == null) return;
        javafx.animation.ScaleTransition st1 = new javafx.animation.ScaleTransition(Duration.millis(110), node);
        st1.setFromX(1.0); st1.setFromY(1.0);
        st1.setToX(1.06); st1.setToY(1.06);
        javafx.animation.ScaleTransition st2 = new javafx.animation.ScaleTransition(Duration.millis(140), node);
        st2.setFromX(1.06); st2.setFromY(1.06);
        st2.setToX(1.0); st2.setToY(1.0);
        st1.setOnFinished(ev -> st2.play());
        st1.play();
    }

    private void animateMoney(Label label, double target) {
        if (label == null) return;
        double start = currentMoney(label);
        DoubleProperty val = new SimpleDoubleProperty(start);
        val.addListener((obs, ov, nv) -> label.setText(String.format(Locale.US, "$%.2f", nv.doubleValue())));
        Timeline tl = new Timeline(new KeyFrame(Duration.millis(600), new javafx.animation.KeyValue(val, target, Interpolator.EASE_BOTH)));
        tl.setOnFinished(ev -> bump(label));
        tl.play();
    }

    private void animateInt(Label label, int target) {
        if (label == null) return;
        double start = currentInt(label);
        DoubleProperty val = new SimpleDoubleProperty(start);
        val.addListener((obs, ov, nv) -> label.setText(String.format(Locale.US, "%,d", (int)Math.round(nv.doubleValue()))));
        Timeline tl = new Timeline(new KeyFrame(Duration.millis(520), new javafx.animation.KeyValue(val, target, Interpolator.EASE_BOTH)));
        tl.setOnFinished(ev -> bump(label));
        tl.play();
    }

    // 3D showcase
    private final Rotate rotateX = new Rotate(25, Rotate.X_AXIS);
    private final Rotate rotateY = new Rotate(45, Rotate.Y_AXIS);
    private Group spinner;
    private double anchorX, anchorY;
    private double pulsePhase = 0;

    private void setup3D() {
        if (threeDPane == null) return;

        Group world = new Group();
        spinner = buildSpinner();
        spinner.getTransforms().addAll(rotateX, rotateY);
        world.getChildren().add(spinner);

        // Lights
        javafx.scene.AmbientLight ambient = new javafx.scene.AmbientLight(Color.color(0.60, 0.62, 0.70));
        javafx.scene.PointLight key = new javafx.scene.PointLight(Color.WHITE);
        key.setTranslateX(220);
        key.setTranslateY(-140);
        key.setTranslateZ(-320);
        world.getChildren().addAll(ambient, key);

        // Camera
        PerspectiveCamera cam = new PerspectiveCamera(true);
        cam.setNearClip(0.1);
        cam.setFarClip(3000);
        cam.setTranslateZ(-650);

        SubScene sub = new SubScene(world, 640, 260, true, SceneAntialiasing.BALANCED);
        sub.setFill(Color.TRANSPARENT);
        sub.setCamera(cam);

        // Fit to container
        sub.widthProperty().bind(threeDPane.widthProperty());
        sub.heightProperty().bind(threeDPane.heightProperty());

        threeDPane.getChildren().setAll(sub);

        // Idle rotation + data-driven pulse (based on alerts)
        int low = 0;
        int exp = 0;
        try (Connection c = Database.getConnection()) {
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT COUNT(*) FROM inventory_items WHERE reorder_level > 0 AND quantity <= reorder_level");
                 ResultSet rs = ps.executeQuery()) {
                if (rs.next()) low = rs.getInt(1);
            }
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT COUNT(*) FROM item_batches WHERE expiry_date IS NOT NULL AND expiry_date BETWEEN CURDATE() AND DATE_ADD(CURDATE(), INTERVAL 30 DAY)");
                 ResultSet rs = ps.executeQuery()) {
                if (rs.next()) exp = rs.getInt(1);
            }
        } catch (Exception ignore) {}

        final int alertsTotal = low + exp;
        final double speed = 0.18 + Math.min(0.5, 0.02 * alertsTotal);
        Color lightColor = alertsTotal > 0 ? Color.web("#f43f5e") : Color.WHITE;
        key.setColor(lightColor);

        final double pulseStep = 0.12 + 0.02 * alertsTotal;

        Timeline tl = new Timeline(new KeyFrame(Duration.millis(16), ev -> {
            rotateY.setAngle(rotateY.getAngle() + speed);
            pulsePhase += pulseStep;
            double s = 1.0 + 0.04 * Math.sin(pulsePhase);
            spinner.setScaleX(s);
            spinner.setScaleY(s);
            spinner.setScaleZ(s);
        }));
        tl.setCycleCount(Timeline.INDEFINITE);
        tl.play();

        // Drag to rotate
        sub.setOnMousePressed(e -> { anchorX = e.getSceneX(); anchorY = e.getSceneY(); });
        sub.setOnMouseDragged(e -> {
            double dx = e.getSceneX() - anchorX;
            double dy = e.getSceneY() - anchorY;
            rotateY.setAngle(rotateY.getAngle() + dx * 0.4);
            rotateX.setAngle(rotateX.getAngle() - dy * 0.4);
            anchorX = e.getSceneX();
            anchorY = e.getSceneY();
        });
    }

    private Group buildSpinner() {
        Group g = new Group();

        // Materials (atoms)
        PhongMaterial carbon = new PhongMaterial(Color.web("#374151"));    // gray-700
        carbon.setSpecularColor(Color.web("#9ca3af"));
        PhongMaterial hydrogen = new PhongMaterial(Color.web("#e5e7eb"));  // gray-200
        hydrogen.setSpecularColor(Color.web("#ffffff"));
        PhongMaterial oxygen = new PhongMaterial(Color.web("#ef4444"));    // red-500
        oxygen.setSpecularColor(Color.web("#fecaca"));
        PhongMaterial nitrogen = new PhongMaterial(Color.web("#3b82f6"));  // blue-500
        nitrogen.setSpecularColor(Color.web("#93c5fd"));

        // Stylized aromatic ring + OH and NH groups (pharma-themed molecule)
        double R = 110;   // ring radius
        double R2 = 150;  // hydrogen radius (outer)
        Sphere[] carbons = new Sphere[6];
        Point3D[] cpos = new Point3D[6];

        for (int i = 0; i < 6; i++) {
            double a = Math.toRadians(60 * i);
            Point3D p = new Point3D(R * Math.cos(a), R * Math.sin(a), 0);
            cpos[i] = p;
            Sphere s = new Sphere(12);
            s.setMaterial(carbon);
            s.setTranslateX(p.getX());
            s.setTranslateY(p.getY());
            s.setTranslateZ(p.getZ());
            carbons[i] = s;
            g.getChildren().add(s);
        }

        // Ring bonds
        for (int i = 0; i < 6; i++) {
            Point3D a = cpos[i];
            Point3D b = cpos[(i + 1) % 6];
            Cylinder bond = cylinderBetween(a, b, 4, carbon);
            g.getChildren().add(bond);
        }

        // Outward hydrogens
        for (int i = 0; i < 6; i++) {
            double a = Math.toRadians(60 * i);
            Point3D hPos = new Point3D(R2 * Math.cos(a), R2 * Math.sin(a), 0);
            Sphere h = new Sphere(8);
            h.setMaterial(hydrogen);
            h.setTranslateX(hPos.getX());
            h.setTranslateY(hPos.getY());
            h.setTranslateZ(hPos.getZ());
            g.getChildren().add(h);
            g.getChildren().add(cylinderBetween(cpos[i], hPos, 2.5, hydrogen));
        }

        // Oxygen (OH) group at carbon 0
        Point3D c0 = cpos[0];
        Point3D oPos = c0.add(new Point3D(0, -70, 0));
        Sphere o = new Sphere(10);
        o.setMaterial(oxygen);
        o.setTranslateX(oPos.getX());
        o.setTranslateY(oPos.getY());
        o.setTranslateZ(oPos.getZ());
        g.getChildren().add(o);
        g.getChildren().add(cylinderBetween(c0, oPos, 3.5, oxygen));

        // Nitrogen (NH) group at opposite carbon 3
        Point3D c3 = cpos[3];
        Point3D nPos = c3.add(new Point3D(0, 70, 0));
        Sphere n = new Sphere(10);
        n.setMaterial(nitrogen);
        n.setTranslateX(nPos.getX());
        n.setTranslateY(nPos.getY());
        n.setTranslateZ(nPos.getZ());
        g.getChildren().add(n);
        g.getChildren().add(cylinderBetween(c3, nPos, 3.5, nitrogen));

        return g;
    }

    private Cylinder cylinderBetween(Point3D p1, Point3D p2, double radius, PhongMaterial mat) {
        Point3D diff = p2.subtract(p1);
        double height = diff.magnitude();
        Cylinder cyl = new Cylinder(radius, height);
        cyl.setMaterial(mat);

        Point3D mid = p1.midpoint(p2);
        cyl.setTranslateX(mid.getX());
        cyl.setTranslateY(mid.getY());
        cyl.setTranslateZ(mid.getZ());

        Point3D yAxis = new Point3D(0, 1, 0);
        Point3D axisOfRotation = diff.crossProduct(yAxis);
        double angle = Math.acos(Math.min(1, Math.max(-1, diff.normalize().dotProduct(yAxis))));
        if (axisOfRotation.magnitude() > 1e-6 && !Double.isNaN(angle)) {
            cyl.getTransforms().add(new Rotate(-Math.toDegrees(angle), axisOfRotation));
        }
        return cyl;
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

    public static class TopItemRow {
        private final String name;
        private final Integer qty;
        private final String revenue;

        public TopItemRow(String name, Integer qty, String revenue) {
            this.name = name;
            this.qty = qty;
            this.revenue = revenue;
        }

        public String getName() { return name; }
        public Integer getQty() { return qty; }
        public String getRevenue() { return revenue; }
    }

    @FXML
    private void onRefresh() {
        loadMetrics();
        setupCharts();
        recentSalesTable.setItems(sampleSales());
        if (topItemsTable != null) {
            topItemsTable.setItems(loadTopItems());
        }
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
        animateMoney(metricSalesToday, today);
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
            // Prefer batches-based expiry if available; fallback to legacy per-item expiry
            Integer batchesCount = null;
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT COUNT(*) FROM item_batches WHERE expiry_date IS NOT NULL AND expiry_date BETWEEN CURDATE() AND DATE_ADD(CURDATE(), INTERVAL 30 DAY)");
                 ResultSet rs = ps.executeQuery()) {
                if (rs.next()) batchesCount = rs.getInt(1);
            } catch (Exception ignore) { /* item_batches may not exist in older DBs */ }
            if (batchesCount != null) {
                expiring = batchesCount;
            } else {
                try (PreparedStatement ps = c.prepareStatement(
                        "SELECT COUNT(*) FROM inventory_items WHERE expiry IS NOT NULL AND expiry BETWEEN CURDATE() AND DATE_ADD(CURDATE(), INTERVAL 30 DAY)");
                     ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) expiring = rs.getInt(1);
                }
            }
        } catch (Exception ignore) {}
        animateInt(metricInStock, inStock);
        animateInt(metricLowStock, lowStock);
        animateInt(metricExpiring, expiring);
    }
}
