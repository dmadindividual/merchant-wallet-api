package com.threeline.merchantwallet.ledger.repository;

import com.threeline.merchantwallet.ledger.domain.LedgerEntry;
import com.threeline.merchantwallet.ledger.domain.StatementEntry;

import java.util.List;

public interface LedgerEntryRepository {

    List<LedgerEntry> findByAccount(long accountId, Long beforeId, int limit);

    List<LedgerEntry> findByTransaction(long transactionId);

    long sumBalanceFromEntries(long accountId);

    List<StatementEntry> findStatement(long accountId, Long beforeId, int limit);
}
