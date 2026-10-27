package com.FinAssist.FinAssist.transaction.dto;

import java.math.BigDecimal;

public record CategorySummary(String category, BigDecimal totalAmount) {
}
