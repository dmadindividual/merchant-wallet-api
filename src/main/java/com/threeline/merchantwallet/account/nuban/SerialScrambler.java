package com.threeline.merchantwallet.account.nuban;

import java.math.BigInteger;

public final class SerialScrambler {

    static final long MODULUS = 1_000_000_000L;

    static final long DEFAULT_MULTIPLIER = 387_420_489L;
    static final long DEFAULT_OFFSET = 123_456_791L;

    private final long multiplier;
    private final long offset;
    private final long inverse;

    public SerialScrambler(long multiplier, long offset) {
        if (multiplier <= 0 || multiplier >= MODULUS) {
            throw new IllegalArgumentException("Multiplier must be between 1 and " + (MODULUS - 1));
        }
        if (multiplier % 2 == 0 || multiplier % 5 == 0) {
            throw new IllegalArgumentException(
                    "Multiplier must share no factor with 10 (odd and not a multiple of 5), otherwise outputs collide");
        }
        if (offset < 0 || offset >= MODULUS) {
            throw new IllegalArgumentException("Offset must be between 0 and " + (MODULUS - 1));
        }
        this.multiplier = multiplier;
        this.offset = offset;
        this.inverse = BigInteger.valueOf(multiplier)
                .modInverse(BigInteger.valueOf(MODULUS))
                .longValueExact();
    }

    public static SerialScrambler withDefaults() {
        return new SerialScrambler(DEFAULT_MULTIPLIER, DEFAULT_OFFSET);
    }

    public long scramble(long serial) {
        requireInRange(serial);

        return (multiplier * serial + offset) % MODULUS;
    }

    public long unscramble(long scrambled) {
        requireInRange(scrambled);
        long shifted = Math.floorMod(scrambled - offset, MODULUS);
        return (shifted * inverse) % MODULUS;
    }

    private static void requireInRange(long value) {
        if (value < 0 || value >= MODULUS) {
            throw new IllegalArgumentException("Value must be between 0 and " + (MODULUS - 1) + ": " + value);
        }
    }
}
