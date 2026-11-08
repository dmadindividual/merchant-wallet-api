package com.threeline.merchantwallet.merchant.api;

import com.jayway.jsonpath.JsonPath;
import com.threeline.merchantwallet.account.nuban.NubanGenerator;
import com.threeline.merchantwallet.support.IntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class MerchantApiTest extends IntegrationTestBase {

    @Autowired
    MockMvc mvc;

    @Autowired
    NubanGenerator nubans;

    static String registrationJson(String email, String businessType, String rcNumber) {
        return """
                {
                  "businessName": "Mama Put Ventures",
                  "businessType": "%s",
                  "rcNumber": %s,
                  "email": "%s",
                  "phone": "08031234567",
                  "address": "12 Allen Avenue, Ikeja"
                }
                """.formatted(businessType, rcNumber == null ? "null" : "\"" + rcNumber + "\"", email);
    }

    String register(String email) throws Exception {
        return mvc.perform(post("/api/v1/merchants")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registrationJson(email, "SOLE_PROPRIETORSHIP", null)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
    }

    @Test
    void registrationCreatesMerchantAndPrimaryAccountAndReturnsKeyOnce() throws Exception {
        MvcResult result = mvc.perform(post("/api/v1/merchants")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registrationJson("Shop@Example.com", "SOLE_PROPRIETORSHIP", null)))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/v1/merchants/me"))
                .andExpect(jsonPath("$.merchant.email").value("shop@example.com"))
                .andExpect(jsonPath("$.merchant.phone").value("+2348031234567"))
                .andExpect(jsonPath("$.merchant.status").value("ACTIVE"))
                .andExpect(jsonPath("$.merchant.id").doesNotExist())
                .andExpect(jsonPath("$.primaryAccount.type").value("PRIMARY"))
                .andExpect(jsonPath("$.primaryAccount.balance").value(0.0))
                .andExpect(jsonPath("$.apiKey").isString())
                .andReturn();

        String body = result.getResponse().getContentAsString();
        String accountNumber = JsonPath.read(body, "$.primaryAccount.accountNumber");
        String apiKey = JsonPath.read(body, "$.apiKey");

        assertThat(nubans.isValid(accountNumber)).isTrue();

        Integer rawKeyRows = jdbc.sql("SELECT count(*) FROM merchants WHERE api_key_hash = :raw")
                .param("raw", apiKey).query(Integer.class).single();
        assertThat(rawKeyRows).isZero();
    }

    @Test
    void duplicateEmailIsRejectedCaseInsensitively() throws Exception {
        register("dup@example.com");

        mvc.perform(post("/api/v1/merchants")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registrationJson("DUP@example.com", "SOLE_PROPRIETORSHIP", null)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("MERCHANT_ALREADY_EXISTS"))
                .andExpect(jsonPath("$.requestId").isString());
    }

    @Test
    void invalidFieldsAreListed() throws Exception {
        mvc.perform(post("/api/v1/merchants")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"businessName": "", "businessType": "SOLE_PROPRIETORSHIP",
                                 "email": "not-an-email", "phone": "12345"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors.length()").value(3));
    }

    @Test
    void limitedLiabilityRequiresRcNumber() throws Exception {
        mvc.perform(post("/api/v1/merchants")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registrationJson("ltd@example.com", "LIMITED_LIABILITY", null)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void protectedEndpointsRequireAValidKey() throws Exception {
        mvc.perform(get("/api/v1/merchants/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.requestId").isString());

        mvc.perform(get("/api/v1/merchants/me").header("X-API-Key", "mw_wrong"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void merchantSeesOwnProfileAndAccounts() throws Exception {
        String body = register("me@example.com");
        String apiKey = JsonPath.read(body, "$.apiKey");
        String accountNumber = JsonPath.read(body, "$.primaryAccount.accountNumber");

        mvc.perform(get("/api/v1/merchants/me").header("X-API-Key", apiKey))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("me@example.com"));

        mvc.perform(get("/api/v1/accounts").header("X-API-Key", apiKey))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].accountNumber").value(accountNumber));

        mvc.perform(get("/api/v1/accounts/" + accountNumber).header("X-API-Key", apiKey))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("PRIMARY"));
    }

    @Test
    void merchantCannotSeeAnotherMerchantsAccount() throws Exception {
        String alice = register("alice@example.com");
        String bob = register("bob@example.com");
        String bobsAccount = JsonPath.read(bob, "$.primaryAccount.accountNumber");
        String alicesKey = JsonPath.read(alice, "$.apiKey");

        mvc.perform(get("/api/v1/accounts/" + bobsAccount).header("X-API-Key", alicesKey))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ACCOUNT_NOT_FOUND"));
    }

    @Test
    void registrationIsAuditedWithTheRequestId() throws Exception {
        mvc.perform(post("/api/v1/merchants")
                        .header("X-Request-Id", "reg-test-001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registrationJson("audit@example.com", "SOLE_PROPRIETORSHIP", null)))
                .andExpect(status().isCreated())
                .andExpect(header().string("X-Request-Id", "reg-test-001"));

        Integer audited = jdbc.sql("SELECT count(*) FROM audit_log WHERE request_id = 'reg-test-001'")
                .query(Integer.class).single();
        assertThat(audited).isEqualTo(2);
    }
}
