package com.threeline.merchantwallet.common.security;

import com.threeline.merchantwallet.common.crypto.Hashing;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.util.Base64;

@Component
public class ApiKeys {

    public static final String PREFIX = "mw_";
    private static final int KEY_BYTES = 32;

    private final SecureRandom random = new SecureRandom();

    public String generate() {
        byte[] bytes = new byte[KEY_BYTES];
        random.nextBytes(bytes);
        return PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public String hash(String rawKey) {
        return Hashing.sha256Hex(rawKey);
    }
}
