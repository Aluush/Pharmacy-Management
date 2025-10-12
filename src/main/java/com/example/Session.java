package com.example;

public final class Session {
    private static String username = "";
    private static String displayName = "";

    private Session() {}

    public static void setUser(String user, String display) {
        username = user == null ? "" : user;
        displayName = (display == null || display.isBlank()) ? username : display;
    }

    public static String getUsername() {
        return username;
    }

    public static String getDisplayName() {
        return (displayName == null || displayName.isBlank()) ? username : displayName;
    }

    public static boolean isLoggedIn() {
        return username != null && !username.isBlank();
    }

    public static void clear() {
        username = "";
        displayName = "";
    }
}
