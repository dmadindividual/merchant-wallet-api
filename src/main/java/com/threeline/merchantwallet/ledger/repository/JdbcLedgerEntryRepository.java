package com.threeline.merchantwallet.ledger.repository;

import com.threeline.merchantwallet.ledger.domain.EntrySide;
import com.threeline.merchantwallet.ledger.domain.LedgerEntry;
import com.threeline.merchantwallet.ledger.domain.StatementEntry;
import com.threeline.merchantwallet.ledger.domain.TransactionType;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.List;

import static com.threeline.merchantwallet.common.db.JdbcColumns.enumValue;
import static com.threeline.merchantwallet.common.db.JdbcColumns.instant;
import static com.threeline.merchantwallet.ledger.repository.LedgerEntryRowMapper.COLUMNS;

@Repository
public class JdbcLedgerEntryRepository implements LedgerEntryRepository {

    private final JdbcClient jdbc;

    public JdbcLedgerEntryRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<LedgerEntry> findByAccount(long accountId, Long beforeId, int limit) {

        long before = beforeId == null ? Long.MAX_VALUE : beforeId;
        return jdbc.sql("""
                        SELECT %s FROM ledger_entries
                        WHERE account_id = :accountId AND id < :before
                        ORDER BY id DESC
                        LIMIT :limit
                        """.formatted(COLUMNS))
                .param("accountId", accountId)
                .param("before", before)
                .param("limit", limit)
                .query(LedgerEntryRowMapper.INSTANCE)
                .list();
    }

    @Override
    public List<LedgerEntry> findByTransaction(long transactionId) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM ledger_entries WHERE transaction_id = :txnId ORDER BY id")
                .param("txnId", transactionId)
                .query(LedgerEntryRowMapper.INSTANCE)
                .list();
    }

    @Override
    public long sumBalanceFromEntries(long accountId) {
        return jdbc.sql("""
                        SELECT COALESCE(SUM(CASE WHEN e.entry_type = a.normal_balance THEN e.amount ELSE -e.amount END), 0)
                        FROM accounts a
                        LEFT JOIN ledger_entries e ON e.account_id = a.id
                        WHERE a.id = :accountId
                        """)
                .param("accountId", accountId)
                .query(Long.class)
                .single();
    }

    private static final RowMapper<StatementEntry> STATEMENT_ROW = (rs, n) -> StatementEntry.builder()
            .entryId(rs.getLong("id"))
            .transactionReference(rs.getString("reference"))
            .transactionType(enumValue(rs, "transaction_type", TransactionType.class))
            .side(enumValue(rs, "entry_type", EntrySide.class))
            .amountKobo(rs.getLong("amount"))
            .balanceAfterKobo(rs.getLong("balance_after"))
            .narration(rs.getString("narration"))
            .counterparty(rs.getString("counterparty"))
            .createdAt(instant(rs, "created_at"))
            .build();

    @Override
    public List<StatementEntry> findStatement(long accountId, Long beforeId, int limit) {
        long before = beforeId == null ? Long.MAX_VALUE : beforeId;
        return jdbc.sql("""
                        SELECT e.id, e.entry_type, e.amount, e.balance_after, e.created_at,
                               t.reference, t.transaction_type, t.narration,
                               (SELECT COALESCE(a2.account_number, a2.system_code)
                                FROM ledger_entries e2
                                JOIN accounts a2 ON a2.id = e2.account_id
                                WHERE e2.transaction_id = e.transaction_id AND e2.account_id <> e.account_id
                                ORDER BY e2.id
                                LIMIT 1) AS counterparty
                        FROM ledger_entries e
                        JOIN transactions t ON t.id = e.transaction_id
                        WHERE e.account_id = :accountId AND e.id < :before
                        ORDER BY e.id DESC
                        LIMIT :limit
                        """)
                .param("accountId", accountId)
                .param("before", before)
                .param("limit", limit)
                .query(STATEMENT_ROW)
                .list();
    }
}
