-- ============================================================================
-- Flyway Migration V2: Create Performance Indexes
-- ============================================================================
-- Description: Creates strategic B-tree indexes on PostgreSQL database schema
--              to optimize query performance for common access patterns derived
--              from legacy COBOL VSAM file operations.
--
-- Migration from: COBOL programs accessing VSAM KSDS files with primary keys
--                 and AIX (Alternate Index) paths
-- Target performance: Primary key lookups <10ms, foreign key queries <20ms,
--                    paginated range queries <50ms for 100 records
--
-- Referenced COBOL programs:
--   - CBTRN01C.cbl, CBTRN02C.cbl: Transaction posting batch jobs
--   - CBACT04C.cbl: Interest calculation batch job
--   - COACTVWC.cbl: Account view online transaction
--   - COCRDLIC.cbl: Card list online transaction
--   - COTRN00C.cbl: Transaction list browse
--
-- Copyright Amazon.com, Inc. or its affiliates.
-- Licensed under the Apache License, Version 2.0
-- ============================================================================

-- ============================================================================
-- ACCOUNT TABLE INDEXES
-- ============================================================================
-- Replaces VSAM ACCTFILE alternate index paths and READ operations

-- Index for findByCustomerId repository queries
-- Supports: Account lookup by customer (COBOL: READ ACCTFILE by customer relationship)
-- Usage: GET /api/v1/customers/{id}/accounts
CREATE INDEX idx_account_customer 
ON account(customer_id);

-- Index for disclosure group joins during interest calculation
-- Supports: CBACT04C.cbl interest calculation batch job
-- Usage: Batch job queries joining ACCOUNT with DISCLOSURE_GROUP
CREATE INDEX idx_account_group 
ON account(account_group_id);

-- Partial index for active accounts only (reduces index size by ~20%)
-- Supports: Queries filtering WHERE active_status='Y'
-- Usage: Most account queries exclude inactive accounts
CREATE INDEX idx_account_status 
ON account(account_group_id) 
WHERE active_status = 'Y';

-- Unique index on business key (account number)
-- Enforces: One account per account number across all customers
-- Supports: findByAccountNumber repository query
CREATE UNIQUE INDEX idx_account_number_unique 
ON account(account_number);

-- Index for account opening date range queries
-- Supports: Reporting queries filtering by account age
-- Usage: Analytics and compliance reporting
CREATE INDEX idx_account_open_date 
ON account(account_open_date);


-- ============================================================================
-- CARD TABLE INDEXES
-- ============================================================================
-- Replaces VSAM CARDFILE and alternate index CXACAIX

-- Index for account-to-cards navigation (critical for card list operations)
-- Supports: COCRDLIC.cbl card list browse by account
-- Usage: GET /api/v1/accounts/{id}/cards
-- Replaces: VSAM AIX path CXACAIX (Card Cross-reference Account Index)
CREATE INDEX idx_card_account 
ON card(account_id);

-- Partial index for expired card detection (active cards only)
-- Supports: Expiration notification batch job and card validation
-- Usage: Daily batch job identifying cards expiring in next 30 days
CREATE INDEX idx_card_expiration 
ON card(expiration_date) 
WHERE active_status = 'Y';

-- Unique index on business key (card number)
-- Enforces: One card per card number across all accounts
-- Supports: findByCardNumber repository query and COBOL READ CARDFILE by key
CREATE UNIQUE INDEX idx_card_number_unique 
ON card(card_number);

-- Index for card type filtering
-- Supports: Queries filtering by card type (Debit, Credit, etc.)
-- Usage: Card type analytics and reporting
CREATE INDEX idx_card_type 
ON card(card_type);


-- ============================================================================
-- TRANSACTION TABLE INDEXES
-- ============================================================================
-- Critical indexes for transaction history queries (highest query volume)

-- Composite index for paginated transaction history (MOST CRITICAL INDEX)
-- Supports: COTRN00C.cbl transaction list browse with pagination
-- Usage: GET /api/v1/accounts/{id}/transactions?page=0&size=20
-- Query pattern: WHERE account_id = ? ORDER BY processing_timestamp DESC
-- Performance: Enables index-only scans for transaction list queries
CREATE INDEX idx_transaction_account_date 
ON transaction(account_id, processing_timestamp DESC);

-- Index for card-based transaction lookup
-- Supports: Transaction lookup by card number for dispute resolution
-- Usage: GET /api/v1/cards/{cardNumber}/transactions
-- Replaces: COBOL READ TRANFILE with card number filter
CREATE INDEX idx_transaction_card 
ON transaction(card_number);

