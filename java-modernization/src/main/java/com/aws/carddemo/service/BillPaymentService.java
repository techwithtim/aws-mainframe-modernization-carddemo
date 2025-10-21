package com.aws.carddemo.service;

import com.aws.carddemo.dto.BillPaymentRequest;
import com.aws.carddemo.dto.BillPaymentResponse;
import com.aws.carddemo.exception.ResourceNotFoundException;
import com.aws.carddemo.model.Account;
import com.aws.carddemo.model.CardXref;
import com.aws.carddemo.model.Transaction;
import com.aws.carddemo.repository.AccountRepository;
import com.aws.carddemo.repository.CardXrefRepository;
import com.aws.carddemo.repository.TransactionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
@Transactional
public class BillPaymentService {

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private CardXrefRepository cardXrefRepository;

    @Autowired
    private TransactionRepository transactionRepository;

    public BillPaymentResponse processBillPayment(BillPaymentRequest request) {
        Account account = accountRepository.findById(request.getAccountId())
                .orElseThrow(() -> new ResourceNotFoundException("Account ID NOT found..."));

        if (account.getCurrentBalance().compareTo(BigDecimal.ZERO) <= 0) {
            BillPaymentResponse response = new BillPaymentResponse(false, "You have nothing to pay...");
            response.setAccountId(account.getAccountId());
            response.setCurrentBalance(account.getCurrentBalance());
            return response;
        }

        if (!request.isConfirmed()) {
            BillPaymentResponse response = new BillPaymentResponse(false, "Confirm to make a bill payment...");
            response.setAccountId(account.getAccountId());
            response.setCurrentBalance(account.getCurrentBalance());
            return response;
        }

        List<CardXref> xrefs = cardXrefRepository.findByAcctId(account.getAccountId());
        if (xrefs.isEmpty()) {
            throw new ResourceNotFoundException("No card found for account");
        }
        CardXref xref = xrefs.get(0);

        String transactionId = generateTransactionId();
        BigDecimal paymentAmount = account.getCurrentBalance();

        Transaction transaction = new Transaction();
        transaction.setTransactionId(transactionId);
        transaction.setTransactionTypeCode("02");
        transaction.setTransactionCategoryCode(2);
        transaction.setTransactionSource("POS TERM");
        transaction.setTransactionDescription("BILL PAYMENT - ONLINE");
        transaction.setTransactionAmount(paymentAmount);
        transaction.setCardNumber(xref.getCardNum());
        transaction.setMerchantId(999999999L);
        transaction.setMerchantName("BILL PAYMENT");
        transaction.setMerchantCity("N/A");
        transaction.setMerchantZip("N/A");
        
        LocalDateTime now = LocalDateTime.now();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        String timestamp = now.format(formatter);
        transaction.setOriginalTimestamp(timestamp);
        transaction.setProcessedTimestamp(timestamp);

        transactionRepository.save(transaction);

        BigDecimal newBalance = account.getCurrentBalance().subtract(paymentAmount);
        account.setCurrentBalance(newBalance);
        accountRepository.save(account);

        BillPaymentResponse response = new BillPaymentResponse(true, "Bill payment successful");
        response.setAccountId(account.getAccountId());
        response.setCurrentBalance(account.getCurrentBalance().add(paymentAmount));
        response.setPaymentAmount(paymentAmount);
        response.setNewBalance(newBalance);
        response.setTransactionId(transactionId);

        return response;
    }

    private String generateTransactionId() {
        Transaction lastTransaction = transactionRepository.findTopByOrderByTransactionIdDesc();
        if (lastTransaction == null) {
            return "0000000000000001";
        }
        
        try {
            long lastId = Long.parseLong(lastTransaction.getTransactionId());
            long newId = lastId + 1;
            return String.format("%016d", newId);
        } catch (NumberFormatException e) {
            return String.format("%016d", System.currentTimeMillis());
        }
    }
}
