package com.threeline.merchantwallet;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
@OpenAPIDefinition(
        info = @Info(
                title = "Merchant Wallet API",
                version = "1.0",
                description = "Merchant wallet system with NUBAN generation, double-entry ledger and fund transfers"
        )
)
public class MerchantWalletApplication {

    public static void main(String[] args) {
        SpringApplication.run(MerchantWalletApplication.class, args);
    }

}
