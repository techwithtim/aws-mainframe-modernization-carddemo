package com.aws.carddemo.service;

import com.aws.carddemo.dto.TransactionDTO;
import com.aws.carddemo.exception.ResourceNotFoundException;
import com.aws.carddemo.model.Transaction;
import com.aws.carddemo.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class TransactionService {

    private final TransactionRepository transactionRepository;

    public TransactionDTO getTransactionById(String transactionId) {
        log.info("Fetching transaction with ID: {}", transactionId);
        Transaction transaction = transactionRepository.findByTransactionId(transactionId)
                .orElseThrow(() -> new ResourceNotFoundException("Transaction not found with ID: " + transactionId));
        return mapToDTO(transaction);
    }

    public List<TransactionDTO> getTransactionsByCardNumber(String cardNumber) {
        log.info("Fetching transactions for card number: {}", cardNumber);
        return transactionRepository.findByCardNumber(cardNumber).stream()
                .map(this::mapToDTO)
                .collect(Collectors.toList());
    }

    public List<TransactionDTO> getTransactionsByCardNumberAndDateRange(
            String cardNumber, LocalDateTime startDate, LocalDateTime endDate) {
        log.info("Fetching transactions for card {} between {} and {}", cardNumber, startDate, endDate);
        return transactionRepository.findByCardNumberAndDateRange(cardNumber, startDate, endDate).stream()
                .map(this::mapToDTO)
                .collect(Collectors.toList());
    }

    public List<TransactionDTO> getAllTransactions() {
        log.info("Fetching all transactions");
        return transactionRepository.findAll().stream()
                .map(this::mapToDTO)
                .collect(Collectors.toList());
    }

    public TransactionDTO createTransaction(TransactionDTO transactionDTO) {
        log.info("Creating new transaction");
        
        if (transactionDTO.getTransactionId() == null || transactionDTO.getTransactionId().isEmpty()) {
            transactionDTO.setTransactionId(generateTransactionId());
        }
        
        if (transactionRepository.existsByTransactionId(transactionDTO.getTransactionId())) {
            throw new IllegalArgumentException("Transaction already exists with ID: " + transactionDTO.getTransactionId());
        }

        if (transactionDTO.getProcessTimestamp() == null) {
            transactionDTO.setProcessTimestamp(LocalDateTime.now());
        }

        Transaction transaction = mapToEntity(transactionDTO);
        Transaction savedTransaction = transactionRepository.save(transaction);
        log.info("Transaction created with ID: {}", savedTransaction.getTransactionId());
        return mapToDTO(savedTransaction);
    }

    public TransactionDTO updateTransaction(String transactionId, TransactionDTO transactionDTO) {
        log.info("Updating transaction with ID: {}", transactionId);
        Transaction existingTransaction = transactionRepository.findByTransactionId(transactionId)
                .orElseThrow(() -> new ResourceNotFoundException("Transaction not found with ID: " + transactionId));

        existingTransaction.setTransactionTypeCode(transactionDTO.getTransactionTypeCode());
        existingTransaction.setTransactionCategoryCode(transactionDTO.getTransactionCategoryCode());
        existingTransaction.setTransactionSource(transactionDTO.getTransactionSource());
        existingTransaction.setDescription(transactionDTO.getDescription());
        existingTransaction.setAmount(transactionDTO.getAmount());
        existingTransaction.setMerchantId(transactionDTO.getMerchantId());
        existingTransaction.setMerchantName(transactionDTO.getMerchantName());
        existingTransaction.setMerchantCity(transactionDTO.getMerchantCity());
        existingTransaction.setMerchantZip(transactionDTO.getMerchantZip());
        existingTransaction.setCardNumber(transactionDTO.getCardNumber());
        existingTransaction.setOriginTimestamp(transactionDTO.getOriginTimestamp());
        existingTransaction.setProcessTimestamp(transactionDTO.getProcessTimestamp());

        Transaction updatedTransaction = transactionRepository.save(existingTransaction);
        return mapToDTO(updatedTransaction);
    }

    public void deleteTransaction(String transactionId) {
        log.info("Deleting transaction with ID: {}", transactionId);
        if (!transactionRepository.existsByTransactionId(transactionId)) {
            throw new ResourceNotFoundException("Transaction not found with ID: " + transactionId);
        }
        transactionRepository.deleteById(transactionId);
    }

    private String generateTransactionId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }

    private TransactionDTO mapToDTO(Transaction transaction) {
        TransactionDTO dto = new TransactionDTO();
        dto.setTransactionId(transaction.getTransactionId());
        dto.setTransactionTypeCode(transaction.getTransactionTypeCode());
        dto.setTransactionCategoryCode(transaction.getTransactionCategoryCode());
        dto.setTransactionSource(transaction.getTransactionSource());
        dto.setDescription(transaction.getDescription());
        dto.setAmount(transaction.getAmount());
        dto.setMerchantId(transaction.getMerchantId());
        dto.setMerchantName(transaction.getMerchantName());
        dto.setMerchantCity(transaction.getMerchantCity());
        dto.setMerchantZip(transaction.getMerchantZip());
        dto.setCardNumber(transaction.getCardNumber());
        dto.setOriginTimestamp(transaction.getOriginTimestamp());
        dto.setProcessTimestamp(transaction.getProcessTimestamp());
        return dto;
    }

    private Transaction mapToEntity(TransactionDTO dto) {
        Transaction transaction = new Transaction();
        transaction.setTransactionId(dto.getTransactionId());
        transaction.setTransactionTypeCode(dto.getTransactionTypeCode());
        transaction.setTransactionCategoryCode(dto.getTransactionCategoryCode());
        transaction.setTransactionSource(dto.getTransactionSource());
        transaction.setDescription(dto.getDescription());
        transaction.setAmount(dto.getAmount());
        transaction.setMerchantId(dto.getMerchantId());
        transaction.setMerchantName(dto.getMerchantName());
        transaction.setMerchantCity(dto.getMerchantCity());
        transaction.setMerchantZip(dto.getMerchantZip());
        transaction.setCardNumber(dto.getCardNumber());
        transaction.setOriginTimestamp(dto.getOriginTimestamp());
        transaction.setProcessTimestamp(dto.getProcessTimestamp());
        return transaction;
    }
}
