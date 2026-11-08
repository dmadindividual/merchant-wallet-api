package com.threeline.merchantwallet.dynamicaccount.service;

import com.threeline.merchantwallet.dynamicaccount.domain.DynamicAccount;

public record DynamicAccountView(DynamicAccount account, String paymentReference) {
}
