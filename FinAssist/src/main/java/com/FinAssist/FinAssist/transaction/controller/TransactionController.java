package com.FinAssist.FinAssist.transaction.controller;

import com.FinAssist.FinAssist.transaction.dto.CreateTransactionRequest;
import com.FinAssist.FinAssist.transaction.dto.TransactionPageResponse;
import com.FinAssist.FinAssist.transaction.dto.TransactionResponse;
import com.FinAssist.FinAssist.transaction.dto.TransactionSummaryResponse;
import com.FinAssist.FinAssist.transaction.dto.UpdateTransactionRequest;
import com.FinAssist.FinAssist.transaction.entity.TransactionStatus;
import com.FinAssist.FinAssist.transaction.entity.TransactionType;
import com.FinAssist.FinAssist.transaction.service.TransactionService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.math.BigDecimal;
import java.net.URI;
import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/transactions")
public class TransactionController {

    private final TransactionService transactionService;

    public TransactionController(TransactionService transactionService) {
        this.transactionService = transactionService;
    }

    @PostMapping
    public ResponseEntity<TransactionResponse> create(@Valid @RequestBody CreateTransactionRequest request) {
        TransactionResponse created = transactionService.create(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(created.id())
                .toUri();
        return ResponseEntity.created(location).body(created);
    }

    @GetMapping
    public TransactionPageResponse findAll(
            @RequestParam(required = false) BigDecimal minAmount,
            @RequestParam(required = false) BigDecimal maxAmount,
            @RequestParam(required = false) TransactionStatus status,
            @RequestParam(required = false) TransactionType transactionType,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) LocalDateTime startDate,
            @RequestParam(required = false) LocalDateTime endDate,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        Page<TransactionResponse> transactions = transactionService.findAll(
                minAmount, maxAmount, status, transactionType, category, startDate, endDate, page, size);
        return TransactionPageResponse.from(transactions);
    }

    @GetMapping("/summary")
    public TransactionSummaryResponse summarize(
            @RequestParam(required = false) LocalDateTime startDate,
            @RequestParam(required = false) LocalDateTime endDate
    ) {
        return transactionService.summarize(startDate, endDate);
    }

    @GetMapping("/{id}")
    public TransactionResponse findById(@PathVariable Long id) {
        return transactionService.findById(id);
    }

    @PutMapping("/{id}")
    public TransactionResponse update(
            @PathVariable Long id,
            @Valid @RequestBody UpdateTransactionRequest request
    ) {
        return transactionService.update(id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        transactionService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
