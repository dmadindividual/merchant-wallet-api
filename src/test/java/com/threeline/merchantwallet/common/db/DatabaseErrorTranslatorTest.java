package com.threeline.merchantwallet.common.db;

import com.threeline.merchantwallet.common.error.ErrorCode;
import org.junit.jupiter.api.Test;
import org.postgresql.util.PSQLException;
import org.postgresql.util.ServerErrorMessage;
import org.springframework.dao.DataIntegrityViolationException;

import static org.assertj.core.api.Assertions.assertThat;

class DatabaseErrorTranslatorTest {

    private final DatabaseErrorTranslator translator = new DatabaseErrorTranslator();

    private static DataIntegrityViolationException dbError(String sqlState, String constraint, String message) {

        String fields = "SERROR\u0000C" + sqlState + "\u0000M" + message + "\u0000"
                + (constraint == null ? "" : "n" + constraint + "\u0000");
        PSQLException driverError = new PSQLException(new ServerErrorMessage(fields));
        return new DataIntegrityViolationException("wrapped", driverError);
    }

    @Test
    void duplicateEmailBecomesMerchantAlreadyExists() {
        assertThat(translator.translate(dbError("23505", "uq_merchants_email_lower", "duplicate key")))
                .contains(ErrorCode.MERCHANT_ALREADY_EXISTS);
    }

    @Test
    void idempotencyRaceBecomesDuplicateRequest() {
        assertThat(translator.translate(dbError("23505", "uq_transactions_merchant_idempotency", "duplicate key")))
                .contains(ErrorCode.DUPLICATE_REQUEST);
    }

    @Test
    void overdraftBackstopBecomesInsufficientFunds() {
        assertThat(translator.translate(dbError("23514", "chk_accounts_no_overdraft", "check violated")))
                .contains(ErrorCode.INSUFFICIENT_FUNDS);
    }

    @Test
    void unknownConstraintIsNotTranslated_soItSurfacesAsA500() {
        assertThat(translator.translate(dbError("23505", "uq_accounts_account_number", "duplicate key"))).isEmpty();
    }

    @Test
    void ledgerInvariantBreachIsNotTranslated() {
        assertThat(translator.translate(dbError("P0001", null, "LEDGER_UNBALANCED"))).isEmpty();
    }

    @Test
    void nonDatabaseErrorIsNotTranslated() {
        assertThat(translator.translate(new IllegalStateException("boom"))).isEmpty();
    }
}
