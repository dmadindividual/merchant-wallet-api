package com.threeline.merchantwallet.ledger.repository;

import com.threeline.merchantwallet.ledger.domain.Transaction;
import com.threeline.merchantwallet.ledger.domain.TransactionStatus;
import com.threeline.merchantwallet.ledger.domain.TransactionType;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.sql.SQLException;

import static com.threeline.merchantwallet.common.db.JdbcColumns.*;

public final class TransactionRowMapper implements RowMapper<Transaction> {

    public static final TransactionRowMapper INSTANCE = new TransactionRowMapper();

    public static final String COLUMNS = """
            id, reference, merchant_id, transaction_type, status, amount, currency,
            source_account_id, destination_account_id, parent_transaction_id, dynamic_account_id,
            idempotency_key, request_hash, external_reference, narration, failure_code, failure_reason,
            created_at, completed_at""";

    private TransactionRowMapper() {
    }

    @Override
    public Transaction mapRow(ResultSet rs, int rowNum) throws SQLException {
        return Transaction.builder()
                .id(rs.getLong("id"))
                .reference(rs.getString("reference"))
                .merchantId(nullableLong(rs, "merchant_id"))
                .type(enumValue(rs, "transaction_type", TransactionType.class))
                .status(enumValue(rs, "status", TransactionStatus.class))
                .amountKobo(rs.getLong("amount"))
                .currency(rs.getString("currency"))
                .sourceAccountId(nullableLong(rs, "source_account_id"))
                .destinationAccountId(nullableLong(rs, "destination_account_id"))
                .parentTransactionId(nullableLong(rs, "parent_transaction_id"))
                .dynamicAccountId(nullableLong(rs, "dynamic_account_id"))
                .idempotencyKey(rs.getString("idempotency_key"))
                .requestHash(rs.getString("request_hash"))
                .externalReference(rs.getString("external_reference"))
                .narration(rs.getString("narration"))
                .failureCode(rs.getString("failure_code"))
                .failureReason(rs.getString("failure_reason"))
                .createdAt(instant(rs, "created_at"))
                .completedAt(instant(rs, "completed_at"))
                .build();
    }
}
