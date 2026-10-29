package com.FinAssist.FinAssist.ask.dto;

import java.util.List;

public record TransactionSearchData(
        List<TransactionDataItem> transactions,
        long totalMatches,
        int returnedCount,
        boolean truncated
) {
}
