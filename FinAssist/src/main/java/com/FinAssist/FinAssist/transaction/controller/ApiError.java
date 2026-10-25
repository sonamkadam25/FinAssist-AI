package com.FinAssist.FinAssist.transaction.controller;

import java.util.Map;

public record ApiError(int status, String error, String message, Map<String, String> details) {
}
