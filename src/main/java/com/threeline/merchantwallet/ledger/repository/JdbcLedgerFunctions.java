package com.threeline.merchantwallet.ledger.repository;

import com.threeline.merchantwallet.common.db.JdbcColumns;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcLedgerFunctions implements LedgerFunctions {

    private static final RowMapper<PostingOutcome> OUTCOME = (rs, n) -> new PostingOutcome(
            JdbcColumns.nullableLong(rs, "out_transaction_id"),
            rs.getBoolean("out_replayed"),
            rs.getString("out_error_code"));

    private final JdbcClient jdbc;

    public JdbcLedgerFunctions(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public PostingOutcome transfer(long merchantId, String sourceAccountNumber, String destinationAccountNumber,
                                   long amountKobo, String narration, String idempotencyKey, String requestHash) {
        return jdbc.sql("""
                        SELECT out_transaction_id, out_replayed, out_error_code
                        FROM fn_transfer(:merchantId::bigint, :source::text, :destination::text, :amount::bigint,
                                         :narration::text, :idempotencyKey::text, :requestHash::text)
                        """)
                .param("merchantId", merchantId)
                .param("source", sourceAccountNumber)
                .param("destination", destinationAccountNumber)
                .param("amount", amountKobo)
                .param("narration", narration)
                .param("idempotencyKey", idempotencyKey)
                .param("requestHash", requestHash)
                .query(OUTCOME)
                .single();
    }

    @Override
    public PostingOutcome inflow(String accountNumber, long amountKobo, String sessionId,
                                 String senderName, String narration) {
        return jdbc.sql("""
                        SELECT out_transaction_id, out_replayed, out_error_code
                        FROM fn_post_inflow(:accountNumber::text, :amount::bigint, :sessionId::text,
                                            :senderName::text, :narration::text)
                        """)
                .param("accountNumber", accountNumber)
                .param("amount", amountKobo)
                .param("sessionId", sessionId)
                .param("senderName", senderName)
                .param("narration", narration)
                .query(OUTCOME)
                .single();
    }
}
