package com.FinAssist.FinAssist.transaction.repository;

import com.FinAssist.FinAssist.transaction.entity.Transaction;
import com.FinAssist.FinAssist.transaction.entity.TransactionStatus;
import com.FinAssist.FinAssist.transaction.entity.TransactionType;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public final class TransactionSpecifications {

    private TransactionSpecifications() {
    }

    public static Specification<Transaction> withFilters(
            BigDecimal minAmount,
            BigDecimal maxAmount,
            TransactionStatus status,
            TransactionType transactionType,
            String category,
            LocalDateTime startDate,
            LocalDateTime endDate
    ) {
        return (root, query, criteriaBuilder) -> {
            var predicates = criteriaBuilder.conjunction();
            if (minAmount != null) {
                predicates = criteriaBuilder.and(
                        predicates, criteriaBuilder.greaterThanOrEqualTo(root.get("amount"), minAmount));
            }
            if (maxAmount != null) {
                predicates = criteriaBuilder.and(
                        predicates, criteriaBuilder.lessThanOrEqualTo(root.get("amount"), maxAmount));
            }
            if (status != null) {
                predicates = criteriaBuilder.and(
                        predicates, criteriaBuilder.equal(root.get("status"), status));
            }
            if (transactionType != null) {
                predicates = criteriaBuilder.and(
                        predicates, criteriaBuilder.equal(root.get("transactionType"), transactionType));
            }
            if (category != null && !category.isBlank()) {
                predicates = criteriaBuilder.and(
                        predicates, criteriaBuilder.equal(root.get("category"), category));
            }
            if (startDate != null) {
                predicates = criteriaBuilder.and(
                        predicates, criteriaBuilder.greaterThanOrEqualTo(root.get("transactionDate"), startDate));
            }
            if (endDate != null) {
                predicates = criteriaBuilder.and(
                        predicates, criteriaBuilder.lessThanOrEqualTo(root.get("transactionDate"), endDate));
            }
            return predicates;
        };
    }
}