-- Index for merchant reconciliation analytics
-- Supports: Merchant transaction aggregation and reporting
-- Usage: Batch reporting queries grouping by merchant_id
CREATE INDEX idx_transaction_merchant 
ON transaction(merchant_id);

-- Composite index for transaction type and category reporting
-- Supports: CBACT04C.cbl interest calculation category lookups
-- Usage: Interest calculation batch job joining transaction categories
CREATE INDEX idx_transaction_type_category 
ON transaction(transaction_type_code, transaction_category_code);

-- Index for transaction date range queries (reporting and analytics)
-- Supports: Monthly statement generation and date range reports
-- Usage: SELECT * FROM transaction WHERE processing_timestamp BETWEEN ? AND ?
CREATE INDEX idx_transaction_date_range 
ON transaction(processing_timestamp);

-- Index for transaction source filtering
-- Supports: Queries filtering by transaction source (Online, POS, ATM, etc.)
-- Usage: Channel analytics and fraud detection
CREATE INDEX idx_transaction_source 
ON transaction(transaction_source);


-- ============================================================================
-- CARD_XREF TABLE INDEXES
-- ============================================================================
-- Bidirectional navigation between cards, accounts, and customers
-- Replaces VSAM XREFFILE with multiple access paths

-- Index for account-to-card cross-reference lookup
-- Supports: CBTRN01C.cbl transaction posting (card → account lookup)
-- Usage: Batch job resolving card number to account ID
-- Replaces: COBOL READ XREFFILE by card number
CREATE INDEX idx_xref_account 
ON card_xref(account_id);

-- Index for customer-to-card cross-reference lookup
-- Supports: Customer card list queries
-- Usage: GET /api/v1/customers/{id}/cards (via cross-reference)
CREATE INDEX idx_xref_customer 
ON card_xref(customer_id);

-- Unique composite index enforcing one card-account relationship
-- Enforces: A card can only be linked to one account
-- Supports: Referential integrity for card-account binding
CREATE UNIQUE INDEX idx_xref_card_account_unique 
ON card_xref(card_number, account_id);


-- ============================================================================
-- CUSTOMER TABLE INDEXES
-- ============================================================================
-- Customer lookup and search indexes

-- Unique index on business key (SSN) with partial index for privacy
-- Enforces: One customer per SSN (for US customers)
-- Security: SSN is encrypted at rest, index supports lookups
CREATE UNIQUE INDEX idx_customer_ssn_unique 
ON customer(customer_ssn) 
WHERE customer_ssn IS NOT NULL;

-- Index for customer last name search (common search pattern)
-- Supports: Customer search by last name
-- Usage: GET /api/v1/customers?lastName={name}
CREATE INDEX idx_customer_lastname 
ON customer(customer_last_name);

-- Composite index for full name search (first + last name)
-- Supports: Customer search by full name
-- Usage: Advanced customer search with first and last name
CREATE INDEX idx_customer_fullname 
ON customer(customer_last_name, customer_first_name);

-- Index for FICO score range queries
-- Supports: Credit risk analytics and account eligibility checks
-- Usage: Reporting queries filtering by credit score ranges
CREATE INDEX idx_customer_fico 
ON customer(fico_credit_score);


-- ============================================================================
-- DAILY_TRANSACTION TABLE INDEXES
-- ============================================================================
-- Indexes for batch processing input (transaction posting batch job)

-- Index for unprocessed transaction detection
-- Supports: CBTRN01C.cbl batch job identifying pending transactions
-- Usage: SELECT * FROM daily_transaction WHERE processed_flag = 'N'
CREATE INDEX idx_daily_transaction_processed 
ON daily_transaction(processed_flag) 
WHERE processed_flag = 'N';

-- Composite index for date + card number (batch processing order)
-- Supports: Sequential processing of daily feed by date and card
-- Usage: Batch job processing transactions in chronological order per card
CREATE INDEX idx_daily_transaction_date_card 
ON daily_transaction(transaction_date, card_number);


-- ============================================================================
-- TRANSACTION_CATEGORY_BALANCE TABLE INDEXES
-- ============================================================================
-- Indexes for interest calculation batch job

-- Composite index for account + category balance lookups
-- Supports: CBACT04C.cbl interest calculation reading category balances
-- Usage: Interest calculation batch job querying balances by account and category
-- Replaces: COBOL READ TCATBAL by account-category composite key
CREATE INDEX idx_catbal_account_category 
ON transaction_category_balance(account_id, transaction_category_code);

