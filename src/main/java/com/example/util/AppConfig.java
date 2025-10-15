package com.example.util;

import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;

/**
 * Centralized non-UI application configuration with profiles and hot-reload.
 *
 * Load order (lowest priority to highest):
 *  1) Built-in defaults (hardcoded)
 *  2) classpath:/application.properties
 *  3) ~/.pharmapro/application.properties
 *  4) classpath:/application-{profile}.properties (if profile set)
 *  5) ~/.pharmapro/application-{profile}.properties (if exists)
 *  6) System properties (-Dkey=value)
 *  7) Environment variables (mapped to keys)
 *
 * Saving:
 *  - Saves to ~/.pharmapro/application-{activeProfileOrBase}.properties
 *  - Does NOT attempt to modify classpath resources
 *
 * Profile:
 *  - Determined from (in order): -Dprofile, $PROFILE, $APP_PROFILE
 *  - Methods provided to get/set active profile, and reload config.
 */
public final class AppConfig {
    private static final AppConfig INSTANCE = new AppConfig();

    private final Properties props = new Properties();
    private String activeProfile; // e.g. "dev", "test", "prod", null for base
    private final Path userConfigDir = Paths.get(System.getProperty("user.home"), ".pharmapro");

    private AppConfig() {
        reload();
    }

    public static AppConfig get() {
        return INSTANCE;
    }

    // ---------- Public API ----------

    public synchronized void reload() {
        props.clear();
        // Defaults
        setDefault("app.title", "PharmaPro - Pharmacy Management");
        setDefault("db.type", "mysql");
        setDefault("db.host", "localhost");
        setDefault("db.port", "3306");
        setDefault("db.name", "pharmapro");
        setDefault("db.user", "root");
        setDefault("db.pass", "");
        setDefault("db.params", "useSSL=false&serverTimezone=UTC");
        setDefault("ui.theme", "light");

        // Determine profile
        this.activeProfile = resolveProfile();

        // Base files
        loadFromClasspath("/application.properties");
        loadFromUserFile("application.properties");

        // Profile overlays
        if (activeProfile != null && !activeProfile.isBlank()) {
            String cp = "/application-" + activeProfile + ".properties";
            loadFromClasspath(cp);
            loadFromUserFile("application-" + activeProfile + ".properties");
        }

        // System/env overrides
        overrideFromSystem("app.title", "APP_TITLE");
        overrideFromSystem("db.type", "DB_TYPE");
        overrideFromSystem("db.host", "DB_HOST");
        overrideFromSystem("db.port", "DB_PORT");
        overrideFromSystem("db.name", "DB_NAME");
        overrideFromSystem("db.user", "DB_USER");
        overrideFromSystem("db.pass", "DB_PASS");
        overrideFromSystem("db.params", "DB_PARAMS");
        overrideFromSystem("db.url", "DB_URL");
    }

