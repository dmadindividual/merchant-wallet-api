package com.threeline.merchantwallet.account.repository;

import com.threeline.merchantwallet.account.domain.Account;
import com.threeline.merchantwallet.account.domain.AccountStatus;
import com.threeline.merchantwallet.account.domain.AccountType;
import com.threeline.merchantwallet.ledger.domain.EntrySide;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.sql.SQLException;

import static com.threeline.merchantwallet.common.db.JdbcColumns.*;

public final class AccountRowMapper implements RowMapper<Account> {

    public static final AccountRowMapper INSTANCE = new AccountRowMapper();

    public static final String COLUMNS = """
            id, merchant_id, account_number, system_code, account_name, account_type, normal_balance,
            currency, balance, auto_sweep, first_name, last_name, email, phone, status,
            created_at, updated_at""";

    private AccountRowMapper() {
    }

    @Override
    public Account mapRow(ResultSet rs, int rowNum) throws SQLException {
        return Account.builder()
                .id(rs.getLong("id"))
                .merchantId(nullableLong(rs, "merchant_id"))
                .accountNumber(rs.getString("account_number"))
                .systemCode(rs.getString("system_code"))
                .accountName(rs.getString("account_name"))
                .type(enumValue(rs, "account_type", AccountType.class))
                .normalBalance(enumValue(rs, "normal_balance", EntrySide.class))
                .currency(rs.getString("currency"))
                .balanceKobo(rs.getLong("balance"))
                .autoSweep(rs.getBoolean("auto_sweep"))
                .firstName(rs.getString("first_name"))
                .lastName(rs.getString("last_name"))
                .email(rs.getString("email"))
                .phone(rs.getString("phone"))
                .status(enumValue(rs, "status", AccountStatus.class))
                .createdAt(instant(rs, "created_at"))
                .updatedAt(instant(rs, "updated_at"))
                .build();
    }
}
