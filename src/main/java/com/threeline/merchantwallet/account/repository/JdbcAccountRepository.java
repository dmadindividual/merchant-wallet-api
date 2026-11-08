package com.threeline.merchantwallet.account.repository;

import com.threeline.merchantwallet.account.domain.Account;
import com.threeline.merchantwallet.account.domain.AccountType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

import static com.threeline.merchantwallet.account.repository.AccountRowMapper.COLUMNS;

@Repository
public class JdbcAccountRepository implements AccountRepository {

    private final JdbcClient jdbc;

    public JdbcAccountRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Account insert(NewAccount a) {
        if (a.type() == AccountType.SYSTEM) {
            throw new IllegalArgumentException("SYSTEM accounts are created by migrations only");
        }

        return jdbc.sql("""
                        INSERT INTO accounts (merchant_id, account_number, account_name, account_type,
                                              normal_balance, auto_sweep, first_name, last_name, email, phone)
                        VALUES (:merchantId, :accountNumber, :accountName, :type,
                                'CREDIT', :autoSweep, :firstName, :lastName, :email, :phone)
                        RETURNING %s
                        """.formatted(COLUMNS))
                .param("merchantId", a.merchantId())
                .param("accountNumber", a.accountNumber())
                .param("accountName", a.accountName())
                .param("type", a.type().name())
                .param("autoSweep", a.autoSweep())
                .param("firstName", a.firstName())
                .param("lastName", a.lastName())
                .param("email", a.email())
                .param("phone", a.phone())
                .query(AccountRowMapper.INSTANCE)
                .single();
    }

    @Override
    public Optional<Account> findById(long id) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM accounts WHERE id = :id")
                .param("id", id)
                .query(AccountRowMapper.INSTANCE)
                .optional();
    }

    @Override
    public Optional<Account> findByAccountNumber(String accountNumber) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM accounts WHERE account_number = :number")
                .param("number", accountNumber)
                .query(AccountRowMapper.INSTANCE)
                .optional();
    }

    @Override
    public Optional<Account> findPrimaryByMerchantId(long merchantId) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM accounts WHERE merchant_id = :merchantId AND account_type = 'PRIMARY'")
                .param("merchantId", merchantId)
                .query(AccountRowMapper.INSTANCE)
                .optional();
    }

    @Override
    public List<Account> findByMerchantId(long merchantId) {
        return jdbc.sql("""
                        SELECT %s FROM accounts
                        WHERE merchant_id = :merchantId
                        ORDER BY (account_type = 'PRIMARY') DESC, id
                        """.formatted(COLUMNS))
                .param("merchantId", merchantId)
                .query(AccountRowMapper.INSTANCE)
                .list();
    }

    @Override
    public Optional<Account> findBySystemCode(String systemCode) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM accounts WHERE system_code = :code")
                .param("code", systemCode)
                .query(AccountRowMapper.INSTANCE)
                .optional();
    }

    @Override
    public Account updateAutoSweep(long accountId, boolean autoSweep) {
        return jdbc.sql("""
                        UPDATE accounts SET auto_sweep = :autoSweep, updated_at = now()
                        WHERE id = :id AND account_type = 'SUBSIDIARY'
                        RETURNING %s
                        """.formatted(COLUMNS))
                .param("autoSweep", autoSweep)
                .param("id", accountId)
                .query(AccountRowMapper.INSTANCE)
                .single();
    }
}
