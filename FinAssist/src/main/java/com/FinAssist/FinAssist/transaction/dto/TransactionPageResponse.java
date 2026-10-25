package com.FinAssist.FinAssist.transaction.dto;

import org.springframework.data.domain.Page;

import java.util.List;

public record TransactionPageResponse(
        List<TransactionResponse> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean last
) {
    public static TransactionPageResponse from(Page<TransactionResponse> transactions) {
        return new TransactionPageResponse(
                transactions.getContent(),
                transactions.getNumber(),
                transactions.getSize(),
                transactions.getTotalElements(),
                transactions.getTotalPages(),
                transactions.isLast()
        );
    }
}
