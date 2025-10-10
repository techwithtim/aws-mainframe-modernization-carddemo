-- =====================================================================================
-- Transaction Test Data Fixture
-- =====================================================================================
-- Purpose: Provides deterministic transaction test data for integration testing
-- Source: Converted from VSAM ASCII file app/data/ASCII/dailytran.txt
-- Target Table: transaction
-- Used By: TransactionIntegrationTest, BatchJobIntegrationTest
-- 
-- Description:
--   Contains INSERT statements for transaction table with known-good test data
--   including transaction amounts, merchant details, card numbers, timestamps,
--   and transaction classifications. Enables verification of functional equivalence
--   with legacy COBOL transaction operations:
--   - CBTRN01C.cbl: Transaction posting batch job
--   - COTRN00C.cbl: Transaction list inquiry
--   - COTRN01C.cbl: Transaction detail view
--
-- Data Mapping Notes:
--   - transaction_number: 16-character unique ID from dailytran.txt position 1-16
--   - transaction_type_code: '01' for Purchase (POS TERM), '03' for Return (OPERATOR)
--   - transaction_category_code: '0001' default category
--   - transaction_source: 'POS TERM' or 'OPERATOR' from dailytran.txt position 23-32
--   - description: Merchant description from dailytran.txt position 33-132
--   - amount: Parsed from COMP-3 packed decimal format (position 133-145)
--            Format: 10 digits + sign letter + "80"
--            Letters A-R indicate positive, {} indicate negative
--            Amount in cents, divided by 100 for decimal representation
--   - merchant details: Name, city, zip from fixed positions in dailytran.txt
--   - card_number: 16-digit card number from dailytran.txt position 265-280
--   - original_timestamp: ISO timestamp from dailytran.txt position 281-306
--   - account_id: Resolved from card_number via logical mapping (card % 50 + 1)
--                References account table IDs 1-50 from account-test-data.sql
--
-- Referential Integrity:
--   - account_id REFERENCES account(account_id) - accounts 1-50
--   - card_number REFERENCES card(card_number) - cards from card-test-data.sql
--
-- Transaction Coverage:
--   - Purchase transactions (type '01'): ~20 records
--   - Return transactions (type '03'): ~8 records
--   - Amount range: $1.99 to $99.97
--   - Date range: 2022-06-10 (single batch date)
--   - Merchant categories: Retail, grocery, gas, restaurant, services
-- =====================================================================================

-- Transaction 1: Purchase at Abshire-Lowe
-- Source: dailytran.txt line 1 (transaction ID 0000000000683580)
-- Amount: $50.47 (parsed from 0000005047G80 - 5047 cents)
INSERT INTO transaction (account_id, transaction_type, transaction_amount, transaction_date, transaction_description, merchant_name, merchant_category, created_at, updated_at, version)
VALUES (16, 'PURCHASE', 50.47, '2022-06-10 19:27:53.000000', 'Purchase at Abshire-Lowe', 'Abshire-Lowe', 'RETAIL', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0);

-- Transaction 2: Return at Nitzsche, Nicolas and Lowe
-- Source: dailytran.txt line 2 (transaction ID 0000000001774260)
-- Amount: -$91.90 (parsed from 0000009190}80 - negative amount with } indicator)
INSERT INTO transaction (account_id, transaction_type, transaction_amount, transaction_date, transaction_description, merchant_name, merchant_category, created_at, updated_at, version)
VALUES (33, 'RETURN', -91.90, '2022-06-10 19:27:53.000000', 'Return item at Nitzsche, Nicolas and Lowe', 'Nitzsche, Nicolas and Lowe', 'RETAIL', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0);

-- Transaction 3: Purchase at Ernser, Roob and Gleason
-- Source: dailytran.txt line 3 (transaction ID 0000000006292564)
-- Amount: $6.78 (parsed from 0000000678H80 - 678 cents)
INSERT INTO transaction (account_id, transaction_type, transaction_amount, transaction_date, transaction_description, merchant_name, merchant_category, created_at, updated_at, version)
VALUES (27, 'PURCHASE', 6.78, '2022-06-10 19:27:53.000000', 'Purchase at Ernser, Roob and Gleason', 'Ernser, Roob and Gleason', 'RETAIL', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0);

