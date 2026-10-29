package com.FinAssist.FinAssist.ask.service;

import com.FinAssist.FinAssist.ask.dto.AskTransactionData;
import com.FinAssist.FinAssist.ask.dto.TransactionDataItem;
import com.FinAssist.FinAssist.ask.dto.TransactionSearchData;
import com.FinAssist.FinAssist.ask.dto.TransactionSummaryData;
import com.FinAssist.FinAssist.transaction.dto.TransactionResponse;
import com.FinAssist.FinAssist.transaction.dto.TransactionSummaryResponse;
import com.FinAssist.FinAssist.transaction.entity.TransactionStatus;
import com.FinAssist.FinAssist.transaction.entity.TransactionType;
import com.FinAssist.FinAssist.transaction.service.TransactionService;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.data.domain.Page;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Locale;

public class TransactionDataTools {

    static final int MAX_RESULTS = 20;

    private final TransactionService transactionService;
    private final Clock reportingClock;
    private boolean operationExecuted;
    private AskTransactionData data;
    private TransactionDataToolException failure;

    public TransactionDataTools(TransactionService transactionService, Clock reportingClock) {
        this.transactionService = transactionService;
        this.reportingClock = reportingClock;
    }

    @Tool(name = "searchTransactions", description = """
            Search transaction records using supported filters. Use this for requests to show or find individual transactions.
            Each call returns at most 20 records and only exposes id, type, amount, category, transaction date, and status.
            Supported filters: status (PENDING, COMPLETED, FAILED), transactionType (INCOME, EXPENSE), exact category,
            minAmount, maxAmount, startDate, and endDate. Dates must be ISO local date-time values.
            Do not call with guessed filters. If the requested details are unclear, ask the user to clarify.
            """)
    public TransactionSearchData searchTransactions(
            @ToolParam(required = false, description = "Transaction status: PENDING, COMPLETED, or FAILED")
            String status,
            @ToolParam(required = false, description = "Transaction type: INCOME or EXPENSE")
            String transactionType,
            @ToolParam(required = false, description = "Exact category name")
            String category,
            @ToolParam(required = false, description = "Inclusive minimum positive amount")
            BigDecimal minAmount,
            @ToolParam(required = false, description = "Inclusive maximum positive amount")
            BigDecimal maxAmount,
            @ToolParam(required = false, description = "Inclusive ISO local date-time lower bound")
            String startDate,
            @ToolParam(required = false, description = "Inclusive ISO local date-time upper bound")
            String endDate
    ) {
        beginOperation();
        try {
            validateAmount(minAmount, "minAmount");
            validateAmount(maxAmount, "maxAmount");
            if (minAmount != null && maxAmount != null && minAmount.compareTo(maxAmount) > 0) {
                throw invalid("minAmount must be less than or equal to maxAmount");
            }
            if (category != null && category.length() > 50) {
                throw invalid("category must be 50 characters or fewer");
            }
            TransactionStatus parsedStatus = parseEnum(status, TransactionStatus.class, "status");
            TransactionType parsedType = parseEnum(transactionType, TransactionType.class, "transactionType");
            LocalDateTime parsedStart = parseDate(startDate, "startDate");
            LocalDateTime parsedEnd = parseDate(endDate, "endDate");
            if (parsedStart != null && parsedEnd != null && parsedStart.isAfter(parsedEnd)) {
                throw invalid("startDate must be less than or equal to endDate");
            }

            Page<TransactionResponse> page = transactionService.findAll(
                    minAmount,
                    maxAmount,
                    parsedStatus,
                    parsedType,
                    category,
                    parsedStart,
                    parsedEnd,
                    0,
                    MAX_RESULTS
            );
            List<TransactionDataItem> transactions = page.getContent().stream()
                    .map(this::toTransactionDataItem)
                    .toList();
            TransactionSearchData searchData = new TransactionSearchData(
                    transactions,
                    page.getTotalElements(),
                    transactions.size(),
                    page.getTotalElements() > transactions.size()
            );
            data = new AskTransactionData(null, searchData);
            return searchData;
        } catch (TransactionDataToolException exception) {
            failure = exception;
            throw exception;
        } catch (IllegalArgumentException exception) {
            TransactionDataToolException toolException =
                    new TransactionDataToolException(exception.getMessage(), exception, true);
            failure = toolException;
            throw toolException;
        } catch (RuntimeException exception) {
            TransactionDataToolException toolException = new TransactionDataToolException(
                    "Transaction search could not be completed", exception, false);
            failure = toolException;
            throw toolException;
        }
    }

