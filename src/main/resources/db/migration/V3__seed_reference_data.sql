-- =============================================================================
-- Flyway Migration V3: Seed Reference Data
-- =============================================================================
-- Description: Populates static reference data tables from legacy VSAM ASCII 
--              export files. This migration loads transaction types, transaction
--              categories, and disclosure group interest rate configurations.
--
-- Source Files:
--   - app/data/ASCII/trantype.txt    (7 transaction type codes)
--   - app/data/ASCII/trancatg.txt    (18 transaction category codes)
--   - app/data/ASCII/discgrp.txt     (51 APR interest rate configurations)
--
-- Data Preservation: All data is migrated exactly as defined in legacy system,
--                    preserving leading zeros and exact descriptions.
--
-- Caching: Reference data is cached at application level via Spring @Cacheable
--          annotations to minimize database queries.
--
-- Migrated from: AWS CardDemo COBOL Application
-- Migration Date: 2024
-- =============================================================================

-- Start transaction to ensure atomic reference data population
BEGIN;

-- =============================================================================
-- 1. TRANSACTION TYPE REFERENCE DATA
-- =============================================================================
-- Purpose: Defines the 7 core transaction types used throughout the system
-- Source: app/data/ASCII/trantype.txt
-- Format: 2-digit code + description (right-padded, trailing '00000000' removed)
-- Usage: Foreign key referenced by transaction_category and transactions

INSERT INTO transaction_type (type_code, type_description) VALUES
('01', 'Purchase'),
('02', 'Payment'),
('03', 'Credit'),
('04', 'Authorization'),
('05', 'Refund'),
('06', 'Reversal'),
('07', 'Adjustment');

-- =============================================================================
-- 2. TRANSACTION CATEGORY REFERENCE DATA
-- =============================================================================
-- Purpose: Defines 18 transaction categories organized under transaction types
-- Source: app/data/ASCII/trancatg.txt
-- Format: 6-digit code (first 2 = type, last 4 = category) + description
-- Usage: Foreign key referenced by transactions, used in interest calculations
-- Hierarchy:
--   Type 01 (Purchase): 5 categories (0001-0005)
--   Type 02 (Payment): 3 categories (0001-0003)
--   Type 03 (Credit): 3 categories (0001-0003)
--   Type 04 (Authorization): 3 categories (0001-0003)
--   Type 05 (Refund): 1 category (0001)
--   Type 06 (Reversal): 2 categories (0001-0002)
--   Type 07 (Adjustment): 1 category (0001)

-- Type 01: Purchase Categories
INSERT INTO transaction_category (transaction_type_code, category_code, category_description) VALUES
('01', '0001', 'Regular Sales Draft'),
('01', '0002', 'Regular Cash Advance'),
('01', '0003', 'Convenience Check Debit'),
('01', '0004', 'ATM Cash Advance'),
('01', '0005', 'Interest Amount');

-- Type 02: Payment Categories
INSERT INTO transaction_category (transaction_type_code, category_code, category_description) VALUES
('02', '0001', 'Cash payment'),
('02', '0002', 'Electronic payment'),
('02', '0003', 'Check payment');

-- Type 03: Credit Categories
INSERT INTO transaction_category (transaction_type_code, category_code, category_description) VALUES
('03', '0001', 'Credit to Account'),
('03', '0002', 'Credit to Purchase balance'),
('03', '0003', 'Credit to Cash balance');

-- Type 04: Authorization Categories
INSERT INTO transaction_category (transaction_type_code, category_code, category_description) VALUES
('04', '0001', 'Zero dollar authorization'),
('04', '0002', 'Online purchase authorization'),
('04', '0003', 'Travel booking authorization');

-- Type 05: Refund Categories
INSERT INTO transaction_category (transaction_type_code, category_code, category_description) VALUES
('05', '0001', 'Refund credit');

