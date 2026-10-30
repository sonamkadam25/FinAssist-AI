package com.FinAssist.FinAssist.ask.service;

import com.FinAssist.FinAssist.ask.dto.AskResult;
import com.FinAssist.FinAssist.transaction.dto.TransactionResponse;
import com.FinAssist.FinAssist.transaction.dto.TransactionSummaryResponse;
import com.FinAssist.FinAssist.transaction.entity.TransactionStatus;
import com.FinAssist.FinAssist.transaction.entity.TransactionType;
import com.FinAssist.FinAssist.transaction.service.TransactionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AskServiceImplTest {

    @Mock
    private ChatClient.Builder chatClientBuilder;

    @Mock
    private ChatClient chatClient;

    @Mock
    private ChatClient.ChatClientRequestSpec requestSpec;

    @Mock
    private ChatClient.CallResponseSpec responseSpec;

    @Mock
    private TransactionService transactionService;

    private final Clock reportingClock = Clock.fixed(
            Instant.parse("2026-10-30T12:00:00Z"),
            ZoneOffset.UTC
    );

    private AskServiceImpl askService;

    @BeforeEach
    void setUp() {
        when(chatClientBuilder.defaultSystem(anyString())).thenReturn(chatClientBuilder);
        when(chatClientBuilder.build()).thenReturn(chatClient);
        askService = new AskServiceImpl(chatClientBuilder, transactionService, reportingClock);
    }

    @Test
    void sendsGeneralQuestionWithoutRegisteringUnsupportedModelTools() {
        String question = "What is compound interest?";
        stubModelResponse(question, "Interest earned on prior interest.");

        AskResult result = askService.ask(question);

        assertEquals("Interest earned on prior interest.", result.answer());
        assertNull(result.data());
        verify(chatClientBuilder).defaultSystem(AskServiceImpl.SYSTEM_INSTRUCTION);
        verify(requestSpec).user(question);
        verify(requestSpec, never()).tools(any(Object[].class));
    }

    @Test
    void wrapsModelFailureWithoutExposingUnderlyingMessage() {
        when(chatClient.prompt()).thenThrow(new RuntimeException("private host and port details"));

        AskServiceException exception = assertThrows(
                AskServiceException.class,
                () -> askService.ask("What is a budget?")
        );

        assertEquals("The AI service could not generate a response. Please try again later.",
                exception.getMessage());
        assertEquals("private host and port details", exception.getCause().getMessage());
    }

    @Test
    void rejectsBlankModelResponseInsteadOfReturningSuccess() {
        stubModelResponse("What is a budget?", "  ");

        assertThrows(AskServiceException.class, () -> askService.ask("What is a budget?"));
    }

    @Test
    void getsCurrentMonthSummaryBeforePassingBackendDataToModel() {
        String question = "How much did I spend this month?";
        TransactionSummaryResponse summary = summary("1250.00", "450.25", "799.75", 3);
        when(transactionService.summarize(
                LocalDateTime.parse("2026-10-01T00:00"),
                LocalDateTime.parse("2026-10-31T23:59:59.999999")))
                .thenReturn(summary);
        stubModelResponse(question, "The backend summary reports expenses of 450.25.");

        AskResult result = askService.ask(question);

        assertEquals("The backend summary reports expenses of 450.25.", result.answer());
        assertEquals(new BigDecimal("450.25"), result.data().summary().statistics().totalExpenses());
        verify(transactionService).summarize(
                LocalDateTime.parse("2026-10-01T00:00"),
                LocalDateTime.parse("2026-10-31T23:59:59.999999")
        );
        ArgumentCaptor<String> systemContext = ArgumentCaptor.forClass(String.class);
        verify(requestSpec).system(systemContext.capture());
        assertTrue(systemContext.getValue().contains("Validated transaction data from the backend"));
        assertTrue(systemContext.getValue().contains("totalExpenses=450.25"));
        verify(requestSpec, never()).tools(any(Object[].class));
    }

    @Test
    void answersTransactionCountFromBackendSummary() {
        String question = "How many transactions are recorded?";
        when(transactionService.summarize(null, null))
                .thenReturn(summary("0.00", "0.00", "0.00", 4));
        stubModelResponse(question, "The backend summary contains four transactions.");

        AskResult result = askService.ask(question);

        assertEquals(4, result.data().summary().statistics().totalTransactions());
        verify(transactionService).summarize(null, null);
    }

    @Test
    void searchesBackendForStatusQuestion() {
        String question = "Show my completed transactions";
        TransactionResponse record = transaction(1L, new BigDecimal("24.50"), TransactionStatus.COMPLETED);
        when(transactionService.findAll(
                any(), any(), eq(TransactionStatus.COMPLETED), any(), any(), any(), any(), eq(0), eq(20)))
                .thenReturn(new PageImpl<>(List.of(record), PageRequest.of(0, 20), 1));
        stubModelResponse(question, "One completed transaction was found.");

        AskResult result = askService.ask(question);

        assertEquals(1, result.data().search().totalMatches());
        assertEquals(TransactionStatus.COMPLETED, result.data().search().transactions().getFirst().status());
        verify(transactionService).findAll(
                any(), any(), eq(TransactionStatus.COMPLETED), any(), any(), any(), any(), eq(0), eq(20));
    }

    @Test
    void translatesStrictAboveThresholdToSupportedInclusiveAmountFilter() {
        String question = "Find transactions above 5000";
        when(transactionService.findAll(
                eq(new BigDecimal("5000.01")), any(), any(), any(), any(), any(), any(), eq(0), eq(20)))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));
        stubModelResponse(question, "No transactions matched.");

        AskResult result = askService.ask(question);

        assertEquals("No transactions matched the requested filters.", result.answer());
        verify(transactionService).findAll(
                eq(new BigDecimal("5000.01")), any(), any(), any(), any(), any(), any(), eq(0), eq(20));
    }

    @Test
    void doesNotAskModelToGuessUnsupportedPersonalFinancialData() {
        AskResult result = askService.ask("What is my bank account balance?");

        assertEquals(
                "I could not map that transaction question to a supported query. Please specify a transaction status, type, category, amount threshold, or date range.",
                result.answer()
        );
        assertNull(result.data());
        verify(chatClient, never()).prompt();
    }

    @Test
    void emptyBackendSummaryOverridesAnUnsupportedModelClaim() {
        String question = "How much did I spend this month?";
        when(transactionService.summarize(
                LocalDateTime.parse("2026-10-01T00:00"),
                LocalDateTime.parse("2026-10-31T23:59:59.999999")))
                .thenReturn(summary("0.00", "0.00", "0.00", 0));
        stubModelResponse(question, "You spent 450.25.");

        AskResult result = askService.ask(question);

        assertEquals("No transactions were recorded in the requested date range.", result.answer());
        assertEquals(0, result.data().summary().statistics().totalTransactions());
    }

    @Test
    void summarizesAnExplicitDateRangeThroughTheBackend() {
        String question = "Summarize transactions between 2026-10-01T00:00:00 and 2026-10-25T23:59:59";
        when(transactionService.summarize(
                LocalDateTime.parse("2026-10-01T00:00:00"),
                LocalDateTime.parse("2026-10-25T23:59:59")))
                .thenReturn(summary("100.00", "20.00", "80.00", 2));
        stubModelResponse(question, "The backend summary reports two transactions.");

        AskResult result = askService.ask(question);

        assertEquals(2, result.data().summary().statistics().totalTransactions());
        verify(transactionService).summarize(
                LocalDateTime.parse("2026-10-01T00:00:00"),
                LocalDateTime.parse("2026-10-25T23:59:59"));
    }

    private void stubModelResponse(String question, String answer) {
        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.system(anyString())).thenReturn(requestSpec);
        when(requestSpec.user(question)).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(responseSpec);
        when(responseSpec.content()).thenReturn(answer);
    }

    private TransactionSummaryResponse summary(String income, String expenses, String net, long count) {
        return new TransactionSummaryResponse(
                new BigDecimal(income),
                new BigDecimal(expenses),
                new BigDecimal(net),
                count,
                count,
                0,
                0,
                List.of(),
                List.of()
        );
    }

    private TransactionResponse transaction(Long id, BigDecimal amount, TransactionStatus status) {
        return new TransactionResponse(
                id,
                TransactionType.EXPENSE,
                amount,
                "FOOD",
                "Synthetic merchant",
                "Demo record",
                LocalDateTime.parse("2026-10-15T12:00:00"),
                status,
                LocalDateTime.parse("2026-10-15T12:01:00")
        );
    }
}
