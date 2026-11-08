package com.threeline.merchantwallet.merchant.repository;

import com.threeline.merchantwallet.merchant.domain.Merchant;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

import static com.threeline.merchantwallet.merchant.repository.MerchantRowMapper.COLUMNS;

@Repository
public class JdbcMerchantRepository implements MerchantRepository {

    private final JdbcClient jdbc;

    public JdbcMerchantRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Merchant insert(NewMerchant m) {
        return jdbc.sql("""
                        INSERT INTO merchants (business_name, business_type, rc_number, email, phone,
                                               address, api_key_hash, webhook_url)
                        VALUES (:businessName, :businessType, :rcNumber, :email, :phone,
                                :address, :apiKeyHash, :webhookUrl)
                        RETURNING %s
                        """.formatted(COLUMNS))
                .param("businessName", m.businessName())
                .param("businessType", m.businessType().name())
                .param("rcNumber", m.rcNumber())
                .param("email", m.email())
                .param("phone", m.phone())
                .param("address", m.address())
                .param("apiKeyHash", m.apiKeyHash())
                .param("webhookUrl", m.webhookUrl())
                .query(MerchantRowMapper.INSTANCE)
                .single();
    }

    @Override
    public Optional<Merchant> findById(long id) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM merchants WHERE id = :id")
                .param("id", id)
                .query(MerchantRowMapper.INSTANCE)
                .optional();
    }

    @Override
    public Optional<Merchant> findByPublicId(UUID publicId) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM merchants WHERE public_id = :publicId")
                .param("publicId", publicId)
                .query(MerchantRowMapper.INSTANCE)
                .optional();
    }

    @Override
    public Optional<Merchant> findByApiKeyHash(String apiKeyHash) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM merchants WHERE api_key_hash = :hash")
                .param("hash", apiKeyHash)
                .query(MerchantRowMapper.INSTANCE)
                .optional();
    }

    @Override
    public boolean existsByEmail(String email) {
        return jdbc.sql("SELECT EXISTS (SELECT 1 FROM merchants WHERE lower(email) = lower(:email))")
                .param("email", email)
                .query(Boolean.class)
                .single();
    }

    @Override
    public boolean existsByRcNumber(String rcNumber) {
        return jdbc.sql("SELECT EXISTS (SELECT 1 FROM merchants WHERE rc_number = :rc)")
                .param("rc", rcNumber)
                .query(Boolean.class)
                .single();
    }
}
