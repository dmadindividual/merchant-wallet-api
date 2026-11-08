package com.threeline.merchantwallet.common.openapi;

import com.jayway.jsonpath.JsonPath;
import com.threeline.merchantwallet.support.IntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class OpenApiTest extends IntegrationTestBase {

    @Autowired
    MockMvc mvc;

    @Test
    void apiDocsDescribeTheApiKeySchemeAndEveryEndpoint() throws Exception {
        String docs = mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat((String) JsonPath.read(docs, "$.info.title")).isEqualTo("Merchant Wallet API");
        assertThat((String) JsonPath.read(docs, "$.components.securitySchemes.ApiKey.name")).isEqualTo("X-API-Key");

        Map<String, Object> paths = JsonPath.read(docs, "$.paths");
        assertThat(paths).containsKeys(
                "/api/v1/merchants", "/api/v1/merchants/me",
                "/api/v1/accounts", "/api/v1/accounts/subsidiaries", "/api/v1/accounts/{accountNumber}/statement",
                "/api/v1/transfers", "/api/v1/transactions/{reference}",
                "/api/v1/dynamic-accounts", "/api/v1/simulate/inflows");

        List<Object> registrationSecurity = JsonPath.read(docs, "$.paths['/api/v1/merchants'].post.security");
        assertThat(registrationSecurity).isEmpty();

        assertThat(docs).doesNotContain("\"name\":\"merchant\"");
    }

    @Test
    void swaggerUiIsServedWithoutAnApiKey() throws Exception {
        mvc.perform(get("/swagger-ui/index.html")).andExpect(status().isOk());
    }
}
