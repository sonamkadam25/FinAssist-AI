package com.FinAssist.FinAssist.ask.dto;

import com.FinAssist.FinAssist.transaction.dto.TransactionSummaryResponse;

import java.time.LocalDateTime;

public record TransactionSummaryData(
        LocalDateTime startDate,
        LocalDateTime endDate,
        TransactionSummaryResponse statistics
) {
}
