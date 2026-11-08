package com.threeline.merchantwallet.dynamicaccount;

import com.jayway.jsonpath.JsonPath;
import com.threeline.merchantwallet.dynamicaccount.repository.ExpiryOutcome;
import com.threeline.merchantwallet.dynamicaccount.service.DynamicAccountService;
import com.threeline.merchantwallet.support.ApiTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@TestPropertySource(properties = {
        "wallet.dynamic-accounts.ttl=PT2S",
        "wallet.dynamic-accounts.cooldown=PT0S"
})
class DynamicAccountExpiryTest extends ApiTestBase {

    @Autowired DynamicAccountService dynamicAccountService;

    TestMerchant northstar;
    TestMerchant ade;

    @BeforeEach
    void setUp() throws Exception {
        northstar = registerMerchant("northstar@example.com");
        ade = registerMerchant("ade@example.com");
    }

    String createDynamic(TestMerchant m) throws Exception {
        return mvc.perform(post("/api/v1/dynamic-accounts")
                        .header("X-API-Key", m.apiKey())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
    }

    @Test
    void latePaymentGoesToSuspenseEvenBeforeTheJobRuns() throws Exception {
        String body = createDynamic(northstar);
        String number = JsonPath.read(body, "$.accountNumber");
        String reference = JsonPath.read(body, "$.reference");

        Thread.sleep(2_500);

        mvc.perform(get("/api/v1/dynamic-accounts/" + reference).header("X-API-Key", northstar.apiKey()))
                .andExpect(jsonPath("$.status").value("EXPIRED"));

        fund(northstar, number, "800", "NIP-LATE-1")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.destinationAccount").value("SYS_SUSPENSE"))
                .andExpect(jsonPath("$.dynamicAccountReference").value(reference));

        assertThat(balance(northstar.primaryAccount())).isEqualByComparingTo("0.00");
        String stored = jdbc.sql("SELECT status FROM dynamic_accounts WHERE reference = :r")
                .param("r", reference).query(String.class).single();
        assertThat(stored).isEqualTo("EXPIRED");
    }

    @Test
    void expiredNumberIsRecycledToAnotherMerchantSafely() throws Exception {
        String northstarBody = createDynamic(northstar);
        String number = JsonPath.read(northstarBody, "$.accountNumber");
        String northstarRef = JsonPath.read(northstarBody, "$.reference");

        Thread.sleep(2_500);
        ExpiryOutcome outcome = dynamicAccountService.expireDue();
        assertThat(outcome.expired()).isEqualTo(1);
        assertThat(outcome.released()).isEqualTo(1);

        String adeBody = createDynamic(ade);
        assertThat((String) JsonPath.read(adeBody, "$.accountNumber")).isEqualTo(number);

        fund(ade, number, "1200", "NIP-RECYCLED-1")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.destinationAccount").value(ade.primaryAccount()));

        assertThat(balance(ade.primaryAccount())).isEqualByComparingTo("1200.00");
        assertThat(balance(northstar.primaryAccount())).isEqualByComparingTo("0.00");

        mvc.perform(get("/api/v1/dynamic-accounts/" + northstarRef).header("X-API-Key", northstar.apiKey()))
                .andExpect(jsonPath("$.status").value("EXPIRED"));
    }
}
