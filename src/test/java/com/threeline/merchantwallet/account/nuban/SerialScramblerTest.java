package com.threeline.merchantwallet.account.nuban;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Random;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SerialScramblerTest {

    private final SerialScrambler scrambler = SerialScrambler.withDefaults();

    @Test
    void unscrambleReversesScramble_soTheMappingIsOneToOne() {

        long[] edges = {0, 1, 2, 999_999_998, 999_999_999};
        for (long serial : edges) {
            assertThat(scrambler.unscramble(scrambler.scramble(serial))).isEqualTo(serial);
        }

        Random random = new Random(42);
        for (int i = 0; i < 100_000; i++) {
            long serial = Math.floorMod(random.nextLong(), SerialScrambler.MODULUS);
            assertThat(scrambler.unscramble(scrambler.scramble(serial))).isEqualTo(serial);
        }
    }

    @Test
    void consecutiveSerialsNeverCollide() {
        int count = 200_000;
        Set<Long> seen = new HashSet<>(count * 2);
        for (long serial = 1; serial <= count; serial++) {
            assertThat(seen.add(scrambler.scramble(serial)))
                    .as("serial %d produced a duplicate", serial)
                    .isTrue();
        }
    }

    @Test
    void outputsStayWithinNineDigits() {
        for (long serial = 1; serial <= 10_000; serial++) {
            assertThat(scrambler.scramble(serial)).isBetween(0L, 999_999_999L);
        }
    }

    @Test
    void consecutiveSerialsDoNotProduceConsecutiveOutputs() {
        long a = scrambler.scramble(1);
        long b = scrambler.scramble(2);
        assertThat(Math.abs(b - a)).isGreaterThan(1);
    }

    @Test
    void rejectsMultipliersThatWouldCauseCollisions() {
        assertThatThrownBy(() -> new SerialScrambler(2, 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new SerialScrambler(25, 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new SerialScrambler(0, 0)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsValuesOutOfRange() {
        assertThatThrownBy(() -> scrambler.scramble(-1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> scrambler.scramble(1_000_000_000L)).isInstanceOf(IllegalArgumentException.class);
    }
}
