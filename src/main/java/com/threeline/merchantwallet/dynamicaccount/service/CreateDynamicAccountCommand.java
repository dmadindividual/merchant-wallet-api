package com.threeline.merchantwallet.dynamicaccount.service;

public record CreateDynamicAccountCommand(String accountName, Long expectedAmountKobo) {
}
