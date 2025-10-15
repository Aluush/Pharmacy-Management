package com.example;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Date;
import java.time.LocalDate;
import com.example.util.PasswordUtil;
import com.example.util.AppConfig;

public final class Database {
    private static final AppConfig CFG = AppConfig.get();

    private Database() {}

    public static void bootstrap() throws SQLException {
        ensureDatabase();
        migrate();
        seedDemoData();
        test();
    }

    public static Connection getConnection() throws SQLException {
        if (CFG.isSqlite()) {
            return DriverManager.getConnection(CFG.getJdbcUrl());
        }
        return DriverManager.getConnection(CFG.getJdbcUrl(), CFG.getDbUser(), CFG.getDbPass());
    }

    private static void ensureDatabase() throws SQLException {
        if (CFG.isSqlite()) {
            // No separate database to create for SQLite
            return;
        }
        if (CFG.isMySql()) {
            String serverUrl = CFG.getJdbcServerUrl();
            try (Connection conn = DriverManager.getConnection(serverUrl, CFG.getDbUser(), CFG.getDbPass());
                 Statement st = conn.createStatement()) {
                st.executeUpdate("CREATE DATABASE IF NOT EXISTS " + CFG.getDbName() + " CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci");
            }
            return;
        }
        if (CFG.isPostgres()) {
            // Attempt to create DB (ignore errors if already exists or insufficient permission)
            String serverUrl = CFG.getJdbcServerUrl();
            try (Connection conn = DriverManager.getConnection(serverUrl + "postgres", CFG.getDbUser(), CFG.getDbPass());
                 Statement st = conn.createStatement()) {
                st.executeUpdate("CREATE DATABASE " + CFG.getDbName());
            } catch (SQLException ignore) {
                // proceed; likely exists or user lacks createdb
            }
        }
    }

