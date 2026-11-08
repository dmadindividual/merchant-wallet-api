package com.threeline.merchantwallet.dynamicaccount.service;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

@ConfigurationProperties(prefix = "wallet.dynamic-accounts")
public record DynamicAccountProperties(
        @DefaultValue("PT10M") Duration ttl,
        @DefaultValue("PT24H") Duration cooldown,
        @DefaultValue("true") boolean expiryJobEnabled,
        @DefaultValue("PT30S") Duration expiryInterval,
        @DefaultValue("500") int expiryBatchSize
) {
    public DynamicAccountProperties {
        if (ttl.isNegative() || ttl.toSeconds() < 1) {
            throw new IllegalArgumentException("wallet.dynamic-accounts.ttl must be at least 1 second");
        }
        if (cooldown.isNegative()) {
            throw new IllegalArgumentException("wallet.dynamic-accounts.cooldown must not be negative");
        }
    }
}
