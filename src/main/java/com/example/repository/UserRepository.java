package com.example.repository;

import com.example.Database;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

public class UserRepository {

    public static class UserRecord {
        public final int id;
        public final String username;
        public final String displayName;
        public final boolean active;
        public final String passwordHash;

        public UserRecord(int id, String username, String displayName, boolean active, String passwordHash) {
            this.id = id;
            this.username = username;
            this.displayName = displayName;
            this.active = active;
            this.passwordHash = passwordHash;
        }
    }

    public UserRecord findByUsername(String username) {
        if (username == null || username.isBlank()) return null;
        String sql = "SELECT id, username, display_name, active, password_hash FROM users WHERE username = ?";
        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, username.trim());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return new UserRecord(
                        rs.getInt("id"),
                        rs.getString("username"),
                        rs.getString("display_name"),
                        rs.getBoolean("active"),
                        rs.getString("password_hash")
                    );
                }
            }
        } catch (Exception ignore) { }
        return null;
    }

    public Integer getUserIdByUsername(String username) {
        if (username == null || username.isBlank()) return null;
        String sql = "SELECT id FROM users WHERE username = ?";
        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, username.trim());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt(1);
            }
        } catch (Exception ignore) { }
        return null;
    }

    public boolean userHasRole(String username, String roleName) {
        String sql = "SELECT 1 FROM user_roles ur " +
                     "JOIN users u ON u.id = ur.user_id " +
                     "JOIN roles r ON r.id = ur.role_id " +
                     "WHERE u.username = ? AND r.name = ? LIMIT 1";
        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, username);
            ps.setString(2, roleName);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (Exception ignore) { }
        return false;
    }
}
