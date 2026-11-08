package com.threeline.merchantwallet.account.nuban;

import com.threeline.merchantwallet.common.error.ErrorCode;
import com.threeline.merchantwallet.common.error.WalletException;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.SQLException;

@Repository
public class JdbcNubanSerialSource implements NubanSerialSource {

    private static final String SEQUENCE_EXHAUSTED = "2200H";

    private final JdbcClient jdbc;

    public JdbcNubanSerialSource(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public long nextSerial() {
        try {
            return jdbc.sql("SELECT nextval('nuban_serial_seq')")
                    .query(Long.class)
                    .single();
        } catch (DataAccessException e) {
            if (e.getMostSpecificCause() instanceof SQLException sql
                    && SEQUENCE_EXHAUSTED.equals(sql.getSQLState())) {
                throw new WalletException(ErrorCode.ACCOUNT_NUMBER_EXHAUSTED);
            }
            throw e;
        }
    }
}
