package com.threeline.merchantwallet.dynamicaccount.service;

import com.threeline.merchantwallet.common.context.RequestContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@ConditionalOnProperty(prefix = "wallet.dynamic-accounts", name = "expiry-job-enabled",
        havingValue = "true", matchIfMissing = true)
public class DynamicAccountExpiryJob {

    public static final String ACTOR = "system:dynamic-account-expiry";

    private final DynamicAccountService service;

    public DynamicAccountExpiryJob(DynamicAccountService service) {
        this.service = service;
    }

    @Scheduled(fixedDelayString = "${wallet.dynamic-accounts.expiry-interval:PT30S}",
               initialDelayString = "${wallet.dynamic-accounts.expiry-interval:PT30S}")
    public void run() {
        try {
            RequestContext.callAs(ACTOR, service::expireDue);
        } catch (RuntimeException e) {

            log.error("Dynamic account expiry run failed", e);
        }
    }
}
