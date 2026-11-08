package com.threeline.merchantwallet.dynamicaccount;

import com.jayway.jsonpath.JsonPath;
import com.threeline.merchantwallet.account.nuban.NubanGenerator;
import com.threeline.merchantwallet.dynamicaccount.service.CreateDynamicAccountCommand;
import com.threeline.merchantwallet.dynamicaccount.service.DynamicAccountService;
import com.threeline.merchantwallet.merchant.domain.Merchant;
import com.threeline.merchantwallet.merchant.repository.MerchantRepository;
import com.threeline.merchantwallet.support.ApiTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class DynamicAccountApiTest extends ApiTestBase {

    @Autowired NubanGenerator nubans;
    @Autowired DynamicAccountService dynamicAccountService;
    @Autowired MerchantRepository merchants;

    TestMerchant shop;

    @BeforeEach
    void setUp() throws Exception {
        shop = registerMerchant("shop@example.com");
    }

    String createDynamic(TestMerchant m, String json) throws Exception {
        return mvc.perform(post("/api/v1/dynamic-accounts")
                        .header("X-API-Key", m.apiKey())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
    }

    @Test
    void issuesAValidNubanLiveForTenMinutes() throws Exception {
        String body = createDynamic(shop, "{}");

        String number = JsonPath.read(body, "$.accountNumber");
        Integer secondsRemaining = JsonPath.read(body, "$.secondsRemaining");
        assertThat(nubans.isValid(number)).isTrue();
        assertThat(secondsRemaining).isBetween(590, 600);
        assertThat((String) JsonPath.read(body, "$.status")).isEqualTo("ACTIVE");
        assertThat((String) JsonPath.read(body, "$.accountName")).isEqualTo("Test Biz shop@example.com");
        assertThat((String) JsonPath.read(body, "$.reference")).startsWith("DYN-");
    }

    @Test
    void paymentInTimeIsCreditedToPrimaryAndAccountBecomesPaid() throws Exception {
        String body = createDynamic(shop, "{\"accountName\": \"Shop - Order 1001\"}");
        String number = JsonPath.read(body, "$.accountNumber");
        String reference = JsonPath.read(body, "$.reference");

        String payment = fund(shop, number, "4500", "NIP-DYN-1")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("SUCCESSFUL"))
                .andExpect(jsonPath("$.destinationAccount").value(shop.primaryAccount()))
                .andExpect(jsonPath("$.viaDynamicAccount").value(number))
                .andExpect(jsonPath("$.dynamicAccountReference").value(reference))
                .andReturn().getResponse().getContentAsString();

        assertThat(balance(shop.primaryAccount())).isEqualByComparingTo("4500.00");

        mvc.perform(get("/api/v1/dynamic-accounts/" + reference).header("X-API-Key", shop.apiKey()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PAID"))
                .andExpect(jsonPath("$.paymentReference").value((String) JsonPath.read(payment, "$.reference")))
                .andExpect(jsonPath("$.secondsRemaining").doesNotExist());
    }

    @Test
    void secondPaymentToAOneTimeAccountIsHeldInSuspense() throws Exception {
        String number = JsonPath.read(createDynamic(shop, "{}"), "$.accountNumber");
        fund(shop, number, "1000", "NIP-DYN-2").andExpect(status().isCreated());

        fund(shop, number, "250", "NIP-DYN-3")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.destinationAccount").value("SYS_SUSPENSE"))
                .andExpect(jsonPath("$.viaDynamicAccount").value(number));

        assertThat(balance(shop.primaryAccount())).isEqualByComparingTo("1000.00");
    }

    @Test
    void expectedAmountIsEnforced() throws Exception {
        String body = createDynamic(shop, "{\"expectedAmount\": 1500.00}");
        String number = JsonPath.read(body, "$.accountNumber");
        assertThat(((Number) JsonPath.read(body, "$.expectedAmount")).doubleValue()).isEqualTo(1500.0);

        fund(shop, number, "1000", "NIP-DYN-4")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("DYNAMIC_ACCOUNT_AMOUNT_MISMATCH"));

        fund(shop, number, "1500", "NIP-DYN-5").andExpect(status().isCreated());
        assertThat(balance(shop.primaryAccount())).isEqualByComparingTo("1500.00");
    }

    @Test
    void otherMerchantsCannotSeeIt() throws Exception {
        String reference = JsonPath.read(createDynamic(shop, "{}"), "$.reference");
        TestMerchant other = registerMerchant("other@example.com");

        mvc.perform(get("/api/v1/dynamic-accounts/" + reference).header("X-API-Key", other.apiKey()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("DYNAMIC_ACCOUNT_NOT_FOUND"));
    }

    @Test
    void listsNewestFirst() throws Exception {
        String first = JsonPath.read(createDynamic(shop, "{}"), "$.reference");
        String second = JsonPath.read(createDynamic(shop, "{}"), "$.reference");

        mvc.perform(get("/api/v1/dynamic-accounts").header("X-API-Key", shop.apiKey()))
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].reference").value(second))
                .andExpect(jsonPath("$[1].reference").value(first));
    }

    @Test
    void concurrentRequestsNeverShareANumber() throws Exception {
        Merchant merchant = merchants.findById(shop.id()).orElseThrow();
        ExecutorService pool = Executors.newFixedThreadPool(10);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<String>> futures = new ArrayList<>();
            for (int i = 0; i < 25; i++) {
                futures.add(pool.submit(() -> {
                    start.await();
                    return dynamicAccountService.create(merchant, new CreateDynamicAccountCommand(null, null))
                            .account().getAccountNumber();
                }));
            }
            start.countDown();

            Set<String> numbers = new HashSet<>();
            for (Future<String> f : futures) {
                numbers.add(f.get(60, TimeUnit.SECONDS));
            }
            assertThat(numbers).hasSize(25);
        } finally {
            pool.shutdownNow();
        }
    }
}
