package com.FinAssist.FinAssist.ask.service;

import com.FinAssist.FinAssist.ask.dto.AskResult;
import com.FinAssist.FinAssist.transaction.dto.TransactionSummaryResponse;
import com.FinAssist.FinAssist.transaction.service.TransactionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
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
    void sendsActualQuestionAndConfiguredSystemInstruction() {
        String question = "What is compound interest?";
        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.tools(any(Object[].class))).thenReturn(requestSpec);
        when(requestSpec.user(question)).thenReturn(requestSpec);
        when(requestSpec.system(anyString())).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(responseSpec);
        when(responseSpec.content()).thenReturn("Interest earned on prior interest.");

        AskResult result = askService.ask(question);

        assertEquals("Interest earned on prior interest.", result.answer());
        assertNull(result.data());
        verify(chatClientBuilder).defaultSystem(AskServiceImpl.SYSTEM_INSTRUCTION);
        verify(requestSpec).user(question);
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
        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.tools(any(Object[].class))).thenReturn(requestSpec);
        when(requestSpec.user("What is a budget?")).thenReturn(requestSpec);
        when(requestSpec.system(anyString())).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(responseSpec);
        when(responseSpec.content()).thenReturn("  ");

        assertThrows(AskServiceException.class, () -> askService.ask("What is a budget?"));
    }

    @Test
    void registersToolsAndReturnsExactBackendSummaryAsStructuredData() {
        String question = "How much did I spend this month?";
        TransactionSummaryResponse summary = new TransactionSummaryResponse(
                new BigDecimal("1250.00"),
                new BigDecimal("450.25"),
                new BigDecimal("799.75"),
                3,
                2,
                1,
                0,
                List.of(),
                List.of()
        );
        when(transactionService.summarize(
                java.time.LocalDateTime.parse("2026-10-01T00:00"),
                java.time.LocalDateTime.parse("2026-10-31T23:59:59.999999")))
                .thenReturn(summary);
        AtomicReference<Object[]> registeredTools = new AtomicReference<>();
        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.tools(any(Object[].class))).thenAnswer(invocation -> {
            registeredTools.set(new Object[]{invocation.getArgument(0, TransactionDataTools.class)});
            return requestSpec;
        });
        when(requestSpec.user(question)).thenReturn(requestSpec);
        when(requestSpec.system(anyString())).thenReturn(requestSpec);
        when(requestSpec.call()).thenAnswer(invocation -> {
            TransactionDataTools tools = (TransactionDataTools) registeredTools.get()[0];
            tools.getCurrentMonthTransactionSummary();
            return responseSpec;
        });
        when(responseSpec.content()).thenReturn("Backend summary reports expenses of 450.25.");

        AskResult result = askService.ask(question);

        assertEquals("Backend summary reports expenses of 450.25.", result.answer());
        assertEquals(new BigDecimal("450.25"), result.data().summary().statistics().totalExpenses());
        assertEquals(java.time.LocalDateTime.parse("2026-10-01T00:00"),
                result.data().summary().startDate());
        verify(transactionService).summarize(
                java.time.LocalDateTime.parse("2026-10-01T00:00"),
                java.time.LocalDateTime.parse("2026-10-31T23:59:59.999999")
        );
    }

    @Test
    void doesNotTrustAnAnswerToPersonalTransactionQuestionWithoutToolResult() {
        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.user("How much did I spend this month?")).thenReturn(requestSpec);
        when(requestSpec.system(anyString())).thenReturn(requestSpec);
        when(requestSpec.tools(any(Object[].class))).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(responseSpec);
        when(responseSpec.content()).thenReturn("You spent 99.00 this month.");

        AskResult result = askService.ask("How much did I spend this month?");

        assertEquals(
                "I could not map that transaction question to a supported query. Please specify a transaction status, type, category, amount threshold, or date range.",
                result.answer()
        );
        assertNull(result.data());
    }

    @Test
    void emptyBackendSummaryOverridesAnUnsupportedModelClaim() {
        String question = "How much did I spend this month?";
        TransactionSummaryResponse emptySummary = new TransactionSummaryResponse(
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                0,
                0,
                0,
                0,
                List.of(),
                List.of()
        );
        when(transactionService.summarize(
                java.time.LocalDateTime.parse("2026-10-01T00:00"),
                java.time.LocalDateTime.parse("2026-10-31T23:59:59.999999")))
                .thenReturn(emptySummary);
        AtomicReference<TransactionDataTools> registeredTools = new AtomicReference<>();
        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.tools(any(Object[].class))).thenAnswer(invocation -> {
            registeredTools.set(invocation.getArgument(0, TransactionDataTools.class));
            return requestSpec;
        });
        when(requestSpec.system(anyString())).thenReturn(requestSpec);
        when(requestSpec.user(question)).thenReturn(requestSpec);
        when(requestSpec.call()).thenAnswer(invocation -> {
            registeredTools.get().getCurrentMonthTransactionSummary();
            return responseSpec;
        });
        when(responseSpec.content()).thenReturn("You spent 450.25.");

        AskResult result = askService.ask(question);

        assertEquals("No transactions were recorded in the requested date range.", result.answer());
        assertEquals(0, result.data().summary().statistics().totalTransactions());
    }
}
