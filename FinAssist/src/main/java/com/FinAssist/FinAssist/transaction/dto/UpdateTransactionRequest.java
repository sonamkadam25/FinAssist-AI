package com.FinAssist.FinAssist.transaction.dto;

import com.FinAssist.FinAssist.transaction.entity.TransactionStatus;
import com.FinAssist.FinAssist.transaction.entity.TransactionType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record UpdateTransactionRequest(
        @NotNull TransactionType transactionType,
        @NotNull @DecimalMin(value = "0.01") @Digits(integer = 17, fraction = 2) BigDecimal amount,
        @NotBlank @Size(max = 50) String category,
        @NotBlank @Size(max = 100) String merchant,
        @Size(max = 500) String description,
        @NotNull LocalDateTime transactionDate,
        @NotNull TransactionStatus status
) {
}
