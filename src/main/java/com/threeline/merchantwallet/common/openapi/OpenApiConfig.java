package com.threeline.merchantwallet.common.openapi;

import com.threeline.merchantwallet.common.security.ApiKeyAuthenticationFilter;
import com.threeline.merchantwallet.common.security.CurrentMerchant;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.tags.Tag;
import org.springdoc.core.utils.SpringDocUtils;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpenApiConfig {

    public static final String API_KEY_SCHEME = "ApiKey";

    static {
        SpringDocUtils.getConfig().addAnnotationsToIgnore(CurrentMerchant.class);
    }

    @Bean
    public OpenAPI merchantWalletOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Merchant Wallet API")
                        .version("v1")
                        .description("""
                                Merchant wallet with a double-entry ledger, NUBAN account generation, \
                                fund transfers, subsidiary accounts with auto-sweep and one-time dynamic accounts.

                                **Quick start**
                                1. `POST /api/v1/merchants` registers a business and opens its primary account. \
                                Copy the `apiKey` from the response (it is shown only once).
                                2. Click **Authorize** and paste the key.
                                3. `POST /api/v1/simulate/inflows` funds an account (stands in for a customer's bank transfer).
                                4. `POST /api/v1/transfers` moves money. Send a unique `Idempotency-Key` per transfer; \
                                retrying with the same key never pays twice.
                                5. `GET /api/v1/accounts/{accountNumber}/statement` shows the history with running balances.

                                **Amounts** are in naira with at most 2 decimal places. **Errors** are RFC 7807 \
                                `application/problem+json` with a stable `code` and the `requestId` to quote when reporting issues.
                                """))
                .components(new Components().addSecuritySchemes(API_KEY_SCHEME, new SecurityScheme()
                        .type(SecurityScheme.Type.APIKEY)
                        .in(SecurityScheme.In.HEADER)
                        .name(ApiKeyAuthenticationFilter.HEADER)
                        .description("The API key returned once by merchant registration.")))
                .addSecurityItem(new SecurityRequirement().addList(API_KEY_SCHEME))
                .tags(List.of(
                        new Tag().name("Merchants").description("Registration (requirement 1) and profile"),
                        new Tag().name("Accounts").description("Primary and subsidiary accounts, balances and statements"),
                        new Tag().name("Transfers").description("Fund transfer between accounts (requirement 2)"),
                        new Tag().name("Transactions").description("Look up any transaction by reference"),
                        new Tag().name("Dynamic accounts").description("One-time accounts that accept a payment for 10 minutes"),
                        new Tag().name("Simulation").description("Sandbox stand-in for incoming bank transfers")));
    }
}
