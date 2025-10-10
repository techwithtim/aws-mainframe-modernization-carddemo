/*
 * CardControllerTest.java
 *
 * Spring MVC unit test class for CardController REST API endpoints.
 * Migrated from COBOL programs:
 * - app/cbl/COCRDLIC.cbl (Card List Browse)
 * - app/cbl/COCRDSLC.cbl (Card Select/Detail)
 * - app/cbl/COCRDUPC.cbl (Card Update)
 *
 * BMS Screen mappings:
 * - app/bms/COCRDLI.bms (Card List Screen)
 * - app/bms/COCRDSL.bms (Card Select Screen)
 * - app/bms/COCRDUP.bms (Card Update Screen)
 *
 * This test class validates:
 * - GET /api/v1/cards/{id} for card detail retrieval
 * - GET /api/v1/accounts/{accountId}/cards for paginated card listing
 * - PUT /api/v1/cards/{id} for card updates
 * - PCI-DSS compliant card number masking (************1234 format)
 * - HTTP status codes (200 OK, 400 BAD REQUEST, 404 NOT FOUND)
 * - Bean Validation enforcement
 * - Functional equivalence to COBOL programs
 *
 * Copyright Amazon.com, Inc. or its affiliates.
 * All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 * You may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific
 * language governing permissions and limitations under the License.
 */

package com.aws.carddemo.unit.controller;

import com.aws.carddemo.controller.CardController;
import com.aws.carddemo.dto.request.CardUpdateRequest;
import com.aws.carddemo.dto.response.CardResponse;
import com.aws.carddemo.service.CardService;
import com.aws.carddemo.service.MenuService;
import com.aws.carddemo.service.ReportService;
import com.aws.carddemo.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentMatchers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.aws.carddemo.model.Card;
import com.aws.carddemo.model.Account;
import com.aws.carddemo.mapper.CardMapper;
import com.aws.carddemo.exception.ResourceNotFoundException;
import com.aws.carddemo.exception.InvalidInputException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Unit test class for CardController REST API endpoints.
 * 
 * <p>Tests functional equivalence to COBOL programs:
 * <ul>
 *   <li><strong>COCRDLIC.cbl</strong>: Card list browse with STARTBR/READNEXT pagination
 *       → GET /api/v1/accounts/{accountId}/cards</li>
 *   <li><strong>COCRDSLC.cbl</strong>: Card select/detail with READ CARDFILE
 *       → GET /api/v1/cards/{cardNumber}</li>
 *   <li><strong>COCRDUPC.cbl</strong>: Card update with REWRITE CARDFILE
 *       → PUT /api/v1/cards/{id}</li>
 * </ul>
 * 
 * <p><strong>Test Coverage:</strong>
 * <ul>
 *   <li>Successful card detail retrieval (200 OK)</li>
 *   <li>Successful paginated card list browse (200 OK)</li>
 *   <li>Successful card update operations (200 OK)</li>
 *   <li>Resource not found scenarios (404 NOT FOUND)</li>
 *   <li>Bean Validation failures (400 BAD REQUEST)</li>
 *   <li>PCI-DSS compliant card number masking</li>
 *   <li>JSON response structure validation</li>
 *   <li>Multiple card status scenarios (ACTIVE, INACTIVE, EXPIRED, BLOCKED)</li>
 * </ul>
 * 
 * <p><strong>PCI-DSS Compliance Testing:</strong>
 * <p>Validates that card numbers are properly masked in responses showing only the last 4 digits
 * in the format ************1234 per PCI-DSS Requirement 3.3.
 * 
 * <p><strong>Target Coverage:</strong>
 * <p>≥80% line coverage per Agent Action Plan Section 0.8.1 testing requirements.
 * 
 * @see CardController for the controller under test
 * @see CardService for mocked service layer
 * @see CardResponse for response DTO structure
 * @see CardUpdateRequest for request DTO structure
 * @author AWS CardDemo Modernization Team
 * @version 1.0.0
 * @since 2024
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("CardController Unit Tests")
public class CardControllerTest {