    @Tool(name = "getTransactionSummary", description = """
            Retrieve backend-calculated transaction totals and status counts. Use this for totals, counts, or category
            summaries. Only completed income and expenses contribute to monetary totals. Pending and failed transactions
            are counted but excluded from financial totals. The summary is not an account balance.
            Provide date filters only when specified or clearly inferable from the question; date bounds are inclusive.
            """)
    public TransactionSummaryData getTransactionSummary(
            @ToolParam(required = false, description = "Inclusive ISO local date-time lower bound")
            String startDate,
            @ToolParam(required = false, description = "Inclusive ISO local date-time upper bound")
            String endDate
    ) {
        beginOperation();
        try {
            LocalDateTime parsedStart = parseDate(startDate, "startDate");
            LocalDateTime parsedEnd = parseDate(endDate, "endDate");
            validateDateRange(parsedStart, parsedEnd);
            TransactionSummaryResponse summary = transactionService.summarize(parsedStart, parsedEnd);
            TransactionSummaryData summaryData = new TransactionSummaryData(parsedStart, parsedEnd, summary);
            data = new AskTransactionData(summaryData, null);
            return summaryData;
        } catch (TransactionDataToolException exception) {
            failure = exception;
            throw exception;
        } catch (IllegalArgumentException exception) {
            TransactionDataToolException toolException =
                    new TransactionDataToolException(exception.getMessage(), exception, true);
            failure = toolException;
            throw toolException;
        } catch (RuntimeException exception) {
            TransactionDataToolException toolException = new TransactionDataToolException(
                    "Transaction summary could not be completed", exception, false);
            failure = toolException;
            throw toolException;
        }
    }

    @Tool(name = "getCurrentMonthTransactionSummary", description = """
            Retrieve this month's transaction summary using the configured reporting time zone. Use this for questions
            such as how much the user spent this month. The backend determines the date range and calculates exact totals.
            Only completed income and expenses contribute to monetary totals; pending and failed transactions are excluded.
            """)
    public TransactionSummaryData getCurrentMonthTransactionSummary() {
        beginOperation();
        try {
            YearMonth month = YearMonth.now(reportingClock);
            LocalDateTime startDate = month.atDay(1).atStartOfDay();
            LocalDateTime endDate = month.atEndOfMonth().atTime(23, 59, 59, 999_999_000);
            TransactionSummaryResponse summary = transactionService.summarize(startDate, endDate);
            TransactionSummaryData summaryData = new TransactionSummaryData(startDate, endDate, summary);
            data = new AskTransactionData(summaryData, null);
            return summaryData;
        } catch (RuntimeException exception) {
            TransactionDataToolException toolException = new TransactionDataToolException(
                    "Transaction summary could not be completed", exception, false);
            failure = toolException;
            throw toolException;
        }
    }

    public boolean wasOperationExecuted() {
        return operationExecuted;
    }

    public AskTransactionData getData() {
        return data;
    }

    public TransactionDataToolException getFailure() {
        return failure;
    }

    private void beginOperation() {
        if (operationExecuted) {
            TransactionDataToolException exception = invalid(
                    "Only one transaction query is allowed for each AI question");
            failure = exception;
            throw exception;
        }
        operationExecuted = true;
    }

    private void validateAmount(BigDecimal amount, String field) {
        if (amount == null) {
            return;
        }
        if (amount.signum() <= 0) {
            throw invalid(field + " must be greater than zero");
        }
        if (amount.precision() > 19 || amount.scale() > 2) {
            throw invalid(field + " must fit DECIMAL(19,2)");
        }
    }

    private void validateDateRange(LocalDateTime startDate, LocalDateTime endDate) {
        if (startDate != null && endDate != null && startDate.isAfter(endDate)) {
            throw invalid("startDate must be less than or equal to endDate");
        }
    }

    private LocalDateTime parseDate(String value, String field) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return LocalDateTime.parse(value);
        } catch (DateTimeParseException exception) {
            throw invalid(field + " must use ISO local date-time format, for example 2026-10-30T00:00:00");
        }
    }

    private <E extends Enum<E>> E parseEnum(String value, Class<E> enumType, String field) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Enum.valueOf(enumType, value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw invalid(field + " must be one of " + String.join(", ",
                    java.util.Arrays.stream(enumType.getEnumConstants()).map(Enum::name).toList()));
        }
    }

    private TransactionDataItem toTransactionDataItem(TransactionResponse transaction) {
        return new TransactionDataItem(
                transaction.id(),
                transaction.transactionType(),
                transaction.amount(),
                transaction.category(),
                transaction.transactionDate(),
                transaction.status()
        );
    }

    private TransactionDataToolException invalid(String message) {
        return new TransactionDataToolException(message, true);
    }
}
