package com.aws.carddemo.integration;

import com.aws.carddemo.dto.AccountDTO;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class AccountIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine")
            .withDatabaseName("carddemo_test")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void testCreateAndRetrieveAccount() throws Exception {
        AccountDTO accountDTO = new AccountDTO();
        accountDTO.setAccountId(99999999999L);
        accountDTO.setActiveStatus("Y");
        accountDTO.setCurrentBalance(new BigDecimal("1000.00"));
        accountDTO.setCreditLimit(new BigDecimal("5000.00"));
        accountDTO.setCashCreditLimit(new BigDecimal("1000.00"));
        accountDTO.setOpenDate(LocalDate.of(2024, 1, 1));
        accountDTO.setExpirationDate(LocalDate.of(2029, 1, 1));
        accountDTO.setAddressZip("10001");
        accountDTO.setGroupId("TEST01");

        mockMvc.perform(post("/api/v1/accounts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(accountDTO)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.accountId").value(99999999999L))
                .andExpect(jsonPath("$.activeStatus").value("Y"));

        mockMvc.perform(get("/api/v1/accounts/{id}", 99999999999L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountId").value(99999999999L))
                .andExpect(jsonPath("$.currentBalance").value(1000.00));
    }

    @Test
    void testUpdateAccount() throws Exception {
        AccountDTO accountDTO = new AccountDTO();
        accountDTO.setAccountId(88888888888L);
        accountDTO.setActiveStatus("Y");
        accountDTO.setCurrentBalance(new BigDecimal("2000.00"));
        accountDTO.setCreditLimit(new BigDecimal("8000.00"));
        accountDTO.setCashCreditLimit(new BigDecimal("1500.00"));
        accountDTO.setOpenDate(LocalDate.of(2024, 1, 1));
        accountDTO.setExpirationDate(LocalDate.of(2029, 1, 1));
        accountDTO.setAddressZip("20002");
        accountDTO.setGroupId("TEST02");

        mockMvc.perform(post("/api/v1/accounts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(accountDTO)))
                .andExpect(status().isCreated());

        accountDTO.setCurrentBalance(new BigDecimal("3000.00"));

        mockMvc.perform(put("/api/v1/accounts/{id}", 88888888888L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(accountDTO)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentBalance").value(3000.00));
    }

    @Test
    void testDeleteAccount() throws Exception {
        AccountDTO accountDTO = new AccountDTO();
        accountDTO.setAccountId(77777777777L);
        accountDTO.setActiveStatus("Y");
        accountDTO.setCurrentBalance(new BigDecimal("500.00"));
        accountDTO.setCreditLimit(new BigDecimal("3000.00"));
        accountDTO.setCashCreditLimit(new BigDecimal("500.00"));
        accountDTO.setOpenDate(LocalDate.of(2024, 1, 1));
        accountDTO.setExpirationDate(LocalDate.of(2029, 1, 1));
        accountDTO.setAddressZip("30003");
        accountDTO.setGroupId("TEST03");

        mockMvc.perform(post("/api/v1/accounts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(accountDTO)))
                .andExpect(status().isCreated());

        mockMvc.perform(delete("/api/v1/accounts/{id}", 77777777777L))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/accounts/{id}", 77777777777L))
                .andExpect(status().isNotFound());
    }
}
