package com.threeline.merchantwallet.dynamicaccount.repository;

import com.threeline.merchantwallet.dynamicaccount.domain.DynamicAccount;
import com.threeline.merchantwallet.dynamicaccount.domain.DynamicAccountStatus;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.sql.SQLException;

import static com.threeline.merchantwallet.common.db.JdbcColumns.*;

public final class DynamicAccountRowMapper implements RowMapper<DynamicAccount> {

    public static final DynamicAccountRowMapper INSTANCE = new DynamicAccountRowMapper();

    public static final String COLUMNS = """
            id, reference, merchant_id, account_number, account_name, expected_amount, status,
            expires_at, paid_transaction_id, created_at, updated_at""";

    private DynamicAccountRowMapper() {
    }

    @Override
    public DynamicAccount mapRow(ResultSet rs, int rowNum) throws SQLException {
        return DynamicAccount.builder()
                .id(rs.getLong("id"))
                .reference(rs.getString("reference"))
                .merchantId(rs.getLong("merchant_id"))
                .accountNumber(rs.getString("account_number"))
                .accountName(rs.getString("account_name"))
                .expectedAmountKobo(nullableLong(rs, "expected_amount"))
                .status(enumValue(rs, "status", DynamicAccountStatus.class))
                .expiresAt(instant(rs, "expires_at"))
                .paidTransactionId(nullableLong(rs, "paid_transaction_id"))
                .createdAt(instant(rs, "created_at"))
                .updatedAt(instant(rs, "updated_at"))
                .build();
    }
}
