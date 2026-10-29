package com.FinAssist.FinAssist.ask.service;

public class TransactionDataToolException extends RuntimeException {

    private final boolean invalidArguments;

    public TransactionDataToolException(String message, boolean invalidArguments) {
        super(message);
        this.invalidArguments = invalidArguments;
    }

    public TransactionDataToolException(String message, Throwable cause, boolean invalidArguments) {
        super(message, cause);
        this.invalidArguments = invalidArguments;
    }

    public boolean hasInvalidArguments() {
        return invalidArguments;
    }
}
