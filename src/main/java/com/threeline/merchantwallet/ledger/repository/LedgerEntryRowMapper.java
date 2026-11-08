package com.threeline.merchantwallet.ledger.repository;

import com.threeline.merchantwallet.ledger.domain.EntrySide;
import com.threeline.merchantwallet.ledger.domain.LedgerEntry;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.sql.SQLException;

import static com.threeline.merchantwallet.common.db.JdbcColumns.enumValue;
import static com.threeline.merchantwallet.common.db.JdbcColumns.instant;

public final class LedgerEntryRowMapper implements RowMapper<LedgerEntry> {

    public static final LedgerEntryRowMapper INSTANCE = new LedgerEntryRowMapper();

    public static final String COLUMNS = "id, transaction_id, account_id, entry_type, amount, balance_after, created_at";

    private LedgerEntryRowMapper() {
    }

    @Override
    public LedgerEntry mapRow(ResultSet rs, int rowNum) throws SQLException {
        return LedgerEntry.builder()
                .id(rs.getLong("id"))
                .transactionId(rs.getLong("transaction_id"))
                .accountId(rs.getLong("account_id"))
                .side(enumValue(rs, "entry_type", EntrySide.class))
                .amountKobo(rs.getLong("amount"))
                .balanceAfterKobo(rs.getLong("balance_after"))
                .createdAt(instant(rs, "created_at"))
                .build();
    }
}
