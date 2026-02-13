package dev.dentron.filestorage.common.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Random;

public final class DownloadTokenUtils {
    private static final SecureRandom RANDOM = new SecureRandom();
    private final byte[] pepper;

    public DownloadTokenUtils(String pepper) {
        this.pepper = pepper.getBytes(StandardCharsets.UTF_8);
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    public String generateToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public String hash(String token) {
        MessageDigest digest = sha256();
        digest.update(token.getBytes(StandardCharsets.UTF_8));
        digest.update((byte) 0);
        digest.update(pepper);
        byte[] hash = digest.digest();
        return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
    }
}
