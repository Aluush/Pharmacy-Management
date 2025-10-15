package com.example.util;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.SecureRandom;
import java.security.spec.KeySpec;
import java.util.Base64;

public final class PasswordUtil {
    private static final int SALT_BYTES = 16;
    private static final int HASH_BYTES = 32; // 256-bit
    private static final int ITERATIONS = 120_000; // PBKDF2 iterations

    private PasswordUtil() {}

    public static String hashPassword(char[] password) {
        byte[] salt = new byte[SALT_BYTES];
        new SecureRandom().nextBytes(salt);
        byte[] hash = pbkdf2(password, salt, ITERATIONS, HASH_BYTES);
        // Store as: iterations:saltBase64:hashBase64
        return ITERATIONS + ":" + Base64.getEncoder().encodeToString(salt) + ":" + Base64.getEncoder().encodeToString(hash);
    }

    public static boolean verifyPassword(char[] password, String stored) {
        try {
            String[] parts = stored.split(":");
            int iters = Integer.parseInt(parts[0]);
            byte[] salt = Base64.getDecoder().decode(parts[1]);
            byte[] expected = Base64.getDecoder().decode(parts[2]);
            byte[] actual = pbkdf2(password, salt, iters, expected.length);
            return slowEquals(expected, actual);
        } catch (Exception e) {
            return false;
        }
    }

    private static byte[] pbkdf2(char[] password, byte[] salt, int iterations, int bytes) {
        try {
            KeySpec spec = new PBEKeySpec(password, salt, iterations, bytes * 8);
            SecretKeyFactory skf = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
            return skf.generateSecret(spec).getEncoded();
        } catch (Exception e) {
            throw new IllegalStateException("PBKDF2 failure", e);
        }
    }

    private static boolean slowEquals(byte[] a, byte[] b) {
        if (a == null || b == null) return false;
        int diff = a.length ^ b.length;
        for (int i = 0; i < Math.min(a.length, b.length); i++) {
            diff |= a[i] ^ b[i];
        }
        return diff == 0;
    }
}
