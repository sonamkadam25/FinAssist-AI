package com.FinAssist.FinAssist.ask.controller;

import com.FinAssist.FinAssist.ask.service.AskService;
import com.FinAssist.FinAssist.ask.service.AskServiceException;
import com.FinAssist.FinAssist.ask.dto.AskResult;
import com.FinAssist.FinAssist.ask.dto.AskTransactionData;
import com.FinAssist.FinAssist.ask.dto.TransactionSearchData;
import com.FinAssist.FinAssist.transaction.controller.GlobalExceptionHandler;
import com.FinAssist.FinAssist.ask.service.TransactionDataToolException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AskController.class)
@Import(GlobalExceptionHandler.class)
class AskControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AskService askService;

    @Test
    void returnsAnswerForValidQuestion() throws Exception {
        when(askService.ask("Explain the difference between saving and investing."))
                .thenReturn(new AskResult(
                        "Saving prioritizes access; investing seeks long-term growth.",
                        null
                ));

        mockMvc.perform(post("/api/ask")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "question": "Explain the difference between saving and investing."
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.question")
                        .value("Explain the difference between saving and investing."))
                .andExpect(jsonPath("$.answer")
                        .value("Saving prioritizes access; investing seeks long-term growth."))
                .andExpect(jsonPath("$.data").doesNotExist());

        verify(askService).ask("Explain the difference between saving and investing.");
    }

    @Test
    void returnsStructuredTransactionSearchData() throws Exception {
        when(askService.ask("Show completed transactions")).thenReturn(new AskResult(
                "I found no matching transactions.",
                new AskTransactionData(null, new TransactionSearchData(java.util.List.of(), 0, 0, false))
        ));

        mockMvc.perform(post("/api/ask")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"question": "Show completed transactions"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.search.totalMatches").value(0))
                .andExpect(jsonPath("$.data.search.returnedCount").value(0))
                .andExpect(jsonPath("$.data.search.truncated").value(false))
                .andExpect(jsonPath("$.data.search.transactions").isEmpty());
    }

    @Test
    void rejectsEmptyQuestionWithoutCallingService() throws Exception {
        mockMvc.perform(post("/api/ask")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"question": ""}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.question").value("question must not be blank"));

        verify(askService, never()).ask(org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void rejectsWhitespaceQuestionWithoutCallingService() throws Exception {
        mockMvc.perform(post("/api/ask")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"question": "   "}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.question").value("question must not be blank"));

        verify(askService, never()).ask(org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void rejectsMissingQuestionWithoutCallingService() throws Exception {
        mockMvc.perform(post("/api/ask")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.question").value("question must not be blank"));

        verify(askService, never()).ask(org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void returnsServiceUnavailableWhenAnswerGenerationFails() throws Exception {
        when(askService.ask("What is compound interest?"))
                .thenThrow(new AskServiceException("internal exception detail"));

        mockMvc.perform(post("/api/ask")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"question": "What is compound interest?"}
                                """))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.message")
                        .value("The AI service is currently unavailable. Please try again later."))
                .andExpect(jsonPath("$.details").isEmpty());
    }

    @Test
    void sanitizesTransactionToolExecutionFailure() throws Exception {
        when(askService.ask("Show transactions"))
                .thenThrow(new TransactionDataToolException("private database host", false));

        mockMvc.perform(post("/api/ask")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"question": "Show transactions"}
                                """))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.message").value("Transaction data could not be retrieved"))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("private database host"))));
    }
}
