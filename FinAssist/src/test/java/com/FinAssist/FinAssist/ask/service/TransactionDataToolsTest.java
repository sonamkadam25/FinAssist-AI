package com.FinAssist.FinAssist.ask.service;

import com.FinAssist.FinAssist.transaction.dto.TransactionResponse;
import com.FinAssist.FinAssist.transaction.dto.TransactionSummaryResponse;
import com.FinAssist.FinAssist.transaction.entity.TransactionStatus;
import com.FinAssist.FinAssist.transaction.entity.TransactionType;
import com.FinAssist.FinAssist.transaction.service.TransactionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TransactionDataToolsTest {

    @Mock
    private TransactionService transactionService;

    private TransactionDataTools tools;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(Instant.parse("2026-10-30T12:00:00Z"), ZoneOffset.UTC);
        tools = new TransactionDataTools(transactionService, clock);
    }

    @Test
    void validSearchDelegatesFiltersAndUsesHardBoundedPageSize() {
        List<TransactionResponse> records = java.util.stream.IntStream.range(0, 20)
                .mapToObj(index -> transaction((long) index + 1))
                .toList();
        when(transactionService.findAll(
                eq(new BigDecimal("5000.00")),
                eq(null),
                eq(TransactionStatus.COMPLETED),
                eq(TransactionType.EXPENSE),
                eq("FOOD"),
                eq(LocalDateTime.parse("2026-10-01T00:00:00")),
                eq(null),
                eq(0),
                eq(TransactionDataTools.MAX_RESULTS)
        )).thenReturn(new PageImpl<>(records, PageRequest.of(0, 20), 27));

        var result = tools.searchTransactions(
                "COMPLETED", "EXPENSE", "FOOD", new BigDecimal("5000.00"), null,
                "2026-10-01T00:00:00", null);

        assertEquals(20, result.returnedCount());
        assertEquals(27, result.totalMatches());
        assertTrue(result.truncated());
        assertEquals(TransactionType.EXPENSE, result.transactions().getFirst().transactionType());
        verify(transactionService).findAll(
                eq(new BigDecimal("5000.00")),
                eq(null),
                eq(TransactionStatus.COMPLETED),
                eq(TransactionType.EXPENSE),
                eq("FOOD"),
                eq(LocalDateTime.parse("2026-10-01T00:00:00")),
                eq(null),
                eq(0),
                eq(20)
        );
    }

    @Test
    void emptySearchReturnsEmptyDataWithoutNotFound() {
        when(transactionService.findAll(any(), any(), any(), any(), any(), any(), any(), eq(0), eq(20)))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        var result = tools.searchTransactions(null, null, null, null, null, null, null);

        assertTrue(result.transactions().isEmpty());
        assertEquals(0, result.totalMatches());
        assertEquals(0, result.returnedCount());
        assertFalse(result.truncated());
    }

    @Test
    void rejectsUnsupportedStatusBeforeCallingTransactionService() {
        TransactionDataToolException exception = assertThrows(
                TransactionDataToolException.class,
                () -> tools.searchTransactions("ARCHIVED", null, null, null, null, null, null)
        );

        assertTrue(exception.hasInvalidArguments());
        assertTrue(exception.getMessage().contains("PENDING, COMPLETED, FAILED"));
        verify(transactionService, never()).findAll(any(), any(), any(), any(), any(), any(), any(), any(Integer.class),
                any(Integer.class));
    }

    @Test
    void rejectsInvalidDateAmountAndRangeArguments() {
        assertThrows(TransactionDataToolException.class, () -> tools.searchTransactions(
                null, null, null, BigDecimal.ZERO, null, null, null));
        assertThrows(TransactionDataToolException.class, () -> tools.searchTransactions(
                null, null, null, new BigDecimal("600"), new BigDecimal("500"), null, null));
        assertThrows(TransactionDataToolException.class, () -> tools.searchTransactions(
                null, null, null, null, null, "not-a-date", null));
        assertThrows(TransactionDataToolException.class, () -> tools.searchTransactions(
                null, null, null, null, null,
                "2026-10-30T00:00:00", "2026-10-01T00:00:00"));
    }

    @Test
    void rejectsMoreThanOneTransactionOperationPerQuestion() {
        when(transactionService.findAll(any(), any(), any(), any(), any(), any(), any(), eq(0), eq(20)))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        tools.searchTransactions(null, null, null, null, null, null, null);
        TransactionDataToolException exception = assertThrows(
                TransactionDataToolException.class,
                () -> tools.getTransactionSummary(null, null)
        );

        assertTrue(exception.hasInvalidArguments());
        verify(transactionService, never()).summarize(any(), any());
    }

    @Test
    void currentMonthSummaryUsesConfiguredReportingZoneAndBackendTotals() {
        TransactionSummaryResponse summary = new TransactionSummaryResponse(
                new BigDecimal("3000.00"),
                new BigDecimal("1200.00"),
                new BigDecimal("1800.00"),
                7,
                5,
                1,
                1,
                List.of(),
                List.of()
        );
        when(transactionService.summarize(
                eq(LocalDateTime.parse("2026-10-01T00:00")),
                eq(LocalDateTime.parse("2026-10-31T23:59:59.999999"))))
                .thenReturn(summary);

        var result = tools.getCurrentMonthTransactionSummary();

        assertEquals(summary, result.statistics());
        assertEquals(LocalDateTime.parse("2026-10-01T00:00"), result.startDate());
        assertEquals(LocalDateTime.parse("2026-10-31T23:59:59.999999"), result.endDate());
        verify(transactionService).summarize(result.startDate(), result.endDate());
    }

    private TransactionResponse transaction(Long id) {
        return new TransactionResponse(
                id,
                TransactionType.EXPENSE,
                new BigDecimal("5000.00"),
                "FOOD",
                "Synthetic merchant",
                "Not included in tool response",
                LocalDateTime.parse("2026-10-15T12:00:00"),
                TransactionStatus.COMPLETED,
                LocalDateTime.parse("2026-10-15T12:01:00")
        );
    }
}