-- Type 06: Reversal Categories
INSERT INTO transaction_category (transaction_type_code, category_code, category_description) VALUES
('06', '0001', 'Fraud reversal'),
('06', '0002', 'Non-fraud reversal');

-- Type 07: Adjustment Categories
INSERT INTO transaction_category (transaction_type_code, category_code, category_description) VALUES
('07', '0001', 'Sales draft credit adjustment');

-- =============================================================================
-- 3. DISCLOSURE GROUP APR INTEREST RATE CONFIGURATIONS
-- =============================================================================
-- Purpose: Defines APR interest rates for type-category combinations by account group
-- Source: app/data/ASCII/discgrp.txt
-- Format: Fixed-width (group ID at 0-9, type at 10-11, category at 12-15, 
--         rate at 16-20 as 5-digit integer / 100 = percentage)
-- Account Groups:
--   - 'A': Standard interest rates (1.50%-2.50% for most categories)
--   - 'DEFAULT': Default interest rates (same as group A)
--   - 'ZEROAPR': Promotional 0% APR for all categories
-- Usage: Referenced by CBACT04C interest calculation batch job
-- Total Records: 51 (17 type-category combinations × 3 groups)

-- ================================
-- Group A: Standard Interest Rates
-- ================================
INSERT INTO disclosure_group (account_group_id, transaction_type_code, transaction_category_code, interest_rate) VALUES
-- Purchase Categories (Type 01)
('A', '01', '0001', 1.50),  -- Regular Sales Draft: 1.50% APR
('A', '01', '0002', 2.50),  -- Regular Cash Advance: 2.50% APR
('A', '01', '0003', 2.50),  -- Convenience Check Debit: 2.50% APR
('A', '01', '0004', 2.50),  -- ATM Cash Advance: 2.50% APR
-- Payment Categories (Type 02): 0% interest (payments reduce balance)
('A', '02', '0001', 0.00),  -- Cash payment
('A', '02', '0002', 0.00),  -- Electronic payment
('A', '02', '0003', 0.00),  -- Check payment
-- Credit Categories (Type 03): 0% interest (credits reduce balance)
('A', '03', '0001', 0.00),  -- Credit to Account
('A', '03', '0002', 0.00),  -- Credit to Purchase balance
('A', '03', '0003', 0.00),  -- Credit to Cash balance
-- Authorization Categories (Type 04): 1.50% APR
('A', '04', '0001', 1.50),  -- Zero dollar authorization
('A', '04', '0002', 1.50),  -- Online purchase authorization
('A', '04', '0003', 1.50),  -- Travel booking authorization
-- Refund Categories (Type 05): 1.50% APR
('A', '05', '0001', 1.50),  -- Refund credit
-- Reversal Categories (Type 06): 1.50% APR
('A', '06', '0001', 1.50),  -- Fraud reversal
('A', '06', '0002', 1.50),  -- Non-fraud reversal
-- Adjustment Categories (Type 07): 1.50% APR
('A', '07', '0001', 1.50);  -- Sales draft credit adjustment

