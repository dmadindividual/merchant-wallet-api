package com.threeline.merchantwallet.common.security;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class ApiKeysTest {

    private final ApiKeys apiKeys = new ApiKeys();

    @Test
    void keysArePrefixedLongAndUnique() {
        Set<String> keys = new HashSet<>();
        for (int i = 0; i < 1_000; i++) {
            String key = apiKeys.generate();
            assertThat(key).startsWith(ApiKeys.PREFIX).hasSize(ApiKeys.PREFIX.length() + 43);
            assertThat(keys.add(key)).isTrue();
        }
    }

    @Test
    void hashIsDeterministicSha256Hex() {
        assertThat(apiKeys.hash("mw_test")).isEqualTo(apiKeys.hash("mw_test")).hasSize(64).matches("[0-9a-f]{64}");
        assertThat(apiKeys.hash("mw_test")).isNotEqualTo(apiKeys.hash("mw_test2"));
    }
}
