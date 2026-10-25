package com.FinAssist.FinAssist.transaction.controller;

import com.FinAssist.FinAssist.transaction.dto.TransactionResponse;
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
                .andExpect(jsonPath("$.message")
                        .value("One or more query parameters have an invalid value"));
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