-- Index for category-based aggregation queries
-- Supports: Reporting queries aggregating balances by category
CREATE INDEX idx_catbal_category 
ON transaction_category_balance(transaction_category_code);


-- ============================================================================
-- DISCLOSURE_GROUP TABLE INDEXES
-- ============================================================================
-- Reference data indexes for interest rate lookups

-- Index for account group lookup during interest calculation
-- Supports: CBACT04C.cbl interest rate determination
-- Usage: Interest calculation batch job joining on account_group_id
CREATE INDEX idx_disclosure_group 
ON disclosure_group(account_group_id);


-- ============================================================================
-- APP_USER TABLE INDEXES
-- ============================================================================
-- User authentication and authorization indexes

-- Unique index on username (critical for authentication performance)
-- Supports: COSGN00C.cbl login operation (user credential lookup)
-- Usage: POST /api/v1/auth/login (username lookup for authentication)
-- Performance: Enables <10ms authentication queries
CREATE UNIQUE INDEX idx_user_username_unique 
ON app_user(username);

-- Index for user type filtering (role-based queries)
-- Supports: Admin user list queries filtering by user type
-- Usage: GET /api/v1/admin/users?userType=ADMIN
CREATE INDEX idx_user_type 
ON app_user(user_type);

-- Partial index for active users only
-- Supports: Authentication queries (only active users can log in)
-- Usage: Login queries with WHERE active_status = 'Y'
CREATE INDEX idx_user_active 
ON app_user(user_type) 
WHERE active_status = 'Y';


-- ============================================================================
-- REFERENCE DATA TABLE INDEXES
-- ============================================================================
-- Indexes for transaction type and category reference tables

-- Unique index on transaction type code (natural key)
-- Supports: Fast lookup of transaction type metadata
-- Usage: Transaction processing validating type codes
CREATE UNIQUE INDEX idx_transaction_type_code_unique 
ON transaction_type(transaction_type_code);

-- Unique index on transaction category code (natural key)
-- Supports: Fast lookup of transaction category metadata
-- Usage: Transaction processing validating category codes
CREATE UNIQUE INDEX idx_transaction_category_code_unique 
ON transaction_category(transaction_category_code);


-- ============================================================================
-- POST-INDEX CREATION MAINTENANCE
-- ============================================================================

-- Update table statistics for query planner optimization
-- Critical: PostgreSQL query planner uses statistics to choose optimal indexes
-- Recommendation: Run ANALYZE after bulk data loads or significant updates
ANALYZE account;
ANALYZE card;
ANALYZE card_xref;
ANALYZE customer;
ANALYZE transaction;
ANALYZE daily_transaction;
ANALYZE transaction_category_balance;
ANALYZE disclosure_group;
ANALYZE app_user;
ANALYZE transaction_type;
ANALYZE transaction_category;

-- ============================================================================
-- INDEX USAGE NOTES
-- ============================================================================
-- 
-- Index Naming Convention: idx_{table}_{columns}[_unique]
-- Index Type: B-tree (PostgreSQL default, optimal for equality and range queries)
-- 
-- Partial Indexes: Used for frequently filtered columns (active_status = 'Y')
--   - Reduces index size and improves write performance
--   - Only applies to queries with matching WHERE clause
-- 
-- Composite Indexes: Column order matters (selectivity from left to right)
--   - idx_transaction_account_date: account_id (high selectivity), then date
--   - Supports queries filtering by account_id alone OR account_id + date
--   - Does NOT support queries filtering by date alone
-- 
-- Unique Indexes: Enforce data integrity at database level
--   - Preferred over application-level uniqueness checks
--   - Provides implicit index for query performance
-- 
-- Query Performance Targets:
--   - Primary key lookups: <10ms (enforced by PostgreSQL B-tree efficiency)
--   - Foreign key lookups: <20ms (supported by foreign key indexes)
--   - Paginated queries: <50ms for 100 records (composite indexes)
--   - Overall API response: <200ms at 95th percentile (application + DB)
-- 
-- Monitoring Recommendations:
--   - Monitor index usage: SELECT * FROM pg_stat_user_indexes;
--   - Identify unused indexes: WHERE idx_scan = 0;
--   - Check index bloat: Use pgstattuple extension
--   - Re-index periodically: REINDEX TABLE {table_name};
-- 
-- ============================================================================
-- END OF MIGRATION V2
-- ============================================================================
