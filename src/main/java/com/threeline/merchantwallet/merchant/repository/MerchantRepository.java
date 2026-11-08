package com.threeline.merchantwallet.merchant.repository;

import com.threeline.merchantwallet.merchant.domain.Merchant;

import java.util.Optional;
import java.util.UUID;

public interface MerchantRepository {

    Merchant insert(NewMerchant merchant);

    Optional<Merchant> findById(long id);

    Optional<Merchant> findByPublicId(UUID publicId);

    Optional<Merchant> findByApiKeyHash(String apiKeyHash);

    boolean existsByEmail(String email);

    boolean existsByRcNumber(String rcNumber);
}
