package com.threeline.merchantwallet.ledger.service;

public interface TransferService {

    PostingResult transfer(TransferCommand command);
}
