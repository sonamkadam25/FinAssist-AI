package com.FinAssist.FinAssist.ask.service;

import com.FinAssist.FinAssist.ask.dto.AskResult;
import com.FinAssist.FinAssist.transaction.service.TransactionService;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Locale;

@Service
public class AskServiceImpl implements AskService {

    static final String SYSTEM_INSTRUCTION = """
            You are FinAssist AI, a helpful financial information assistant. Explain financial concepts clearly and concisely. Do not invent personal financial information, transaction records, account balances, or spending statistics. If the answer requires transaction data that has not been provided by the application, clearly state that the data is not available. Do not claim to access a user's bank account.
            When asked about the user's transaction records, you must use one of the supplied transaction tools. Use only the returned records or backend-calculated statistics; never calculate totals yourself, change exact values, or describe transaction totals as an account balance. Do not claim that a tool ran unless it returned data. Ask for clarification when the requested filters are unclear. Only one transaction operation is allowed for each question. Transaction data is demo data, not a live bank feed.
            """;

    private static final String MODEL_FAILURE_MESSAGE =
            "The AI service could not generate a response. Please try again later.";
    private static final String TRANSACTION_DATA_UNAVAILABLE =
            "I could not map that transaction question to a supported query. Please specify a transaction status, type, category, amount threshold, or date range.";

    private final ChatClient chatClient;
    private final TransactionService transactionService;
    private final Clock reportingClock;

    public AskServiceImpl(
            ChatClient.Builder chatClientBuilder,
            TransactionService transactionService,
            Clock reportingClock
    ) {
        this.chatClient = chatClientBuilder
                .defaultSystem(SYSTEM_INSTRUCTION)
                .build();
        this.transactionService = transactionService;
        this.reportingClock = reportingClock;
    }

    @Override
    public AskResult ask(String question) {
        TransactionDataTools tools = new TransactionDataTools(transactionService, reportingClock);
        try {
            String answer = chatClient.prompt()
                    .tools(tools)
                    .system("Current date: " + LocalDate.now(reportingClock)
                            + ". Reporting time zone: " + reportingClock.getZone().getId() + ".")
                    .user(question)
                    .call()
                    .content();
            if (tools.getFailure() != null) {
                throw tools.getFailure();
            }
            if (answer == null || answer.isBlank()) {
                throw new AskServiceException(MODEL_FAILURE_MESSAGE);
            }
            if (requiresTransactionData(question) && !tools.wasOperationExecuted()) {
                return new AskResult(TRANSACTION_DATA_UNAVAILABLE, null);
            }
            return new AskResult(answer, tools.getData());
        } catch (TransactionDataToolException exception) {
            throw exception;
        } catch (AskServiceException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            if (tools.getFailure() != null) {
                throw tools.getFailure();
            }
            throw new AskServiceException(MODEL_FAILURE_MESSAGE, exception);
        }
    }

    private boolean requiresTransactionData(String question) {
        String normalized = question.toLowerCase(Locale.ROOT);
        return normalized.matches("(?s).*\\btransactions?\\b.*")
                || normalized.matches("(?s).*\\b(how much did i spend|how much have i spent|my spending|my expenses|my income|my balance)\\b.*")
                || normalized.matches("(?s).*\\b(this month|last month)\\b.*\\b(spend|spent|spending|expense|expenses|income|transaction|transactions)\\b.*");
    }
}
