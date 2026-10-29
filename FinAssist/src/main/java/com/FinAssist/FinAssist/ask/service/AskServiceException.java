package com.FinAssist.FinAssist.ask.service;

public class AskServiceException extends RuntimeException {

    public AskServiceException(String message) {
        super(message);
    }

    public AskServiceException(String message, Throwable cause) {
        super(message, cause);
    }
}
