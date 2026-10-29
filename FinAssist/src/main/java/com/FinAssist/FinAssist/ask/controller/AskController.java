package com.FinAssist.FinAssist.ask.controller;

import com.FinAssist.FinAssist.ask.dto.AskRequest;
import com.FinAssist.FinAssist.ask.dto.AskResponse;
import com.FinAssist.FinAssist.ask.service.AskService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/ask")
public class AskController {

    private final AskService askService;

    public AskController(AskService askService) {
        this.askService = askService;
    }

    @PostMapping
    public AskResponse ask(@Valid @RequestBody AskRequest request) {
        return new AskResponse(request.question(), askService.ask(request.question()));
    }
}
