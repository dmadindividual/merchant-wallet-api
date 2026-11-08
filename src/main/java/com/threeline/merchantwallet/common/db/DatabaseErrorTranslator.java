package com.threeline.merchantwallet.common.db;

import com.threeline.merchantwallet.common.error.ErrorCode;
import org.postgresql.util.PSQLException;
import org.postgresql.util.ServerErrorMessage;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;

@Component
public class DatabaseErrorTranslator {

    private static final String UNIQUE_VIOLATION = "23505";
    private static final String CHECK_VIOLATION = "23514";

    private static final Map<String, ErrorCode> UNIQUE_CONSTRAINTS = Map.of(
            "uq_merchants_email_lower", ErrorCode.MERCHANT_ALREADY_EXISTS,
            "uq_merchants_rc_number", ErrorCode.MERCHANT_ALREADY_EXISTS,
            "uq_transactions_merchant_idempotency", ErrorCode.DUPLICATE_REQUEST
    );

    private static final Map<String, ErrorCode> CHECK_CONSTRAINTS = Map.of(
            "chk_accounts_no_overdraft", ErrorCode.INSUFFICIENT_FUNDS
    );

    public Optional<ErrorCode> translate(Throwable error) {
        return findServerError(error).flatMap(server -> {
            String constraint = server.getConstraint();
            if (constraint == null) {
                return Optional.empty();
            }
            return switch (String.valueOf(server.getSQLState())) {
                case UNIQUE_VIOLATION -> Optional.ofNullable(UNIQUE_CONSTRAINTS.get(constraint));
                case CHECK_VIOLATION -> Optional.ofNullable(CHECK_CONSTRAINTS.get(constraint));
                default -> Optional.empty();
            };
        });
    }

    public String describe(Throwable error) {
        return findServerError(error)
                .map(s -> "sqlState=%s constraint=%s message=%s".formatted(s.getSQLState(), s.getConstraint(), s.getMessage()))
                .orElse("no server error details");
    }

    private static Optional<ServerErrorMessage> findServerError(Throwable error) {
        for (Throwable t = error; t != null; t = t.getCause()) {
            if (t instanceof PSQLException psql && psql.getServerErrorMessage() != null) {
                return Optional.of(psql.getServerErrorMessage());
            }
            if (t.getCause() == t) {
                break;
            }
        }
        return Optional.empty();
    }
}
