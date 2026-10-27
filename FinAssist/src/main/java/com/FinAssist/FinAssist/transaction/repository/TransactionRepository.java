package com.FinAssist.FinAssist.transaction.repository;

import com.FinAssist.FinAssist.transaction.entity.Transaction;
import com.FinAssist.FinAssist.transaction.entity.TransactionStatus;
import com.FinAssist.FinAssist.transaction.entity.TransactionType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface TransactionRepository extends JpaRepository<Transaction, Long>,
        JpaSpecificationExecutor<Transaction> {

    @Query("""
            select
                sum(case when t.transactionType = :income and t.status = :completed
                         then t.amount else null end) as totalIncome,
                sum(case when t.transactionType = :expense and t.status = :completed
                         then t.amount else null end) as totalExpenses,
                count(t.id) as totalTransactions,
                count(case when t.status = :completed then t.id else null end) as completedTransactions,
                count(case when t.status = :pending then t.id else null end) as pendingTransactions,
                count(case when t.status = :failed then t.id else null end) as failedTransactions
            from Transaction t
            where (:startDate is null or t.transactionDate >= :startDate)
              and (:endDate is null or t.transactionDate <= :endDate)
            """)
    TransactionSummaryAggregate summarize(
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate,
            @Param("income") TransactionType income,
            @Param("expense") TransactionType expense,
            @Param("completed") TransactionStatus completed,
            @Param("pending") TransactionStatus pending,
            @Param("failed") TransactionStatus failed
    );

    @Query("""
            select t.category as category,
                   t.transactionType as transactionType,
                   sum(t.amount) as totalAmount
            from Transaction t
            where t.status = :completed
              and (:startDate is null or t.transactionDate >= :startDate)
              and (:endDate is null or t.transactionDate <= :endDate)
            group by t.category, t.transactionType
            order by t.category, t.transactionType
            """)
    List<CategoryTransactionAggregate> summarizeByCategory(
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate,
            @Param("completed") TransactionStatus completed
    );
}