    public static void migrate() throws SQLException {
        try (Connection conn = getConnection(); Statement st = conn.createStatement()) {
            // Inventory items table (legacy flat items for current UI)
            st.execute("""
                CREATE TABLE IF NOT EXISTS inventory_items (
                  id INT AUTO_INCREMENT PRIMARY KEY,
                  name VARCHAR(255) NOT NULL UNIQUE,
                  category VARCHAR(100),
                  batch VARCHAR(100),
                  quantity INT NOT NULL DEFAULT 0,
                  price DECIMAL(10,2) NOT NULL DEFAULT 0.00,
                  expiry DATE NULL,
                  supplier VARCHAR(255),
                  reorder_level INT NOT NULL DEFAULT 0,
                  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
                """);

            // Core Phase 1 schema

            // Suppliers
            st.execute("""
                CREATE TABLE IF NOT EXISTS suppliers (
                  id INT AUTO_INCREMENT PRIMARY KEY,
                  name VARCHAR(255) NOT NULL,
                  phone VARCHAR(50),
                  email VARCHAR(255),
                  address VARCHAR(500),
                  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
                """);

            // Items master (normalized). If not present, derive from inventory_items when we migrate data later.
            st.execute("""
                CREATE TABLE IF NOT EXISTS items (
                  id INT AUTO_INCREMENT PRIMARY KEY,
                  name VARCHAR(255) NOT NULL,
                  generic_name VARCHAR(255),
                  category_id INT NULL,
                  barcode VARCHAR(100),
                  dosage_form VARCHAR(100),
                  strength VARCHAR(100),
                  tax_rate_id INT NULL,
                  reorder_level INT NOT NULL DEFAULT 0,
                  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
                """);

            // Item batches
            st.execute("""
                CREATE TABLE IF NOT EXISTS item_batches (
                  id INT AUTO_INCREMENT PRIMARY KEY,
                  item_id INT NOT NULL,
                  batch_no VARCHAR(100),
                  expiry_date DATE NULL,
                  qty_on_hand INT NOT NULL DEFAULT 0,
                  purchase_price DECIMAL(10,2) NOT NULL DEFAULT 0.00,
                  sell_price DECIMAL(10,2) NOT NULL DEFAULT 0.00,
                  location VARCHAR(100),
                  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                  CONSTRAINT fk_item_batches_item FOREIGN KEY (item_id) REFERENCES items(id) ON DELETE CASCADE,
                  INDEX idx_item_batches_item_exp (item_id, expiry_date),
                  INDEX idx_item_batches_batch (batch_no)
                )
                """);

            // Purchase orders
            st.execute("""
                CREATE TABLE IF NOT EXISTS purchase_orders (
                  id INT AUTO_INCREMENT PRIMARY KEY,
                  supplier_id INT NOT NULL,
                  status VARCHAR(30) NOT NULL DEFAULT 'DRAFT',
                  ordered_at DATETIME NULL,
                  expected_at DATETIME NULL,
                  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                  CONSTRAINT fk_po_supplier FOREIGN KEY (supplier_id) REFERENCES suppliers(id)
                )
                """);

            st.execute("""
                CREATE TABLE IF NOT EXISTS purchase_order_items (
                  id INT AUTO_INCREMENT PRIMARY KEY,
                  po_id INT NOT NULL,
                  item_id INT NOT NULL,
                  qty INT NOT NULL,
                  price DECIMAL(10,2) NOT NULL DEFAULT 0.00,
                  CONSTRAINT fk_poi_po FOREIGN KEY (po_id) REFERENCES purchase_orders(id) ON DELETE CASCADE,
                  CONSTRAINT fk_poi_item FOREIGN KEY (item_id) REFERENCES items(id) ON DELETE RESTRICT
                )
                """);

            // Goods receipts
            st.execute("""
                CREATE TABLE IF NOT EXISTS goods_receipts (
                  id INT AUTO_INCREMENT PRIMARY KEY,
                  po_id INT NOT NULL,
                  received_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                  user_id INT NULL,
                  CONSTRAINT fk_grn_po FOREIGN KEY (po_id) REFERENCES purchase_orders(id) ON DELETE CASCADE
                )
                """);

            // Inventory movements (audit-friendly)
            st.execute("""
                CREATE TABLE IF NOT EXISTS inventory_movements (
                  id INT AUTO_INCREMENT PRIMARY KEY,
                  item_batch_id INT NOT NULL,
                  qty INT NOT NULL,
                  movement_type VARCHAR(20) NOT NULL, -- SALE, GRN, ADJUST, RETURN
                  ref_type VARCHAR(50),
                  ref_id INT NULL,
                  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                  user_id INT NULL,
                  CONSTRAINT fk_inv_mov_batch FOREIGN KEY (item_batch_id) REFERENCES item_batches(id) ON DELETE RESTRICT,
                  INDEX idx_inv_mov_batch (item_batch_id),
                  INDEX idx_inv_mov_type (movement_type, created_at)
                )
                """);

            // RBAC, Customers, Settings
            st.execute("""
                CREATE TABLE IF NOT EXISTS users (
                  id INT AUTO_INCREMENT PRIMARY KEY,
                  username VARCHAR(100) NOT NULL UNIQUE,
                  password_hash VARCHAR(255) NOT NULL,
                  display_name VARCHAR(255),
                  active TINYINT(1) NOT NULL DEFAULT 1,
                  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
                """);
            st.execute("""
                CREATE TABLE IF NOT EXISTS roles (
                  id INT AUTO_INCREMENT PRIMARY KEY,
                  name VARCHAR(100) NOT NULL UNIQUE
                )
                """);
            st.execute("""
                CREATE TABLE IF NOT EXISTS user_roles (
                  user_id INT NOT NULL,
                  role_id INT NOT NULL,
                  PRIMARY KEY (user_id, role_id),
                  CONSTRAINT fk_user_roles_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
                  CONSTRAINT fk_user_roles_role FOREIGN KEY (role_id) REFERENCES roles(id) ON DELETE CASCADE
                )
                """);
            st.execute("""
                CREATE TABLE IF NOT EXISTS permissions (
                  id INT AUTO_INCREMENT PRIMARY KEY,
                  name VARCHAR(150) NOT NULL UNIQUE
                )
                """);
            st.execute("""
                CREATE TABLE IF NOT EXISTS audit_logs (
                  id INT AUTO_INCREMENT PRIMARY KEY,
                  user_id INT NULL,
                  action VARCHAR(50) NOT NULL,
                  entity VARCHAR(100) NOT NULL,
                  entity_id VARCHAR(100) NULL,
                  details TEXT,
                  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                  CONSTRAINT fk_audit_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE SET NULL
                )
                """);
            st.execute("""
                CREATE TABLE IF NOT EXISTS customers (
                  id INT AUTO_INCREMENT PRIMARY KEY,
                  name VARCHAR(255) NOT NULL,
                  phone VARCHAR(50),
                  dob DATE NULL,
                  address VARCHAR(500),
                  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
                """);
            st.execute("""
                CREATE TABLE IF NOT EXISTS settings (
                  id INT AUTO_INCREMENT PRIMARY KEY,
                  k VARCHAR(100) NOT NULL UNIQUE,
                  v VARCHAR(1000)
                )
                """);

            // Sales (existing minimal POS schema)
            st.execute("""
                CREATE TABLE IF NOT EXISTS sales (
                  id INT AUTO_INCREMENT PRIMARY KEY,
                  customer VARCHAR(255),
                  sale_date DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                  subtotal DECIMAL(10,2) NOT NULL DEFAULT 0.00,
                  discount_pct DECIMAL(5,2) NOT NULL DEFAULT 0.00,
                  tax_pct DECIMAL(5,2) NOT NULL DEFAULT 0.00,
                  grand_total DECIMAL(10,2) NOT NULL DEFAULT 0.00
                )
                """);

            st.execute("""
                CREATE TABLE IF NOT EXISTS sale_items (
                  id INT AUTO_INCREMENT PRIMARY KEY,
                  sale_id INT NOT NULL,
                  item_name VARCHAR(255) NOT NULL,
                  qty INT NOT NULL DEFAULT 1,
                  unit_price DECIMAL(10,2) NOT NULL DEFAULT 0.00,
                  line_total DECIMAL(10,2) NOT NULL DEFAULT 0.00,
                  CONSTRAINT fk_sale_items_sales FOREIGN KEY (sale_id) REFERENCES sales(id) ON DELETE CASCADE
                )
                """);

            // Seed normalized items from legacy inventory_items (idempotent)
            st.execute("""
                INSERT INTO items (name, reorder_level)
                SELECT ii.name, MAX(ii.reorder_level)
                FROM inventory_items ii
                LEFT JOIN items i ON i.name = ii.name
                WHERE i.id IS NULL
                  AND ii.name IS NOT NULL AND ii.name <> ''
                GROUP BY ii.name
                """);

            // Seed item_batches from legacy inventory rows where missing (idempotent)
            st.execute("""
                INSERT INTO item_batches (item_id, batch_no, expiry_date, qty_on_hand, purchase_price, sell_price, location)
                SELECT i.id,
                       NULLIF(ii.batch, ''),
                       ii.expiry,
                       ii.quantity,
                       0.00,
                       ii.price,
                       NULL
                FROM inventory_items ii
                JOIN items i ON i.name = ii.name
                LEFT JOIN item_batches b
                  ON b.item_id = i.id
                 AND (
                      (b.batch_no IS NULL AND (ii.batch IS NULL OR ii.batch = ''))
                      OR b.batch_no = ii.batch
                 )
                WHERE b.id IS NULL
                  AND ii.quantity > 0
                """);
        }
    }

