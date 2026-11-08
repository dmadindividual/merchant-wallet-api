package com.threeline.merchantwallet.account.nuban;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

class NubanGeneratorTest {

    private static final String BANK_CODE = "999";

    @Test
    void generatesValidUniqueTenDigitNumbers() {
        AtomicLong counter = new AtomicLong();
        NubanGenerator generator =
                new NubanGenerator(counter::incrementAndGet, SerialScrambler.withDefaults(), BANK_CODE);

        int count = 50_000;
        Set<String> issued = new HashSet<>(count * 2);
        for (int i = 0; i < count; i++) {
            String nuban = generator.next();
            assertThat(nuban).hasSize(10).containsOnlyDigits();
            assertThat(generator.isValid(nuban)).isTrue();
            assertThat(issued.add(nuban)).as("duplicate NUBAN %s", nuban).isTrue();
        }
    }

    @Test
    void sameSerialAlwaysGivesSameNuban() {
        NubanGenerator first = new NubanGenerator(() -> 7L, SerialScrambler.withDefaults(), BANK_CODE);
        NubanGenerator second = new NubanGenerator(() -> 7L, SerialScrambler.withDefaults(), BANK_CODE);
        assertThat(first.next()).isEqualTo(second.next());
    }
}
