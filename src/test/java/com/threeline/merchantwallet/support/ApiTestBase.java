package com.threeline.merchantwallet.support;

import com.jayway.jsonpath.JsonPath;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.math.BigDecimal;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

public abstract class ApiTestBase extends IntegrationTestBase {

    @Autowired
    protected MockMvc mvc;

    public record TestMerchant(long id, String apiKey, String primaryAccount) {
    }

    protected TestMerchant registerMerchant(String email) throws Exception {
        String body = mvc.perform(post("/api/v1/merchants")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"businessName": "Test Biz %s", "businessType": "SOLE_PROPRIETORSHIP",
                                 "email": "%s", "phone": "08031234567"}
                                """.formatted(email, email)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        long id = jdbc.sql("SELECT id FROM merchants WHERE email = :email")
                .param("email", email).query(Long.class).single();
        return new TestMerchant(id, JsonPath.read(body, "$.apiKey"), JsonPath.read(body, "$.primaryAccount.accountNumber"));
    }

    protected ResultActions fund(TestMerchant caller, String accountNumber, String naira, String sessionId) throws Exception {
        return mvc.perform(post("/api/v1/simulate/inflows")
                .header("X-API-Key", caller.apiKey())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"accountNumber": "%s", "amount": %s, "sessionId": "%s", "senderName": "Test Customer"}
                        """.formatted(accountNumber, naira, sessionId)));
    }

    protected void fund(TestMerchant caller, String accountNumber, String naira) throws Exception {
        fund(caller, accountNumber, naira, "SESS-" + UUID.randomUUID()).andExpect(status().isCreated());
    }

    protected ResultActions transfer(TestMerchant caller, String idempotencyKey,
                                     String from, String to, String naira, String narration) throws Exception {
        return mvc.perform(post("/api/v1/transfers")
                .header("X-API-Key", caller.apiKey())
                .header("Idempotency-Key", idempotencyKey)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"sourceAccountNumber": "%s", "destinationAccountNumber": "%s",
                         "amount": %s, "narration": %s}
                        """.formatted(from, to, naira, narration == null ? "null" : "\"" + narration + "\"")));
    }

    protected BigDecimal balance(String accountNumber) {
        long kobo = jdbc.sql("SELECT balance FROM accounts WHERE account_number = :n")
                .param("n", accountNumber).query(Long.class).single();
        return BigDecimal.valueOf(kobo, 2);
    }

    protected static String newKey() {
        return "test-" + UUID.randomUUID();
    }
}
