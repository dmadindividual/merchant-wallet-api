package com.threeline.merchantwallet.dynamicaccount.repository;

import com.threeline.merchantwallet.common.db.JdbcColumns;
import com.threeline.merchantwallet.dynamicaccount.domain.DynamicAccount;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

import static com.threeline.merchantwallet.dynamicaccount.repository.DynamicAccountRowMapper.COLUMNS;

@Repository
public class JdbcDynamicAccountRepository implements DynamicAccountRepository {

    private final JdbcClient jdbc;

    public JdbcDynamicAccountRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<DynamicAccount> findById(long id) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM dynamic_accounts WHERE id = :id")
                .param("id", id)
                .query(DynamicAccountRowMapper.INSTANCE)
                .optional();
    }

    @Override
    public Optional<DynamicAccount> findByReference(String reference) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM dynamic_accounts WHERE reference = :reference")
                .param("reference", reference)
                .query(DynamicAccountRowMapper.INSTANCE)
                .optional();
    }

    @Override
    public List<DynamicAccount> findRecentByMerchant(long merchantId, int limit) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM dynamic_accounts WHERE merchant_id = :merchantId ORDER BY id DESC LIMIT :limit")
                .param("merchantId", merchantId)
                .param("limit", limit)
                .query(DynamicAccountRowMapper.INSTANCE)
                .list();
    }

    @Override
    public void addNumberToPool(String accountNumber) {
        jdbc.sql("INSERT INTO dynamic_account_numbers (account_number) VALUES (:number)")
                .param("number", accountNumber)
                .update();
    }

    @Override
    public AllocationOutcome allocate(long merchantId, String accountName, Long expectedAmountKobo, int ttlSeconds) {
        return jdbc.sql("""
                        SELECT out_dynamic_account_id, out_error_code
                        FROM fn_allocate_dynamic_account(:merchantId::bigint, :accountName::text,
                                                         :expectedAmount::bigint, :ttlSeconds::int)
                        """)
                .param("merchantId", merchantId)
                .param("accountName", accountName)
                .param("expectedAmount", expectedAmountKobo)
                .param("ttlSeconds", ttlSeconds)
                .query((rs, n) -> new AllocationOutcome(
                        JdbcColumns.nullableLong(rs, "out_dynamic_account_id"),
                        rs.getString("out_error_code")))
                .single();
    }

    @Override
    public ExpiryOutcome expireDue(int cooldownSeconds, int batchSize) {
        return jdbc.sql("SELECT out_expired, out_released FROM fn_expire_dynamic_accounts(:cooldown::int, :batch::int)")
                .param("cooldown", cooldownSeconds)
                .param("batch", batchSize)
                .query((rs, n) -> new ExpiryOutcome(rs.getInt("out_expired"), rs.getInt("out_released")))
                .single();
    }
}