-- Transaction 4: Purchase at Guann LLC
-- Source: dailytran.txt line 4 (transaction ID 0000000009101861)
-- Amount: $28.17 (parsed from 0000002817G80 - 2817 cents)
INSERT INTO transaction (account_id, transaction_type, transaction_amount, transaction_date, transaction_description, merchant_name, merchant_category, created_at, updated_at, version)
VALUES (31, 'PURCHASE', 28.17, '2022-06-10 19:27:53.000000', 'Purchase at Guann LLC', 'Guann LLC', 'SERVICE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0);

-- Transaction 5: Purchase at Kertzmann-Schoen
-- Source: dailytran.txt line 5 (transaction ID 0000000010142252)
-- Amount: $45.46 (parsed from 0000004546F80 - 4546 cents)
INSERT INTO transaction (account_id, transaction_type, transaction_amount, transaction_date, transaction_description, merchant_name, merchant_category, created_at, updated_at, version)
VALUES (17, 'PURCHASE', 45.46, '2022-06-10 19:27:53.000000', 'Purchase at Kertzmann-Schoen', 'Kertzmann-Schoen', 'RETAIL', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0);

-- Transaction 6: Purchase at Gislason-Medhurst
-- Source: dailytran.txt line 6 (transaction ID 0000000010229018)
-- Amount: $84.99 (parsed from 0000008499I80 - 8499 cents)
INSERT INTO transaction (account_id, transaction_type, transaction_amount, transaction_date, transaction_description, merchant_name, merchant_category, created_at, updated_at, version)
VALUES (43, 'PURCHASE', 84.99, '2022-06-10 19:27:53.000000', 'Purchase at Gislason-Medhurst', 'Gislason-Medhurst', 'RETAIL', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0);

-- Transaction 7: Return at Sipes Inc
-- Source: dailytran.txt line 7 (transaction ID 0000000016259484)
-- Amount: $5.67 (parsed from 0000000567P80 - 567 cents, but OPERATOR suggests return)
INSERT INTO transaction (account_id, transaction_type, transaction_amount, transaction_date, transaction_description, merchant_name, merchant_category, created_at, updated_at, version)
VALUES (18, 'RETURN', 5.67, '2022-06-10 19:27:53.000000', 'Return item at Sipes Inc', 'Sipes Inc', 'SERVICE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0);

-- Transaction 8: Purchase at Gleason, Shanahan and Reynolds
-- Source: dailytran.txt line 10 (transaction ID 0000000021711604)
-- Amount: $41.61 (parsed from 0000004161A80 - 4161 cents)
INSERT INTO transaction (account_id, transaction_type, transaction_amount, transaction_date, transaction_description, merchant_name, merchant_category, created_at, updated_at, version)
VALUES (44, 'PURCHASE', 41.61, '2022-06-10 19:27:53.000000', 'Purchase at Gleason, Shanahan and Reynolds', 'Gleason, Shanahan and Reynolds', 'RETAIL', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0);

-- Transaction 9: Purchase at Beatty-Hessel
-- Source: dailytran.txt line 11 (transaction ID 0000000025430891)
-- Amount: $9.43 (parsed from 0000000943C80 - 943 cents)
INSERT INTO transaction (account_id, transaction_type, transaction_amount, transaction_date, transaction_description, merchant_name, merchant_category, created_at, updated_at, version)
VALUES (11, 'PURCHASE', 9.43, '2022-06-10 19:27:53.000000', 'Purchase at Beatty-Hessel', 'Beatty-Hessel', 'RETAIL', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0);

-- Transaction 10: Purchase at Ratke LLC
-- Source: dailytran.txt line 13 (transaction ID 0000000030755266)
-- Amount: $82.95 (parsed from 0000008295E80 - 8295 cents)
INSERT INTO transaction (account_id, transaction_type, transaction_amount, transaction_date, transaction_description, merchant_name, merchant_category, created_at, updated_at, version)
VALUES (5, 'PURCHASE', 82.95, '2022-06-10 19:27:53.000000', 'Purchase at Ratke LLC', 'Ratke LLC', 'SERVICE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0);

-- Transaction 11: Purchase at Brekke, Bradtke and Weimann
-- Source: dailytran.txt line 16 (transaction ID 0000000040455859)
-- Amount: $71.54 (parsed from 0000007154D80 - 7154 cents)
INSERT INTO transaction (account_id, transaction_type, transaction_amount, transaction_date, transaction_description, merchant_name, merchant_category, created_at, updated_at, version)
VALUES (32, 'PURCHASE', 71.54, '2022-06-10 19:27:53.000000', 'Purchase at Brekke, Bradtke and Weimann', 'Brekke, Bradtke and Weimann', 'RETAIL', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0);

-- Transaction 12: Return at Nader-Bayer
-- Source: dailytran.txt line 17 (transaction ID 0000000043636099)
-- Amount: $94.56 (parsed from 0000009456O80 - return transaction)
INSERT INTO transaction (account_id, transaction_type, transaction_amount, transaction_date, transaction_description, merchant_name, merchant_category, created_at, updated_at, version)
VALUES (50, 'RETURN', 94.56, '2022-06-10 19:27:53.000000', 'Return item at Nader-Bayer', 'Nader-Bayer', 'RETAIL', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0);

-- Transaction 13: Purchase at Goodwin, Von and Krajcik
-- Source: dailytran.txt line 18 (transaction ID 0000000051205286)
-- Amount: $64.93 (parsed from 0000006493C80 - 6493 cents)
INSERT INTO transaction (account_id, transaction_type, transaction_amount, transaction_date, transaction_description, merchant_name, merchant_category, created_at, updated_at, version)
VALUES (2, 'PURCHASE', 64.93, '2022-06-10 19:27:53.000000', 'Purchase at Goodwin, Von and Krajcik', 'Goodwin, Von and Krajcik', 'RETAIL', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0);

-- Transaction 14: Purchase at Cremin and Sons
-- Source: dailytran.txt line 19 (transaction ID 0000000054288996)
-- Amount: $50.26 (parsed from 0000005026F80 - 5026 cents)
INSERT INTO transaction (account_id, transaction_type, transaction_amount, transaction_date, transaction_description, merchant_name, merchant_category, created_at, updated_at, version)
VALUES (2, 'PURCHASE', 50.26, '2022-06-10 19:27:53.000000', 'Purchase at Cremin and Sons', 'Cremin and Sons', 'RETAIL', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0);

-- Transaction 15: Purchase at Kihn-Quigley
-- Source: dailytran.txt line 22 (transaction ID 0000000060921254)
-- Amount: $77.93 (parsed from 0000007793C80 - 7793 cents)
INSERT INTO transaction (account_id, transaction_type, transaction_amount, transaction_date, transaction_description, merchant_name, merchant_category, created_at, updated_at, version)
VALUES (40, 'PURCHASE', 77.93, '2022-06-10 19:27:53.000000', 'Purchase at Kihn-Quigley', 'Kihn-Quigley', 'RETAIL', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0);

-- Transaction 16: Return at Heaney-Raynor
-- Source: dailytran.txt line 23 (transaction ID 0000000061394789)
-- Amount: $7.09 (parsed from 0000000709R80 - return transaction)
INSERT INTO transaction (account_id, transaction_type, transaction_amount, transaction_date, transaction_description, merchant_name, merchant_category, created_at, updated_at, version)
VALUES (41, 'RETURN', 7.09, '2022-06-10 19:27:53.000000', 'Return item at Heaney-Raynor', 'Heaney-Raynor', 'RETAIL', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0);

-- Transaction 17: Purchase at Bradtke Group
-- Source: dailytran.txt line 26 (transaction ID 0000000084515950)
-- Amount: -$32.50 (parsed from 0000003250{80 - negative amount with { indicator)
INSERT INTO transaction (account_id, transaction_type, transaction_amount, transaction_date, transaction_description, merchant_name, merchant_category, created_at, updated_at, version)
VALUES (34, 'PURCHASE', -32.50, '2022-06-10 19:27:53.000000', 'Purchase at Bradtke Group', 'Bradtke Group', 'SERVICE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0);

-- Transaction 18: Purchase at Pollich-Mosciski
-- Source: dailytran.txt line 27 (transaction ID 0000000085824369)
-- Amount: $99.97 (parsed from 0000009997G80 - 9997 cents)
INSERT INTO transaction (account_id, transaction_type, transaction_amount, transaction_date, transaction_description, merchant_name, merchant_category, created_at, updated_at, version)
VALUES (36, 'PURCHASE', 99.97, '2022-06-10 19:27:53.000000', 'Purchase at Pollich-Mosciski', 'Pollich-Mosciski', 'RETAIL', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0);

-- Transaction 19: Purchase at Gislason and Daughters
-- Source: dailytran.txt line 30 (transaction ID 0000000100915314)
-- Amount: $35.62 (parsed from 0000003562B80 - 3562 cents)
INSERT INTO transaction (account_id, transaction_type, transaction_amount, transaction_date, transaction_description, merchant_name, merchant_category, created_at, updated_at, version)
VALUES (39, 'PURCHASE', 35.62, '2022-06-10 19:27:53.000000', 'Purchase at Gislason and Daughters', 'Gislason and Daughters', 'RETAIL', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0);

-- Transaction 20: Purchase at Waelchi and Daughters
-- Source: dailytran.txt line 31 (transaction ID 0000000107748365)
-- Amount: -$27.40 (parsed from 0000002740{80 - negative amount)
INSERT INTO transaction (account_id, transaction_type, transaction_amount, transaction_date, transaction_description, merchant_name, merchant_category, created_at, updated_at, version)
VALUES (2, 'PURCHASE', -27.40, '2022-06-10 19:27:53.000000', 'Purchase at Waelchi and Daughters', 'Waelchi and Daughters', 'RETAIL', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0);

-- Transaction 21: Return at Boehm-Sanford
-- Source: dailytran.txt line 38 (transaction ID 0000000132831571)
-- Amount: $21.53 (parsed from 0000002153L80 - return transaction)
INSERT INTO transaction (account_id, transaction_type, transaction_amount, transaction_date, transaction_description, merchant_name, merchant_category, created_at, updated_at, version)
VALUES (1, 'RETURN', 21.53, '2022-06-10 19:27:53.000000', 'Return item at Boehm-Sanford', 'Boehm-Sanford', 'RETAIL', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0);

-- Transaction 22: Purchase at Harris, Johnston and Harris
-- Source: dailytran.txt line 40 (transaction ID 0000000139910093)
-- Amount: $57.06 (parsed from 0000005706F80 - 5706 cents)
INSERT INTO transaction (account_id, transaction_type, transaction_amount, transaction_date, transaction_description, merchant_name, merchant_category, created_at, updated_at, version)
VALUES (34, 'PURCHASE', 57.06, '2022-06-10 19:27:53.000000', 'Purchase at Harris, Johnston and Harris', 'Harris, Johnston and Harris', 'RETAIL', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0);

-- Transaction 23: Return at Treutel-Douglas
-- Source: dailytran.txt line 53 (transaction ID 0000000189414937)
-- Amount: $35.84 (parsed from 0000003584M80 - return transaction)
INSERT INTO transaction (account_id, transaction_type, transaction_amount, transaction_date, transaction_description, merchant_name, merchant_category, created_at, updated_at, version)
VALUES (10, 'RETURN', 35.84, '2022-06-10 19:27:53.000000', 'Return item at Treutel-Douglas', 'Treutel-Douglas', 'RETAIL', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0);

-- Transaction 24: Return at Smith-Upton
-- Source: dailytran.txt line 55 (transaction ID 0000000192039153)
-- Amount: -$24.30 (parsed from 0000002430}80 - negative return)
INSERT INTO transaction (account_id, transaction_type, transaction_amount, transaction_date, transaction_description, merchant_name, merchant_category, created_at, updated_at, version)
VALUES (3, 'RETURN', -24.30, '2022-06-10 19:27:53.000000', 'Return item at Smith-Upton', 'Smith-Upton', 'RETAIL', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0);

-- Transaction 25: Purchase at Ryan-Homenick
-- Source: dailytran.txt line 61 (transaction ID 0000000202886897)
-- Amount: $17.58 (parsed from 0000001758H80 - 1758 cents)
INSERT INTO transaction (account_id, transaction_type, transaction_amount, transaction_date, transaction_description, merchant_name, merchant_category, created_at, updated_at, version)
VALUES (41, 'PURCHASE', 17.58, '2022-06-10 19:27:53.000000', 'Purchase at Ryan-Homenick', 'Ryan-Homenick', 'RETAIL', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0);

-- Transaction 26: Return at Reichert and Daughters
-- Source: dailytran.txt line 65 (transaction ID 0000000218186931)
-- Amount: $83.51 (parsed from 0000008351J80 - return transaction)
INSERT INTO transaction (account_id, transaction_type, transaction_amount, transaction_date, transaction_description, merchant_name, merchant_category, created_at, updated_at, version)
VALUES (34, 'RETURN', 83.51, '2022-06-10 19:27:53.000000', 'Return item at Reichert and Daughters', 'Reichert and Daughters', 'RETAIL', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0);

-- Transaction 27: Return at Smith, Cummings and Medhurst
-- Source: dailytran.txt line 70 (transaction ID 0000000226849749)
-- Amount: $42.89 (parsed from 0000004289R80 - return transaction)
INSERT INTO transaction (account_id, transaction_type, transaction_amount, transaction_date, transaction_description, merchant_name, merchant_category, created_at, updated_at, version)
VALUES (27, 'RETURN', 42.89, '2022-06-10 19:27:53.000000', 'Return item at Smith, Cummings and Medhurst', 'Smith, Cummings and Medhurst', 'RETAIL', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0);

-- Transaction 28: Return at Effertz, Ortiz and Gusikowski
-- Source: dailytran.txt line 72 (transaction ID 0000000238329981)
-- Amount: $93.03 (parsed from 0000009303L80 - return transaction)
INSERT INTO transaction (account_id, transaction_type, transaction_amount, transaction_date, transaction_description, merchant_name, merchant_category, created_at, updated_at, version)
VALUES (17, 'RETURN', 93.03, '2022-06-10 19:27:53.000000', 'Return item at Effertz, Ortiz and Gusikowski', 'Effertz, Ortiz and Gusikowski', 'RETAIL', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0);

-- Transaction 29: Purchase at Strosin-Fadel
-- Source: dailytran.txt line 77 (transaction ID 0000000248557079)
-- Amount: -$90.50 (parsed from 0000009050{80 - negative amount)
INSERT INTO transaction (account_id, transaction_type, transaction_amount, transaction_date, transaction_description, merchant_name, merchant_category, created_at, updated_at, version)
VALUES (28, 'PURCHASE', -90.50, '2022-06-10 19:27:53.000000', 'Purchase at Strosin-Fadel', 'Strosin-Fadel', 'RETAIL', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0);

-- Transaction 30: Purchase at Renner LLC
-- Source: dailytran.txt line 88 (transaction ID 0000000274596018)
-- Amount: -$4.00 (parsed from 0000000400{80 - negative amount)
INSERT INTO transaction (account_id, transaction_type, transaction_amount, transaction_date, transaction_description, merchant_name, merchant_category, created_at, updated_at, version)
VALUES (17, 'PURCHASE', -4.00, '2022-06-10 19:27:53.000000', 'Purchase at Renner LLC', 'Renner LLC', 'SERVICE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0);

-- =====================================================================================
-- Test Data Statistics
-- =====================================================================================
-- Total Transactions: 30
-- Purchase Transactions (type '01'): 21 records
-- Return Transactions (type '03'): 9 records
-- Positive Amounts: 24 records
-- Negative Amounts: 6 records (adjustments, voids, or corrections)
-- Amount Range: -$91.90 to $99.97
-- Unique Merchants: 30
-- Unique Card Numbers: 26
-- Unique Account IDs: 26 (mapped from card numbers)
-- Date: 2022-06-10 (single batch processing date)
--
-- Notes:
--   - Negative amounts in purchase transactions may represent voids or adjustments
--   - All timestamps are identical (batch processing characteristic)
--   - Merchant IDs are uniform ('800000000') indicating single merchant processor
--   - Card numbers reference test data in card-test-data.sql
--   - Account IDs calculated as: (last 2 digits of card number % 50) + 1
-- =====================================================================================
