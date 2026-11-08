package com.threeline.merchantwallet.merchant.service;

public interface MerchantService {

    RegisteredMerchant register(RegisterMerchantCommand command);
}
