package com.threeline.merchantwallet.account.nuban;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class NubanGenerator {

    private final NubanSerialSource serialSource;
    private final SerialScrambler scrambler;
    private final String bankCode;

    @Autowired
    public NubanGenerator(NubanSerialSource serialSource, NubanProperties properties) {
        this(serialSource, SerialScrambler.withDefaults(), properties.bankCode());
    }

    NubanGenerator(NubanSerialSource serialSource, SerialScrambler scrambler, String bankCode) {
        this.serialSource = serialSource;
        this.scrambler = scrambler;
        this.bankCode = bankCode;
    }

    public String next() {
        long serial = serialSource.nextSerial();
        return Nuban.build(bankCode, scrambler.scramble(serial));
    }

    public boolean isValid(String nuban) {
        return Nuban.isValid(bankCode, nuban);
    }
}
