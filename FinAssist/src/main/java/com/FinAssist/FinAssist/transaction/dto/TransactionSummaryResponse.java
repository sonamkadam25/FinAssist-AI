package com.FinAssist.FinAssist.transaction.dto;

import java.math.BigDecimal;
import java.util.List;

public record TransactionSummaryResponse(
        BigDecimal totalIncome,
        BigDecimal totalExpenses,
        BigDecimal netBalance,
        long totalTransactions,
        long completedTransactions,
        long pendingTransactions,
        long failedTransactions,
        List<CategorySummary> expenseTotalsByCategory,
        List<CategorySummary> incomeTotalsByCategory
) {
}
