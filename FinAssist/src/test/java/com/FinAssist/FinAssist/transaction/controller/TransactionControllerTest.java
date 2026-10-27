package com.FinAssist.FinAssist.transaction.controller;

import com.FinAssist.FinAssist.transaction.dto.TransactionResponse;
import com.FinAssist.FinAssist.transaction.dto.TransactionSummaryResponse;
import com.FinAssist.FinAssist.transaction.entity.TransactionStatus;
import com.FinAssist.FinAssist.transaction.entity.TransactionType;
import com.FinAssist.FinAssist.transaction.service.TransactionNotFoundException;
import com.FinAssist.FinAssist.transaction.service.TransactionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(TransactionController.class)
@Import(GlobalExceptionHandler.class)
class TransactionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TransactionService transactionService;

    @Test
    void createsTransactionAndReturnsLocation() throws Exception {
        when(transactionService.create(any())).thenReturn(sampleTransaction());

        mockMvc.perform(post("/api/transactions")
                        .contentType("application/json")
                        .content("""
                                {
                                  "transactionType": "EXPENSE",
                                  "amount": 42.75,
                                  "category": "FOOD",
                                  "merchant": "Harbor Cafe",
                                  "description": "Lunch",
                                  "transactionDate": "2026-10-24T12:30:00"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "http://localhost/api/transactions/12"))
                .andExpect(jsonPath("$.id").value(12))
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    void rejectsInvalidAmount() throws Exception {
        mockMvc.perform(post("/api/transactions")
                        .contentType("application/json")
                        .content("""
                                {
                                  "transactionType": "EXPENSE",
                                  "amount": -1,
                                  "category": "FOOD",
                                  "merchant": "Harbor Cafe",
                                  "transactionDate": "2026-10-24T12:30:00"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Request validation failed"))
                .andExpect(jsonPath("$.details.amount").exists());
        verify(transactionService, never()).create(any());
    }

    @Test
    void rejectsAmountWithTooManyFractionDigits() throws Exception {
        mockMvc.perform(post("/api/transactions")
                        .contentType("application/json")
                        .content("""
                                {
                                  "transactionType": "EXPENSE",
                                  "amount": 42.755,
                                  "category": "FOOD",
                                  "merchant": "Harbor Cafe",
                                  "transactionDate": "2026-10-24T12:30:00"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.amount").exists());
    }

    @Test
    void explainsInvalidEnumInRequestBody() throws Exception {
        mockMvc.perform(post("/api/transactions")
                        .contentType("application/json")
                        .content("""
                                {
                                  "transactionType": "TRANSFER",
                                  "amount": 42.75,
                                  "category": "FOOD",
                                  "merchant": "Harbor Cafe",
                                  "transactionDate": "2026-10-24T12:30:00"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.requestBody")
                        .value("enum value must be one of: INCOME, EXPENSE"));
    }

    @Test
    void explainsInvalidDateTimeInRequestBody() throws Exception {
        mockMvc.perform(post("/api/transactions")
                        .contentType("application/json")
                        .content("""
                                {
                                  "transactionType": "EXPENSE",
                                  "amount": 42.75,
                                  "category": "FOOD",
                                  "merchant": "Harbor Cafe",
                                  "transactionDate": "not-a-date"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.requestBody")
                        .value("date-time values must use ISO local date-time format, for example 2026-10-26T22:00:00"));
    }

    @Test
    void returnsNotFoundForMissingTransaction() throws Exception {
        when(transactionService.findById(999L)).thenThrow(new TransactionNotFoundException(999L));

        mockMvc.perform(get("/api/transactions/999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Transaction with ID 999 was not found"));
    }

    @Test
    void rejectsAnInvalidStatusFilter() throws Exception {
        mockMvc.perform(get("/api/transactions").param("status", "UNKNOWN"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Query parameter has an invalid value"))
                .andExpect(jsonPath("$.details.status")
                        .value("must be one of: PENDING, COMPLETED, FAILED"));
    }

    @Test
    void summaryRouteReturnsDedicatedSummaryResponse() throws Exception {
        when(transactionService.summarize(null, null)).thenReturn(new TransactionSummaryResponse(
                new BigDecimal("3200.00"),
                new BigDecimal("245.50"),
                new BigDecimal("2954.50"),
                5,
                2,
                2,
                1,
                java.util.List.of(),
                java.util.List.of()
        ));

        mockMvc.perform(get("/api/transactions/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalIncome").value(3200.00))
                .andExpect(jsonPath("$.totalExpenses").value(245.50))
                .andExpect(jsonPath("$.netBalance").value(2954.50))
                .andExpect(jsonPath("$.totalTransactions").value(5))
                .andExpect(jsonPath("$.pendingTransactions").value(2));

        verify(transactionService).summarize(null, null);
    }

    @Test
    void summaryRouteAcceptsOptionalDateRange() throws Exception {
        when(transactionService.summarize(any(), any())).thenReturn(new TransactionSummaryResponse(
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                0,
                0,
                0,
                0,
                java.util.List.of(),
                java.util.List.of()
        ));

        mockMvc.perform(get("/api/transactions/summary")
                        .param("startDate", "2026-10-01T00:00:00")
                        .param("endDate", "2026-10-27T23:59:59"))
                .andExpect(status().isOk());

        verify(transactionService).summarize(
                eq(LocalDateTime.parse("2026-10-01T00:00:00")),
                eq(LocalDateTime.parse("2026-10-27T23:59:59"))
        );
    }

    @Test
    void summaryRouteRejectsMalformedDateParameter() throws Exception {
        mockMvc.perform(get("/api/transactions/summary").param("startDate", "not-a-date"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.startDate").exists());
    }

    @Test
    void explainsInvalidDateFilterFormat() throws Exception {
        mockMvc.perform(get("/api/transactions").param("startDate", "not-a-date"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.startDate")
                        .value("must use ISO local date-time format, for example 2026-10-26T22:00:00"));
    }

    @Test
    void returnsFieldSpecificErrorForInvalidAmountRange() throws Exception {
        when(transactionService.findAll(
                any(), any(), any(), any(), any(), any(), any(), anyInt(), anyInt()))
                .thenThrow(new IllegalArgumentException("minAmount must be less than or equal to maxAmount"));

        mockMvc.perform(get("/api/transactions")
                        .param("minAmount", "500")
                        .param("maxAmount", "100"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.minAmount")
                        .value("minAmount must be less than or equal to maxAmount"));
    }

    @Test
    void hidesUnexpectedServerExceptionDetails() throws Exception {
        when(transactionService.findById(998L))
                .thenThrow(new RuntimeException("database credentials must not be returned"));

        mockMvc.perform(get("/api/transactions/998"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.message").value("An unexpected error occurred"))
                .andExpect(jsonPath("$.details").isEmpty());
    }

    private TransactionResponse sampleTransaction() {
        return new TransactionResponse(
                12L,
                TransactionType.EXPENSE,
                new BigDecimal("42.75"),
                "FOOD",
                "Harbor Cafe",
                "Lunch",
                LocalDateTime.parse("2026-10-24T12:30:00"),
                TransactionStatus.PENDING,
                LocalDateTime.parse("2026-10-24T12:31:00")
        );
    }
}
