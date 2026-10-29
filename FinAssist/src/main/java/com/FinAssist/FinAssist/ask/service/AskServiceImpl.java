package com.FinAssist.FinAssist.ask.service;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

@Service
public class AskServiceImpl implements AskService {

    static final String SYSTEM_INSTRUCTION = """
            You are FinAssist AI, a helpful financial information assistant. Explain financial concepts clearly and concisely. Do not invent personal financial information, transaction records, account balances, or spending statistics. If the answer requires transaction data that has not been provided by the application, clearly state that the data is not available. Do not claim to access a user's bank account.
            """;

    private static final String MODEL_FAILURE_MESSAGE =
            "The AI service could not generate a response. Please try again later.";

    private final ChatClient chatClient;

    public AskServiceImpl(ChatClient.Builder chatClientBuilder) {
        this.chatClient = chatClientBuilder
                .defaultSystem(SYSTEM_INSTRUCTION)
                .build();
    }

    @Override
    public String ask(String question) {
        try {
            String answer = chatClient.prompt()
                    .user(question)
                    .call()
                    .content();
            if (answer == null || answer.isBlank()) {
                throw new AskServiceException(MODEL_FAILURE_MESSAGE);
            }
            return answer;
        } catch (AskServiceException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new AskServiceException(MODEL_FAILURE_MESSAGE, exception);
        }
    }
}
