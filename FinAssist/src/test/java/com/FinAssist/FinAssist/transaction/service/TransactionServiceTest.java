package com.FinAssist.FinAssist.transaction.service;

import com.FinAssist.FinAssist.transaction.dto.CreateTransactionRequest;
import com.FinAssist.FinAssist.transaction.dto.TransactionResponse;
import com.FinAssist.FinAssist.transaction.dto.UpdateTransactionRequest;
import com.FinAssist.FinAssist.transaction.entity.Transaction;
import com.FinAssist.FinAssist.transaction.entity.TransactionStatus;
import com.FinAssist.FinAssist.transaction.entity.TransactionType;
import com.FinAssist.FinAssist.transaction.repository.TransactionRepository;
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
}
