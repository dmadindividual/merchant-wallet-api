package com.threeline.merchantwallet.ledger.repository;

import com.threeline.merchantwallet.ledger.domain.Transaction;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

import static com.threeline.merchantwallet.ledger.repository.TransactionRowMapper.COLUMNS;

@Repository
public class JdbcTransactionRepository implements TransactionRepository {

    private final JdbcClient jdbc;

    public JdbcTransactionRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<Transaction> findById(long id) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM transactions WHERE id = :id")
                .param("id", id)
                .query(TransactionRowMapper.INSTANCE)
                .optional();
    }

    @Override
    public Optional<Transaction> findByReference(String reference) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM transactions WHERE reference = :reference")
                .param("reference", reference)
                .query(TransactionRowMapper.INSTANCE)
                .optional();
    }

    @Override
    public Optional<Transaction> findByMerchantAndIdempotencyKey(long merchantId, String idempotencyKey) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM transactions WHERE merchant_id = :merchantId AND idempotency_key = :key")
                .param("merchantId", merchantId)
                .param("key", idempotencyKey)
                .query(TransactionRowMapper.INSTANCE)
                .optional();
    }

    @Override
    public List<Transaction> findChildren(long parentTransactionId) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM transactions WHERE parent_transaction_id = :parentId ORDER BY id")
                .param("parentId", parentTransactionId)
                .query(TransactionRowMapper.INSTANCE)
                .list();
    }
}
