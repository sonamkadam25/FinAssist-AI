package com.FinAssist.FinAssist.ask.service;

import com.FinAssist.FinAssist.ask.dto.AskResult;
import com.FinAssist.FinAssist.ask.dto.AskTransactionData;
import com.FinAssist.FinAssist.transaction.service.TransactionService;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class AskServiceImpl implements AskService {

    static final String SYSTEM_INSTRUCTION = """
            You are FinAssist AI, a helpful financial information assistant. Explain financial concepts clearly and concisely. Do not invent personal financial information, transaction records, account balances, or spending statistics. If the answer requires transaction data that has not been provided by the application, clearly state that the data is not available. Do not claim to access a user's bank account.
            When validated transaction data is included in the context, use only those returned records or backend-calculated statistics; never calculate totals yourself, change exact values, or describe transaction totals as an account balance. Do not claim that a query ran unless backend data is included. Ask for clarification when the requested filters are unclear. Transaction data is demo data, not a live bank feed.
            """;

    private static final String MODEL_FAILURE_MESSAGE =
            "The AI service could not generate a response. Please try again later.";
    private static final String TRANSACTION_DATA_UNAVAILABLE =
            "I could not map that transaction question to a supported query. Please specify a transaction status, type, category, amount threshold, or date range.";
    private static final String NO_MATCHING_TRANSACTIONS =
            "No transactions matched the requested filters.";
    private static final String NO_TRANSACTIONS_IN_RANGE =
            "No transactions were recorded in the requested date range.";
    private static final Pattern DATE_TIME = Pattern.compile(
            "\\b\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}(?::\\d{2}(?:\\.\\d{1,9})?)?\\b"
    );
    private static final Pattern AMOUNT = Pattern.compile(
            "(?i)\\b(at least|minimum|above|over|more than|greater than|up to|at most|below|under|less than)\\s+\\$?([\\d,]+(?:\\.\\d{1,2})?)"
    );
    private static final Pattern STATUS = Pattern.compile("\\b(PENDING|COMPLETED|FAILED)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern TRANSACTION_TYPE = Pattern.compile("\\b(INCOME|EXPENSES?)\\b", Pattern.CASE_INSENSITIVE);

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
            if (requiresTransactionData(question)) {
                routeTransactionQuestion(question, tools);
                if (!tools.wasOperationExecuted()) {
                    return new AskResult(TRANSACTION_DATA_UNAVAILABLE, null);
                }
            }

            String systemContext = "Current date: " + LocalDate.now(reportingClock)
                    + ". Reporting time zone: " + reportingClock.getZone().getId() + ".";
            if (tools.getData() != null) {
                systemContext += "\nValidated transaction data from the backend: " + tools.getData()
                        + "\nUse these exact values in your answer. If this data does not contain a requested fact, say it is unavailable.";
            }

            String answer = chatClient.prompt()
                    .system(systemContext)
                    .user(question)
                    .call()
                    .content();
            if (tools.getFailure() != null) {
                throw tools.getFailure();
            }
            if (answer == null || answer.isBlank()) {
                throw new AskServiceException(MODEL_FAILURE_MESSAGE);
            }
            String emptyResultAnswer = emptyResultAnswer(tools.getData());
            if (emptyResultAnswer != null) {
                return new AskResult(emptyResultAnswer, tools.getData());
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

    private void routeTransactionQuestion(String question, TransactionDataTools tools) {
        String normalized = question.toLowerCase(Locale.ROOT);

        if (normalized.contains("this month")
                && containsAny(normalized, "spend", "spent", "spending", "expense", "expenses", "income", "transaction", "transactions")) {
            tools.getCurrentMonthTransactionSummary();
            return;
        }

        List<String> dateTimes = extractDateTimes(question);
        if (dateTimes.size() >= 2 && containsAny(normalized, "between", "from", "date", "range", "summarize", "summary")) {
            tools.getTransactionSummary(dateTimes.get(0), dateTimes.get(1));
            return;
        }

        if (containsAny(normalized, "how many", "count", "number of", "total number", "summarize", "summary by status")
                && containsAny(normalized, "transaction", "transactions", "record", "records", "status")) {
            tools.getTransactionSummary(null, null);
            return;
        }

        Matcher amountMatcher = AMOUNT.matcher(question);
        Matcher statusMatcher = STATUS.matcher(question);
        Matcher typeMatcher = TRANSACTION_TYPE.matcher(question);
        if (amountMatcher.find() || statusMatcher.find() || typeMatcher.find()) {
            String status = statusMatcher.reset().find()
                    ? statusMatcher.group(1).toUpperCase(Locale.ROOT)
                    : null;
            String transactionType = null;
            if (typeMatcher.reset().find()) {
                String type = typeMatcher.group(1).toUpperCase(Locale.ROOT);
                transactionType = type.startsWith("EXPENSE") ? "EXPENSE" : type;
            }

            BigDecimal minAmount = null;
            BigDecimal maxAmount = null;
            if (amountMatcher.reset().find()) {
                BigDecimal threshold = new BigDecimal(amountMatcher.group(2).replace(",", ""));
                String comparison = amountMatcher.group(1).toLowerCase(Locale.ROOT);
                if (containsAny(comparison, "above", "over", "more than", "greater than")) {
                    minAmount = threshold.add(new BigDecimal("0.01"));
                } else if (containsAny(comparison, "below", "under", "less than")) {
                    maxAmount = threshold.subtract(new BigDecimal("0.01"));
                    if (maxAmount.signum() <= 0) {
                        return;
                    }
                } else if (containsAny(comparison, "at least", "minimum")) {
                    minAmount = threshold;
                } else {
                    maxAmount = threshold;
                }
            }

            tools.searchTransactions(status, transactionType, null, minAmount, maxAmount,
                    dateTimes.isEmpty() ? null : dateTimes.get(0),
                    dateTimes.size() < 2 ? null : dateTimes.get(1));
            return;
        }

        if (containsAny(normalized, "show", "list", "find", "display")
                && containsAny(normalized, "transaction", "transactions")) {
            tools.searchTransactions(null, null, null, null, null, null, null);
        }
    }

    private List<String> extractDateTimes(String question) {
        Matcher matcher = DATE_TIME.matcher(question);
        List<String> values = new ArrayList<>(2);
        while (matcher.find() && values.size() < 2) {
            values.add(matcher.group());
        }
        return values;
    }

    private boolean containsAny(String text, String... values) {
        for (String value : values) {
            if (text.contains(value)) {
                return true;
            }
        }
        return false;
    }

    private boolean requiresTransactionData(String question) {
        String normalized = question.toLowerCase(Locale.ROOT);
        return normalized.matches("(?s).*\\btransactions?\\b.*")
                || normalized.matches("(?s).*\\b(how much did i spend|how much have i spent|my spending|my expenses|my income)\\b.*")
                || normalized.matches("(?s).*\\bmy\\b.*\\b(balance|account|credit score)\\b.*")
                || normalized.matches("(?s).*\\b(this month|last month)\\b.*\\b(spend|spent|spending|expense|expenses|income|transaction|transactions)\\b.*");
    }

    private String emptyResultAnswer(AskTransactionData data) {
        if (data == null) {
            return null;
        }
        if (data.search() != null && data.search().totalMatches() == 0) {
            return NO_MATCHING_TRANSACTIONS;
        }
        if (data.summary() != null && data.summary().statistics().totalTransactions() == 0) {
            return NO_TRANSACTIONS_IN_RANGE;
        }
        return null;
    }
}
