package com.threeline.merchantwallet.common.money;

import java.math.BigDecimal;
import java.math.RoundingMode;

public record Money(long kobo) {

    public static final Money ZERO = new Money(0);

    public Money {
        if (kobo < 0) {
            throw new IllegalArgumentException("Money cannot be negative: " + kobo + " kobo");
        }
    }

    public static Money ofKobo(long kobo) {
        return new Money(kobo);
    }

    public static Money ofNaira(BigDecimal naira) {
        if (naira == null) {
            throw new IllegalArgumentException("Amount is required");
        }
        try {
            BigDecimal exact = naira.setScale(2, RoundingMode.UNNECESSARY);
            return new Money(exact.movePointRight(2).longValueExact());
        } catch (ArithmeticException e) {
            throw new IllegalArgumentException(
                    "Amount must have at most 2 decimal places and fit in range: " + naira.toPlainString(), e);
        }
    }

    public BigDecimal toNaira() {
        return BigDecimal.valueOf(kobo, 2);
    }

    public boolean isZero() {
        return kobo == 0;
    }

    public boolean isPositive() {
        return kobo > 0;
    }

    public boolean isGreaterThanOrEqual(Money other) {
        return kobo >= other.kobo;
    }

    public Money plus(Money other) {
        return new Money(Math.addExact(kobo, other.kobo));
    }

    public Money minus(Money other) {
        return new Money(Math.subtractExact(kobo, other.kobo));
    }

    @Override
    public String toString() {
        return "NGN " + toNaira().toPlainString();
    }
}
