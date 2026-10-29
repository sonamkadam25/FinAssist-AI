package com.FinAssist.FinAssist.ask.dto;

import com.FinAssist.FinAssist.transaction.entity.TransactionStatus;
import com.FinAssist.FinAssist.transaction.entity.TransactionType;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record TransactionDataItem(
        Long id,
        TransactionType transactionType,
        BigDecimal amount,
        String category,
        LocalDateTime transactionDate,
        TransactionStatus status
) {
}
