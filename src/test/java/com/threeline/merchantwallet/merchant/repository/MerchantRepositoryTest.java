package com.threeline.merchantwallet.merchant.repository;

import com.threeline.merchantwallet.common.context.RequestContext;
import com.threeline.merchantwallet.common.db.AuditedTransactions;
import com.threeline.merchantwallet.merchant.domain.BusinessType;
import com.threeline.merchantwallet.merchant.domain.Merchant;
import com.threeline.merchantwallet.merchant.domain.MerchantStatus;
import com.threeline.merchantwallet.support.IntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MerchantRepositoryTest extends IntegrationTestBase {

    @Autowired
    MerchantRepository merchants;

    @Autowired
    AuditedTransactions tx;

    static NewMerchant newMerchant(String email, String rc) {
        return new NewMerchant("Mama Put Ventures", BusinessType.SOLE_PROPRIETORSHIP, rc,
                email, "08031234567", "12 Allen Avenue, Ikeja", fakeHash(email), null);
    }

    static String fakeHash(String email) {
        return String.format("%064x", Math.abs((long) email.hashCode()));
    }

    @Test
    void insertReturnsStoredRowWithGeneratedFields() {
        Merchant saved = merchants.insert(newMerchant("shop@example.com", "BN123456"));

        assertThat(saved.getId()).isPositive();
        assertThat(saved.getPublicId()).isNotNull();
        assertThat(saved.getStatus()).isEqualTo(MerchantStatus.ACTIVE);
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(merchants.findByPublicId(saved.getPublicId())).contains(saved);
        assertThat(merchants.findByApiKeyHash(fakeHash("shop@example.com"))).contains(saved);
    }

    @Test
    void emailUniquenessIsCaseInsensitive() {
        merchants.insert(newMerchant("shop@example.com", null));

        assertThat(merchants.existsByEmail("SHOP@Example.com")).isTrue();
        assertThatThrownBy(() -> merchants.insert(newMerchant("SHOP@EXAMPLE.COM", null)))
                .isInstanceOf(DuplicateKeyException.class);
    }

    @Test
    void insertThroughAuditedTransactionRecordsTheActor() {
        RequestContext.setRequestId("req-test-1");
        RequestContext.setActor("admin:test");

        Merchant saved = tx.execute(() -> merchants.insert(newMerchant("audited@example.com", null)));

        String actor = jdbc.sql("""
                        SELECT actor FROM audit_log
                        WHERE table_name = 'merchants' AND row_id = :id AND operation = 'INSERT'
                        """)
                .param("id", saved.getId())
                .query(String.class)
                .single();
        assertThat(actor).isEqualTo("admin:test");
    }
}
