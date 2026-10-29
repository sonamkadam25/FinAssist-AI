package com.FinAssist.FinAssist.ask.dto;

public record AskTransactionData(
        TransactionSummaryData summary,
        TransactionSearchData search
) {
}
