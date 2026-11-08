package com.threeline.merchantwallet.account.repository;

import com.threeline.merchantwallet.account.domain.Account;
import com.threeline.merchantwallet.account.domain.AccountType;
import com.threeline.merchantwallet.account.nuban.NubanGenerator;
import com.threeline.merchantwallet.ledger.domain.EntrySide;
import com.threeline.merchantwallet.merchant.domain.BusinessType;
import com.threeline.merchantwallet.merchant.domain.Merchant;
import com.threeline.merchantwallet.merchant.repository.MerchantRepository;
import com.threeline.merchantwallet.merchant.repository.NewMerchant;
import com.threeline.merchantwallet.support.IntegrationTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DuplicateKeyException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AccountRepositoryTest extends IntegrationTestBase {

    @Autowired
    AccountRepository accounts;

    @Autowired
    MerchantRepository merchants;

    @Autowired
    NubanGenerator nubans;

    Merchant merchant;

    @BeforeEach
    void createMerchant() {
        merchant = merchants.insert(new NewMerchant("Ade Stores", BusinessType.LIMITED_LIABILITY, "RC777",
                "ade@example.com", "08030000000", null, "b".repeat(64), null));
    }

    @Test
    void systemAccountsAreSeededForEveryTest() {
        assertThat(accounts.findBySystemCode("SYS_INBOUND_SETTLEMENT"))
                .get()
                .extracting(Account::getType, Account::getNormalBalance)
                .containsExactly(AccountType.SYSTEM, EntrySide.DEBIT);
        assertThat(accounts.findBySystemCode("SYS_SUSPENSE")).isPresent();
    }

    @Test
    void insertsPrimaryAccountAsCreditNormalWithZeroBalance() {
        Account primary = accounts.insert(NewAccount.primary(merchant.getId(), nubans.next(), "Ade Stores"));

        assertThat(primary.getType()).isEqualTo(AccountType.PRIMARY);
        assertThat(primary.getNormalBalance()).isEqualTo(EntrySide.CREDIT);
        assertThat(primary.getBalanceKobo()).isZero();
        assertThat(primary.getCurrency()).isEqualTo("NGN");
        assertThat(nubans.isValid(primary.getAccountNumber())).isTrue();
        assertThat(accounts.findByAccountNumber(primary.getAccountNumber())).contains(primary);
        assertThat(accounts.findPrimaryByMerchantId(merchant.getId())).contains(primary);
    }

    @Test
    void merchantCanHaveOnlyOnePrimaryAccount() {
        accounts.insert(NewAccount.primary(merchant.getId(), nubans.next(), "Ade Stores"));

        assertThatThrownBy(() -> accounts.insert(NewAccount.primary(merchant.getId(), nubans.next(), "Again")))
                .isInstanceOf(DuplicateKeyException.class);
    }

    @Test
    void listsPrimaryFirstThenSubsidiaries() {
        Account sub = accounts.insert(NewAccount.subsidiary(merchant.getId(), nubans.next(), "Ade Stores - Lekki",
                "Tunde", "Ade", "lekki@example.com", null, true));
        Account primary = accounts.insert(NewAccount.primary(merchant.getId(), nubans.next(), "Ade Stores"));

        assertThat(accounts.findByMerchantId(merchant.getId())).containsExactly(primary, sub);
    }

    @Test
    void databaseRejectsBalanceChangesOutsideTheLedger() {
        Account primary = accounts.insert(NewAccount.primary(merchant.getId(), nubans.next(), "Ade Stores"));

        assertThatThrownBy(() -> jdbc.sql("UPDATE accounts SET balance = 1000000 WHERE id = :id")
                .param("id", primary.getId())
                .update())
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("BALANCE_CHANGE_OUTSIDE_LEDGER");
    }

    @Test
    void databaseRejectsChangingAnAccountNumber() {
        Account primary = accounts.insert(NewAccount.primary(merchant.getId(), nubans.next(), "Ade Stores"));

        assertThatThrownBy(() -> jdbc.sql("UPDATE accounts SET account_number = '0000000000' WHERE id = :id")
                .param("id", primary.getId())
                .update())
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("IMMUTABLE_FIELD");
    }
}
