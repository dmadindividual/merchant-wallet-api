package com.threeline.merchantwallet.common.db;

import com.threeline.merchantwallet.common.context.RequestContext;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.function.Supplier;

@Component
public class AuditedTransactions {

    private final TransactionTemplate transactionTemplate;
    private final JdbcClient jdbc;

    public AuditedTransactions(PlatformTransactionManager transactionManager, JdbcClient jdbc) {
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.jdbc = jdbc;
    }

    public <T> T execute(Supplier<T> work) {
        return transactionTemplate.execute(status -> {
            stampSession();
            return work.get();
        });
    }

    public void run(Runnable work) {
        execute(() -> {
            work.run();
            return null;
        });
    }

    private void stampSession() {
        jdbc.sql("SELECT set_config('app.actor', :actor, true), set_config('app.request_id', :requestId, true)")
                .param("actor", RequestContext.actor())
                .param("requestId", RequestContext.requestId() == null ? "" : RequestContext.requestId())
                .query()
                .singleRow();
    }
}
