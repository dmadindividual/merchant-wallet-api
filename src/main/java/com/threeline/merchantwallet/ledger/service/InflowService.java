package com.threeline.merchantwallet.ledger.service;

public interface InflowService {

    PostingResult receive(InflowCommand command);
}
