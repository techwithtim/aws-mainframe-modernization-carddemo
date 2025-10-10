# CardDemo Modernized - System Architecture

## Table of Contents
1. [Executive Summary](#executive-summary)
2. [Architecture Overview](#architecture-overview)
3. [Layered Architecture](#layered-architecture)
4. [Component Details](#component-details)
5. [Technology Stack](#technology-stack)
6. [Database Architecture](#database-architecture)
7. [Security Architecture](#security-architecture)
8. [Batch Processing Architecture](#batch-processing-architecture)
9. [Deployment Architecture](#deployment-architecture)
10. [Component Interaction Flows](#component-interaction-flows)
11. [Technology Decisions](#technology-decisions)
12. [Performance Considerations](#performance-considerations)
13. [Scalability and Resilience](#scalability-and-resilience)

---

## Executive Summary

The modernized CardDemo application transforms a legacy IBM mainframe credit card management system into a cloud-native Java 21 application. This architecture document describes the complete system design, including the layered architecture pattern, component interactions, technology choices, and deployment model.

### Key Architectural Characteristics

- **Architecture Pattern**: Layered monolithic cloud-native application
- **Runtime Platform**: Spring Boot 3.3.x on Java 21 (LTS)
- **Database**: PostgreSQL 15+ (Single-store relational database)
- **Deployment**: Docker containers orchestrated by Kubernetes
- **API Style**: RESTful HTTP/JSON APIs
- **Security**: Spring Security 6.x with JWT-based authentication
- **Batch Processing**: Spring Batch 5.x with chunk-oriented processing

### Migration Context

**From**: COBOL/CICS/VSAM mainframe application  
**To**: Java 21/Spring Boot/PostgreSQL cloud-native application  
**Primary Goal**: Maintain complete functional equivalence while enabling cloud deployment

---

## Architecture Overview

### High-Level System Context

```
┌─────────────────────────────────────────────────────────────────┐
│                        External Clients                         │
│                  (Web, Mobile, API Consumers)                   │
└───────────────────────────┬─────────────────────────────────────┘
                            │ HTTPS (TLS 1.3)
                            │ REST/JSON
                            ▼
┌─────────────────────────────────────────────────────────────────┐
│                    Kubernetes Ingress                           │
│                  (AWS Load Balancer)                            │
└───────────────────────────┬─────────────────────────────────────┘
                            │
                            ▼
┌─────────────────────────────────────────────────────────────────┐
│               CardDemo Application (Pods 1-3)                   │
│          Spring Boot 3.3.x / Java 21 / Docker                   │
│                                                                 │
│  ┌──────────────────────────────────────────────────────────┐  │
│  │            Presentation Layer                            │  │
│  │         REST Controllers (8 classes)                     │  │
│  └──────────────────┬───────────────────────────────────────┘  │
│                     │                                           │
│  ┌──────────────────▼───────────────────────────────────────┐  │
│  │            Service Layer                                 │  │
│  │         Business Logic (9 service classes)               │  │
│  │         @Transactional / Spring Batch Jobs               │  │
│  └──────────────────┬───────────────────────────────────────┘  │
│                     │                                           │
│  ┌──────────────────▼───────────────────────────────────────┐  │
│  │         Data Access Layer                                │  │
│  │       Spring Data JPA Repositories (11 interfaces)       │  │
│  └──────────────────┬───────────────────────────────────────┘  │
└────────────────────┬────────────────────────────────────────────┘
                     │ JDBC (HikariCP Connection Pool)
                     │
                     ▼
┌─────────────────────────────────────────────────────────────────┐
│           PostgreSQL 15+ Database (AWS RDS)                     │
│                Multi-AZ, Encrypted at Rest                      │
└─────────────────────────────────────────────────────────────────┘
```

### Architecture Style: Layered Cloud-Native Monolith

The application follows a **layered monolithic architecture** with clear separation of concerns:

1. **Presentation Layer**: REST API controllers handling HTTP requests/responses
2. **Service Layer**: Business logic implementation with transactional boundaries
3. **Data Access Layer**: JPA repositories abstracting database operations
4. **Infrastructure Layer**: Cross-cutting concerns (security, logging, monitoring)

**Rationale for Monolith**: Maintains functional equivalence with the legacy COBOL application, which was a single integrated system. Microservices decomposition is deliberately deferred to avoid introducing complexity during initial migration.

---

## Layered Architecture

### Layer 1: Presentation Layer (REST API Controllers)

The presentation layer exposes RESTful HTTP endpoints that replace the 17 legacy BMS 3270 terminal screens.

#### Controller Inventory

| Controller Class | Base Path | Replaces | Endpoints | Purpose |
|-----------------|-----------|----------|-----------|---------|
| `AuthController` | `/api/v1/auth` | COSGN00.bms | POST /login<br>POST /logout | User authentication |
| `MenuController` | `/api/v1/menu` | COMEN01.bms<br>COADM01.bms | GET /menu<br>GET /admin/menu | Application navigation |
| `AccountController` | `/api/v1/accounts` | COACTVW.bms<br>COACTUP.bms | GET /{id}<br>PUT /{id} | Account inquiry/update |
| `CardController` | `/api/v1/cards` | COCRDLI.bms<br>COCRDSL.bms<br>COCRDUP.bms | GET /{id}<br>GET /account/{accountId}<br>PUT /{id} | Card management |
| `TransactionController` | `/api/v1/transactions` | COTRN00.bms<br>COTRN01.bms<br>COTRN02.bms | GET /{id}<br>GET /account/{accountId}<br>POST / | Transaction operations |
| `PaymentController` | `/api/v1/accounts/{id}/payments` | COBIL00.bms | POST / | Bill payment processing |
| `ReportController` | `/api/v1/reports` | CORPT00.bms | GET /?type={type} | Report generation |
| `AdminController` | `/api/v1/admin/users` | COUSR00-03.bms | GET /<br>POST /<br>PUT /{id}<br>DELETE /{id} | User administration |

#### Controller Design Patterns

```java
@RestController
@RequestMapping("/api/v1/accounts")
@Validated
public class AccountController {
    
    private final AccountService accountService;
    
    // Constructor injection (immutable dependency)
    public AccountController(AccountService accountService) {
        this.accountService = accountService;
    }
    
    @GetMapping("/{id}")
    public ResponseEntity<AccountResponse> getAccount(@PathVariable Long id) {
        return ResponseEntity.ok(accountService.getAccountById(id));
    }
    
    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    public ResponseEntity<AccountResponse> updateAccount(
        @PathVariable Long id,
        @Valid @RequestBody AccountUpdateRequest request
    ) {
        return ResponseEntity.ok(accountService.updateAccount(id, request));
    }
}
```

**Key Characteristics**:
- **Stateless**: No session state stored in controllers
- **DTO Pattern**: Request/Response DTOs separate from entity classes
- **Validation**: Bean Validation (JSR-380) annotations on DTOs
- **Security**: Method-level authorization with `@PreAuthorize`
- **Error Handling**: Delegated to `GlobalExceptionHandler` (`@ControllerAdvice`)

---

### Layer 2: Service Layer (Business Logic)

The service layer encapsulates all business rules, calculations, and transaction management.

#### Service Class Inventory

| Service Class | Replaces COBOL Program(s) | Responsibilities |
|--------------|---------------------------|------------------|
| `AuthenticationService` | COSGN00C.cbl | User authentication, JWT token generation, password validation |
| `MenuService` | COMEN01C.cbl, COADM01C.cbl | Menu option retrieval based on user role |
| `AccountService` | COACTVWC.cbl, COACTUPC.cbl | Account inquiry, balance updates, account management |
| `CardService` | COCRDLIC.cbl, COCRDSLC.cbl, COCRDUPC.cbl | Card list/browse, card details, card status updates |
| `TransactionService` | COTRN00C.cbl, COTRN01C.cbl, COTRN02C.cbl | Transaction browse, detail view, manual transaction entry |
| `PaymentService` | COBIL00C.cbl | Payment posting, balance adjustments, payment validation |
| `UserService` | COUSR00C.cbl, COUSR01C.cbl, COUSR02C.cbl, COUSR03C.cbl | User CRUD operations, password management |
| `ReportService` | CORPT00C.cbl, CBTRN03C.cbl | Report generation, batch job triggering |
| `InterestCalculationService` | CBACT04C.cbl | Interest calculation algorithm (preserves COBOL precision) |

#### Service Layer Design

```java
@Service
@Transactional
@Slf4j
public class TransactionService {
    
    private final TransactionRepository transactionRepository;
    private final AccountRepository accountRepository;
    private final CardXrefRepository cardXrefRepository;
    private final TransactionMapper transactionMapper;
    
    // Constructor injection for all dependencies
    public TransactionService(
        TransactionRepository transactionRepository,
        AccountRepository accountRepository,
        CardXrefRepository cardXrefRepository,
        TransactionMapper transactionMapper
    ) {
        this.transactionRepository = transactionRepository;
        this.accountRepository = accountRepository;
        this.cardXrefRepository = cardXrefRepository;
        this.transactionMapper = transactionMapper;
    }
    
    /**
     * Post a new transaction (manual entry).
     * Business logic migrated from: COTRN02C.cbl
     */
    public TransactionResponse postTransaction(TransactionRequest request) {
        // 1. Validate card number and resolve to account
        CardXref cardXref = cardXrefRepository.findByCardNumber(request.getCardNumber())
            .orElseThrow(() -> new ResourceNotFoundException("Card not found"));
        
        // 2. Retrieve account and validate balance
        Account account = accountRepository.findById(cardXref.getAccountId())
            .orElseThrow(() -> new ResourceNotFoundException("Account not found"));
        
        // 3. Apply business rules (e.g., credit limit check)
        if (request.getTransactionAmount().compareTo(account.getCreditLimit()) > 0) {
            throw new InsufficientFundsException("Transaction exceeds credit limit");
        }
        
        // 4. Create transaction entity
        Transaction transaction = transactionMapper.toEntity(request);
        transaction.setAccountId(account.getAccountId());
        transaction.setTransactionTimestamp(LocalDateTime.now());
        
        // 5. Update account balance (preserving decimal precision)
        BigDecimal newBalance = account.getCurrentBalance()
            .add(request.getTransactionAmount());
        account.setCurrentBalance(newBalance);
        
        // 6. Persist transaction and updated account
        Transaction savedTransaction = transactionRepository.save(transaction);
        accountRepository.save(account);
        
        log.info("Transaction posted: ID={}, Account={}, Amount={}", 
            savedTransaction.getTransactionId(),
            account.getAccountId(),
            request.getTransactionAmount());
        
        return transactionMapper.toResponse(savedTransaction);
    }
}
```

**Key Characteristics**:
- **@Transactional**: Ensures ACID properties (replaces COBOL EXEC CICS SYNCPOINT)
- **Constructor Injection**: Immutable dependencies, easier to test with mocks
- **BigDecimal**: Preserves exact decimal precision for financial amounts (replaces COBOL COMP-3)
- **Exception Handling**: Business rule violations throw custom exceptions
- **Logging**: Structured logging with SLF4J (masked sensitive data)

---

### Layer 3: Data Access Layer (Spring Data JPA)

The data access layer provides an abstraction over PostgreSQL database operations using Spring Data JPA.

#### Repository Interface Inventory

| Repository Interface | Entity | Replaces VSAM File | Custom Query Methods |
|---------------------|--------|-------------------|---------------------|
| `AccountRepository` | Account | ACCTFILE (KSDS) | findByAccountNumber() |
| `CardRepository` | Card | CARDFILE (KSDS) | findByCardNumber()<br>findByAccountId() |
| `CardXrefRepository` | CardXref | XREFFILE (KSDS) | findByCardNumber()<br>findByAccountId() |
| `CustomerRepository` | Customer | CUSTFILE (KSDS) | findByCustomerId()<br>findBySsn() |
| `TransactionRepository` | Transaction | TRANSACT (KSDS) | findByAccountId()<br>findByAccountIdAndDateRange() |
| `DailyTransactionRepository` | DailyTransaction | DALYTRAN (Sequential) | findUnprocessed() |
| `TransactionCategoryRepository` | TransactionCategory | TRANCATG (Sequential) | findByCode() |
| `TransactionTypeRepository` | TransactionType | TRANTYPE (Sequential) | findByCode() |
| `TransactionCategoryBalanceRepository` | TransactionCategoryBalance | TCATBAL (KSDS) | findByAccountId() |
| `DisclosureGroupRepository` | DisclosureGroup | DISCGRP (Sequential) | findByGroupId() |
| `UserRepository` | User | USRSEC (KSDS) | findByUsername()<br>existsByUsername() |

#### Repository Design Pattern

```java
@Repository
public interface TransactionRepository extends JpaRepository<Transaction, Long> {
    
    /**
     * Find all transactions for a specific account.
     * Replaces: COBOL READ TRANSACT WHERE ACCT-ID = ws-account-id
     */
    List<Transaction> findByAccountId(Long accountId);
    
    /**
     * Find transactions for an account within a date range (paginated).
     * Replaces: COBOL STARTBR + READNEXT loop with date filtering
     */
    @Query("SELECT t FROM Transaction t WHERE t.accountId = :accountId " +
           "AND t.transactionTimestamp BETWEEN :startDate AND :endDate " +
           "ORDER BY t.transactionTimestamp DESC")
    Page<Transaction> findByAccountIdAndDateRange(
        @Param("accountId") Long accountId,
        @Param("startDate") LocalDateTime startDate,
        @Param("endDate") LocalDateTime endDate,
        Pageable pageable
    );
    
    /**
     * Count transactions for an account in a specific category.
     * Business logic from: CBACT04C.cbl (interest calculation)
     */
    @Query("SELECT COUNT(t) FROM Transaction t WHERE t.accountId = :accountId " +
           "AND t.transactionCategoryId = :categoryId")
    Long countByAccountIdAndCategory(
        @Param("accountId") Long accountId,
        @Param("categoryId") Integer categoryId
    );
}
```

**Key Characteristics**:
- **JpaRepository**: Inherits CRUD operations (save, findById, delete, etc.)
- **Derived Queries**: Method name parsing (e.g., findByAccountId)
- **@Query Annotation**: Custom JPQL for complex queries
- **Pagination**: Page<T> and Pageable for large result sets
- **Transactional**: Read operations are @Transactional(readOnly=true) by default

---

### Layer 4: Infrastructure Layer (Cross-Cutting Concerns)

The infrastructure layer provides foundational services used across all layers.

#### Infrastructure Components

**4.1 Security Configuration**

```java
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {
    
    private final JwtAuthenticationFilter jwtAuthFilter;
    private final UserDetailsServiceImpl userDetailsService;
    
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/v1/auth/**").permitAll()
                .requestMatchers("/actuator/health").permitAll()
                .requestMatchers("/actuator/prometheus").permitAll()
                .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
                .anyRequest().authenticated()
            )
            .sessionManagement(session -> session
                .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            )
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);
        
        return http.build();
    }
    
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(10); // 10 rounds for PCI-DSS compliance
    }
}
```

**4.2 Exception Handling**

```java
@ControllerAdvice
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {
    
    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiError> handleResourceNotFound(ResourceNotFoundException ex) {
        log.warn("Resource not found: {}", ex.getMessage());
        ApiError error = ApiError.builder()
            .timestamp(LocalDateTime.now())
            .status(HttpStatus.NOT_FOUND.value())
            .error("Resource Not Found")
            .message(ex.getMessage())
            .build();
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
    }
    
    @ExceptionHandler(InsufficientFundsException.class)
    public ResponseEntity<ApiError> handleInsufficientFunds(InsufficientFundsException ex) {
        log.warn("Business rule violation: {}", ex.getMessage());
        ApiError error = ApiError.builder()
            .timestamp(LocalDateTime.now())
            .status(HttpStatus.BAD_REQUEST.value())
            .error("Business Rule Violation")
            .message(ex.getMessage())
            .build();
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }
    
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidationException(MethodArgumentNotValidException ex) {
        List<String> errors = ex.getBindingResult().getFieldErrors().stream()
            .map(error -> error.getField() + ": " + error.getDefaultMessage())
            .collect(Collectors.toList());
        
        ApiError error = ApiError.builder()
            .timestamp(LocalDateTime.now())
            .status(HttpStatus.BAD_REQUEST.value())
            .error("Validation Failed")
            .message("Input validation errors")
            .details(errors)
            .build();
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }
}
```

**4.3 Data Mapping (Entity ↔ DTO)**

```java
@Mapper(componentModel = "spring")
public interface AccountMapper {
    
    /**
     * Map Account entity to AccountResponse DTO.
     * Sensitive fields (e.g., SSN) are masked in the response.
     */
    @Mapping(target = "accountNumber", expression = "java(maskAccountNumber(account.getAccountNumber()))")
    AccountResponse toResponse(Account account);
    
    /**
     * Map AccountUpdateRequest DTO to Account entity.
     */
    @Mapping(target = "accountId", ignore = true)
    @Mapping(target = "createdDate", ignore = true)
    @Mapping(target = "updatedDate", ignore = true)
    void updateEntityFromRequest(AccountUpdateRequest request, @MappingTarget Account account);
    
    default String maskAccountNumber(String accountNumber) {
        if (accountNumber == null || accountNumber.length() < 4) {
            return "****";
        }
        return "****" + accountNumber.substring(accountNumber.length() - 4);
    }
}
```

**4.4 Utility Classes**

| Utility Class | Replaces | Purpose |
|--------------|----------|---------|
| `DateFormatter` | COBDATFT.asm | Date format conversions (YYYY-MM-DD, MM/DD/YYYY) |
| `DateValidator` | CSUTLDTC.cbl | Date validation (leap year, valid ranges) |
| `ValidationUtil` | CSLKPCDY.cpy | Area code and state validation |
| `FinancialCalculator` | CBACT04C.cbl arithmetic | BigDecimal utilities for interest calculations |
| `Constants` | COTTL01Y.cpy, CSMSG01Y.cpy | Application constants and message literals |

---

## Component Details

### Entity Model (JPA Entities)

The entity model represents the database schema and replaces COBOL copybook data structures.

#### Core Entity: Account

```java
@Entity
@Table(name = "account", indexes = {
    @Index(name = "idx_account_number", columnList = "account_number"),
    @Index(name = "idx_customer_id", columnList = "customer_id")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Account extends BaseEntity {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "account_id")
    private Long accountId;
    
    // From COBOL: 05 ACCT-ID PIC 9(11)
    @Column(name = "account_number", length = 11, unique = true, nullable = false)
    @Pattern(regexp = "\\d{11}")
    private String accountNumber;
    
    // From COBOL: 05 ACCT-ACTIVE-STATUS PIC X(01)
    @Column(name = "account_status", length = 1, nullable = false)
    @Pattern(regexp = "[YN]")
    private String accountStatus;
    
    // From COBOL: 05 ACCT-CURR-BAL PIC S9(09)V99 COMP-3
    @Column(name = "current_balance", precision = 11, scale = 2, nullable = false)
    private BigDecimal currentBalance;
    
    // From COBOL: 05 ACCT-CREDIT-LIMIT PIC S9(09)V99 COMP-3
    @Column(name = "credit_limit", precision = 11, scale = 2, nullable = false)
    private BigDecimal creditLimit;
    
    // From COBOL: 05 ACCT-CASH-CREDIT-LIMIT PIC S9(09)V99 COMP-3
    @Column(name = "cash_credit_limit", precision = 11, scale = 2)
    private BigDecimal cashCreditLimit;
    
    // From COBOL: 05 ACCT-OPEN-DATE PIC X(10)
    @Column(name = "open_date")
    private LocalDate openDate;
    
    // From COBOL: 05 ACCT-EXPIRAION-DATE PIC X(10)
    @Column(name = "expiration_date")
    private LocalDate expirationDate;
    
    // From COBOL: 05 ACCT-GROUP-ID PIC X(10)
    @Column(name = "disclosure_group_id", length = 10)
    private String disclosureGroupId;
    
    @Column(name = "customer_id", nullable = false)
    private Long customerId;
    
    // Audit fields from BaseEntity (createdDate, updatedDate, version)
}
```

**COBOL to Java Data Type Mapping**:

| COBOL Data Type | Example | Java Type | Annotations |
|----------------|---------|-----------|-------------|
| PIC 9(n) | PIC 9(11) | Long or String | @Column(length=n) |
| PIC X(n) | PIC X(16) | String | @Column(length=n) |
| PIC S9(n)V99 COMP-3 | PIC S9(09)V99 COMP-3 | BigDecimal | @Column(precision=11, scale=2) |
| PIC X(10) (date) | PIC X(10) | LocalDate | @Column |
| 88-level (condition) | 88 ACTIVE-STATUS VALUE 'Y' | Enum or boolean method | N/A |

#### Core Entity: Transaction

```java
@Entity
@Table(name = "transaction", indexes = {
    @Index(name = "idx_account_id", columnList = "account_id"),
    @Index(name = "idx_transaction_timestamp", columnList = "transaction_timestamp"),
    @Index(name = "idx_card_number", columnList = "card_number")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Transaction extends BaseEntity {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "transaction_id")
    private Long transactionId;
    
    @Column(name = "account_id", nullable = false)
    private Long accountId;
    
    // From COBOL: 05 TRAN-CARD-NUM PIC 9(16)
    @Column(name = "card_number", length = 16, nullable = false)
    @ToString.Exclude  // Mask in logs for PCI-DSS
    private String cardNumber;
    
    // From COBOL: 05 TRAN-TYPE-CD PIC X(02)
    @Column(name = "transaction_type_code", length = 2, nullable = false)
    private String transactionTypeCode;
    
    // From COBOL: 05 TRAN-CAT-CD PIC 9(04)
    @Column(name = "transaction_category_code")
    private Integer transactionCategoryCode;
    
    // From COBOL: 05 TRAN-SOURCE PIC X(10)
    @Column(name = "transaction_source", length = 10)
    private String transactionSource;
    
    // From COBOL: 05 TRAN-DESC PIC X(100)
    @Column(name = "transaction_description", length = 100)
    private String transactionDescription;
    
    // From COBOL: 05 TRAN-AMT PIC S9(09)V99 COMP-3
    @Column(name = "transaction_amount", precision = 11, scale = 2, nullable = false)
    private BigDecimal transactionAmount;
    
    // From COBOL: 05 TRAN-MERCHANT-ID PIC 9(15)
    @Column(name = "merchant_id", length = 15)
    private String merchantId;
    
    // From COBOL: 05 TRAN-MERCHANT-NAME PIC X(50)
    @Column(name = "merchant_name", length = 50)
    private String merchantName;
    
    // From COBOL: 05 TRAN-MERCHANT-CITY PIC X(50)
    @Column(name = "merchant_city", length = 50)
    private String merchantCity;
    
    // From COBOL: 05 TRAN-MERCHANT-ZIP PIC X(10)
    @Column(name = "merchant_zip", length = 10)
    private String merchantZip;
    
    @Column(name = "transaction_timestamp", nullable = false)
    private LocalDateTime transactionTimestamp;
}
```

#### Entity Relationships

```
Customer (1) ──────────────────> (*) Account
                                      │
                                      │
                                      ▼
CardXref (*) <──────────────────── (*) Card
      │                               │
      │                               │
      ▼                               ▼
Transaction (*) ───────────────> (1) Account
      │
      │
      ▼
TransactionType (1)
TransactionCategory (1)
```

**Relationship Cardinalities**:
- One Customer has many Accounts
- One Account has many Cards (via CardXref)
- One Card has many Transactions
- One Account has many Transactions
- One Transaction has one TransactionType
- One Transaction has one TransactionCategory

---

## Technology Stack

### Core Technologies

| Component | Technology | Version | Purpose |
|-----------|-----------|---------|---------|
| **Runtime** | Java | 21 LTS | Application runtime (Virtual Threads support) |
| **Framework** | Spring Boot | 3.3.0 | Application framework |
| **Web** | Spring Web MVC | 3.3.0 | REST API implementation |
| **ORM** | Hibernate | 6.4.x | JPA provider for database access |
| **Database** | PostgreSQL | 15+ | Relational database (AWS RDS) |
| **Connection Pool** | HikariCP | 5.1.0 | Database connection pooling |
| **Migration** | Flyway | 10.13.0 | Database schema version control |
| **Security** | Spring Security | 6.2.x | Authentication and authorization |
| **JWT** | JJWT | 0.12.5 | JSON Web Token implementation |
| **Batch** | Spring Batch | 5.1.x | Batch processing framework |
| **Validation** | Hibernate Validator | 8.0.x | Bean Validation (JSR-380) |
| **Mapping** | MapStruct | 1.5.5.Final | Entity ↔ DTO mapping |
| **Logging** | SLF4J + Logback | 2.0.13 / 1.5.6 | Structured logging |
| **Monitoring** | Micrometer + Prometheus | 1.13.0 | Metrics and observability |
| **Testing** | JUnit 5 + Mockito | 5.10.x / 5.12.x | Unit testing |
| **Integration Testing** | Testcontainers | 1.19.8 | Integration testing with PostgreSQL |
| **Build Tool** | Maven | 3.9.x | Dependency management and build |
| **Container** | Docker | 24.x | Application containerization |
| **Orchestration** | Kubernetes | 1.28+ | Container orchestration |

### Spring Boot Starters

```xml
<dependencies>
    <!-- Web Layer -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-web</artifactId>
    </dependency>
    
    <!-- Data Access -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-data-jpa</artifactId>
    </dependency>
    
    <!-- Security -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-security</artifactId>
    </dependency>
    
    <!-- Batch Processing -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-batch</artifactId>
    </dependency>
    
    <!-- Validation -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-validation</artifactId>
    </dependency>
    
    <!-- Monitoring -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-actuator</artifactId>
    </dependency>
    
    <!-- Testing -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-test</artifactId>
        <scope>test</scope>
    </dependency>
</dependencies>
```

---

## Database Architecture

### PostgreSQL 15+ Single-Store Architecture

The modernized application uses PostgreSQL as the sole database, replacing the legacy VSAM/DB2/IMS environment.

#### Database Configuration

**Hosting**: AWS RDS PostgreSQL 15+ (Multi-AZ deployment)  
**Instance Class**: db.r6g.xlarge (4 vCPU, 32 GiB RAM) or larger  
**Storage**: General Purpose SSD (gp3) with 100 GB minimum  
**Backup**: Automated daily backups with 7-day retention  
**Encryption**: Encryption at rest using AWS KMS  
**High Availability**: Multi-AZ with automatic failover  

#### Schema Management with Flyway

Database schema is version-controlled using Flyway migrations:

```
src/main/resources/db/migration/
├── V1__create_tables.sql          # Initial schema (11 core tables)
├── V2__create_indexes.sql         # Performance indexes
├── V3__seed_reference_data.sql    # Transaction types, categories
└── V4__load_test_data.sql         # Test data from VSAM files
```

**Flyway Configuration** (`application.yml`):

```yaml
spring:
  flyway:
    enabled: true
    baseline-on-migrate: true
    validate-on-migrate: true
    clean-disabled: true
    locations: classpath:db/migration
    out-of-order: false
```

#### Database Schema

**Core Tables** (11 tables):

| Table Name | Replaces VSAM File | Primary Key | Rows (Initial) | Purpose |
|------------|-------------------|-------------|----------------|---------|
| `account` | ACCTFILE (KSDS) | account_id | 50 | Account master records |
| `card` | CARDFILE (KSDS) | card_id | 50 | Credit card information |
| `card_xref` | XREFFILE (KSDS) | xref_id | 50 | Card-to-account cross-reference |
| `customer` | CUSTFILE (KSDS) | customer_id | 50 | Customer demographics |
| `transaction` | TRANSACT (KSDS) | transaction_id | Variable | Posted transactions |
| `daily_transaction` | DALYTRAN (Sequential) | daily_transaction_id | Variable | Daily transaction feed |
| `transaction_category` | TRANCATG (Sequential) | category_id | 18 | Transaction categories |
| `transaction_type` | TRANTYPE (Sequential) | type_id | 7 | Transaction type codes |
| `transaction_category_balance` | TCATBAL (KSDS) | tcatbal_id | 50 | Category balance tracking |
| `disclosure_group` | DISCGRP (Sequential) | group_id | 51 | Interest rate groups |
| `app_user` | USRSEC (KSDS) | user_id | Variable | User authentication |

**Example DDL** (`V1__create_tables.sql`):

```sql
-- Account table (from CVACT01Y.cpy)
CREATE TABLE account (
    account_id BIGSERIAL PRIMARY KEY,
    account_number VARCHAR(11) UNIQUE NOT NULL,
    account_status VARCHAR(1) NOT NULL CHECK (account_status IN ('Y', 'N')),
    current_balance NUMERIC(11, 2) NOT NULL DEFAULT 0.00,
    credit_limit NUMERIC(11, 2) NOT NULL,
    cash_credit_limit NUMERIC(11, 2),
    open_date DATE NOT NULL,
    expiration_date DATE,
    disclosure_group_id VARCHAR(10),
    customer_id BIGINT NOT NULL,
    created_date TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_date TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version INT NOT NULL DEFAULT 0,
    CONSTRAINT fk_account_customer FOREIGN KEY (customer_id) REFERENCES customer(customer_id)
);

-- Transaction table (from CVTRA05Y.cpy)
CREATE TABLE transaction (
    transaction_id BIGSERIAL PRIMARY KEY,
    account_id BIGINT NOT NULL,
    card_number VARCHAR(16) NOT NULL,
    transaction_type_code VARCHAR(2) NOT NULL,
    transaction_category_code INT,
    transaction_source VARCHAR(10),
    transaction_description VARCHAR(100),
    transaction_amount NUMERIC(11, 2) NOT NULL,
    merchant_id VARCHAR(15),
    merchant_name VARCHAR(50),
    merchant_city VARCHAR(50),
    merchant_zip VARCHAR(10),
    transaction_timestamp TIMESTAMP NOT NULL,
    created_date TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_date TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version INT NOT NULL DEFAULT 0,
    CONSTRAINT fk_transaction_account FOREIGN KEY (account_id) REFERENCES account(account_id),
    CONSTRAINT fk_transaction_type FOREIGN KEY (transaction_type_code) REFERENCES transaction_type(type_code),
    CONSTRAINT fk_transaction_category FOREIGN KEY (transaction_category_code) REFERENCES transaction_category(category_code)
);

-- User table (from CSUSR01Y.cpy)
CREATE TABLE app_user (
    user_id BIGSERIAL PRIMARY KEY,
    username VARCHAR(8) UNIQUE NOT NULL,
    password_hash VARCHAR(60) NOT NULL,  -- BCrypt hash
    user_type VARCHAR(1) NOT NULL CHECK (user_type IN ('U', 'A')),  -- U=User, A=Admin
    first_name VARCHAR(25),
    last_name VARCHAR(25),
    created_date TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_date TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version INT NOT NULL DEFAULT 0
);
```

**Indexes** (`V2__create_indexes.sql`):

```sql
-- Performance indexes for frequently accessed columns
CREATE INDEX idx_account_number ON account(account_number);
CREATE INDEX idx_account_customer_id ON account(customer_id);
CREATE INDEX idx_card_number ON card(card_number);
CREATE INDEX idx_card_account_id ON card(account_id);
CREATE INDEX idx_card_xref_card_number ON card_xref(card_number);
CREATE INDEX idx_card_xref_account_id ON card_xref(account_id);
CREATE INDEX idx_transaction_account_id ON transaction(account_id);
CREATE INDEX idx_transaction_timestamp ON transaction(transaction_timestamp);
CREATE INDEX idx_transaction_card_number ON transaction(card_number);
CREATE INDEX idx_user_username ON app_user(username);
```

#### Data Migration Strategy

**5-Step Migration Process** (from VSAM to PostgreSQL):

1. **Export VSAM Datasets**: Use COBOL programs or utilities to export VSAM files to ASCII flat files
2. **Transform Data**: Convert EBCDIC to ASCII, handle packed decimal (COMP-3) fields
3. **Load Reference Data**: Execute `V3__seed_reference_data.sql` to populate transaction types and categories
4. **Load Master Data**: Execute `V4__load_test_data.sql` to load accounts, cards, customers
5. **Validation**: Compare record counts and key field values between VSAM and PostgreSQL

**Example Data Load** (`V4__load_test_data.sql`):

```sql
-- Load transaction types (from trantype.txt)
INSERT INTO transaction_type (type_code, type_description) VALUES
('01', 'Purchase'),
('02', 'Cash Advance'),
('03', 'Balance Transfer'),
('04', 'Payment'),
('05', 'Refund'),
('06', 'Fee'),
('07', 'Interest Charge');

-- Load accounts (from acctdata.txt)
INSERT INTO account (account_number, account_status, current_balance, credit_limit, 
    cash_credit_limit, open_date, expiration_date, disclosure_group_id, customer_id)
SELECT ...
FROM external_file('/app/data/ASCII/acctdata.txt');
```

#### PCI-DSS Compliance

**Sensitive Data Protection**:

1. **Card Numbers**: Stored as-is in database, masked in logs and API responses
2. **CVV Codes**: NEVER stored in the database (per PCI-DSS requirement 3.2)
3. **SSN**: Encrypted at rest using AWS KMS, masked in logs
4. **Passwords**: BCrypt hashed (10 rounds minimum), never stored in plain text
5. **Encryption at Rest**: AWS RDS encryption enabled (AES-256)
6. **Encryption in Transit**: TLS 1.3 for all database connections

**Data Masking Example**:

```java
public class DataMaskingUtil {
    public static String maskCardNumber(String cardNumber) {
        if (cardNumber == null || cardNumber.length() < 4) {
            return "****";
        }
        return "****-****-****-" + cardNumber.substring(cardNumber.length() - 4);
    }
    
    public static String maskSsn(String ssn) {
        if (ssn == null || ssn.length() < 4) {
            return "***-**-****";
        }
        return "***-**-" + ssn.substring(ssn.length() - 4);
    }
}
```

---

## Security Architecture

### Authentication and Authorization

The security architecture implements stateless JWT-based authentication to replace legacy RACF security.

#### Security Flow

```
1. User Login
   ┌───────────┐
   │  Client   │
   └─────┬─────┘
         │ POST /api/v1/auth/login
         │ {username, password}
         ▼
   ┌─────────────────┐
   │ AuthController  │
   └────────┬────────┘
            │
            ▼
   ┌────────────────────────┐
   │ AuthenticationService  │
   │ - Validate credentials │
   │ - Load user from DB    │
   │ - Generate JWT token   │
   └────────┬───────────────┘
            │
            ▼
   ┌──────────────────┐
   │ JwtTokenProvider │
   │ - Create token   │
   │ - Sign with key  │
   └────────┬─────────┘
            │
            ▼
   {
     "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
     "expiresIn": 3600,
     "tokenType": "Bearer",
     "user": { "username": "USER0001", "role": "USER" }
   }

2. Authenticated Request
   ┌───────────┐
   │  Client   │
   └─────┬─────┘
         │ GET /api/v1/accounts/1
         │ Authorization: Bearer <token>
         ▼
   ┌──────────────────────────┐
   │ JwtAuthenticationFilter  │
   │ - Extract token          │
   │ - Validate signature     │
   │ - Validate expiration    │
   └────────┬─────────────────┘
            │
            ▼
   ┌─────────────────────┐
   │ SecurityContext     │
   │ - Set authentication│
   └────────┬────────────┘
            │
            ▼
   ┌──────────────────────┐
   │ AccountController    │
   │ @PreAuthorize("...")│
   └────────┬─────────────┘
            │
            ▼
   Account details returned
```

#### JWT Token Structure

**Token Payload**:

```json
{
  "sub": "USER0001",
  "role": "USER",
  "iat": 1704123600,
  "exp": 1704127200
}
```

**Token Configuration**:
- **Algorithm**: HMAC-SHA256 (HS256)
- **Expiration**: 1 hour (3600 seconds)
- **Secret Key**: 256-bit key stored in AWS Secrets Manager
- **Refresh**: Sliding window refresh (30 minutes before expiration)

#### Role-Based Access Control (RBAC)

**User Roles**:

| Role | Code | Permissions | User Type |
|------|------|-------------|-----------|
| `ROLE_USER` | U | View/update own account, cards, transactions | Regular user |
| `ROLE_ADMIN` | A | All USER permissions + user management | Administrator |

**Authorization Annotations**:

```java
// Public endpoint (no authentication required)
@GetMapping("/api/v1/health")
public ResponseEntity<String> health() { ... }

// Authenticated endpoint (any authenticated user)
@GetMapping("/api/v1/accounts/{id}")
@PreAuthorize("isAuthenticated()")
public ResponseEntity<AccountResponse> getAccount(@PathVariable Long id) { ... }

// Admin-only endpoint
@DeleteMapping("/api/v1/admin/users/{id}")
@PreAuthorize("hasRole('ADMIN')")
public ResponseEntity<Void> deleteUser(@PathVariable Long id) { ... }
```

#### Security Components

**1. JWT Token Provider**:

```java
@Component
@Slf4j
public class JwtTokenProvider {
    
    @Value("${jwt.secret}")
    private String jwtSecret;
    
    @Value("${jwt.expiration}")
    private long jwtExpiration;
    
    public String generateToken(UserDetails userDetails) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + jwtExpiration);
        
        return Jwts.builder()
            .setSubject(userDetails.getUsername())
            .claim("role", userDetails.getAuthorities().stream()
                .findFirst()
                .map(GrantedAuthority::getAuthority)
                .orElse("ROLE_USER"))
            .setIssuedAt(now)
            .setExpiration(expiryDate)
            .signWith(Keys.hmacShaKeyFor(jwtSecret.getBytes()), SignatureAlgorithm.HS256)
            .compact();
    }
    
    public boolean validateToken(String token) {
        try {
            Jwts.parserBuilder()
                .setSigningKey(Keys.hmacShaKeyFor(jwtSecret.getBytes()))
                .build()
                .parseClaimsJws(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            log.error("Invalid JWT token: {}", e.getMessage());
            return false;
        }
    }
    
    public String getUsernameFromToken(String token) {
        Claims claims = Jwts.parserBuilder()
            .setSigningKey(Keys.hmacShaKeyFor(jwtSecret.getBytes()))
            .build()
            .parseClaimsJws(token)
            .getBody();
        return claims.getSubject();
    }
}
```

**2. JWT Authentication Filter**:

```java
@Component
@Slf4j
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    
    private final JwtTokenProvider jwtTokenProvider;
    private final UserDetailsServiceImpl userDetailsService;
    
    @Override
    protected void doFilterInternal(
        HttpServletRequest request,
        HttpServletResponse response,
        FilterChain filterChain
    ) throws ServletException, IOException {
        
        String token = extractTokenFromRequest(request);
        
        if (token != null && jwtTokenProvider.validateToken(token)) {
            String username = jwtTokenProvider.getUsernameFromToken(token);
            UserDetails userDetails = userDetailsService.loadUserByUsername(username);
            
            UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(
                    userDetails,
                    null,
                    userDetails.getAuthorities()
                );
            
            SecurityContextHolder.getContext().setAuthentication(authentication);
            log.debug("Authenticated user: {}", username);
        }
        
        filterChain.doFilter(request, response);
    }
    
    private String extractTokenFromRequest(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (bearerToken != null && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return null;
    }
}
```

**3. Password Encoding**:

```java
@Configuration
public class PasswordEncoderConfig {
    
    @Bean
    public PasswordEncoder passwordEncoder() {
        // BCrypt with 10 rounds (PCI-DSS compliant)
        return new BCryptPasswordEncoder(10);
    }
}
```

**Password Hashing Example**:

```java
// Legacy COBOL: 05 USRSEC-PWD PIC X(08) (plain text)
// Modern Java: BCrypt hash (60 characters)
String plainPassword = "PASSWORD";
String hashedPassword = passwordEncoder.encode(plainPassword);
// Result: "$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy"
```

#### API Security Headers

**Response Headers**:

```yaml
# Security headers configured in Spring Security
http:
  headers:
    frame-options: DENY
    content-type-options: nosniff
    xss-protection: 1; mode=block
    strict-transport-security: max-age=31536000; includeSubDomains
    content-security-policy: default-src 'self'
```

---

## Batch Processing Architecture

### Spring Batch Framework

Batch processing replaces JCL-orchestrated COBOL batch jobs with Spring Batch jobs.

#### Batch Job Inventory

| Spring Batch Job | Replaces COBOL Program | Execution Schedule | Purpose |
|-----------------|------------------------|-------------------|---------|
| `TransactionPostingJob` | CBTRN01C.cbl, CBTRN02C.cbl | Daily at 11:00 PM | Post daily transactions to master file |
| `InterestCalculationJob` | CBACT04C.cbl | Monthly (last day) | Calculate monthly interest charges |
| `StatementGenerationJob` | CBSTM03A.CBL, CBSTM03B.CBL | Monthly (1st day) | Generate customer statements |
| `TransactionReportJob` | CBTRN03C.cbl | On-demand | Generate transaction reports |

#### Batch Architecture Pattern

```
┌─────────────────────────────────────────────────────────────┐
│                     Spring Batch Job                        │
│                                                             │
│  ┌────────────────────────────────────────────────────┐    │
│  │                 Step 1: Read                       │    │
│  │  ┌──────────────────────────────────────────┐     │    │
│  │  │  ItemReader (DailyTransactionReader)     │     │    │
│  │  │  - Reads from daily_transaction table    │     │    │
│  │  │  - Pagination (chunk size: 100)          │     │    │
│  │  └──────────────────┬───────────────────────┘     │    │
│  └────────────────────┬────────────────────────────────┘    │
│                       │                                     │
│  ┌────────────────────▼────────────────────────────────┐    │
│  │                 Step 2: Process                     │    │
│  │  ┌──────────────────────────────────────────┐      │    │
│  │  │  ItemProcessor (TransactionProcessor)    │      │    │
│  │  │  - Validate transaction                  │      │    │
│  │  │  - Apply business rules                  │      │    │
│  │  │  - Lookup account/card                   │      │    │
│  │  │  - Calculate balances                    │      │    │
│  │  └──────────────────┬───────────────────────┘      │    │
│  └────────────────────┬────────────────────────────────┘    │
│                       │                                     │
│  ┌────────────────────▼────────────────────────────────┐    │
│  │                 Step 3: Write                       │    │
│  │  ┌──────────────────────────────────────────┐      │    │
│  │  │  ItemWriter (TransactionWriter)          │      │    │
│  │  │  - Write to transaction table            │      │    │
│  │  │  - Update account balances               │      │    │
│  │  │  - Update category balances              │      │    │
│  │  └──────────────────────────────────────────┘      │    │
│  └─────────────────────────────────────────────────────┘    │
│                                                             │
│  Transaction committed every 100 records (chunk)           │
└─────────────────────────────────────────────────────────────┘
```

#### Transaction Posting Job Configuration

```java
@Configuration
@Slf4j
public class TransactionPostingJobConfig {
    
    @Bean
    public Job transactionPostingJob(
        JobRepository jobRepository,
        Step transactionPostingStep
    ) {
        return new JobBuilder("transactionPostingJob", jobRepository)
            .incrementer(new RunIdIncrementer())
            .flow(transactionPostingStep)
            .end()
            .listener(new JobExecutionListener() {
                @Override
                public void beforeJob(JobExecution jobExecution) {
                    log.info("Starting transaction posting job");
                }
                
                @Override
                public void afterJob(JobExecution jobExecution) {
                    log.info("Transaction posting job completed: status={}",
                        jobExecution.getStatus());
                }
            })
            .build();
    }
    
    @Bean
    public Step transactionPostingStep(
        JobRepository jobRepository,
        PlatformTransactionManager transactionManager,
        ItemReader<DailyTransaction> reader,
        ItemProcessor<DailyTransaction, Transaction> processor,
        ItemWriter<Transaction> writer
    ) {
        return new StepBuilder("transactionPostingStep", jobRepository)
            .<DailyTransaction, Transaction>chunk(100, transactionManager)
            .reader(reader)
            .processor(processor)
            .writer(writer)
            .faultTolerant()
            .skipLimit(10)
            .skip(InvalidTransactionException.class)
            .retryLimit(3)
            .retry(DeadlockLoserDataAccessException.class)
            .listener(new ChunkListener() {
                @Override
                public void afterChunk(ChunkContext context) {
                    log.info("Processed chunk: items={}",
                        context.getStepContext().getStepExecution().getWriteCount());
                }
            })
            .build();
    }
}
```

#### Transaction Processor

```java
@Component
@Slf4j
public class TransactionProcessor implements ItemProcessor<DailyTransaction, Transaction> {
    
    private final CardXrefRepository cardXrefRepository;
    private final AccountRepository accountRepository;
    private final TransactionCategoryRepository categoryRepository;
    
    /**
     * Process daily transaction and apply business rules.
     * Business logic migrated from: CBTRN01C.cbl
     */
    @Override
    public Transaction process(DailyTransaction dailyTransaction) throws Exception {
        
        // 1. Validate card number and resolve to account
        CardXref cardXref = cardXrefRepository.findByCardNumber(dailyTransaction.getCardNumber())
            .orElseThrow(() -> new InvalidTransactionException(
                "Invalid card number: " + dailyTransaction.getCardNumber()));
        
        // 2. Retrieve account
        Account account = accountRepository.findById(cardXref.getAccountId())
            .orElseThrow(() -> new InvalidTransactionException(
                "Account not found for card: " + dailyTransaction.getCardNumber()));
        
        // 3. Validate account is active (COBOL: IF ACCT-ACTIVE-STATUS = 'Y')
        if (!"Y".equals(account.getAccountStatus())) {
            throw new InvalidTransactionException("Account is not active");
        }
        
        // 4. Validate transaction amount
        if (dailyTransaction.getTransactionAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new InvalidTransactionException("Transaction amount must be positive");
        }
        
        // 5. Check credit limit for purchases (COBOL logic from CBTRN01C.cbl)
        if ("01".equals(dailyTransaction.getTransactionTypeCode())) {  // Purchase
            BigDecimal newBalance = account.getCurrentBalance()
                .add(dailyTransaction.getTransactionAmount());
            if (newBalance.compareTo(account.getCreditLimit()) > 0) {
                throw new InsufficientFundsException("Transaction exceeds credit limit");
            }
        }
        
        // 6. Create transaction entity
        Transaction transaction = Transaction.builder()
            .accountId(account.getAccountId())
            .cardNumber(dailyTransaction.getCardNumber())
            .transactionTypeCode(dailyTransaction.getTransactionTypeCode())
            .transactionCategoryCode(dailyTransaction.getTransactionCategoryCode())
            .transactionSource(dailyTransaction.getTransactionSource())
            .transactionDescription(dailyTransaction.getTransactionDescription())
            .transactionAmount(dailyTransaction.getTransactionAmount())
            .merchantId(dailyTransaction.getMerchantId())
            .merchantName(dailyTransaction.getMerchantName())
            .merchantCity(dailyTransaction.getMerchantCity())
            .merchantZip(dailyTransaction.getMerchantZip())
            .transactionTimestamp(dailyTransaction.getTransactionTimestamp())
            .build();
        
        log.debug("Processed transaction: card={}, amount={}",
            maskCardNumber(dailyTransaction.getCardNumber()),
            dailyTransaction.getTransactionAmount());
        
        return transaction;
    }
    
    private String maskCardNumber(String cardNumber) {
        return "****-****-****-" + cardNumber.substring(cardNumber.length() - 4);
    }
}
```

#### Interest Calculation Job

```java
/**
 * Interest calculation service.
 * Business logic migrated from: CBACT04C.cbl
 * 
 * CRITICAL: This implementation preserves the exact calculation logic
 * from the COBOL program to ensure financial accuracy.
 */
@Service
@Slf4j
public class InterestCalculationService {
    
    private final AccountRepository accountRepository;
    private final TransactionCategoryBalanceRepository tcatBalRepository;
    private final DisclosureGroupRepository disclosureGroupRepository;
    private final TransactionRepository transactionRepository;
    
    /**
     * Calculate monthly interest for an account.
     * 
     * COBOL Logic (from CBACT04C.cbl):
     * 1. PERFORM 1000-READ-ACCOUNT-FILE
     * 2. PERFORM 2000-GET-DISCLOSURE-GROUP
     * 3. PERFORM 3000-CALCULATE-INTEREST
     * 4. PERFORM 4000-POST-INTEREST-TRANSACTION
     * 5. PERFORM 5000-UPDATE-ACCOUNT-BALANCE
     */
    public void calculateInterest(Long accountId) {
        
        // 1. Read account (PERFORM 1000-READ-ACCOUNT-FILE)
        Account account = accountRepository.findById(accountId)
            .orElseThrow(() -> new ResourceNotFoundException("Account not found"));
        
        if (!"Y".equals(account.getAccountStatus())) {
            log.debug("Skipping inactive account: {}", account.getAccountId());
            return;
        }
        
        // 2. Get disclosure group (PERFORM 2000-GET-DISCLOSURE-GROUP)
        DisclosureGroup disclosureGroup = disclosureGroupRepository
            .findByGroupId(account.getDisclosureGroupId())
            .orElseThrow(() -> new ResourceNotFoundException("Disclosure group not found"));
        
        // 3. Calculate interest (PERFORM 3000-CALCULATE-INTEREST)
        // COBOL: COMPUTE WS-INTEREST-AMT = ACCT-CURR-BAL * WS-APR / 12 / 100
        BigDecimal currentBalance = account.getCurrentBalance();
        BigDecimal annualPercentageRate = disclosureGroup.getAnnualPercentageRate();
        
        // Preserve exact COBOL calculation order and precision
        BigDecimal monthlyRate = annualPercentageRate
            .divide(BigDecimal.valueOf(12), 10, RoundingMode.HALF_UP)
            .divide(BigDecimal.valueOf(100), 10, RoundingMode.HALF_UP);
        
        BigDecimal interestAmount = currentBalance.multiply(monthlyRate)
            .setScale(2, RoundingMode.HALF_UP);
        
        if (interestAmount.compareTo(BigDecimal.ZERO) <= 0) {
            log.debug("No interest due for account: {}", account.getAccountId());
            return;
        }
        
        // 4. Post interest transaction (PERFORM 4000-POST-INTEREST-TRANSACTION)
        Transaction interestTransaction = Transaction.builder()
            .accountId(account.getAccountId())
            .cardNumber("0000000000000000")  // System-generated transaction
            .transactionTypeCode("07")  // Interest Charge
            .transactionCategoryCode(7000)  // Finance charge category
            .transactionSource("SYSTEM")
            .transactionDescription("Monthly Interest Charge")
            .transactionAmount(interestAmount)
            .merchantId("000000000000000")
            .merchantName("CARDDEMO SYSTEM")
            .transactionTimestamp(LocalDateTime.now())
            .build();
        
        transactionRepository.save(interestTransaction);
        
        // 5. Update account balance (PERFORM 5000-UPDATE-ACCOUNT-BALANCE)
        BigDecimal newBalance = account.getCurrentBalance().add(interestAmount);
        account.setCurrentBalance(newBalance);
        accountRepository.save(account);
        
        log.info("Interest calculated: account={}, amount={}, newBalance={}",
            account.getAccountId(), interestAmount, newBalance);
    }
}
```

#### Batch Job Scheduling

**Kubernetes CronJob** for batch execution:

```yaml
apiVersion: batch/v1
kind: CronJob
metadata:
  name: transaction-posting-job
  namespace: carddemo
spec:
  schedule: "0 23 * * *"  # Daily at 11:00 PM
  jobTemplate:
    spec:
      template:
        spec:
          containers:
          - name: batch-job
            image: carddemo:latest
            command:
              - java
              - -jar
              - /app/carddemo.jar
              - --spring.batch.job.names=transactionPostingJob
              - --spring.profiles.active=prod
            env:
            - name: SPRING_DATASOURCE_URL
              valueFrom:
                configMapKeyRef:
                  name: carddemo-config
                  key: database.url
            - name: SPRING_DATASOURCE_PASSWORD
              valueFrom:
                secretKeyRef:
                  name: carddemo-secrets
                  key: database.password
          restartPolicy: OnFailure
```

---

## Deployment Architecture

### Kubernetes Deployment Model

The application is deployed as Docker containers orchestrated by Kubernetes on AWS EKS.

#### Kubernetes Architecture

```
┌──────────────────────────────────────────────────────────────────┐
│                       AWS Cloud (us-east-1)                      │
│                                                                  │
│  ┌────────────────────────────────────────────────────────────┐ │
│  │                  EKS Cluster (carddemo)                    │ │
│  │                                                            │ │
│  │  ┌──────────────────────────────────────────────────────┐ │ │
│  │  │               Namespace: carddemo                    │ │ │
│  │  │                                                      │ │ │
│  │  │  ┌────────────────────────────────────────────────┐ │ │ │
│  │  │  │         Ingress (AWS Load Balancer)           │ │ │ │
│  │  │  │  - HTTPS termination (TLS 1.3)                │ │ │ │
│  │  │  │  - Path-based routing                         │ │ │ │
│  │  │  └──────────────────┬─────────────────────────────┘ │ │ │
│  │  │                     │                               │ │ │
│  │  │  ┌──────────────────▼─────────────────────────────┐ │ │ │
│  │  │  │         Service: carddemo-service             │ │ │ │
│  │  │  │  - Type: ClusterIP                            │ │ │ │
│  │  │  │  - Port: 8080                                 │ │ │ │
│  │  │  └──────────────────┬─────────────────────────────┘ │ │ │
│  │  │                     │                               │ │ │
│  │  │  ┌──────────────────▼─────────────────────────────┐ │ │ │
│  │  │  │    Deployment: carddemo (replicas: 3)        │ │ │ │
│  │  │  │                                               │ │ │ │
│  │  │  │  ┌─────────────┐  ┌─────────────┐  ┌────────┐│ │ │ │
│  │  │  │  │  Pod 1      │  │  Pod 2      │  │ Pod 3  ││ │ │ │
│  │  │  │  │ (Running)   │  │ (Running)   │  │(Running)││ │ │ │
│  │  │  │  │             │  │             │  │        ││ │ │ │
│  │  │  │  │ Container:  │  │ Container:  │  │Container││ │ │ │
│  │  │  │  │ carddemo    │  │ carddemo    │  │carddemo││ │ │ │
│  │  │  │  │ Java 21     │  │ Java 21     │  │Java 21 ││ │ │ │
│  │  │  │  │ Spring Boot │  │ Spring Boot │  │Spring  ││ │ │ │
│  │  │  │  │             │  │             │  │Boot    ││ │ │ │
│  │  │  │  │ Resources:  │  │ Resources:  │  │Resources││ │ │ │
│  │  │  │  │ 512Mi RAM   │  │ 512Mi RAM   │  │512Mi   ││ │ │ │
│  │  │  │  │ 500m CPU    │  │ 500m CPU    │  │500m CPU││ │ │ │
│  │  │  │  └─────────────┘  └─────────────┘  └────────┘│ │ │ │
│  │  │  └───────────────────────────────────────────────┘ │ │ │
│  │  │                                                    │ │ │
│  │  │  ┌───────────────────────────────────────────────┐ │ │ │
│  │  │  │      ConfigMap: carddemo-config              │ │ │ │
│  │  │  │  - application.yml (externalized config)     │ │ │ │
│  │  │  └───────────────────────────────────────────────┘ │ │ │
│  │  │                                                    │ │ │
│  │  │  ┌───────────────────────────────────────────────┐ │ │ │
│  │  │  │      Secret: carddemo-secrets                │ │ │ │
│  │  │  │  - Database credentials                      │ │ │ │
│  │  │  │  - JWT secret key                            │ │ │ │
│  │  │  └───────────────────────────────────────────────┘ │ │ │
│  │  │                                                    │ │ │
│  │  │  ┌───────────────────────────────────────────────┐ │ │ │
│  │  │  │  HorizontalPodAutoscaler (HPA)               │ │ │ │
│  │  │  │  - Min replicas: 3                           │ │ │ │
│  │  │  │  - Max replicas: 10                          │ │ │ │
│  │  │  │  - Target CPU: 70%                           │ │ │ │
│  │  │  │  - Target Memory: 80%                        │ │ │ │
│  │  │  └───────────────────────────────────────────────┘ │ │ │
│  │  └──────────────────────────────────────────────────┘ │ │
│  └────────────────────────────────────────────────────────┘ │
│                                                              │
│  ┌────────────────────────────────────────────────────────┐ │
│  │        AWS RDS PostgreSQL 15+ (Multi-AZ)               │ │
│  │  - Instance: db.r6g.xlarge                             │ │
│  │  - Storage: 100 GB gp3 SSD                             │ │
│  │  - Encryption: AWS KMS                                 │ │
│  │  - Backup: 7-day retention                             │ │
│  └────────────────────────────────────────────────────────┘ │
└──────────────────────────────────────────────────────────────┘
```

#### Docker Container Image

**Multi-Stage Dockerfile**:

```dockerfile
# Stage 1: Build
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN mvn clean package -DskipTests

# Stage 2: Runtime
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Create non-root user for security
RUN addgroup -S appgroup && adduser -S appuser -G appgroup

# Copy JAR from build stage
COPY --from=build /app/target/carddemo-*.jar app.jar

# Change ownership to non-root user
RUN chown -R appuser:appgroup /app

# Switch to non-root user
USER appuser

# Expose application port
EXPOSE 8080

# Health check
HEALTHCHECK --interval=30s --timeout=3s --start-period=60s --retries=3 \
  CMD wget --quiet --tries=1 --spider http://localhost:8080/actuator/health || exit 1

# Run application
ENTRYPOINT ["java", \
    "-XX:+UseContainerSupport", \
    "-XX:MaxRAMPercentage=75.0", \
    "-XX:+UseG1GC", \
    "-XX:+UseStringDeduplication", \
    "-jar", "app.jar"]
```

**Container Image Characteristics**:
- **Base Image**: Eclipse Temurin 21 (official OpenJDK distribution)
- **Size**: ~350 MB (compressed)
- **Security**: Non-root user, no shell access
- **JVM Flags**: Container-aware, G1 GC, 75% max heap
- **Health Check**: Built-in health endpoint polling

#### Kubernetes Deployment Manifest

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: carddemo
  namespace: carddemo
  labels:
    app: carddemo
    version: v1.0.0
spec:
  replicas: 3
  strategy:
    type: RollingUpdate
    rollingUpdate:
      maxSurge: 1
      maxUnavailable: 0
  selector:
    matchLabels:
      app: carddemo
  template:
    metadata:
      labels:
        app: carddemo
        version: v1.0.0
    spec:
      containers:
      - name: carddemo
        image: 123456789012.dkr.ecr.us-east-1.amazonaws.com/carddemo:1.0.0
        imagePullPolicy: IfNotPresent
        ports:
        - containerPort: 8080
          name: http
          protocol: TCP
        env:
        - name: SPRING_PROFILES_ACTIVE
          value: "prod"
        - name: SPRING_DATASOURCE_URL
          valueFrom:
            configMapKeyRef:
              name: carddemo-config
              key: database.url
        - name: SPRING_DATASOURCE_USERNAME
          valueFrom:
            secretKeyRef:
              name: carddemo-secrets
              key: database.username
        - name: SPRING_DATASOURCE_PASSWORD
          valueFrom:
            secretKeyRef:
              name: carddemo-secrets
              key: database.password
        - name: JWT_SECRET
          valueFrom:
            secretKeyRef:
              name: carddemo-secrets
              key: jwt.secret
        resources:
          requests:
            memory: "512Mi"
            cpu: "500m"
          limits:
            memory: "1Gi"
            cpu: "1000m"
        livenessProbe:
          httpGet:
            path: /actuator/health/liveness
            port: 8080
          initialDelaySeconds: 30
          periodSeconds: 10
          timeoutSeconds: 3
          failureThreshold: 3
        readinessProbe:
          httpGet:
            path: /actuator/health/readiness
            port: 8080
          initialDelaySeconds: 20
          periodSeconds: 5
          timeoutSeconds: 3
          failureThreshold: 3
        securityContext:
          runAsNonRoot: true
          runAsUser: 1000
          allowPrivilegeEscalation: false
          readOnlyRootFilesystem: false
          capabilities:
            drop:
            - ALL
      restartPolicy: Always
```

#### Service and Ingress

**Service Manifest** (ClusterIP):

```yaml
apiVersion: v1
kind: Service
metadata:
  name: carddemo-service
  namespace: carddemo
spec:
  type: ClusterIP
  selector:
    app: carddemo
  ports:
  - name: http
    port: 8080
    targetPort: 8080
    protocol: TCP
```

**Ingress Manifest** (AWS Load Balancer):

```yaml
apiVersion: networking.k8s.io/v1
kind: Ingress
metadata:
  name: carddemo-ingress
  namespace: carddemo
  annotations:
    kubernetes.io/ingress.class: alb
    alb.ingress.kubernetes.io/scheme: internet-facing
    alb.ingress.kubernetes.io/target-type: ip
    alb.ingress.kubernetes.io/listen-ports: '[{"HTTP": 80}, {"HTTPS": 443}]'
    alb.ingress.kubernetes.io/ssl-redirect: '443'
    alb.ingress.kubernetes.io/certificate-arn: arn:aws:acm:us-east-1:123456789012:certificate/...
spec:
  rules:
  - host: carddemo.example.com
    http:
      paths:
      - path: /
        pathType: Prefix
        backend:
          service:
            name: carddemo-service
            port:
              number: 8080
```

#### Horizontal Pod Autoscaling

```yaml
apiVersion: autoscaling/v2
kind: HorizontalPodAutoscaler
metadata:
  name: carddemo-hpa
  namespace: carddemo
spec:
  scaleTargetRef:
    apiVersion: apps/v1
    kind: Deployment
    name: carddemo
  minReplicas: 3
  maxReplicas: 10
  metrics:
  - type: Resource
    resource:
      name: cpu
      target:
        type: Utilization
        averageUtilization: 70
  - type: Resource
    resource:
      name: memory
      target:
        type: Utilization
        averageUtilization: 80
  behavior:
    scaleDown:
      stabilizationWindowSeconds: 300
      policies:
      - type: Percent
        value: 50
        periodSeconds: 60
    scaleUp:
      stabilizationWindowSeconds: 0
      policies:
      - type: Percent
        value: 100
        periodSeconds: 30
      - type: Pods
        value: 2
        periodSeconds: 30
      selectPolicy: Max
```

---

## Component Interaction Flows

### Request Flow: POST /api/v1/transactions

This flow diagram illustrates how a transaction posting request flows through all architectural layers.

```
┌─────────────┐
│   Client    │
└──────┬──────┘
       │ 1. POST /api/v1/transactions
       │    Authorization: Bearer <jwt-token>
       │    {
       │      "cardNumber": "4111111111111111",
       │      "transactionAmount": 100.00,
       │      "merchantName": "AMAZON.COM"
       │    }
       ▼
┌─────────────────────────────────────────────┐
│     JwtAuthenticationFilter                 │
│  - Extract and validate JWT token           │
│  - Set SecurityContext with authentication  │
└──────┬──────────────────────────────────────┘
       │ 2. Authenticated request
       ▼
┌─────────────────────────────────────────────┐
│     TransactionController                   │
│  @PostMapping("/api/v1/transactions")       │
│  @PreAuthorize("isAuthenticated()")         │
│  - Validate request DTO (Bean Validation)   │
│  - Delegate to service layer                │
└──────┬──────────────────────────────────────┘
       │ 3. transactionService.postTransaction(request)
       ▼
┌─────────────────────────────────────────────┐
│     TransactionService                      │
│  @Service @Transactional                    │
│  - Begin transaction                        │
└──────┬──────────────────────────────────────┘
       │
       │ 4. cardXrefRepository.findByCardNumber(...)
       ▼
┌─────────────────────────────────────────────┐
│     CardXrefRepository                      │
│  - Execute SELECT query                     │
│  - Return CardXref entity                   │
└──────┬──────────────────────────────────────┘
       │ 5. CardXref entity
       ▼
┌─────────────────────────────────────────────┐
│     TransactionService (continued)          │
│  - Resolve account ID from card xref        │
└──────┬──────────────────────────────────────┘
       │
       │ 6. accountRepository.findById(accountId)
       ▼
┌─────────────────────────────────────────────┐
│     AccountRepository                       │
│  - Execute SELECT query                     │
│  - Return Account entity                    │
└──────┬──────────────────────────────────────┘
       │ 7. Account entity
       ▼
┌─────────────────────────────────────────────┐
│     TransactionService (continued)          │
│  - Validate business rules:                 │
│    * Account is active                      │
│    * Transaction amount is valid            │
│    * Credit limit not exceeded              │
│  - Create Transaction entity                │
│  - Calculate new account balance            │
└──────┬──────────────────────────────────────┘
       │
       │ 8. transactionRepository.save(transaction)
       ▼
┌─────────────────────────────────────────────┐
│     TransactionRepository                   │
│  - Execute INSERT query                     │
│  - Return saved Transaction entity          │
└──────┬──────────────────────────────────────┘
       │ 9. Transaction entity (with ID)
       ▼
┌─────────────────────────────────────────────┐
│     TransactionService (continued)          │
│  - Update account balance                   │
└──────┬──────────────────────────────────────┘
       │
       │ 10. accountRepository.save(account)
       ▼
┌─────────────────────────────────────────────┐
│     AccountRepository                       │
│  - Execute UPDATE query                     │
│  - Return updated Account entity            │
└──────┬──────────────────────────────────────┘
       │ 11. Account entity (updated)
       ▼
┌─────────────────────────────────────────────┐
│     TransactionService (continued)          │
│  - Commit transaction                       │
│  - Map Transaction entity to DTO            │
│  - Return TransactionResponse               │
└──────┬──────────────────────────────────────┘
       │ 12. TransactionResponse DTO
       ▼
┌─────────────────────────────────────────────┐
│     TransactionController                   │
│  - Wrap response in ResponseEntity          │
│  - Return HTTP 200 OK                       │
└──────┬──────────────────────────────────────┘
       │ 13. HTTP 200 OK
       │     {
       │       "transactionId": 12345,
       │       "accountId": 1001,
       │       "cardNumber": "****-****-****-1111",
       │       "transactionAmount": 100.00,
       │       "transactionTimestamp": "2024-01-15T14:30:00"
       │     }
       ▼
┌─────────────┐
│   Client    │
└─────────────┘
```

### Data Flow: COBOL to Java Mapping

This diagram shows how data structures transform from COBOL copybooks to Java entities.

```
COBOL Copybook (CVTRA05Y.cpy)                 Java Entity (Transaction.java)
┌─────────────────────────────────────┐       ┌──────────────────────────────────────┐
│ 01 TRAN-RECORD.                     │       │ @Entity                              │
│   05 TRAN-ID PIC 9(16).             │  ───> │ @Table(name = "transaction")         │
│   05 TRAN-CARD-NUM PIC 9(16).       │       │ public class Transaction {           │
│   05 TRAN-TYPE-CD PIC X(02).        │       │                                      │
│   05 TRAN-CAT-CD PIC 9(04).         │       │   @Id                                │
│   05 TRAN-SOURCE PIC X(10).         │       │   @GeneratedValue                    │
│   05 TRAN-DESC PIC X(100).          │       │   private Long transactionId;        │
│   05 TRAN-AMT PIC S9(09)V99 COMP-3. │       │                                      │
│   05 TRAN-MERCHANT-ID PIC 9(15).    │       │   @Column(length = 16)               │
│   05 TRAN-MERCHANT-NAME PIC X(50).  │       │   private String cardNumber;         │
│   05 TRAN-MERCHANT-CITY PIC X(50).  │       │                                      │
│   05 TRAN-MERCHANT-ZIP PIC X(10).   │       │   @Column(length = 2)                │
│   05 TRAN-ORIG-TS PIC X(26).        │       │   private String transactionTypeCode;│
│   05 TRAN-PROC-TS PIC X(26).        │       │                                      │
└─────────────────────────────────────┘       │   private Integer categoryCode;      │
                                              │                                      │
COBOL File I/O                                │   @Column(precision = 11, scale = 2) │
┌─────────────────────────────────────┐       │   private BigDecimal amount;         │
│ EXEC CICS READ                      │       │                                      │
│   FILE('TRANFILE')                  │  ───> │   private String merchantName;       │
│   RIDFLD(WS-TRAN-KEY)               │       │                                      │
│   INTO(TRAN-RECORD)                 │       │   private LocalDateTime timestamp;   │
│ END-EXEC.                           │       │ }                                    │
└─────────────────────────────────────┘       └──────────────────────────────────────┘
                                                             │
                                                             │ JPA Repository
                                                             ▼
                                              ┌──────────────────────────────────────┐
                                              │ public interface                     │
                                              │   TransactionRepository              │
                                              │   extends JpaRepository<...> {       │
                                              │                                      │
                                              │   Optional<Transaction> findById(...);│
                                              │   List<Transaction> findByAccountId();│
                                              │ }                                    │
                                              └──────────────────────────────────────┘
                                                             │
                                                             │ Generated SQL
                                                             ▼
                                              ┌──────────────────────────────────────┐
                                              │ SELECT t.transaction_id,             │
                                              │        t.card_number,                │
                                              │        t.transaction_amount,         │
                                              │        ...                           │
                                              │ FROM transaction t                   │
                                              │ WHERE t.transaction_id = ?           │
                                              └──────────────────────────────────────┘
```

---

## Technology Decisions

### Decision 1: Monolithic vs. Microservices

**Decision**: Layered cloud-native monolith  
**Alternatives Considered**: Microservices architecture  
**Rationale**:

1. **Functional Equivalence**: The legacy COBOL/CICS application is a single integrated system. Maintaining a monolith preserves the original architecture's characteristics.
2. **Transaction Management**: Many operations span multiple entities (e.g., posting a transaction updates account balance, transaction history, and category balances). Distributed transactions across microservices would add significant complexity.
3. **Minimal Change Discipline**: Decomposing into microservices would require redesigning business process flows and data ownership boundaries, violating the "minimal changes" constraint.
4. **Operational Simplicity**: A monolith reduces operational overhead during initial migration (single deployment, simpler monitoring, fewer network calls).
5. **Future Evolution**: The monolithic design does not preclude future microservices decomposition if business requirements warrant it.

**Trade-offs**:
- **Advantages**: Simpler transaction management, easier debugging, faster development velocity
- **Disadvantages**: Harder to scale individual components, longer deployment times, potential for tight coupling

---

### Decision 2: PostgreSQL vs. Other Databases

**Decision**: PostgreSQL 15+ as the sole database  
**Alternatives Considered**: MySQL, Oracle Database, Amazon DynamoDB  
**Rationale**:

1. **Relational Model Match**: VSAM KSDS files with primary and alternate indexes map naturally to PostgreSQL tables with indexes.
2. **ACID Compliance**: PostgreSQL provides full ACID transaction support, matching COBOL/CICS SYNCPOINT semantics.
3. **Numeric Precision**: PostgreSQL NUMERIC type provides exact decimal arithmetic for financial calculations (preserves COBOL COMP-3 precision).
4. **Feature Set**: Advanced features include JSON support (for future extensibility), full-text search, and window functions.
5. **AWS Integration**: Amazon RDS for PostgreSQL provides managed service with Multi-AZ, automated backups, and encryption.
6. **Open Source**: No vendor lock-in, strong community support, no licensing costs.

**Trade-offs**:
- **Advantages**: Mature, battle-tested, excellent Spring Data JPA support
- **Disadvantages**: Requires careful index design for optimal performance, vertical scaling limits

---

### Decision 3: Spring Batch vs. Custom Batch Framework

**Decision**: Spring Batch 5.x for batch processing  
**Alternatives Considered**: Apache Camel, Quartz Scheduler with custom code, AWS Batch  
**Rationale**:

1. **Chunk-Oriented Processing**: Spring Batch's chunk pattern (reader → processor → writer) maps cleanly to COBOL batch job structures.
2. **Transaction Management**: Built-in support for commit intervals, rollback on errors, and restartability.
3. **Job Orchestration**: JobLauncher and JobRepository provide job scheduling, monitoring, and restart capabilities.
4. **Error Handling**: Skip and retry policies replicate COBOL error handling patterns.
5. **Spring Ecosystem Integration**: Seamless integration with Spring Boot, Spring Data JPA, and Spring Security.

**Trade-offs**:
- **Advantages**: Production-proven, declarative configuration, extensive monitoring hooks
- **Disadvantages**: Learning curve for developers unfamiliar with Spring Batch concepts

---

### Decision 4: JWT vs. Session-Based Authentication

**Decision**: Stateless JWT-based authentication  
**Alternatives Considered**: Session-based authentication with Redis  
**Rationale**:

1. **Stateless Architecture**: JWTs eliminate the need for server-side session storage, enabling horizontal scaling without session affinity.
2. **Cloud-Native**: Stateless authentication aligns with 12-Factor App principles (no sticky sessions required).
3. **Kubernetes Friendly**: Pods can be killed and recreated without losing user sessions.
4. **Performance**: No database lookup required to validate tokens (signature verification is sufficient).
5. **API-First Design**: RESTful APIs are inherently stateless; JWT complements this design.

**Trade-offs**:
- **Advantages**: Scalable, no session store dependency, distributed systems friendly
- **Disadvantages**: Cannot revoke tokens before expiration, larger request size (token in header)

---

### Decision 5: Flyway vs. Liquibase

**Decision**: Flyway for database migrations  
**Alternatives Considered**: Liquibase, manual SQL scripts  
**Rationale**:

1. **Simplicity**: Flyway uses plain SQL scripts (easier for COBOL developers to understand).
2. **Version Control**: Migration scripts are versioned and tracked in Git.
3. **Idempotency**: Flyway ensures migrations run exactly once (checksum validation).
4. **Spring Boot Integration**: First-class support with `spring-boot-starter-flyway`.
5. **Rollback Strategy**: Rollback scripts can be created for production deployments.

**Trade-offs**:
- **Advantages**: Simple, SQL-based, excellent tooling support
- **Disadvantages**: Less powerful than Liquibase for complex database refactorings

---

## Performance Considerations

### Response Time Targets

| Operation | Target (95th Percentile) | Strategy |
|-----------|--------------------------|----------|
| Account inquiry (GET /accounts/{id}) | <200ms | Indexed lookups, connection pooling |
| Transaction list (GET /transactions?accountId=X) | <300ms | Pagination, indexed queries |
| Transaction posting (POST /transactions) | <500ms | Batch commits, optimistic locking |
| Payment processing (POST /payments) | <500ms | Asynchronous processing for long operations |
| Report generation (GET /reports) | <2000ms | Async job submission, caching |

### Database Performance Optimizations

1. **Connection Pooling** (HikariCP):
   ```yaml
   spring:
     datasource:
       hikari:
         maximum-pool-size: 20
         minimum-idle: 5
         connection-timeout: 30000
         idle-timeout: 600000
         max-lifetime: 1800000
   ```

2. **Index Strategy**:
   - Primary keys on all tables (B-tree indexes)
   - Foreign key indexes for JOIN operations
   - Composite indexes for multi-column queries
   - Partial indexes for filtered queries (e.g., `WHERE account_status = 'Y'`)

3. **Query Optimization**:
   - Use `@Query` annotations for complex queries
   - Fetch only required columns (projection queries)
   - Use `@EntityGraph` to avoid N+1 query problems
   - Enable Hibernate query plan caching

4. **Caching**:
   ```java
   @Cacheable("transactionTypes")
   public List<TransactionType> getAllTransactionTypes() {
       return transactionTypeRepository.findAll();
   }
   ```

### JVM Tuning for Java 21

```bash
java -XX:+UseContainerSupport \
     -XX:MaxRAMPercentage=75.0 \
     -XX:+UseG1GC \
     -XX:MaxGCPauseMillis=200 \
     -XX:+UseStringDeduplication \
     -XX:+HeapDumpOnOutOfMemoryError \
     -XX:HeapDumpPath=/app/logs/heapdump.hprof \
     -jar carddemo.jar
```

**Key Flags**:
- `-XX:+UseContainerSupport`: JVM detects container memory limits
- `-XX:MaxRAMPercentage=75.0`: Use 75% of container memory for heap
- `-XX:+UseG1GC`: Garbage-First GC (low latency)
- `-XX:MaxGCPauseMillis=200`: Target max GC pause of 200ms
- `-XX:+UseStringDeduplication`: Reduce memory footprint for duplicate strings

---

## Scalability and Resilience

### Horizontal Scaling Strategy

**Scaling Model**: Kubernetes Horizontal Pod Autoscaler (HPA)

- **Minimum Replicas**: 3 (high availability)
- **Maximum Replicas**: 10 (cost control)
- **Scale-Up Trigger**: CPU > 70% or Memory > 80%
- **Scale-Down Trigger**: CPU < 30% for 5 minutes
- **Scale-Up Rate**: Add 2 pods per 30 seconds (max 100% increase)
- **Scale-Down Rate**: Remove 50% of pods per 60 seconds (gradual)

**Stateless Design**:
- No server-side session state
- All data persisted in PostgreSQL
- Idempotent API endpoints (safe to retry)

### Resilience Patterns

**1. Database Connection Resilience**:

```yaml
spring:
  datasource:
    hikari:
      connection-test-query: SELECT 1
      validation-timeout: 5000
```

**2. Circuit Breaker** (Future Enhancement):

```java
@CircuitBreaker(name = "account-service", fallbackMethod = "getAccountFallback")
public AccountResponse getAccount(Long id) {
    return accountService.getAccountById(id);
}

public AccountResponse getAccountFallback(Long id, Exception ex) {
    log.error("Circuit breaker activated for account {}", id, ex);
    return AccountResponse.builder()
        .message("Service temporarily unavailable")
        .build();
}
```

**3. Graceful Shutdown**:

```yaml
server:
  shutdown: graceful

spring:
  lifecycle:
    timeout-per-shutdown-phase: 30s
```

**4. Health Probes**:

- **Liveness Probe**: `/actuator/health/liveness` (restart if fails)
- **Readiness Probe**: `/actuator/health/readiness` (remove from load balancer if fails)

### Disaster Recovery

**Backup Strategy**:
- **RDS Automated Backups**: Daily snapshots with 7-day retention
- **Point-in-Time Recovery**: Restore to any point in the last 7 days
- **Cross-Region Replication**: RDS read replica in secondary region

**Recovery Time Objective (RTO)**: <1 hour  
**Recovery Point Objective (RPO)**: <15 minutes

---

## Monitoring and Observability

### Metrics (Prometheus + Grafana)

**Application Metrics**:
- JVM metrics (heap, GC, threads)
- HTTP metrics (request rate, latency, errors)
- Database connection pool metrics
- Custom business metrics (transaction count, payment success rate)

**Prometheus Endpoint**: `/actuator/prometheus`

### Logging (ELK Stack)

**Log Format**: JSON (structured logging)

```json
{
  "timestamp": "2024-01-15T14:30:00.123Z",
  "level": "INFO",
  "thread": "http-nio-8080-exec-1",
  "logger": "com.aws.carddemo.service.TransactionService",
  "message": "Transaction posted successfully",
  "context": {
    "transactionId": 12345,
    "accountId": 1001,
    "amount": 100.00,
    "userId": "USER0001"
  }
}
```

**Sensitive Data Masking**: Card numbers, SSNs, passwords masked in all logs

### Distributed Tracing (Future Enhancement)

**OpenTelemetry Integration**:
- Trace HTTP requests across all layers
- Correlate logs with traces
- Visualize request flows in Jaeger UI

---

## Conclusion

The modernized CardDemo application successfully transforms a legacy mainframe COBOL/CICS/VSAM system into a cloud-native Java 21 application. The layered monolithic architecture maintains functional equivalence with the legacy system while adopting modern cloud-native patterns, Spring Boot best practices, and Kubernetes deployment strategies.

**Key Achievements**:
✅ Complete functional parity with legacy COBOL application  
✅ Cloud-native deployment on Kubernetes (AWS EKS)  
✅ RESTful API layer replacing 3270 terminal screens  
✅ PostgreSQL database replacing VSAM/DB2/IMS  
✅ Spring Batch replacing JCL batch jobs  
✅ JWT-based security replacing RACF  
✅ PCI-DSS compliant data protection  
✅ Comprehensive monitoring and observability  
✅ Horizontal scalability with HPA  
✅ Production-ready with proper error handling, logging, and resilience  

**Future Enhancements** (Out of Scope for Initial Migration):
- Microservices decomposition (if business requirements warrant)
- Event-driven architecture with Kafka
- GraphQL API layer
- Machine learning fraud detection
- Real-time analytics dashboards

This architecture documentation serves as the authoritative guide for developers, operators, and stakeholders working with the modernized CardDemo application.

---

**Document Version**: 1.0.0  
**Last Updated**: January 2025  
**Authors**: AWS CardDemo Modernization Team  
**Related Documents**:
- [Modernization Guide](./modernization.md)
- [API Specification](./api-specification.md)
- [Data Migration Guide](./data-migration.md)
- [Deployment Guide](./deployment-guide.md)
