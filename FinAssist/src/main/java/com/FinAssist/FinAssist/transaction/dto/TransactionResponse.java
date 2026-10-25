package com.FinAssist.FinAssist.transaction.dto;

import com.FinAssist.FinAssist.transaction.entity.Transaction;
import com.FinAssist.FinAssist.transaction.entity.TransactionStatus;
import com.FinAssist.FinAssist.transaction.entity.TransactionType;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record TransactionResponse(
        Long id,
        TransactionType transactionType,
        BigDecimal amount,
        String category,
        String merchant,
        String description,
        LocalDateTime transactionDate,
        TransactionStatus status,
        LocalDateTime createdAt
) {
    public static TransactionResponse from(Transaction transaction) {
        return new TransactionResponse(
                transaction.getId(),
                transaction.getTransactionType(),
                transaction.getAmount(),
                transaction.getCategory(),
                transaction.getMerchant(),
                transaction.getDescription(),
                transaction.getTransactionDate(),
                transaction.getStatus(),
                transaction.getCreatedAt()
        );
    }
}
