package com.threeline.merchantwallet.account.nuban;

public final class Nuban {

    public static final int LENGTH = 10;
    public static final int SERIAL_LENGTH = 9;
    public static final int BANK_CODE_LENGTH = 3;
    public static final long MAX_SERIAL = 999_999_999L;

    private static final int[] WEIGHTS = {3, 7, 3, 3, 7, 3, 3, 7, 3, 3, 7, 3};

    private Nuban() {
    }

    public static int checkDigit(String bankCode, String serial) {
        requireDigits(bankCode, BANK_CODE_LENGTH, "bankCode");
        requireDigits(serial, SERIAL_LENGTH, "serial");

        String payload = bankCode + serial;
        int sum = 0;
        for (int i = 0; i < WEIGHTS.length; i++) {
            sum += (payload.charAt(i) - '0') * WEIGHTS[i];
        }
        return (10 - (sum % 10)) % 10;
    }

    public static String build(String bankCode, long serial) {
        if (serial < 0 || serial > MAX_SERIAL) {
            throw new IllegalArgumentException("Serial must be between 0 and " + MAX_SERIAL + ": " + serial);
        }
        String serialDigits = String.format("%09d", serial);
        return serialDigits + checkDigit(bankCode, serialDigits);
    }

    public static boolean isValid(String bankCode, String nuban) {
        if (nuban == null || nuban.length() != LENGTH || !isAllDigits(nuban)) {
            return false;
        }
        int expected = checkDigit(bankCode, nuban.substring(0, SERIAL_LENGTH));
        return expected == nuban.charAt(SERIAL_LENGTH) - '0';
    }

    private static void requireDigits(String value, int length, String name) {
        if (value == null || value.length() != length || !isAllDigits(value)) {
            throw new IllegalArgumentException(name + " must be exactly " + length + " digits: " + value);
        }
    }

    private static boolean isAllDigits(String value) {
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c < '0' || c > '9') {
                return false;
            }
        }
        return true;
    }
}
