package com.threeline.merchantwallet.account.repository;

import com.threeline.merchantwallet.account.domain.AccountType;

public record NewAccount(
        long merchantId,
        String accountNumber,
        String accountName,
        AccountType type,
        boolean autoSweep,
        String firstName,
        String lastName,
        String email,
        String phone
) {

    public static NewAccount primary(long merchantId, String accountNumber, String accountName) {
        return new NewAccount(merchantId, accountNumber, accountName, AccountType.PRIMARY,
                false, null, null, null, null);
    }

    public static NewAccount subsidiary(long merchantId, String accountNumber, String accountName,
                                        String firstName, String lastName, String email, String phone,
                                        boolean autoSweep) {
        return new NewAccount(merchantId, accountNumber, accountName, AccountType.SUBSIDIARY,
                autoSweep, firstName, lastName, email, phone);
    }
}
