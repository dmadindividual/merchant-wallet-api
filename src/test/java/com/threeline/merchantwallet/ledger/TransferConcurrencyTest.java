package com.threeline.merchantwallet.ledger;

import com.threeline.merchantwallet.account.domain.Account;
import com.threeline.merchantwallet.account.repository.AccountRepository;
import com.threeline.merchantwallet.ledger.domain.EntrySide;
import com.threeline.merchantwallet.ledger.domain.TransactionStatus;
import com.threeline.merchantwallet.ledger.repository.LedgerEntryRepository;
import com.threeline.merchantwallet.ledger.service.*;
import com.threeline.merchantwallet.merchant.domain.BusinessType;
import com.threeline.merchantwallet.merchant.service.MerchantService;
import com.threeline.merchantwallet.merchant.service.RegisterMerchantCommand;
import com.threeline.merchantwallet.merchant.service.RegisteredMerchant;
import com.threeline.merchantwallet.support.IntegrationTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;

class TransferConcurrencyTest extends IntegrationTestBase {

    private static final int THREADS = 16;

    @Autowired MerchantService merchantService;
    @Autowired InflowService inflowService;
    @Autowired TransferService transferService;
    @Autowired AccountRepository accounts;
    @Autowired LedgerEntryRepository ledgerEntries;

    RegisteredMerchant alice;
    RegisteredMerchant bob;

    @BeforeEach
    void setUp() {
        alice = register("alice@example.com");
        bob = register("bob@example.com");
        inflowService.receive(new InflowCommand(primary(alice), 1_000_000, "SEED-" + UUID.randomUUID(), "Seed", null));
    }

    @Test
    void parallelTransfersNeverOverdraw() throws Exception {

        List<Callable<PostingResult>> tasks = new ArrayList<>();
        for (int i = 0; i < 50; i++) {
            tasks.add(() -> transfer(alice, primary(alice), primary(bob), 50_000, "race-" + UUID.randomUUID()));
        }

        List<PostingResult> results = runConcurrently(tasks);

        long succeeded = results.stream().filter(r -> r.view().transaction().getStatus() == TransactionStatus.SUCCESSFUL).count();
        long insufficient = results.stream().filter(r -> "INSUFFICIENT_FUNDS".equals(r.view().transaction().getFailureCode())).count();

        assertThat(succeeded).isEqualTo(20);
        assertThat(insufficient).isEqualTo(30);
        assertThat(balanceKobo(primary(alice))).isZero();
        assertThat(balanceKobo(primary(bob))).isEqualTo(1_000_000);
        assertLedgerIsConsistent();
    }

    @Test
    void opposingTransfersDoNotDeadlock() throws Exception {
        inflowService.receive(new InflowCommand(primary(bob), 1_000_000, "SEED-" + UUID.randomUUID(), "Seed", null));

        List<Callable<PostingResult>> tasks = new ArrayList<>();
        for (int i = 0; i < 40; i++) {
            tasks.add(() -> transfer(alice, primary(alice), primary(bob), 10_000, "ab-" + UUID.randomUUID()));
            tasks.add(() -> transfer(bob, primary(bob), primary(alice), 10_000, "ba-" + UUID.randomUUID()));
        }

        List<PostingResult> results = runConcurrently(tasks);

        assertThat(results).allMatch(r -> r.view().transaction().getStatus() == TransactionStatus.SUCCESSFUL);
        assertThat(balanceKobo(primary(alice))).isEqualTo(1_000_000);
        assertThat(balanceKobo(primary(bob))).isEqualTo(1_000_000);
        assertLedgerIsConsistent();
    }

    @Test
    void concurrentRetriesWithTheSameKeyPostOnce() throws Exception {
        String key = "same-key-" + UUID.randomUUID();
        List<Callable<PostingResult>> tasks = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            tasks.add(() -> transfer(alice, primary(alice), primary(bob), 12_345, key));
        }

        List<PostingResult> results = runConcurrently(tasks);

        assertThat(results).extracting(r -> r.view().transaction().getReference()).containsOnly(
                results.get(0).view().transaction().getReference());
        assertThat(results).filteredOn(r -> !r.replayed()).hasSize(1);
        assertThat(balanceKobo(primary(bob))).isEqualTo(12_345);
        assertLedgerIsConsistent();
    }

    private RegisteredMerchant register(String email) {
        return merchantService.register(new RegisterMerchantCommand(
                "Biz " + email, BusinessType.SOLE_PROPRIETORSHIP, null, email, "08031234567", null, null));
    }

    private static String primary(RegisteredMerchant m) {
        return m.primaryAccount().getAccountNumber();
    }

    private PostingResult transfer(RegisteredMerchant caller, String from, String to, long kobo, String key) {
        return transferService.transfer(new TransferCommand(caller.merchant().getId(), from, to, kobo, null, key));
    }

    private long balanceKobo(String accountNumber) {
        return accounts.findByAccountNumber(accountNumber).orElseThrow().getBalanceKobo();
    }

    private static <T> List<T> runConcurrently(List<Callable<T>> tasks) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(THREADS);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<T>> futures = new ArrayList<>();
            for (Callable<T> task : tasks) {
                futures.add(pool.submit(() -> {
                    start.await();
                    return task.call();
                }));
            }
            start.countDown();

            List<T> results = new ArrayList<>();
            for (Future<T> f : futures) {
                results.add(f.get(60, TimeUnit.SECONDS));
            }
            return results;
        } finally {
            pool.shutdownNow();
        }
    }

    private void assertLedgerIsConsistent() {
        long debitNormal = 0;
        long creditNormal = 0;
        List<Account> all = jdbc.sql("SELECT id FROM accounts").query(Long.class).list().stream()
                .map(id -> accounts.findById(id).orElseThrow()).toList();
        for (Account a : all) {
            assertThat(ledgerEntries.sumBalanceFromEntries(a.getId()))
                    .as("ledger vs cached balance for account %s", a.getId())
                    .isEqualTo(a.getBalanceKobo());
            if (a.getNormalBalance() == EntrySide.DEBIT) {
                debitNormal += a.getBalanceKobo();
            } else {
                creditNormal += a.getBalanceKobo();
            }
        }

        Integer unbalanced = jdbc.sql("""
                        SELECT count(*) FROM (
                            SELECT transaction_id FROM ledger_entries
                            GROUP BY transaction_id
                            HAVING SUM(CASE WHEN entry_type = 'DEBIT' THEN amount ELSE -amount END) <> 0
                        ) x
                        """)
                .query(Integer.class).single();
        assertThat(unbalanced).isZero();
        assertThat(debitNormal).isEqualTo(creditNormal);
    }
}
