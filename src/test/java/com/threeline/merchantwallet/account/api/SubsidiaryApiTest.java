package com.threeline.merchantwallet.account.api;

import com.jayway.jsonpath.JsonPath;
import com.threeline.merchantwallet.support.ApiTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SubsidiaryApiTest extends ApiTestBase {

    TestMerchant shop;

    @BeforeEach
    void setUp() throws Exception {
        shop = registerMerchant("shop@example.com");
    }

    String createSubsidiary(TestMerchant m, String first, boolean autoSweep) throws Exception {
        String body = mvc.perform(post("/api/v1/accounts/subsidiaries")
                        .header("X-API-Key", m.apiKey())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"firstName": "%s", "lastName": "Okafor", "email": "%s@example.com",
                                 "phone": "08051234567", "autoSweep": %s}
                                """.formatted(first, first.toLowerCase(), autoSweep)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.type").value("SUBSIDIARY"))
                .andExpect(jsonPath("$.autoSweep").value(autoSweep))
                .andExpect(jsonPath("$.balance").value(0.0))
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.accountNumber");
    }

    @Test
    void createsSubsidiariesListedAfterPrimary() throws Exception {
        String hold = createSubsidiary(shop, "Ngozi", false);
        String sweep = createSubsidiary(shop, "Emeka", true);

        mvc.perform(get("/api/v1/accounts").header("X-API-Key", shop.apiKey()))
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].type").value("PRIMARY"))
                .andExpect(jsonPath("$[1].accountNumber").value(hold))
                .andExpect(jsonPath("$[2].accountNumber").value(sweep))
                .andExpect(jsonPath("$[1].accountName").value("Test Biz shop@example.com - Ngozi Okafor"));
    }

    @Test
    void holdingSubsidiaryKeepsFundsAndCanSpendThem() throws Exception {
        String hold = createSubsidiary(shop, "Ngozi", false);
        TestMerchant other = registerMerchant("other@example.com");

        fund(shop, hold, "3000");
        assertThat(balance(hold)).isEqualByComparingTo("3000.00");

        transfer(shop, newKey(), hold, other.primaryAccount(), "1200", "Paid from branch")
                .andExpect(status().isCreated());
        assertThat(balance(hold)).isEqualByComparingTo("1800.00");
        assertThat(balance(other.primaryAccount())).isEqualByComparingTo("1200.00");
    }

    @Test
    void togglingAutoSweepAppliesToFutureCreditsOnly() throws Exception {
        String sub = createSubsidiary(shop, "Ngozi", false);
        fund(shop, sub, "1000");

        mvc.perform(patch("/api/v1/accounts/" + sub + "/auto-sweep")
                        .header("X-API-Key", shop.apiKey())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"autoSweep\": true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.autoSweep").value(true));

        fund(shop, sub, "500");

        assertThat(balance(sub)).isEqualByComparingTo("1000.00");
        assertThat(balance(shop.primaryAccount())).isEqualByComparingTo("500.00");

        Integer audited = jdbc.sql("""
                        SELECT count(*) FROM audit_log
                        WHERE table_name = 'accounts' AND operation = 'UPDATE' AND 'auto_sweep' = ANY(changed_fields)
                        """).query(Integer.class).single();
        assertThat(audited).isEqualTo(1);
    }

    @Test
    void autoSweepCannotBeSetOnPrimary() throws Exception {
        mvc.perform(patch("/api/v1/accounts/" + shop.primaryAccount() + "/auto-sweep")
                        .header("X-API-Key", shop.apiKey())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"autoSweep\": true}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("NOT_A_SUBSIDIARY"));
    }

    @Test
    void cannotToggleAnotherMerchantsSubsidiary() throws Exception {
        String sub = createSubsidiary(shop, "Ngozi", false);
        TestMerchant other = registerMerchant("other@example.com");

        mvc.perform(patch("/api/v1/accounts/" + sub + "/auto-sweep")
                        .header("X-API-Key", other.apiKey())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"autoSweep\": true}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void subsidiaryRequiresHolderDetails() throws Exception {
        mvc.perform(post("/api/v1/accounts/subsidiaries")
                        .header("X-API-Key", shop.apiKey())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"firstName\": \"\", \"email\": \"bad\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.length()").value(3));
    }
}