    private static void seedDemoData() throws SQLException {
        try (Connection c = getConnection();
             Statement st = c.createStatement()) {
            boolean hasData = false;
            try (PreparedStatement ps = c.prepareStatement("SELECT COUNT(*) FROM inventory_items");
                 java.sql.ResultSet rs = ps.executeQuery()) {
                if (rs.next()) hasData = rs.getInt(1) > 0;
            }
            if (!hasData) {
                try (PreparedStatement ins = c.prepareStatement(
                        "INSERT INTO inventory_items (name, category, batch, quantity, price, expiry, supplier, reorder_level) VALUES (?,?,?,?,?,?,?,?)")) {
                    // 1) Paracetamol 500mg - good stock
                    ins.setString(1, "Paracetamol 500mg");
                    ins.setString(2, "Analgesic");
                    ins.setString(3, "PCM2409A");
                    ins.setInt(4, 200);
                    ins.setDouble(5, 1.50);
                    ins.setDate(6, Date.valueOf(LocalDate.now().plusDays(180)));
                    ins.setString(7, "ACME Pharma");
                    ins.setInt(8, 50);
                    ins.addBatch();

                    // 2) Amoxicillin 250mg - near expiry
                    ins.setString(1, "Amoxicillin 250mg");
                    ins.setString(2, "Antibiotic");
                    ins.setString(3, "AMX2410B");
                    ins.setInt(4, 80);
                    ins.setDouble(5, 2.70);
                    ins.setDate(6, Date.valueOf(LocalDate.now().plusDays(25)));
                    ins.setString(7, "MedSupply Co");
                    ins.setInt(8, 40);
                    ins.addBatch();

                    // 3) Ibuprofen 200mg - expired and low
                    ins.setString(1, "Ibuprofen 200mg");
                    ins.setString(2, "NSAID");
                    ins.setString(3, "IBU2408C");
                    ins.setInt(4, 20);
                    ins.setDouble(5, 1.20);
                    ins.setDate(6, Date.valueOf(LocalDate.now().minusDays(5)));
                    ins.setString(7, "Wellness Labs");
                    ins.setInt(8, 30);
                    ins.addBatch();

                    // 4) Cetirizine 10mg
                    ins.setString(1, "Cetirizine 10mg");
                    ins.setString(2, "Antihistamine");
                    ins.setString(3, "CTZ2412D");
                    ins.setInt(4, 120);
                    ins.setDouble(5, 1.30);
                    ins.setDate(6, Date.valueOf(LocalDate.now().plusDays(60)));
                    ins.setString(7, "ACME Pharma");
                    ins.setInt(8, 20);
                    ins.addBatch();

                    // 5) Vitamin C 1000mg
                    ins.setString(1, "Vitamin C 1000mg");
                    ins.setString(2, "Supplement");
                    ins.setString(3, "VTC2407E");
                    ins.setInt(4, 300);
                    ins.setDouble(5, 0.80);
                    ins.setDate(6, Date.valueOf(LocalDate.now().plusDays(365)));
                    ins.setString(7, "NutriPlus");
                    ins.setInt(8, 100);
                    ins.addBatch();

                    ins.executeBatch();
                }

                // After inserting into legacy table, seed normalized items and batches (idempotent)
                st.execute("""
                    INSERT INTO items (name, reorder_level)
                    SELECT ii.name, MAX(ii.reorder_level)
                    FROM inventory_items ii
                    LEFT JOIN items i ON i.name = ii.name
                    WHERE i.id IS NULL
                      AND ii.name IS NOT NULL AND ii.name <> ''
                    GROUP BY ii.name
                    """);
                st.execute("""
                    INSERT INTO item_batches (item_id, batch_no, expiry_date, qty_on_hand, purchase_price, sell_price, location)
                    SELECT i.id,
                           NULLIF(ii.batch, ''),
                           ii.expiry,
                           ii.quantity,
                           0.00,
                           ii.price,
                           NULL
                    FROM inventory_items ii
                    JOIN items i ON i.name = ii.name
                    LEFT JOIN item_batches b
                      ON b.item_id = i.id
                     AND (
                          (b.batch_no IS NULL AND (ii.batch IS NULL OR ii.batch = ''))
                          OR b.batch_no = ii.batch
                     )
                    WHERE b.id IS NULL
                      AND ii.quantity > 0
                    """);
            }

            // Seed RBAC admin user/roles if none exist
            boolean hasUsers = false;
            try (PreparedStatement ps = c.prepareStatement("SELECT COUNT(*) FROM users");
                 java.sql.ResultSet rs = ps.executeQuery()) {
                if (rs.next()) hasUsers = rs.getInt(1) > 0;
            }
            if (!hasUsers) {
                // roles
                st.execute("INSERT INTO roles (name) VALUES ('ADMIN'),('PHARMACIST'),('ACCOUNTANT')");
                // admin user
                String hash = PasswordUtil.hashPassword("admin123".toCharArray());
                int adminId = 0;
                try (PreparedStatement ps = c.prepareStatement(
                        "INSERT INTO users (username, password_hash, display_name, active) VALUES (?,?,?,1)",
                        Statement.RETURN_GENERATED_KEYS)) {
                    ps.setString(1, "admin");
                    ps.setString(2, hash);
                    ps.setString(3, "Administrator");
                    ps.executeUpdate();
                    try (java.sql.ResultSet keys = ps.getGeneratedKeys()) {
                        if (keys.next()) adminId = keys.getInt(1);
                    }
                }
                // map role ADMIN
                int roleId = 0;
                try (PreparedStatement ps = c.prepareStatement("SELECT id FROM roles WHERE name='ADMIN'");
                     java.sql.ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) roleId = rs.getInt(1);
                }
                if (adminId > 0 && roleId > 0) {
                    try (PreparedStatement ps = c.prepareStatement("INSERT INTO user_roles (user_id, role_id) VALUES (?,?)")) {
                        ps.setInt(1, adminId);
                        ps.setInt(2, roleId);
                        ps.executeUpdate();
                    }
                }
            }

            // Seed a sample sale if none exists
            boolean hasSales = false;
            try (PreparedStatement ps = c.prepareStatement("SELECT COUNT(*) FROM sales");
                 java.sql.ResultSet rs = ps.executeQuery()) {
                if (rs.next()) hasSales = rs.getInt(1) > 0;
            }
            if (!hasSales) {
                int saleId = 0;
                try (PreparedStatement ps = c.prepareStatement(
                        "INSERT INTO sales (customer, subtotal, discount_pct, tax_pct, grand_total) VALUES (?,?,?,?,?)",
                        Statement.RETURN_GENERATED_KEYS)) {
                    double sub = 3 * 1.50 + 2 * 1.30;
                    ps.setString(1, "Walk-in");
                    ps.setDouble(2, sub);
                    ps.setDouble(3, 0.0);
                    ps.setDouble(4, 0.0);
                    ps.setDouble(5, sub);
                    ps.executeUpdate();
                    try (java.sql.ResultSet keys = ps.getGeneratedKeys()) {
                        if (keys.next()) saleId = keys.getInt(1);
                    }
                }
                if (saleId > 0) {
                    try (PreparedStatement ps = c.prepareStatement(
                            "INSERT INTO sale_items (sale_id, item_name, qty, unit_price, line_total) VALUES (?,?,?,?,?)")) {
                        ps.setInt(1, saleId);
                        ps.setString(2, "Paracetamol 500mg");
                        ps.setInt(3, 3);
                        ps.setDouble(4, 1.50);
                        ps.setDouble(5, 4.50);
                        ps.addBatch();

                        ps.setInt(1, saleId);
                        ps.setString(2, "Cetirizine 10mg");
                        ps.setInt(3, 2);
                        ps.setDouble(4, 1.30);
                        ps.setDouble(5, 2.60);
                        ps.addBatch();

                        ps.executeBatch();
                    }
                }
            }
        }
    }

    private static void test() throws SQLException {
        try (Connection c = getConnection();
             PreparedStatement ps = c.prepareStatement("SELECT 1")) {
            ps.execute();
        }
    }
}
