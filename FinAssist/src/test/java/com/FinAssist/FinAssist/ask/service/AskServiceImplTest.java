package com.FinAssist.FinAssist.ask.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
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

    private AskServiceImpl askService;

    @BeforeEach
    void setUp() {
        when(chatClientBuilder.defaultSystem(anyString())).thenReturn(chatClientBuilder);
        when(chatClientBuilder.build()).thenReturn(chatClient);
        askService = new AskServiceImpl(chatClientBuilder);
    }

    @Test
    void sendsActualQuestionAndConfiguredSystemInstruction() {
        String question = "What is compound interest?";
        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.user(question)).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(responseSpec);
        when(responseSpec.content()).thenReturn("Interest earned on prior interest.");

        String answer = askService.ask(question);

        assertEquals("Interest earned on prior interest.", answer);
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
        when(requestSpec.user("What is a budget?")).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(responseSpec);
        when(responseSpec.content()).thenReturn("  ");

        assertThrows(AskServiceException.class, () -> askService.ask("What is a budget?"));
    }
}
