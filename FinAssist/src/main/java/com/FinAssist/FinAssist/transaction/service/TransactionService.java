package com.FinAssist.FinAssist.transaction.service;

import com.FinAssist.FinAssist.transaction.dto.CreateTransactionRequest;
import com.FinAssist.FinAssist.transaction.dto.TransactionResponse;
import com.FinAssist.FinAssist.transaction.dto.UpdateTransactionRequest;
import com.FinAssist.FinAssist.transaction.entity.Transaction;
import com.FinAssist.FinAssist.transaction.entity.TransactionStatus;
import com.FinAssist.FinAssist.transaction.entity.TransactionType;
import com.FinAssist.FinAssist.transaction.repository.TransactionRepository;
import com.FinAssist.FinAssist.transaction.repository.TransactionSpecifications;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Service
@Transactional
public class TransactionService {

    private static final int MAX_PAGE_SIZE = 100;

    private final TransactionRepository transactionRepository;

    public TransactionService(TransactionRepository transactionRepository) {
        this.transactionRepository = transactionRepository;
    }

    public TransactionResponse create(CreateTransactionRequest request) {
        Transaction transaction = new Transaction(
                request.transactionType(),
                request.amount(),
                request.category(),
                request.merchant(),
                request.description(),
                request.transactionDate(),
                request.status() == null ? TransactionStatus.PENDING : request.status()
        );
        return TransactionResponse.from(transactionRepository.save(transaction));
    }

    @Transactional(readOnly = true)
    public Page<TransactionResponse> findAll(
            BigDecimal minAmount,
            BigDecimal maxAmount,
            TransactionStatus status,
            TransactionType transactionType,
            String category,
            LocalDateTime startDate,
            LocalDateTime endDate,
            int page,
            int size
    ) {
        validateFilters(minAmount, maxAmount, startDate, endDate);
        if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
            throw new IllegalArgumentException("page must be non-negative and size must be between 1 and 100");
        }

        var specification = TransactionSpecifications.withFilters(
                minAmount, maxAmount, status, transactionType, category, startDate, endDate);
        var pageRequest = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "transactionDate"));
        return transactionRepository.findAll(specification, pageRequest).map(TransactionResponse::from);
    }

    @Transactional(readOnly = true)
    public TransactionResponse findById(Long id) {
        return TransactionResponse.from(findTransaction(id));
    }

    public TransactionResponse update(Long id, UpdateTransactionRequest request) {
        Transaction transaction = findTransaction(id);
        transaction.setTransactionType(request.transactionType());
        transaction.setAmount(request.amount());
        transaction.setCategory(request.category());
        transaction.setMerchant(request.merchant());
        transaction.setDescription(request.description());
        transaction.setTransactionDate(request.transactionDate());
        transaction.setStatus(request.status());
        return TransactionResponse.from(transactionRepository.save(transaction));
    }

    public void delete(Long id) {
        Transaction transaction = findTransaction(id);
        transactionRepository.delete(transaction);
    }

    private Transaction findTransaction(Long id) {
        return transactionRepository.findById(id).orElseThrow(() -> new TransactionNotFoundException(id));
    }

    private void validateFilters(
            BigDecimal minAmount,
            BigDecimal maxAmount,
            LocalDateTime startDate,
            LocalDateTime endDate
    ) {
        if (minAmount != null && minAmount.signum() <= 0) {
            throw new IllegalArgumentException("minAmount must be greater than zero");
        }
        if (minAmount != null && maxAmount != null && minAmount.compareTo(maxAmount) > 0) {
            throw new IllegalArgumentException("minAmount must be less than or equal to maxAmount");
        }
        if (startDate != null && endDate != null && startDate.isAfter(endDate)) {
            throw new IllegalArgumentException("startDate must be less than or equal to endDate");
        }
    }
}
