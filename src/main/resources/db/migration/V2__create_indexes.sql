-- ============================================================================
-- Flyway Migration V2: Create Additional Performance Indexes
-- ============================================================================
-- Description: Creates additional strategic B-tree indexes to complement the
--              indexes already created in V1__create_tables.sql
--
-- NOTE: V1__create_tables.sql already creates 33 indexes on core tables.
--       This migration adds ONLY non-duplicate indexes for specific use cases.
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
-- ACCOUNT TABLE ADDITIONAL INDEXES
-- ============================================================================

-- Partial index for active accounts with group filtering
-- Supports: Interest calculation queries filtering active accounts by group
-- Usage: WHERE active_status = 'A' AND group_id = ?
-- NOTE: V1 already has idx_account_active_status and idx_account_group_id
--       This composite partial index optimizes the common combined query
CREATE INDEX idx_account_active_group 
ON account(group_id, active_status) 
WHERE active_status = 'A';

-- Index for account opening date range queries
-- Supports: Reporting queries filtering by account age
-- Usage: Analytics and compliance reporting (WHERE open_date BETWEEN ? AND ?)
CREATE INDEX idx_account_open_date 
ON account(open_date);


-- ============================================================================
-- CARD TABLE ADDITIONAL INDEXES
-- ============================================================================
-- NOTE: V1 already has idx_card_number, idx_card_account_id, idx_card_active_status

-- Partial index for active card expiration monitoring
-- Supports: Expiration notification batch job and card validation
-- Usage: Daily batch job identifying cards expiring soon (WHERE active_status = 'A' AND expiration_date < ?)
-- Reduces index size by excluding inactive cards (~30% reduction)
CREATE INDEX idx_card_active_expiration 
ON card(expiration_date) 
WHERE active_status = 'A';


-- ============================================================================
-- TRANSACTION TABLE ADDITIONAL INDEXES
-- ============================================================================
-- NOTE: V1 already has idx_tran_id, idx_tran_account_id, idx_tran_card_number,
--       idx_tran_orig_timestamp, idx_tran_classification

-- Composite index for paginated transaction history (CRITICAL FOR API PERFORMANCE)
-- Supports: COTRN00C.cbl transaction list browse with pagination
-- Usage: GET /api/v1/accounts/{id}/transactions?page=0&size=20
-- Query pattern: WHERE account_id = ? ORDER BY original_timestamp DESC
-- Performance: Enables index-only scans, covers both filter and sort
-- V1's idx_tran_account_id doesn't cover the ORDER BY, this composite index does
CREATE INDEX idx_transaction_account_timestamp 
ON transaction(account_id, original_timestamp DESC);

-- Index for merchant reconciliation analytics
-- Supports: Merchant transaction aggregation and reporting
-- Usage: Batch reporting queries grouping by merchant_id (WHERE merchant_id = ?)
CREATE INDEX idx_transaction_merchant 
ON transaction(merchant_id);

-- Index for transaction source filtering
-- Supports: Queries filtering by transaction source (Online, POS, ATM, etc.)
-- Usage: Channel analytics and fraud detection (WHERE tran_source = ?)
CREATE INDEX idx_transaction_source 
ON transaction(tran_source);


-- ============================================================================
-- CARD_XREF TABLE ADDITIONAL INDEXES
-- ============================================================================
-- NOTE: V1 already has idx_xref_card_number, idx_xref_cust_id, idx_xref_acct_id
--       and unique constraint uq_xref_card_number

-- No additional indexes needed for card_xref - V1 provides complete coverage


-- ============================================================================
-- CUSTOMER TABLE ADDITIONAL INDEXES
-- ============================================================================
-- NOTE: V1 already has idx_customer_cust_id, idx_customer_ssn, idx_customer_name

-- Index for FICO score range queries
-- Supports: Credit risk analytics and account eligibility checks
-- Usage: Reporting queries filtering by credit score ranges (WHERE fico_credit_score BETWEEN ? AND ?)
CREATE INDEX idx_customer_fico 
ON customer(fico_credit_score);


-- ============================================================================
-- DAILY_TRANSACTION TABLE ADDITIONAL INDEXES
-- ============================================================================
-- NOTE: V1 already has idx_daily_tran_id, idx_daily_tran_card_number,
--       idx_daily_tran_status, idx_daily_tran_timestamp

-- Composite index for chronological batch processing per card
-- Supports: Sequential processing of daily feed by timestamp and card
-- Usage: Batch job processing transactions in order (WHERE card_number = ? ORDER BY original_timestamp)
-- Complements V1's separate single-column indexes with a composite for better performance
CREATE INDEX idx_daily_transaction_card_timestamp 
ON daily_transaction(card_number, original_timestamp);


-- ============================================================================
-- TRANSACTION_CATEGORY_BALANCE TABLE ADDITIONAL INDEXES
-- ============================================================================
-- NOTE: V1 already has idx_cat_bal_account_id and idx_cat_bal_composite
--       which covers (account_id, type_code, category_code)

-- No additional indexes needed - V1's composite index provides full coverage


-- ============================================================================
-- DISCLOSURE_GROUP TABLE ADDITIONAL INDEXES
-- ============================================================================
-- NOTE: V1 already has idx_disclosure_group_id and idx_disclosure_composite

-- No additional indexes needed - V1 provides full coverage


-- ============================================================================
-- APP_USER TABLE ADDITIONAL INDEXES
-- ============================================================================
-- NOTE: V1 already has idx_user_username, idx_user_type, idx_user_locked

-- No additional indexes needed - V1 provides full coverage


-- ============================================================================
-- REFERENCE DATA TABLE ADDITIONAL INDEXES
-- ============================================================================
-- NOTE: V1 already has idx_tran_type_code, idx_tran_cat_type_code, idx_tran_cat_composite
--       with unique constraints on natural keys

-- No additional indexes needed - V1 provides full coverage


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