-- ====================================
-- Group DEFAULT: Default Interest Rates
-- ====================================
INSERT INTO disclosure_group (account_group_id, transaction_type_code, transaction_category_code, interest_rate) VALUES
-- Purchase Categories (Type 01)
('DEFAULT', '01', '0001', 1.50),  -- Regular Sales Draft: 1.50% APR
('DEFAULT', '01', '0002', 2.50),  -- Regular Cash Advance: 2.50% APR
('DEFAULT', '01', '0003', 2.50),  -- Convenience Check Debit: 2.50% APR
('DEFAULT', '01', '0004', 2.50),  -- ATM Cash Advance: 2.50% APR
-- Payment Categories (Type 02): 0% interest
('DEFAULT', '02', '0001', 0.00),  -- Cash payment
('DEFAULT', '02', '0002', 0.00),  -- Electronic payment
('DEFAULT', '02', '0003', 0.00),  -- Check payment
-- Credit Categories (Type 03): 0% interest
('DEFAULT', '03', '0001', 0.00),  -- Credit to Account
('DEFAULT', '03', '0002', 0.00),  -- Credit to Purchase balance
('DEFAULT', '03', '0003', 0.00),  -- Credit to Cash balance
-- Authorization Categories (Type 04): 1.50% APR
('DEFAULT', '04', '0001', 1.50),  -- Zero dollar authorization
('DEFAULT', '04', '0002', 1.50),  -- Online purchase authorization
('DEFAULT', '04', '0003', 1.50),  -- Travel booking authorization
-- Refund Categories (Type 05): 1.50% APR
('DEFAULT', '05', '0001', 1.50),  -- Refund credit
-- Reversal Categories (Type 06): 1.50% APR
('DEFAULT', '06', '0001', 1.50),  -- Fraud reversal
('DEFAULT', '06', '0002', 1.50),  -- Non-fraud reversal
-- Adjustment Categories (Type 07): 0% APR
('DEFAULT', '07', '0001', 0.00);  -- Sales draft credit adjustment

-- ====================================
-- Group ZEROAPR: Promotional 0% APR
-- ====================================
INSERT INTO disclosure_group (account_group_id, transaction_type_code, transaction_category_code, interest_rate) VALUES
-- All categories have 0% promotional APR
-- Purchase Categories (Type 01)
('ZEROAPR', '01', '0001', 0.00),  -- Regular Sales Draft
('ZEROAPR', '01', '0002', 0.00),  -- Regular Cash Advance
('ZEROAPR', '01', '0003', 0.00),  -- Convenience Check Debit
('ZEROAPR', '01', '0004', 0.00),  -- ATM Cash Advance
-- Payment Categories (Type 02)
('ZEROAPR', '02', '0001', 0.00),  -- Cash payment
('ZEROAPR', '02', '0002', 0.00),  -- Electronic payment
('ZEROAPR', '02', '0003', 0.00),  -- Check payment
-- Credit Categories (Type 03)
('ZEROAPR', '03', '0001', 0.00),  -- Credit to Account
('ZEROAPR', '03', '0002', 0.00),  -- Credit to Purchase balance
('ZEROAPR', '03', '0003', 0.00),  -- Credit to Cash balance
-- Authorization Categories (Type 04)
('ZEROAPR', '04', '0001', 0.00),  -- Zero dollar authorization
('ZEROAPR', '04', '0002', 0.00),  -- Online purchase authorization
('ZEROAPR', '04', '0003', 0.00),  -- Travel booking authorization
-- Refund Categories (Type 05)
('ZEROAPR', '05', '0001', 0.00),  -- Refund credit
-- Reversal Categories (Type 06)
('ZEROAPR', '06', '0001', 0.00),  -- Fraud reversal
('ZEROAPR', '06', '0002', 0.00),  -- Non-fraud reversal
-- Adjustment Categories (Type 07)
('ZEROAPR', '07', '0001', 0.00);  -- Sales draft credit adjustment

-- Commit transaction
COMMIT;

-- =============================================================================
-- Migration Complete
-- =============================================================================
-- Summary:
--   - 7 transaction types inserted
--   - 18 transaction categories inserted
--   - 51 disclosure group APR configurations inserted (17 per group × 3 groups)
--
-- Validation Queries:
--   SELECT COUNT(*) FROM transaction_type;           -- Expected: 7
--   SELECT COUNT(*) FROM transaction_category;       -- Expected: 18
--   SELECT COUNT(*) FROM disclosure_group;           -- Expected: 51
--
-- Foreign Key Relationships:
--   transaction_category.transaction_type_code → transaction_type.type_code
--   disclosure_group.transaction_type_code → transaction_type.type_code
--   disclosure_group.transaction_category_code → transaction_category.category_code
-- =============================================================================
