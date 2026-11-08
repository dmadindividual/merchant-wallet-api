package com.threeline.merchantwallet.support;

import com.threeline.merchantwallet.common.context.RequestContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

import javax.sql.DataSource;

@SpringBootTest(properties = "wallet.dynamic-accounts.expiry-job-enabled=false")
@AutoConfigureMockMvc
public abstract class IntegrationTestBase {

    private static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:17-alpine")
                    .withDatabaseName("merchant_wallet")
                    .withUsername("wallet")
                    .withPassword("wallet");

    static {
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    protected JdbcClient jdbc;

    @Autowired
    private DataSource dataSource;

    @BeforeEach
    void resetDatabase() {
        jdbc.sql("""
                TRUNCATE ledger_entries, transactions, dynamic_accounts, dynamic_account_numbers,
                         accounts, merchants, audit_log
                RESTART IDENTITY CASCADE
                """).update();

        new ResourceDatabasePopulator(new ClassPathResource("db/migration/V2__seed_system_accounts.sql"))
                .execute(dataSource);
    }

    @AfterEach
    void clearRequestContext() {
        RequestContext.clear();
    }
}
