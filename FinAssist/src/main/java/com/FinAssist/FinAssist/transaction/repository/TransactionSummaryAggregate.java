package com.FinAssist.FinAssist.transaction.repository;

import java.math.BigDecimal;

public interface TransactionSummaryAggregate {

    BigDecimal getTotalIncome();

    BigDecimal getTotalExpenses();

    Long getTotalTransactions();

    Long getCompletedTransactions();

    Long getPendingTransactions();

    Long getFailedTransactions();
}