    /**
     * Test security configuration that enables method-level security for @PreAuthorize testing.
     * 
     * <p>This configuration is necessary because the main SecurityConfig has @Profile("!test"),
     * which excludes it from test execution. This test configuration ensures @PreAuthorize
     * annotations are properly enforced during testing.</p>
     */
    @TestConfiguration
    @EnableMethodSecurity(prePostEnabled = true)
    @EnableWebSecurity
    static class TestSecurityConfig {
        
        @Bean
        public SecurityFilterChain cardTestSecurityFilterChain(HttpSecurity http) throws Exception {
            http
                    .csrf(AbstractHttpConfigurer::disable)
                    .authorizeHttpRequests(auth -> auth
                            .anyRequest().authenticated()
                    )
                    .exceptionHandling(exception -> exception
                            .authenticationEntryPoint((request, response, authException) -> {
                                response.setStatus(401);
                                response.setContentType("application/json");
                                response.getWriter().write("{\"message\":\"Unauthorized\"}");
                            })
                    )
                    .sessionManagement(session -> session
                            .sessionCreationPolicy(org.springframework.security.config.http.SessionCreationPolicy.STATELESS)
                    );
            
            return http.build();
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private CardService cardService;

    @MockBean
    private CardMapper cardMapper;

    @MockBean
    private MenuService menuService;

    @MockBean
    private UserRepository userRepository;

    @MockBean
    private ReportService reportService;

    @Autowired
    private ObjectMapper objectMapper;

    private Card testCard;
    private Account testAccount;
    private CardResponse testCardResponse;

    /**
     * Setup method executed before each test.
     * 
     * <p>Initializes test fixtures including:
     * <ul>
     *   <li>Sample Account entity with account ID 12345678901</li>
     *   <li>Sample Card entity with card number 4506445678901234</li>
     *   <li>Sample CardResponse DTO with PCI-DSS compliant masked card number</li>
     * </ul>
     * 
     * <p>These fixtures simulate COBOL copybook data structures:
     * <ul>
     *   <li>CVACT01Y.cpy (Account record)</li>
     *   <li>CVACT02Y.cpy (Card record)</li>
     * </ul>
     */
    @BeforeEach
    public void setUp() {
        // Create test account (simulates ACCTFILE record from COBOL)
        testAccount = new Account();
        testAccount.setAccountId(12345678901L);
        testAccount.setAccountNumber("12345678901");
        testAccount.setActiveStatus("Y");
        testAccount.setCurrentBalance(BigDecimal.valueOf(5000.00));
        testAccount.setCreditLimit(BigDecimal.valueOf(10000.00));

        // Create test card (simulates CARDDAT record from COBOL)
        testCard = new Card();
        testCard.setCardId(1L);
        testCard.setCardNumber("4506445678901234");
        testCard.setEmbossedName("JOHN DOE");
        testCard.setExpirationDate(LocalDate.of(2025, 12, 31));
        testCard.setActiveStatus("Y");
        testCard.setAccount(testAccount);

        // Create test card response (simulates BMS screen output)
        testCardResponse = CardResponse.builder()
                .cardId(1L)
                .cardNumberMasked("************1234") // PCI-DSS compliant masking
                .embossedName("JOHN DOE")
                .expirationDate(LocalDate.of(2025, 12, 31))
                .activeStatus("Y")
                .accountId(12345678901L)
                .build();
    }

    /**
     * Test GET /api/v1/cards/id/{id} endpoint for successful card retrieval.
     * 
     * <p>Validates functional equivalence to COBOL COCRDSLC.cbl program:
     * <pre>
     * COBOL Logic:
     *     EXEC CICS READ DATASET('CARDDAT')
     *         RIDFLD(FD-CARD-ID)
     *         INTO(CARD-RECORD)
     *     END-EXEC.
     * </pre>
     * 
     * <p><strong>Expected Behavior:</strong>
     * <ul>
     *   <li>HTTP Status: 200 OK</li>
     *   <li>Response contains card details with masked card number (************1234)</li>
     *   <li>Response includes account ID for cross-reference</li>
     *   <li>All response fields match CardResponse DTO structure</li>
     * </ul>
     */
    @Test
    @WithMockUser(roles = "USER")
    @DisplayName("Test GET /api/v1/cards/id/{id} - Success with masked card number")
    public void testGetCardById_Success() throws Exception {
        // Arrange: Mock service layer to return test card
        when(cardService.getCardById(anyLong())).thenReturn(testCard);
        when(cardMapper.toResponse(ArgumentMatchers.any(Card.class))).thenReturn(testCardResponse);

        // Act & Assert: Perform GET request and validate response
        mockMvc.perform(get("/api/v1/cards/id/{id}", 1L)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.card_id").value(1))
                .andExpect(jsonPath("$.card_number_masked").value("************1234"))
                .andExpect(jsonPath("$.card_number_masked").value(matchesPattern("^\\*{12}\\d{4}$")))
                .andExpect(jsonPath("$.embossed_name").value("JOHN DOE"))
                .andExpect(jsonPath("$.expiration_date").value("2025-12-31"))
                .andExpect(jsonPath("$.active_status").value("Y"))
                .andExpect(jsonPath("$.account_id").value(12345678901L));

        // Verify service method was called
        verify(cardService, times(1)).getCardById(1L);
        verify(cardMapper, times(1)).toResponse(testCard);
    }

    /**
     * Test GET /api/v1/cards/id/{id} endpoint when card does not exist.
     * 
     * <p>Validates functional equivalence to COBOL FILE STATUS '23' handling:
     * <pre>
     * COBOL Logic:
     *     IF EIBRESP NOT = DFHRESP(NORMAL)
     *         MOVE 'Card not found' TO WS-MESSAGE
     *         PERFORM 9999-ABEND-PROGRAM
     *     END-IF.
     * </pre>
     * 
     * <p><strong>Expected Behavior:</strong>
     * <ul>
     *   <li>HTTP Status: 404 NOT FOUND</li>
     *   <li>Service throws ResourceNotFoundException</li>
     *   <li>Exception handled by GlobalExceptionHandler</li>
     * </ul>
     */
    @Test
    @WithMockUser(roles = "USER")
    @DisplayName("Test GET /api/v1/cards/id/{id} - Card Not Found (404)")
    public void testGetCardById_NotFound() throws Exception {
        // Arrange: Mock service to throw ResourceNotFoundException
        when(cardService.getCardById(anyLong()))
                .thenThrow(new ResourceNotFoundException("Card", 999L));

        // Act & Assert: Perform GET request and expect 404 status
        mockMvc.perform(get("/api/v1/cards/id/{id}", 999L)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound());

        // Verify service method was called
        verify(cardService, times(1)).getCardById(999L);
        verify(cardMapper, never()).toResponse(ArgumentMatchers.any(Card.class));
    }

    /**
     * Test GET /api/v1/accounts/{accountId}/cards endpoint for successful paginated list retrieval.
     * 
     * <p>Validates functional equivalence to COBOL COCRDLIC.cbl program:
     * <pre>
     * COBOL Logic:
     *     EXEC CICS STARTBR DATASET('CARDDAT')
     *         RIDFLD(WS-CARD-ACCT-ID)
     *         GTEQ
     *     END-EXEC.
     *     
     *     PERFORM UNTIL WS-CARD-COUNTER = 10 OR APPL-EOF = 'Y'
     *         EXEC CICS READNEXT DATASET('CARDDAT')
     *             INTO(CARD-RECORD)
     *         END-EXEC
     *         ...
     *     END-PERFORM.
     * </pre>
     * 
     * <p><strong>Expected Behavior:</strong>
     * <ul>
     *   <li>HTTP Status: 200 OK</li>
     *   <li>Response contains paginated card list</li>
     *   <li>Pagination metadata included (totalElements, totalPages, number, size)</li>
     *   <li>Each card has masked card number</li>
     * </ul>
     */
    @Test
    @WithMockUser(roles = "USER")
    @DisplayName("Test GET /api/v1/accounts/{accountId}/cards - Success with pagination")
    public void testGetCardsByAccountId_Success() throws Exception {
        // Arrange: Create additional test cards
        Card card2 = new Card();
        card2.setCardId(2L);
        card2.setCardNumber("4506445678905678");
        card2.setEmbossedName("JANE DOE");
        card2.setExpirationDate(LocalDate.of(2026, 6, 30));
        card2.setActiveStatus("Y");
        card2.setAccount(testAccount);

        CardResponse cardResponse2 = CardResponse.builder()
                .cardId(2L)
                .cardNumberMasked("************5678")
                .embossedName("JANE DOE")
                .expirationDate(LocalDate.of(2026, 6, 30))
                .activeStatus("Y")
                .accountId(12345678901L)
                .build();

        // Create Page with test cards
        List<Card> cardList = List.of(testCard, card2);
        Page<Card> cardPage = new PageImpl<>(cardList, PageRequest.of(0, 10), 2);

        // Mock service and mapper
        when(cardService.getCardsByAccountId(anyLong(), ArgumentMatchers.any(Pageable.class)))
                .thenReturn(cardPage);
        when(cardMapper.toResponse(testCard)).thenReturn(testCardResponse);
        when(cardMapper.toResponse(card2)).thenReturn(cardResponse2);

        // Act & Assert: Perform GET request and validate response
        mockMvc.perform(get("/api/v1/accounts/{accountId}/cards", 12345678901L)
                        .param("page", "0")
                        .param("size", "10")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content", hasSize(2)))
                .andExpect(jsonPath("$.content[0].card_number_masked").value("************1234"))
                .andExpect(jsonPath("$.content[0].card_number_masked").value(matchesPattern("^\\*{12}\\d{4}$")))
                .andExpect(jsonPath("$.content[0].embossed_name").value("JOHN DOE"))
                .andExpect(jsonPath("$.content[1].card_number_masked").value("************5678"))
                .andExpect(jsonPath("$.content[1].card_number_masked").value(matchesPattern("^\\*{12}\\d{4}$")))
                .andExpect(jsonPath("$.content[1].embossed_name").value("JANE DOE"))
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.totalPages").value(1))
                .andExpect(jsonPath("$.number").value(0))
                .andExpect(jsonPath("$.size").value(10));

        // Verify service method was called
        verify(cardService, times(1)).getCardsByAccountId(eq(12345678901L), ArgumentMatchers.any(Pageable.class));
        verify(cardMapper, times(2)).toResponse(ArgumentMatchers.any(Card.class));
    }

    /**
     * Test GET /api/v1/accounts/{accountId}/cards endpoint when account has no cards.
     * 
     * <p>Validates functional equivalence to COBOL APPL-EOF handling:
     * <pre>
     * COBOL Logic:
     *     IF APPL-EOF = 'Y' OR WS-CARD-COUNTER = 0
     *         MOVE 'No cards found' TO WS-MESSAGE
     *     END-IF.
     * </pre>
     * 
     * <p><strong>Expected Behavior:</strong>
     * <ul>
     *   <li>HTTP Status: 200 OK (empty list is valid)</li>
     *   <li>Response contains empty array</li>
     *   <li>Pagination metadata shows 0 total elements</li>
     * </ul>
     */
    @Test
    @WithMockUser(roles = "USER")
    @DisplayName("Test GET /api/v1/accounts/{accountId}/cards - Empty list for account with no cards")
    public void testGetCardsByAccountId_EmptyList() throws Exception {
        // Arrange: Create empty page
        Page<Card> emptyPage = new PageImpl<>(Collections.emptyList(), PageRequest.of(0, 10), 0);

        when(cardService.getCardsByAccountId(anyLong(), ArgumentMatchers.any(Pageable.class)))
                .thenReturn(emptyPage);

        // Act & Assert: Perform GET request and validate response
        mockMvc.perform(get("/api/v1/accounts/{accountId}/cards", 12345678901L)
                        .param("page", "0")
                        .param("size", "10")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content", hasSize(0)))
                .andExpect(jsonPath("$.totalElements").value(0))
                .andExpect(jsonPath("$.totalPages").value(0))
                .andExpect(jsonPath("$.number").value(0))
                .andExpect(jsonPath("$.size").value(10));

        // Verify service method was called
        verify(cardService, times(1)).getCardsByAccountId(eq(12345678901L), ArgumentMatchers.any(Pageable.class));
        verify(cardMapper, never()).toResponse(ArgumentMatchers.any(Card.class));
    }

    /**
     * Test GET /api/v1/cards/{cardNumber} endpoint for successful card retrieval by card number.
     * 
     * <p>Validates functional equivalence to COBOL COCRDSLC.cbl program:
     * <pre>
     * COBOL Logic:
     *     MOVE CARD-NUM-ENTERED TO FD-XREF-CARD-NUM.
     *     
     *     EXEC CICS READ DATASET('XREFFILE')
     *         RIDFLD(FD-XREF-CARD-NUM)
     *         INTO(CARD-XREF-RECORD)
     *     END-EXEC.
     *     
     *     EXEC CICS READ DATASET('CARDDAT')
     *         RIDFLD(CARD-NUM)
     *         INTO(CARD-RECORD)
     *     END-EXEC.
     * </pre>
     * 
     * <p><strong>Expected Behavior:</strong>
     * <ul>
     *   <li>HTTP Status: 200 OK</li>
     *   <li>Response contains card details with account information</li>
     *   <li>Card number is masked in response</li>
     * </ul>
     */
    @Test
    @WithMockUser(roles = "USER")
    @DisplayName("Test GET /api/v1/cards/{cardNumber} - Success with enriched card details")
    public void testGetCardByCardNumber_Success() throws Exception {
        // Arrange: Mock service to return enriched card with account info
        when(cardService.getCardWithAccountInfo(anyString())).thenReturn(testCard);
        when(cardMapper.toResponse(ArgumentMatchers.any(Card.class))).thenReturn(testCardResponse);

        // Act & Assert: Perform GET request and validate response
        mockMvc.perform(get("/api/v1/cards/{cardNumber}", "4506445678901234")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.card_id").value(1))
                .andExpect(jsonPath("$.card_number_masked").value("************1234"))
                .andExpect(jsonPath("$.card_number_masked").value(matchesPattern("^\\*{12}\\d{4}$")))
                .andExpect(jsonPath("$.embossed_name").value("JOHN DOE"))
                .andExpect(jsonPath("$.expiration_date").value("2025-12-31"))
                .andExpect(jsonPath("$.active_status").value("Y"))
                .andExpect(jsonPath("$.account_id").value(12345678901L));

        // Verify service method was called
        verify(cardService, times(1)).getCardWithAccountInfo("4506445678901234");
        verify(cardMapper, times(1)).toResponse(testCard);
    }

    /**
     * Test GET /api/v1/cards/{cardNumber} endpoint with invalid card number format.
     * 
     * <p>Validates functional equivalence to COBOL INSPECT NUMERIC validation:
     * <pre>
     * COBOL Logic:
     *     IF CARD-NUM-ENTERED NOT NUMERIC OR LENGTH NOT = 16
     *         MOVE 'Invalid card number format' TO WS-MESSAGE
     *         PERFORM 9999-ABEND-PROGRAM
     *     END-IF.
     * </pre>
     * 
     * <p><strong>Expected Behavior:</strong>
     * <ul>
     *   <li>HTTP Status: 400 BAD REQUEST</li>
     *   <li>Service not called (validation at controller level)</li>
     * </ul>
     */
    @Test
    @WithMockUser(roles = "USER")
    @DisplayName("Test GET /api/v1/cards/{cardNumber} - Invalid card number format (400)")
    public void testGetCardByCardNumber_InvalidFormat() throws Exception {
        // Act & Assert: Perform GET request with invalid card number (not 16 digits)
        mockMvc.perform(get("/api/v1/cards/{cardNumber}", "123456")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());

        // Verify service method was not called
        verify(cardService, never()).getCardWithAccountInfo(anyString());
        verify(cardMapper, never()).toResponse(ArgumentMatchers.any(Card.class));
    }

    /**
     * Test PUT /api/v1/cards/{id} endpoint for successful card update.
     * 
     * <p>Validates functional equivalence to COBOL COCRDUPC.cbl program:
     * <pre>
     * COBOL Logic:
     *     EXEC CICS READ DATASET('CARDDAT')
     *         RIDFLD(FD-CARD-ID)
     *         INTO(CARD-RECORD)
     *         UPDATE
     *     END-EXEC.
     *     
     *     MOVE NEW-STATUS TO CARD-ACTIVE-STATUS.
     *     MOVE NEW-NAME TO CARD-EMBOSSED-NAME.
     *     MOVE NEW-EXP-DATE TO CARD-EXPIRATION-DATE.
     *     
     *     EXEC CICS REWRITE DATASET('CARDDAT')
     *         FROM(CARD-RECORD)
     *     END-EXEC.
     *     
     *     EXEC CICS SYNCPOINT.
     * </pre>
     * 
     * <p><strong>Expected Behavior:</strong>
     * <ul>
     *   <li>HTTP Status: 200 OK</li>
     *   <li>Response contains updated card details</li>
     *   <li>Card status updated to requested value</li>
     * </ul>
     */
    @Test
    @WithMockUser(roles = "USER")
    @DisplayName("Test PUT /api/v1/cards/{id} - Success with updated card details")
    public void testUpdateCard_Success() throws Exception {
        // Arrange: Create update request
        CardUpdateRequest updateRequest = CardUpdateRequest.builder()
                .cardholderName("JOHN A DOE")
                .cardStatus("A")
                .expirationMonth(12)
                .expirationYear(2026)
                .build();

        // Create updated card response
        CardResponse updatedCardResponse = CardResponse.builder()
                .cardId(1L)
                .cardNumberMasked("************1234")
                .embossedName("JOHN A DOE")
                .expirationDate(LocalDate.of(2026, 12, 31))
                .activeStatus("Y")
                .accountId(12345678901L)
                .build();

        // Mock service and mapper
        when(cardService.getCardById(anyLong())).thenReturn(testCard);
        when(cardService.updateCardStatus(anyLong(), anyString())).thenReturn(testCard);
        when(cardMapper.toResponse(ArgumentMatchers.any(Card.class))).thenReturn(testCardResponse, updatedCardResponse);

        // Act & Assert: Perform PUT request and validate response
        mockMvc.perform(put("/api/v1/cards/{id}", 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.card_id").value(1))
                .andExpect(jsonPath("$.card_number_masked").value(matchesPattern("^\\*{12}\\d{4}$")))
                .andExpect(jsonPath("$.active_status").value("Y"));

        // Verify service methods were called
        verify(cardService, times(1)).getCardById(1L);
        verify(cardService, times(1)).updateCardStatus(eq(1L), eq("Y"));
    }

    /**
     * Test PUT /api/v1/cards/{id} endpoint with Bean Validation failure.
     * 
     * <p>Validates Bean Validation enforcement:
     * <ul>
     *   <li>Cardholder name required and max 50 chars</li>
     *   <li>Card status must be A/C/S</li>
     *   <li>Expiration month must be 1-12</li>
     *   <li>Expiration year must be ≥2024</li>
     * </ul>
     * 
     * <p><strong>Expected Behavior:</strong>
     * <ul>
     *   <li>HTTP Status: 400 BAD REQUEST</li>
     *   <li>Validation errors in response</li>
     * </ul>
     */
    @Test
    @WithMockUser(roles = "USER")
    @DisplayName("Test PUT /api/v1/cards/{id} - Validation failure with invalid inputs (400)")
    public void testUpdateCard_ValidationError() throws Exception {
        // Arrange: Create update request with invalid data
        CardUpdateRequest invalidRequest = CardUpdateRequest.builder()
                .cardholderName("") // Empty name (should fail @NotBlank)
                .cardStatus("X") // Invalid status (should fail @Pattern)
                .expirationMonth(13) // Invalid month (should fail @Max)
                .expirationYear(2020) // Past year (should fail @Min)
                .build();

        // Act & Assert: Perform PUT request and expect validation failure
        mockMvc.perform(put("/api/v1/cards/{id}", 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidRequest))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());

        // Verify service methods were not called
        verify(cardService, never()).getCardById(anyLong());
        verify(cardService, never()).updateCardStatus(anyLong(), anyString());
    }

    /**
     * Test PUT /api/v1/cards/{id} endpoint when card does not exist.
     * 
     * <p>Validates functional equivalence to COBOL FILE STATUS '23' handling:
     * <pre>
     * COBOL Logic:
     *     IF EIBRESP NOT = DFHRESP(NORMAL)
     *         MOVE 'Card not found' TO WS-MESSAGE
     *         PERFORM 9999-ABEND-PROGRAM
     *     END-IF.
     * </pre>
     * 
     * <p><strong>Expected Behavior:</strong>
     * <ul>
     *   <li>HTTP Status: 404 NOT FOUND</li>
     *   <li>Service throws ResourceNotFoundException</li>
     * </ul>
     */
    @Test
    @WithMockUser(roles = "USER")
    @DisplayName("Test PUT /api/v1/cards/{id} - Card not found (404)")
    public void testUpdateCard_NotFound() throws Exception {
        // Arrange: Create valid update request
        CardUpdateRequest updateRequest = CardUpdateRequest.builder()
                .cardholderName("JOHN DOE")
                .cardStatus("A")
                .expirationMonth(12)
                .expirationYear(2025)
                .build();

        // Mock service to throw ResourceNotFoundException
        when(cardService.getCardById(anyLong()))
                .thenThrow(new ResourceNotFoundException("Card", 999L));

        // Act & Assert: Perform PUT request and expect 404 status
        mockMvc.perform(put("/api/v1/cards/{id}", 999L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound());

        // Verify service method was called
        verify(cardService, times(1)).getCardById(999L);
        verify(cardService, never()).updateCardStatus(anyLong(), anyString());
    }

    /**
     * Test PUT /api/v1/cards/{id} endpoint attempting to activate expired card.
     * 
     * <p>Validates functional equivalence to COBOL business rule validation:
     * <pre>
     * COBOL Logic:
     *     IF CARD-EXPIRE-DATE < WS-CURRENT-DATE AND NEW-STATUS = 'Y'
     *         MOVE 'Cannot activate expired card' TO WS-MESSAGE
     *         PERFORM 9999-ABEND-PROGRAM
     *     END-IF.
     * </pre>
     * 
     * <p><strong>Expected Behavior:</strong>
     * <ul>
     *   <li>HTTP Status: 400 BAD REQUEST</li>
     *   <li>Service throws InvalidInputException</li>
     * </ul>
     */
    @Test
    @WithMockUser(roles = "USER")
    @DisplayName("Test PUT /api/v1/cards/{id} - Cannot activate expired card (400)")
    public void testUpdateCard_CannotActivateExpiredCard() throws Exception {
        // Arrange: Create expired card
        Card expiredCard = new Card();
        expiredCard.setCardId(1L);
        expiredCard.setCardNumber("4506445678901234");
        expiredCard.setEmbossedName("JOHN DOE");
        expiredCard.setExpirationDate(LocalDate.of(2020, 12, 31)); // Expired
        expiredCard.setActiveStatus("N");
        expiredCard.setAccount(testAccount);

        CardUpdateRequest updateRequest = CardUpdateRequest.builder()
                .cardholderName("JOHN DOE")
                .cardStatus("A") // Attempting to activate
                .expirationMonth(12)
                .expirationYear(2025)
                .build();

        // Mock service to return expired card, then throw exception on activation
        when(cardService.getCardById(anyLong())).thenReturn(expiredCard);
        when(cardMapper.toResponse(ArgumentMatchers.any(Card.class))).thenReturn(testCardResponse);
        when(cardService.updateCardStatus(anyLong(), eq("Y")))
                .thenThrow(new InvalidInputException("activeStatus", 
                        "Cannot activate card that expired on 2020-12-31"));

        // Act & Assert: Perform PUT request and expect 400 status
        mockMvc.perform(put("/api/v1/cards/{id}", 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());

        // Verify service methods were called
        verify(cardService, times(1)).getCardById(1L);
        verify(cardService, times(1)).updateCardStatus(eq(1L), eq("Y"));
    }

    /**
     * Parameterized test for various card status scenarios.
     * 
     * <p>Tests card status transitions mapping to COBOL 88-level condition names:
     * <ul>
     *   <li>'A' = Active (COBOL: CARD-ACTIVE-STATUS = 'Y')</li>
     *   <li>'C' = Closed (COBOL: CARD-CLOSED)</li>
     *   <li>'S' = Suspended (COBOL: CARD-SUSPENDED)</li>
     * </ul>
     * 
     * <p>Validates status codes from CVACT02Y.cpy copybook card status field.
     * 
     * @param cardStatus the card status code to test (A/C/S)
     * @param expectedActiveStatus the expected active status in response (Y/N)
     * @param statusDescription the human-readable status description
     */
    @ParameterizedTest(name = "Card Status: {2} ({0}) -> Active Status: {1}")
    @CsvSource({
            "A,Y,Active - Card can be used for transactions",
            "C,N,Closed - Card permanently closed",
            "S,N,Suspended - Card temporarily suspended"
    })
    @WithMockUser(roles = "USER")
    @DisplayName("Test card status transitions with various status codes")
    public void testCardStatusTransitions(String cardStatus, 
                                          String expectedActiveStatus, 
                                          String statusDescription) throws Exception {
        // Arrange: Create update request with specified status
        CardUpdateRequest updateRequest = CardUpdateRequest.builder()
                .cardholderName("JOHN DOE")
                .cardStatus(cardStatus)
                .expirationMonth(12)
                .expirationYear(2025)
                .build();

        // Create response with expected status
        CardResponse statusResponse = CardResponse.builder()
                .cardId(1L)
                .cardNumberMasked("************1234")
                .embossedName("JOHN DOE")
                .expirationDate(LocalDate.of(2025, 12, 31))
                .activeStatus(expectedActiveStatus)
                .accountId(12345678901L)
                .build();

        // Mock service and mapper
        when(cardService.getCardById(anyLong())).thenReturn(testCard);
        when(cardService.updateCardStatus(anyLong(), eq(expectedActiveStatus))).thenReturn(testCard);
        when(cardMapper.toResponse(ArgumentMatchers.any(Card.class))).thenReturn(testCardResponse, statusResponse);

        // Act & Assert: Perform PUT request and validate status
        mockMvc.perform(put("/api/v1/cards/{id}", 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active_status").value(expectedActiveStatus));

        // Verify service methods were called with correct status
        verify(cardService, times(1)).updateCardStatus(eq(1L), eq(expectedActiveStatus));
    }

    /**
     * Test PCI-DSS compliant card number masking in all responses.
     * 
     * <p>Validates that card numbers are always masked in the format:
     * <ul>
     *   <li>Pattern: ************1234 (12 asterisks + last 4 digits)</li>
     *   <li>Regex: ^\\*{12}\\d{4}$</li>
     * </ul>
     * 
     * <p>This test ensures compliance with PCI-DSS Requirement 3.3:
     * "Mask PAN when displayed (the first six and last four digits are the maximum number
     * of digits to be displayed)"
     * 
     * <p>The implementation shows only the last 4 digits for enhanced security.
     */
    @Test
    @WithMockUser(roles = "USER")
    @DisplayName("Test PCI-DSS compliant card number masking in response")
    public void testCardNumberMasking_PciDssCompliance() throws Exception {
        // Arrange: Mock service
        when(cardService.getCardById(anyLong())).thenReturn(testCard);
        when(cardMapper.toResponse(ArgumentMatchers.any(Card.class))).thenReturn(testCardResponse);

        // Act & Assert: Verify masked card number format
        mockMvc.perform(get("/api/v1/cards/id/{id}", 1L)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.card_number_masked").value("************1234"))
                .andExpect(jsonPath("$.card_number_masked").value(matchesPattern("^\\*{12}\\d{4}$")))
                .andExpect(jsonPath("$.card_number_masked").value(not(containsString("4506"))))
                .andExpect(jsonPath("$.card_number_masked").value(not(containsString("4567"))))
                .andExpect(jsonPath("$.card_number_masked").value(not(containsString("8901"))));

        // Verify no full card number in response
        String response = mockMvc.perform(get("/api/v1/cards/id/{id}", 1L)
                        .accept(MediaType.APPLICATION_JSON))
                .andReturn()
                .getResponse()
                .getContentAsString();

        // Assert full card number is not present in response
        org.junit.jupiter.api.Assertions.assertFalse(
                response.contains("4506445678901234"),
                "Full card number should never appear in API response (PCI-DSS violation)"
        );
    }
}
