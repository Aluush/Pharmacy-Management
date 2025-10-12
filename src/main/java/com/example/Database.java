package com.example;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;

public final class Database {
    private static final String HOST = "localhost";
    private static final int PORT = 3306;
    private static final String USER = "root";
    private static final String PASS = "";
    private static final String DB_NAME = "pharmapro";

    private static final String JDBC_BASE = "jdbc:mysql://" + HOST + ":" + PORT;
    private static final String JDBC_PARAMS = "?useSSL=false&serverTimezone=UTC";
    private static final String JDBC_DB_URL = JDBC_BASE + "/" + DB_NAME + JDBC_PARAMS;

    private Database() {}

    public static void bootstrap() throws SQLException {
        ensureDatabase();
        migrate();
        test();
    }

    public static Connection getConnection() throws SQLException {
        return DriverManager.getConnection(JDBC_DB_URL, USER, PASS);
    }

    private static void ensureDatabase() throws SQLException {
        try (Connection conn = DriverManager.getConnection(JDBC_BASE + "/" + JDBC_PARAMS, USER, PASS);
             Statement st = conn.createStatement()) {
            st.executeUpdate("CREATE DATABASE IF NOT EXISTS " + DB_NAME + " CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci");
        }
    }

    public static void migrate() throws SQLException {
        try (Connection conn = getConnection(); Statement st = conn.createStatement()) {
            // Inventory items table
            st.execute("""
                CREATE TABLE IF NOT EXISTS inventory_items (
                  id INT AUTO_INCREMENT PRIMARY KEY,
                  name VARCHAR(255) NOT NULL,
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

            // Sales table
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

            // Sale items table
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
        }
    }

    private static void test() throws SQLException {
        try (Connection c = getConnection();
             PreparedStatement ps = c.prepareStatement("SELECT 1")) {
            ps.execute();
        }
    }
}
