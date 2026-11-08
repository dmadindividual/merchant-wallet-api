package com.threeline.merchantwallet.dynamicaccount.service;

import com.threeline.merchantwallet.dynamicaccount.repository.ExpiryOutcome;
import com.threeline.merchantwallet.merchant.domain.Merchant;

import java.util.List;

public interface DynamicAccountService {

    DynamicAccountView create(Merchant merchant, CreateDynamicAccountCommand command);

    DynamicAccountView get(long merchantId, String reference);

    List<DynamicAccountView> listRecent(long merchantId, int limit);

    ExpiryOutcome expireDue();
}
