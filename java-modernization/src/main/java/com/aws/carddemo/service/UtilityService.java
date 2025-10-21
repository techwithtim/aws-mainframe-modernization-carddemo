package com.aws.carddemo.service;

import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

@Service
public class UtilityService {

    private static final DateTimeFormatter COBOL_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final DateTimeFormatter COBOL_TIME_FORMAT = DateTimeFormatter.ofPattern("HHmmss");
    private static final DateTimeFormatter COBOL_TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    public String formatDateForCobol(LocalDate date) {
        if (date == null) {
            return "00000000";
        }
        return date.format(COBOL_DATE_FORMAT);
    }

    public LocalDate parseDateFromCobol(String cobolDate) {
        if (cobolDate == null || cobolDate.trim().isEmpty() || "00000000".equals(cobolDate)) {
            return null;
        }
        try {
            return LocalDate.parse(cobolDate, COBOL_DATE_FORMAT);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("Invalid COBOL date format: " + cobolDate, e);
        }
    }

    public String formatTimeForCobol(LocalDateTime dateTime) {
        if (dateTime == null) {
            return "000000";
        }
        return dateTime.format(COBOL_TIME_FORMAT);
    }

    public String formatTimestampForCobol(LocalDateTime dateTime) {
        if (dateTime == null) {
            return "00000000000000";
        }
        return dateTime.format(COBOL_TIMESTAMP_FORMAT);
    }

    public LocalDateTime parseTimestampFromCobol(String cobolTimestamp) {
        if (cobolTimestamp == null || cobolTimestamp.trim().isEmpty() || "00000000000000".equals(cobolTimestamp)) {
            return null;
        }
        try {
            return LocalDateTime.parse(cobolTimestamp, COBOL_TIMESTAMP_FORMAT);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("Invalid COBOL timestamp format: " + cobolTimestamp, e);
        }
    }

    public String padLeft(String input, int length, char padChar) {
        if (input == null) {
            input = "";
        }
        if (input.length() >= length) {
            return input.substring(0, length);
        }
        StringBuilder sb = new StringBuilder();
        for (int i = input.length(); i < length; i++) {
            sb.append(padChar);
        }
        sb.append(input);
        return sb.toString();
    }

    public String padRight(String input, int length, char padChar) {
        if (input == null) {
            input = "";
        }
        if (input.length() >= length) {
            return input.substring(0, length);
        }
        StringBuilder sb = new StringBuilder(input);
        for (int i = input.length(); i < length; i++) {
            sb.append(padChar);
        }
        return sb.toString();
    }

    public boolean isValidAccountNumber(String accountNumber) {
        if (accountNumber == null || accountNumber.trim().isEmpty()) {
            return false;
        }
        return accountNumber.matches("\\d{11}");
    }

    public boolean isValidCardNumber(String cardNumber) {
        if (cardNumber == null || cardNumber.trim().isEmpty()) {
            return false;
        }
        return cardNumber.matches("\\d{16}");
    }

    public boolean isValidCustomerId(String customerId) {
        if (customerId == null || customerId.trim().isEmpty()) {
            return false;
        }
        return customerId.matches("\\d{9}");
    }

    public boolean isValidUserId(String userId) {
        if (userId == null || userId.trim().isEmpty()) {
            return false;
        }
        return userId.matches("[A-Z0-9]{1,8}");
    }

    public String generateNextTransactionId(String lastTransactionId) {
        if (lastTransactionId == null || lastTransactionId.trim().isEmpty()) {
            return "0000000001";
        }
        
        try {
            long nextId = Long.parseLong(lastTransactionId) + 1;
            return String.format("%010d", nextId);
        } catch (NumberFormatException e) {
            return "0000000001";
        }
    }

    public String maskCardNumber(String cardNumber) {
        if (cardNumber == null || cardNumber.length() < 4) {
            return cardNumber;
        }
        
        StringBuilder masked = new StringBuilder();
        for (int i = 0; i < cardNumber.length() - 4; i++) {
            masked.append("*");
        }
        masked.append(cardNumber.substring(cardNumber.length() - 4));
        return masked.toString();
    }

    public String maskAccountNumber(String accountNumber) {
        if (accountNumber == null || accountNumber.length() < 4) {
            return accountNumber;
        }
        
        StringBuilder masked = new StringBuilder();
        for (int i = 0; i < accountNumber.length() - 4; i++) {
            masked.append("*");
        }
        masked.append(accountNumber.substring(accountNumber.length() - 4));
        return masked.toString();
    }
}
