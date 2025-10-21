package com.aws.carddemo.controller;

import com.aws.carddemo.dto.AccountDTO;
import com.aws.carddemo.service.AccountService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AccountController.class)
class AccountControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private AccountService accountService;

    private AccountDTO testAccountDTO;

    @BeforeEach
    void setUp() {
        testAccountDTO = new AccountDTO();
        testAccountDTO.setAccountId(12345678901L);
        testAccountDTO.setActiveStatus("Y");
        testAccountDTO.setCurrentBalance(new BigDecimal("5000.00"));
        testAccountDTO.setCreditLimit(new BigDecimal("10000.00"));
        testAccountDTO.setCashCreditLimit(new BigDecimal("2000.00"));
        testAccountDTO.setOpenDate(LocalDate.of(2020, 1, 1));
        testAccountDTO.setExpirationDate(LocalDate.of(2025, 1, 1));
        testAccountDTO.setAddressZip("12345");
        testAccountDTO.setGroupId("GRP001");
    }

    @Test
    void getAccountById_Success() throws Exception {
        when(accountService.getAccountById(anyLong())).thenReturn(testAccountDTO);

        mockMvc.perform(get("/api/v1/accounts/{id}", 12345678901L))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.accountId").value(12345678901L))
                .andExpect(jsonPath("$.activeStatus").value("Y"));

        verify(accountService, times(1)).getAccountById(12345678901L);
    }

    @Test
    void getAllAccounts_Success() throws Exception {
        List<AccountDTO> accounts = Arrays.asList(testAccountDTO);
        when(accountService.getAllAccounts()).thenReturn(accounts);

        mockMvc.perform(get("/api/v1/accounts"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$[0].accountId").value(12345678901L));

        verify(accountService, times(1)).getAllAccounts();
    }

    @Test
    void createAccount_Success() throws Exception {
        when(accountService.createAccount(any(AccountDTO.class))).thenReturn(testAccountDTO);

        mockMvc.perform(post("/api/v1/accounts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(testAccountDTO)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.accountId").value(12345678901L));

        verify(accountService, times(1)).createAccount(any(AccountDTO.class));
    }

    @Test
    void updateAccount_Success() throws Exception {
        when(accountService.updateAccount(anyLong(), any(AccountDTO.class))).thenReturn(testAccountDTO);

        mockMvc.perform(put("/api/v1/accounts/{id}", 12345678901L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(testAccountDTO)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountId").value(12345678901L));

        verify(accountService, times(1)).updateAccount(anyLong(), any(AccountDTO.class));
    }

    @Test
    void deleteAccount_Success() throws Exception {
        doNothing().when(accountService).deleteAccount(anyLong());

        mockMvc.perform(delete("/api/v1/accounts/{id}", 12345678901L))
                .andExpect(status().isNoContent());

        verify(accountService, times(1)).deleteAccount(12345678901L);
    }
}