    public synchronized boolean saveToUserConfig() {
        try {
            if (!Files.exists(userConfigDir)) {
                Files.createDirectories(userConfigDir);
            }
            String fileName = (activeProfile == null || activeProfile.isBlank())
                ? "application.properties"
                : ("application-" + activeProfile + ".properties");

            Path out = userConfigDir.resolve(fileName);

            try (OutputStream os = new FileOutputStream(out.toFile())) {
                Properties toStore = new Properties();
                // store effective values (props)
                toStore.putAll(props);
                toStore.store(os, "PharmaPro configuration (" + (activeProfile == null ? "base" : activeProfile) + ")");
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public synchronized void update(Map<String, String> changes) {
        if (changes == null) return;
        for (Map.Entry<String, String> e : changes.entrySet()) {
            if (e.getKey() == null) continue;
            props.setProperty(e.getKey(), e.getValue() == null ? "" : e.getValue());
        }
    }

    public synchronized String getActiveProfile() {
        return activeProfile;
    }

    public synchronized void setActiveProfile(String profile) {
        String p = (profile == null || profile.isBlank()) ? null : profile.trim().toLowerCase(Locale.ROOT);
        this.activeProfile = p;
        // Re-apply load order with new profile
        reload();
    }

    // ---------- Getters ----------

    public String getAppTitle() {
        return getProp("app.title");
    }

    public String getThemeMode() {
        String m = getProp("ui.theme");
        return (m == null || m.isBlank()) ? "light" : m.trim().toLowerCase(Locale.ROOT);
    }

    public void setThemeMode(String mode) {
        String m = (mode == null ? "light" : mode.trim().toLowerCase(Locale.ROOT));
        props.setProperty("ui.theme", m);
    }

    public String getDbType() {
        return getProp("db.type").toLowerCase(Locale.ROOT);
    }

    public boolean isMySql() {
        return "mysql".equalsIgnoreCase(getDbType());
    }

    public boolean isPostgres() {
        return "postgres".equalsIgnoreCase(getDbType()) || "postgresql".equalsIgnoreCase(getDbType());
    }

    public boolean isSqlite() {
        return "sqlite".equalsIgnoreCase(getDbType());
    }

    public String getDbHost() {
        return getProp("db.host");
    }

    public int getDbPort() {
        try {
            return Integer.parseInt(getProp("db.port"));
        } catch (NumberFormatException e) {
            return isPostgres() ? 5432 : 3306;
        }
    }

    public String getDbName() {
        return getProp("db.name");
    }

    public String getDbUser() {
        return getProp("db.user");
    }

    public String getDbPass() {
        return getProp("db.pass");
    }

    public String getDbParams() {
        return getProp("db.params");
    }

    public String getExplicitJdbcUrl() {
        String url = props.getProperty("db.url");
        return (url == null || url.isBlank()) ? null : url;
    }

    /**
     * JDBC URL for connecting to the database (with database selected).
     * If db.url is provided, it is returned as-is.
     */
    public String getJdbcUrl() {
        String explicit = getExplicitJdbcUrl();
        if (explicit != null) return explicit;

        String params = getDbParams();
        String paramsPrefix = (params == null || params.isBlank()) ? "" : (params.startsWith("?") ? params : "?" + params);

        if (isMySql()) {
            return "jdbc:mysql://" + getDbHost() + ":" + getDbPort() + "/" + getDbName() + paramsPrefix;
        } else if (isPostgres()) {
            return "jdbc:postgresql://" + getDbHost() + ":" + getDbPort() + "/" + getDbName() + paramsPrefix;
        } else if (isSqlite()) {
            // db.name acts as filename/path for sqlite
            return "jdbc:sqlite:" + getDbName();
        }
        // Fallback to MySQL format
        return "jdbc:mysql://" + getDbHost() + ":" + getDbPort() + "/" + getDbName() + paramsPrefix;
    }

    /**
     * JDBC URL for server-level connection (no database), used only where needed.
     */
    public String getJdbcServerUrl() {
        String explicit = getExplicitJdbcUrl();
        if (explicit != null) {
            // Cannot reliably derive server URL from explicit string; return explicit
            return explicit;
        }
        String params = getDbParams();
        String paramsPrefix = (params == null || params.isBlank()) ? "" : (params.startsWith("?") ? params : "?" + params);

        if (isMySql()) {
            return "jdbc:mysql://" + getDbHost() + ":" + getDbPort() + "/" + paramsPrefix;
        } else if (isPostgres()) {
            return "jdbc:postgresql://" + getDbHost() + ":" + getDbPort() + "/" + paramsPrefix;
        }
        // Not applicable for sqlite
        return null;
    }

    // ---------- Internals ----------

    private String resolveProfile() {
        String p = System.getProperty("profile");
        if (p == null || p.isBlank()) p = System.getenv("PROFILE");
        if (p == null || p.isBlank()) p = System.getenv("APP_PROFILE");
        if (p == null || p.isBlank()) return null;
        return p.trim().toLowerCase(Locale.ROOT);
    }

    private void setDefault(String key, String value) {
        if (!props.containsKey(key)) {
            props.setProperty(key, value);
        }
    }

    private void overrideFromSystem(String key, String env) {
        String sys = System.getProperty(key);
        if (sys != null) {
            props.setProperty(key, sys);
            return;
        }
        String e = System.getenv(env);
        if (e != null) {
            props.setProperty(key, e);
        }
    }

    private void loadFromClasspath(String resourcePath) {
        try (InputStream in = AppConfig.class.getResourceAsStream(resourcePath)) {
            if (in != null) {
                Properties loaded = new Properties();
                loaded.load(in);
                props.putAll(loaded);
            }
        } catch (Exception ignore) {
        }
    }

    private void loadFromUserFile(String fileName) {
        try {
            Path p = userConfigDir.resolve(fileName);
            if (Files.exists(p)) {
                try (InputStream in = Files.newInputStream(p)) {
                    Properties loaded = new Properties();
                    loaded.load(in);
                    props.putAll(loaded);
                }
            }
        } catch (Exception ignore) {
        }
    }

    private String getProp(String key) {
        return Objects.toString(props.getProperty(key), "");
    }
}
