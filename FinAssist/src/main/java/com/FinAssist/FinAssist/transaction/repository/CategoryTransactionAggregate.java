package com.FinAssist.FinAssist.transaction.repository;

import com.FinAssist.FinAssist.transaction.entity.TransactionType;

import java.math.BigDecimal;

public interface CategoryTransactionAggregate {

    String getCategory();

    TransactionType getTransactionType();

    BigDecimal getTotalAmount();
}
