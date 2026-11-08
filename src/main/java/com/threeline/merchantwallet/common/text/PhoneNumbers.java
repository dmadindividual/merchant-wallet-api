package com.threeline.merchantwallet.common.text;

public final class PhoneNumbers {

    private PhoneNumbers() {
    }

    public static String normalise(String phone) {
        if (phone == null || phone.isBlank()) {
            return null;
        }
        String digits = phone.trim().replaceAll("[\\s-]", "");
        if (digits.startsWith("+")) {
            return digits;
        }
        if (digits.startsWith("234")) {
            return "+" + digits;
        }
        if (digits.startsWith("0")) {
            return "+234" + digits.substring(1);
        }
        return digits;
    }
}
