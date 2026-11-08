package com.threeline.merchantwallet.ledger;

import com.jayway.jsonpath.JsonPath;
import com.threeline.merchantwallet.account.nuban.NubanGenerator;
import com.threeline.merchantwallet.account.repository.AccountRepository;
import com.threeline.merchantwallet.account.repository.NewAccount;
import com.threeline.merchantwallet.support.ApiTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class TransferApiTest extends ApiTestBase {

    @Autowired
    AccountRepository accounts;

    @Autowired
    NubanGenerator nubans;

    TestMerchant alice;
    TestMerchant bob;

    @BeforeEach
    void setUp() throws Exception {
        alice = registerMerchant("alice@example.com");
        bob = registerMerchant("bob@example.com");
        fund(alice, alice.primaryAccount(), "10000.00");
    }

    @Test
    void transfersBetweenMerchantsAndShowsInStatements() throws Exception {
        String body = transfer(alice, newKey(), alice.primaryAccount(), bob.primaryAccount(), "2500.50", "Supplier payment")
                .andExpect(status().isCreated())
                .andExpect(header().string("Idempotent-Replayed", "false"))
                .andExpect(jsonPath("$.status").value("SUCCESSFUL"))
                .andExpect(jsonPath("$.type").value("TRANSFER"))
                .andExpect(jsonPath("$.amount").value(2500.50))
                .andExpect(jsonPath("$.sourceAccount").value(alice.primaryAccount()))
                .andExpect(jsonPath("$.destinationAccount").value(bob.primaryAccount()))
                .andReturn().getResponse().getContentAsString();

        assertThat(balance(alice.primaryAccount())).isEqualByComparingTo("7499.50");
        assertThat(balance(bob.primaryAccount())).isEqualByComparingTo("2500.50");

        String reference = JsonPath.read(body, "$.reference");

        mvc.perform(get("/api/v1/accounts/" + alice.primaryAccount() + "/statement").header("X-API-Key", alice.apiKey()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.entries.length()").value(2))
                .andExpect(jsonPath("$.entries[0].transactionReference").value(reference))
                .andExpect(jsonPath("$.entries[0].direction").value("DEBIT"))
                .andExpect(jsonPath("$.entries[0].balanceAfter").value(7499.50))
                .andExpect(jsonPath("$.entries[0].counterparty").value(bob.primaryAccount()))
                .andExpect(jsonPath("$.entries[1].transactionType").value("INFLOW"))
                .andExpect(jsonPath("$.entries[1].balanceAfter").value(10000.00));

        mvc.perform(get("/api/v1/transactions/" + reference).header("X-API-Key", alice.apiKey()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCESSFUL"));
    }

    @Test
    void insufficientFundsIsRecordedAsFailedAndBalanceIsUntouched() throws Exception {
        String body = transfer(alice, newKey(), alice.primaryAccount(), bob.primaryAccount(), "10000.01", null)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_FUNDS"))
                .andExpect(jsonPath("$.transactionReference").isString())
                .andReturn().getResponse().getContentAsString();

        assertThat(balance(alice.primaryAccount())).isEqualByComparingTo("10000.00");
        assertThat(balance(bob.primaryAccount())).isEqualByComparingTo("0.00");

        String reference = JsonPath.read(body, "$.transactionReference");
        mvc.perform(get("/api/v1/transactions/" + reference).header("X-API-Key", alice.apiKey()))
                .andExpect(jsonPath("$.status").value("FAILED"))
                .andExpect(jsonPath("$.failureCode").value("INSUFFICIENT_FUNDS"));
    }

    @Test
    void retryWithSameIdempotencyKeyIsReplayedNotRepeated() throws Exception {
        String key = newKey();
        String first = transfer(alice, key, alice.primaryAccount(), bob.primaryAccount(), "1000", null)
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String second = transfer(alice, key, alice.primaryAccount(), bob.primaryAccount(), "1000", null)
                .andExpect(status().isCreated())
                .andExpect(header().string("Idempotent-Replayed", "true"))
                .andReturn().getResponse().getContentAsString();

        assertThat((String) JsonPath.read(second, "$.reference")).isEqualTo(JsonPath.read(first, "$.reference"));
        assertThat(balance(alice.primaryAccount())).isEqualByComparingTo("9000.00");
    }

    @Test
    void sameKeyWithDifferentRequestIsRejected() throws Exception {
        String key = newKey();
        transfer(alice, key, alice.primaryAccount(), bob.primaryAccount(), "1000", null).andExpect(status().isCreated());

        transfer(alice, key, alice.primaryAccount(), bob.primaryAccount(), "2000", null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REUSED"));
    }

    @Test
    void cannotSpendFromAnotherMerchantsAccount() throws Exception {
        transfer(bob, newKey(), alice.primaryAccount(), bob.primaryAccount(), "100", null)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ACCOUNT_NOT_FOUND"));
        assertThat(balance(alice.primaryAccount())).isEqualByComparingTo("10000.00");
    }

    @Test
    void rejectsSameAccountUnknownDestinationAndBadAmounts() throws Exception {
        transfer(alice, newKey(), alice.primaryAccount(), alice.primaryAccount(), "100", null)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("SAME_ACCOUNT_TRANSFER"));

        transfer(alice, newKey(), alice.primaryAccount(), "0000000000", "100", null)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ACCOUNT_NOT_FOUND"));

        transfer(alice, newKey(), alice.primaryAccount(), bob.primaryAccount(), "10.005", null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        transfer(alice, newKey(), alice.primaryAccount(), bob.primaryAccount(), "0", null)
                .andExpect(status().isBadRequest());

        Integer transfers = jdbc.sql("SELECT count(*) FROM transactions WHERE transaction_type = 'TRANSFER'")
                .query(Integer.class).single();
        assertThat(transfers).isZero();
    }

    @Test
    void idempotencyKeyHeaderIsRequired() throws Exception {
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v1/transfers")
                        .header("X-API-Key", alice.apiKey())
                        .contentType("application/json")
                        .content("{\"sourceAccountNumber\":\"" + alice.primaryAccount() + "\",\"destinationAccountNumber\":\""
                                + bob.primaryAccount() + "\",\"amount\":10}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void duplicateInflowSessionCreditsOnce() throws Exception {
        fund(alice, bob.primaryAccount(), "500", "NIP-SESSION-1").andExpect(status().isCreated());
        fund(alice, bob.primaryAccount(), "500", "NIP-SESSION-1")
                .andExpect(status().isCreated())
                .andExpect(header().string("Idempotent-Replayed", "true"));

        assertThat(balance(bob.primaryAccount())).isEqualByComparingTo("500.00");
    }

    @Test
    void creditToAutoSweepSubsidiaryIsSweptToPrimary() throws Exception {
        String sub = accounts.insert(NewAccount.subsidiary(bob.id(), nubans.next(), "Bob - Lekki branch",
                "Tunde", "Bello", "lekki@example.com", null, true)).getAccountNumber();

        String body = transfer(alice, newKey(), alice.primaryAccount(), sub, "750", null)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sweepReferences.length()").value(1))
                .andReturn().getResponse().getContentAsString();

        assertThat(balance(sub)).isEqualByComparingTo("0.00");
        assertThat(balance(bob.primaryAccount())).isEqualByComparingTo("750.00");

        String sweepRef = JsonPath.read(body, "$.sweepReferences[0]");
        String transferRef = JsonPath.read(body, "$.reference");
        mvc.perform(get("/api/v1/transactions/" + sweepRef).header("X-API-Key", bob.apiKey()))
                .andExpect(jsonPath("$.type").value("SWEEP"))
                .andExpect(jsonPath("$.parentReference").value(transferRef))
                .andExpect(jsonPath("$.sourceAccount").value(sub))
                .andExpect(jsonPath("$.destinationAccount").value(bob.primaryAccount()));
    }

    @Test
    void creditToHoldingSubsidiaryStaysThere() throws Exception {
        String sub = accounts.insert(NewAccount.subsidiary(bob.id(), nubans.next(), "Bob - Savings",
                "Tunde", "Bello", "savings@example.com", null, false)).getAccountNumber();

        transfer(alice, newKey(), alice.primaryAccount(), sub, "300", null).andExpect(status().isCreated());

        assertThat(balance(sub)).isEqualByComparingTo(new BigDecimal("300.00"));
        assertThat(balance(bob.primaryAccount())).isEqualByComparingTo("0.00");
    }
}
