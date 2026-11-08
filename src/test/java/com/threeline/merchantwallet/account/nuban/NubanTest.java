package com.threeline.merchantwallet.account.nuban;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NubanTest {

    @Test
    void checkDigit_matchesHandComputedExample() {

        assertThat(Nuban.checkDigit("058", "000000001")).isEqualTo(8);
        assertThat(Nuban.build("058", 1)).isEqualTo("0000000018");
    }

    @Test
    void checkDigit_matchesSecondHandComputedExample() {

        assertThat(Nuban.build("011", 123_456_789)).isEqualTo("1234567895");
    }

    @Test
    void checkDigit_isZeroWhenSumIsMultipleOfTen() {

        assertThat(Nuban.build("000", 0)).isEqualTo("0000000000");
    }

    @Test
    void builtNubanIsValid() {
        String nuban = Nuban.build("999", 482_015_337);
        assertThat(nuban).hasSize(10).containsOnlyDigits();
        assertThat(Nuban.isValid("999", nuban)).isTrue();
    }

    @Test
    void everySingleDigitErrorIsDetected() {
        String valid = Nuban.build("999", 482_015_337);

        for (int position = 0; position < Nuban.LENGTH; position++) {
            for (char digit = '0'; digit <= '9'; digit++) {
                if (valid.charAt(position) == digit) {
                    continue;
                }
                String tampered = valid.substring(0, position) + digit + valid.substring(position + 1);
                assertThat(Nuban.isValid("999", tampered))
                        .as("changing position %d to %s should be detected", position, digit)
                        .isFalse();
            }
        }
    }

    @Test
    void sameNumberIsInvalidUnderADifferentBankCode() {
        String nuban = Nuban.build("058", 1);
        assertThat(Nuban.isValid("011", nuban)).isFalse();
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "123", "12345678901", "12345A7890", "１２３４５６７８９０"})
    void isValid_rejectsMalformedInput(String input) {
        assertThat(Nuban.isValid("999", input)).isFalse();
    }

    @Test
    void build_rejectsSerialOutOfRange() {
        assertThatThrownBy(() -> Nuban.build("999", -1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Nuban.build("999", 1_000_000_000L)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void build_rejectsBadBankCode() {
        assertThatThrownBy(() -> Nuban.build("99", 1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Nuban.build("9A9", 1)).isInstanceOf(IllegalArgumentException.class);
    }
}
