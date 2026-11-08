package com.threeline.merchantwallet.merchant.service;

import com.threeline.merchantwallet.account.domain.Account;
import com.threeline.merchantwallet.merchant.domain.Merchant;

public record RegisteredMerchant(Merchant merchant, Account primaryAccount, String apiKey) {

    @Override
    public String toString() {
        return "RegisteredMerchant[merchant=" + merchant.getPublicId()
                + ", primaryAccount=" + primaryAccount.getAccountNumber() + ", apiKey=***]";
    }
}
