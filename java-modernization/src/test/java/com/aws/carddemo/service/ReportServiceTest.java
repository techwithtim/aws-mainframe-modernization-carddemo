package com.aws.carddemo.service;

import com.aws.carddemo.model.Account;
import com.aws.carddemo.model.Card;
import com.aws.carddemo.model.Transaction;
import com.aws.carddemo.repository.AccountRepository;
import com.aws.carddemo.repository.CardRepository;
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
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReportServiceTest {

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private CardRepository cardRepository;

    @Mock
    private TransactionRepository transactionRepository;

    @InjectMocks
    private ReportService reportService;

    private Account testAccount;
    private Card testCard;
    private Transaction testTransaction;

    @BeforeEach
    void setUp() {
        testAccount = new Account();
        testAccount.setAccountId(1L);
        testAccount.setAccountNumber("12345678901");
        testAccount.setCurrentBalance(new BigDecimal("1000.00"));
        testAccount.setCreditLimit(new BigDecimal("5000.00"));
        testAccount.setActiveStatus("Y");

        testCard = new Card();
        testCard.setCardNumber("1234567890123456");
        testCard.setActiveStatus("Y");

        testTransaction = new Transaction();
        testTransaction.setTransactionId("TXN001");
        testTransaction.setCardNumber("1234567890123456");
        testTransaction.setAmount(new BigDecimal("100.00"));
        testTransaction.setTransactionTypeCode("01");
        testTransaction.setTransactionCategoryCode(1001);
        testTransaction.setProcessTimestamp(LocalDateTime.now());
    }

    @Test
    void testGenerateAccountSummaryReport() {
        List<Account> accounts = Arrays.asList(testAccount);
        when(accountRepository.findAll()).thenReturn(accounts);

        Map<String, Object> report = reportService.generateAccountSummaryReport();

        assertNotNull(report);
        assertEquals("Account Summary Report", report.get("reportName"));
        assertEquals(1, report.get("totalAccounts"));
        assertEquals(1, report.get("activeAccounts"));
        verify(accountRepository, times(1)).findAll();
    }

    @Test
    void testGenerateTransactionReport() {
        List<Transaction> transactions = Arrays.asList(testTransaction);
        when(transactionRepository.findAll()).thenReturn(transactions);

        LocalDateTime startDate = LocalDateTime.now().minusDays(7);
        LocalDateTime endDate = LocalDateTime.now();

        Map<String, Object> report = reportService.generateTransactionReport(startDate, endDate);

        assertNotNull(report);
        assertEquals("Transaction Report", report.get("reportName"));
        verify(transactionRepository, times(1)).findAll();
    }

    @Test
    void testGenerateCardUtilizationReport() {
        List<Card> cards = Arrays.asList(testCard);
        List<Account> accounts = Arrays.asList(testAccount);
        when(cardRepository.findAll()).thenReturn(cards);
        when(accountRepository.findAll()).thenReturn(accounts);

        Map<String, Object> report = reportService.generateCardUtilizationReport();

        assertNotNull(report);
        assertEquals("Card Utilization Report", report.get("reportName"));
        assertEquals(1, report.get("totalCards"));
        verify(cardRepository, times(1)).findAll();
        verify(accountRepository, times(1)).findAll();
    }

    @Test
    void testGenerateMonthlyStatementData() {
        when(accountRepository.findById(1L)).thenReturn(Optional.of(testAccount));
        when(cardRepository.findByAccountId(1L)).thenReturn(Arrays.asList(testCard));
        when(transactionRepository.findByCardNumber("1234567890123456")).thenReturn(Arrays.asList(testTransaction));

        Map<String, Object> statement = reportService.generateMonthlyStatementData(1L);

        assertNotNull(statement);
        assertEquals("Monthly Statement", statement.get("statementName"));
        assertEquals(1L, statement.get("accountId"));
        verify(accountRepository, times(1)).findById(1L);
    }
}
