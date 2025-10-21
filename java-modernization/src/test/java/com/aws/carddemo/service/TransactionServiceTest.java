package com.aws.carddemo.service;

import com.aws.carddemo.dto.TransactionDTO;
import com.aws.carddemo.exception.ResourceNotFoundException;
import com.aws.carddemo.model.Transaction;
import com.aws.carddemo.repository.TransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TransactionServiceTest {

    @Mock
    private TransactionRepository transactionRepository;

    @InjectMocks
    private TransactionService transactionService;

    private Transaction testTransaction;
    private TransactionDTO testTransactionDTO;

    @BeforeEach
    void setUp() {
        testTransaction = new Transaction();
        testTransaction.setTransactionId("TXN1234567890123");
        testTransaction.setTransactionTypeCode("01");
        testTransaction.setTransactionCategoryCode(1001);
        testTransaction.setTransactionSource("POS");
        testTransaction.setDescription("Purchase at Store");
        testTransaction.setAmount(new BigDecimal("150.00"));
        testTransaction.setMerchantId(999L);
        testTransaction.setMerchantName("Test Merchant");
        testTransaction.setMerchantCity("New York");
        testTransaction.setMerchantZip("10001");
        testTransaction.setCardNumber("4111111111111111");
        testTransaction.setOriginTimestamp(LocalDateTime.now().minusHours(1));
        testTransaction.setProcessTimestamp(LocalDateTime.now());

        testTransactionDTO = new TransactionDTO();
        testTransactionDTO.setTransactionId("TXN1234567890123");
        testTransactionDTO.setTransactionTypeCode("01");
        testTransactionDTO.setTransactionCategoryCode(1001);
        testTransactionDTO.setTransactionSource("POS");
        testTransactionDTO.setDescription("Purchase at Store");
        testTransactionDTO.setAmount(new BigDecimal("150.00"));
        testTransactionDTO.setMerchantId(999L);
        testTransactionDTO.setMerchantName("Test Merchant");
        testTransactionDTO.setMerchantCity("New York");
        testTransactionDTO.setMerchantZip("10001");
        testTransactionDTO.setCardNumber("4111111111111111");
        testTransactionDTO.setOriginTimestamp(LocalDateTime.now().minusHours(1));
        testTransactionDTO.setProcessTimestamp(LocalDateTime.now());
    }

    @Test
    void getTransactionById_Success() {
        when(transactionRepository.findByTransactionId(anyString())).thenReturn(Optional.of(testTransaction));

        TransactionDTO result = transactionService.getTransactionById("TXN1234567890123");

        assertNotNull(result);
        assertEquals(testTransaction.getTransactionId(), result.getTransactionId());
        assertEquals(testTransaction.getAmount(), result.getAmount());
        verify(transactionRepository, times(1)).findByTransactionId("TXN1234567890123");
    }

    @Test
    void getTransactionById_NotFound() {
        when(transactionRepository.findByTransactionId(anyString())).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> {
            transactionService.getTransactionById("TXN1234567890123");
        });

        verify(transactionRepository, times(1)).findByTransactionId("TXN1234567890123");
    }

    @Test
    void getTransactionsByCardNumber_Success() {
        List<Transaction> transactions = Arrays.asList(testTransaction);
        when(transactionRepository.findByCardNumber(anyString())).thenReturn(transactions);

        List<TransactionDTO> result = transactionService.getTransactionsByCardNumber("4111111111111111");

        assertNotNull(result);
        assertEquals(1, result.size());
        verify(transactionRepository, times(1)).findByCardNumber("4111111111111111");
    }

    @Test
    void createTransaction_Success() {
        when(transactionRepository.existsByTransactionId(anyString())).thenReturn(false);
        when(transactionRepository.save(any(Transaction.class))).thenReturn(testTransaction);

        TransactionDTO result = transactionService.createTransaction(testTransactionDTO);

        assertNotNull(result);
        assertEquals(testTransactionDTO.getTransactionId(), result.getTransactionId());
        verify(transactionRepository, times(1)).save(any(Transaction.class));
    }

    @Test
    void createTransaction_AlreadyExists() {
        when(transactionRepository.existsByTransactionId(anyString())).thenReturn(true);

        assertThrows(IllegalArgumentException.class, () -> {
            transactionService.createTransaction(testTransactionDTO);
        });

        verify(transactionRepository, times(1)).existsByTransactionId(testTransactionDTO.getTransactionId());
        verify(transactionRepository, never()).save(any(Transaction.class));
    }

    @Test
    void deleteTransaction_Success() {
        when(transactionRepository.existsByTransactionId(anyString())).thenReturn(true);
        doNothing().when(transactionRepository).deleteById(anyString());

        transactionService.deleteTransaction("TXN1234567890123");

        verify(transactionRepository, times(1)).existsByTransactionId("TXN1234567890123");
        verify(transactionRepository, times(1)).deleteById("TXN1234567890123");
    }
}
