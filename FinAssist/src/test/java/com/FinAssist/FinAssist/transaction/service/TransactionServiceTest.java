package com.FinAssist.FinAssist.transaction.service;

import com.FinAssist.FinAssist.transaction.dto.CreateTransactionRequest;
import com.FinAssist.FinAssist.transaction.dto.TransactionResponse;
import com.FinAssist.FinAssist.transaction.dto.UpdateTransactionRequest;
import com.FinAssist.FinAssist.transaction.entity.Transaction;
import com.FinAssist.FinAssist.transaction.entity.TransactionStatus;
import com.FinAssist.FinAssist.transaction.entity.TransactionType;
import com.FinAssist.FinAssist.transaction.repository.CategoryTransactionAggregate;
import com.FinAssist.FinAssist.transaction.repository.TransactionRepository;
import com.FinAssist.FinAssist.transaction.repository.TransactionSummaryAggregate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TransactionServiceTest {

    @Mock
    private TransactionRepository transactionRepository;

    @InjectMocks
    private TransactionService transactionService;

    @Test
    void createsTransactionAndDefaultsStatusToPending() {
        when(transactionRepository.save(any(Transaction.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        TransactionResponse response = transactionService.create(createRequest(null));

        ArgumentCaptor<Transaction> transaction = ArgumentCaptor.forClass(Transaction.class);
        verify(transactionRepository).save(transaction.capture());
        assertEquals(new BigDecimal("125.50"), transaction.getValue().getAmount());
        assertEquals(TransactionStatus.PENDING, response.status());
    }

    @Test
    void retrievesTransactionsWithAmountAndStatusFilters() {
        Transaction transaction = sampleTransaction();
        when(transactionRepository.findAll(
                org.mockito.ArgumentMatchers.<Specification<Transaction>>any(),
                any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(transaction)));

        var page = transactionService.findAll(
                new BigDecimal("100.00"),
                new BigDecimal("500.00"),
                TransactionStatus.COMPLETED,
                TransactionType.EXPENSE,
                "FOOD",
                null,
                null,
                0,
                20
        );

        assertEquals(1, page.getTotalElements());
        assertEquals(TransactionStatus.COMPLETED, page.getContent().getFirst().status());
        verify(transactionRepository).findAll(
                org.mockito.ArgumentMatchers.<Specification<Transaction>>any(),
                any(Pageable.class));
    }

    @Test
    void updatesExistingTransaction() {
        Transaction transaction = sampleTransaction();
        when(transactionRepository.findById(12L)).thenReturn(Optional.of(transaction));
        when(transactionRepository.save(any(Transaction.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        UpdateTransactionRequest request = new UpdateTransactionRequest(
                TransactionType.INCOME,
                new BigDecimal("2000.00"),
                "SALARY",
                "Northwind Payroll",
                "Monthly pay",
                LocalDateTime.parse("2026-10-25T09:00:00"),
                TransactionStatus.COMPLETED
        );

        TransactionResponse response = transactionService.update(12L, request);

        assertEquals(TransactionType.INCOME, response.transactionType());
        assertEquals(new BigDecimal("2000.00"), response.amount());
        assertEquals(TransactionStatus.COMPLETED, response.status());
    }

    @Test
    void deletesExistingTransaction() {
        when(transactionRepository.findById(12L)).thenReturn(Optional.of(sampleTransaction()));

        transactionService.delete(12L);

        verify(transactionRepository).delete(any(Transaction.class));
    }

    @Test
    void returnsNotFoundWhenTransactionDoesNotExist() {
        when(transactionRepository.findById(anyLong())).thenReturn(Optional.empty());

        assertThrows(TransactionNotFoundException.class, () -> transactionService.findById(999L));
    }

    @Test
    void rejectsAmountRangeWhenMinimumExceedsMaximum() {
        assertThrows(IllegalArgumentException.class, () -> transactionService.findAll(
                new BigDecimal("501"),
                new BigDecimal("500"),
                null,
                null,
                null,
                null,
                null,
                0,
                20
        ));
    }

    @Test
    void rejectsNonPositiveMinimumAmountFilter() {
        assertThrows(IllegalArgumentException.class, () -> transactionService.findAll(
                BigDecimal.ZERO,
                new BigDecimal("500"),
                null,
                null,
                null,
                null,
                null,
                0,
                20
        ));
    }

    @Test
    void rejectsDateRangeWhenStartIsAfterEnd() {
        assertThrows(IllegalArgumentException.class, () -> transactionService.findAll(
                null,
                null,
                null,
                null,
                null,
                LocalDateTime.parse("2026-10-26T00:00:00"),
                LocalDateTime.parse("2026-10-25T00:00:00"),
                0,
                20
        ));
    }

    @Test
    void summarizesCompletedIncomeExpensesCountsAndCategoryTotals() {
        TransactionSummaryAggregate aggregate = summaryAggregate(
                new BigDecimal("3200.00"),
                new BigDecimal("245.50"),
                5L,
                2L,
                2L,
                1L
        );
        when(transactionRepository.summarize(
                isNull(), isNull(), any(), any(), any(), any(), any()))
                .thenReturn(aggregate);
        List<CategoryTransactionAggregate> categoryAggregates = List.of(
                categoryAggregate("FOOD", TransactionType.EXPENSE, new BigDecimal("145.50")),
                categoryAggregate("TRAVEL", TransactionType.EXPENSE, new BigDecimal("100.00")),
                categoryAggregate("SALARY", TransactionType.INCOME, new BigDecimal("3200.00"))
        );
        when(transactionRepository.summarizeByCategory(isNull(), isNull(), any()))
                .thenReturn(categoryAggregates);

        var summary = transactionService.summarize(null, null);

        assertEquals(new BigDecimal("3200.00"), summary.totalIncome());
        assertEquals(new BigDecimal("245.50"), summary.totalExpenses());
        assertEquals(new BigDecimal("2954.50"), summary.netBalance());
        assertEquals(5L, summary.totalTransactions());
        assertEquals(2L, summary.completedTransactions());
        assertEquals(2L, summary.pendingTransactions());
        assertEquals(1L, summary.failedTransactions());
        assertEquals(List.of(
                new com.FinAssist.FinAssist.transaction.dto.CategorySummary("FOOD", new BigDecimal("145.50")),
                new com.FinAssist.FinAssist.transaction.dto.CategorySummary("TRAVEL", new BigDecimal("100.00"))
        ), summary.expenseTotalsByCategory());
        assertEquals(List.of(
                new com.FinAssist.FinAssist.transaction.dto.CategorySummary("SALARY", new BigDecimal("3200.00"))
        ), summary.incomeTotalsByCategory());
        verify(transactionRepository).summarize(
                null,
                null,
                TransactionType.INCOME,
                TransactionType.EXPENSE,
                TransactionStatus.COMPLETED,
                TransactionStatus.PENDING,
                TransactionStatus.FAILED
        );
        verify(transactionRepository).summarizeByCategory(null, null, TransactionStatus.COMPLETED);
    }

    @Test
    void returnsZeroTotalsAndEmptyCategoriesWhenNoTransactionsMatch() {
        TransactionSummaryAggregate aggregate = emptySummaryAggregate();
        when(transactionRepository.summarize(
                isNull(), isNull(), any(), any(), any(), any(), any()))
                .thenReturn(aggregate);
        when(transactionRepository.summarizeByCategory(isNull(), isNull(), any()))
                .thenReturn(List.of());

        var summary = transactionService.summarize(null, null);

        assertEquals(BigDecimal.ZERO, summary.totalIncome());
        assertEquals(BigDecimal.ZERO, summary.totalExpenses());
        assertEquals(BigDecimal.ZERO, summary.netBalance());
        assertEquals(0L, summary.totalTransactions());
        assertEquals(0L, summary.completedTransactions());
        assertEquals(0L, summary.pendingTransactions());
        assertEquals(0L, summary.failedTransactions());
        assertEquals(List.of(), summary.expenseTotalsByCategory());
        assertEquals(List.of(), summary.incomeTotalsByCategory());
    }

    @Test
    void passesInclusiveSummaryDateRangeToAggregateQueries() {
        LocalDateTime startDate = LocalDateTime.parse("2026-10-01T00:00:00");
        LocalDateTime endDate = LocalDateTime.parse("2026-10-27T23:59:59");
        TransactionSummaryAggregate aggregate = emptySummaryAggregate();
        when(transactionRepository.summarize(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(aggregate);
        when(transactionRepository.summarizeByCategory(any(), any(), any())).thenReturn(List.of());

        transactionService.summarize(startDate, endDate);

        verify(transactionRepository).summarize(
                startDate, endDate,
                TransactionType.INCOME, TransactionType.EXPENSE,
                TransactionStatus.COMPLETED, TransactionStatus.PENDING, TransactionStatus.FAILED
        );
        verify(transactionRepository).summarizeByCategory(
                startDate, endDate, TransactionStatus.COMPLETED);
    }

    @Test
    void returnsEmptySummaryWhenDateRangeHasNoMatchingTransactions() {
        LocalDateTime startDate = LocalDateTime.parse("2026-10-01T00:00:00");
        LocalDateTime endDate = LocalDateTime.parse("2026-10-27T23:59:59");
        TransactionSummaryAggregate aggregate = emptySummaryAggregate();
        when(transactionRepository.summarize(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(aggregate);
        when(transactionRepository.summarizeByCategory(any(), any(), any())).thenReturn(List.of());

        var summary = transactionService.summarize(startDate, endDate);

        assertEquals(0L, summary.totalTransactions());
        assertEquals(BigDecimal.ZERO, summary.totalIncome());
        assertEquals(BigDecimal.ZERO, summary.totalExpenses());
        assertEquals(List.of(), summary.expenseTotalsByCategory());
        verify(transactionRepository).summarize(
                startDate, endDate,
                TransactionType.INCOME, TransactionType.EXPENSE,
                TransactionStatus.COMPLETED, TransactionStatus.PENDING, TransactionStatus.FAILED
        );
    }

    @Test
    void supportsSummaryWithOnlyStartDate() {
        LocalDateTime startDate = LocalDateTime.parse("2026-10-01T00:00:00");
        TransactionSummaryAggregate aggregate = emptySummaryAggregate();
        when(transactionRepository.summarize(any(), isNull(), any(), any(), any(), any(), any()))
                .thenReturn(aggregate);
        when(transactionRepository.summarizeByCategory(any(), isNull(), any())).thenReturn(List.of());

        transactionService.summarize(startDate, null);

        verify(transactionRepository).summarize(
                startDate, null,
                TransactionType.INCOME, TransactionType.EXPENSE,
                TransactionStatus.COMPLETED, TransactionStatus.PENDING, TransactionStatus.FAILED
        );
    }

    @Test
    void rejectsInvalidSummaryDateRangeBeforeQueryingRepository() {
        LocalDateTime startDate = LocalDateTime.parse("2026-10-28T00:00:00");
        LocalDateTime endDate = LocalDateTime.parse("2026-10-27T23:59:59");

        assertThrows(IllegalArgumentException.class,
                () -> transactionService.summarize(startDate, endDate));

        verify(transactionRepository, org.mockito.Mockito.never()).summarize(
                any(), any(), any(), any(), any(), any(), any());
        verify(transactionRepository, org.mockito.Mockito.never())
                .summarizeByCategory(any(), any(), any());
    }

    @Test
    void rejectsInvalidPagination() {
        assertThrows(IllegalArgumentException.class, () -> transactionService.findAll(
                null, null, null, null, null, null, null, 0, 101));
    }

    private CreateTransactionRequest createRequest(TransactionStatus status) {
        return new CreateTransactionRequest(
                TransactionType.EXPENSE,
                new BigDecimal("125.50"),
                "FOOD",
                "Harbor Cafe",
                "Dinner",
                LocalDateTime.parse("2026-10-24T19:00:00"),
                status
        );
    }

    private Transaction sampleTransaction() {
        return new Transaction(
                TransactionType.EXPENSE,
                new BigDecimal("125.50"),
                "FOOD",
                "Harbor Cafe",
                "Dinner",
                LocalDateTime.parse("2026-10-24T19:00:00"),
                TransactionStatus.COMPLETED
        );
    }

    private TransactionSummaryAggregate summaryAggregate(
            BigDecimal income,
            BigDecimal expenses,
            Long total,
            Long completed,
            Long pending,
            Long failed
    ) {
        TransactionSummaryAggregate aggregate = org.mockito.Mockito.mock(TransactionSummaryAggregate.class);
        when(aggregate.getTotalIncome()).thenReturn(income);
        when(aggregate.getTotalExpenses()).thenReturn(expenses);
        when(aggregate.getTotalTransactions()).thenReturn(total);
        when(aggregate.getCompletedTransactions()).thenReturn(completed);
        when(aggregate.getPendingTransactions()).thenReturn(pending);
        when(aggregate.getFailedTransactions()).thenReturn(failed);
        return aggregate;
    }

    private TransactionSummaryAggregate emptySummaryAggregate() {
        return summaryAggregate(null, null, 0L, 0L, 0L, 0L);
    }

    private CategoryTransactionAggregate categoryAggregate(
            String category,
            TransactionType type,
            BigDecimal amount
    ) {
        CategoryTransactionAggregate aggregate = org.mockito.Mockito.mock(CategoryTransactionAggregate.class);
        when(aggregate.getCategory()).thenReturn(category);
        when(aggregate.getTransactionType()).thenReturn(type);
        when(aggregate.getTotalAmount()).thenReturn(amount);
        return aggregate;
    }
}
