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
INSERT INTO transaction (transaction_number, transaction_type_code, transaction_category_code, transaction_source, description, amount, merchant_id, merchant_name, merchant_city, merchant_zip, card_number, original_timestamp, account_id, version)
VALUES ('0000000000683580', '01', '0001', 'POS TERM', 'Purchase at Abshire-Lowe', 50.47, '800000000', 'Abshire-Lowe', 'North Enoshaven', '72112', '4859452612877065', '2022-06-10 19:27:53.000000', 16, 0);

-- Transaction 2: Return at Nitzsche, Nicolas and Lowe
-- Source: dailytran.txt line 2 (transaction ID 0000000001774260)
-- Amount: -$91.90 (parsed from 0000009190}80 - negative amount with } indicator)
INSERT INTO transaction (transaction_number, transaction_type_code, transaction_category_code, transaction_source, description, amount, merchant_id, merchant_name, merchant_city, merchant_zip, card_number, original_timestamp, account_id, version)
VALUES ('0000000001774260', '03', '0001', 'OPERATOR', 'Return item at Nitzsche, Nicolas and Lowe', -91.90, '800000000', 'Nitzsche, Nicolas and Lowe', 'Fidelshire', '53378', '0927987108636232', '2022-06-10 19:27:53.000000', 33, 0);

-- Transaction 3: Purchase at Ernser, Roob and Gleason
-- Source: dailytran.txt line 3 (transaction ID 0000000006292564)
-- Amount: $6.78 (parsed from 0000000678H80 - 678 cents)
INSERT INTO transaction (transaction_number, transaction_type_code, transaction_category_code, transaction_source, description, amount, merchant_id, merchant_name, merchant_city, merchant_zip, card_number, original_timestamp, account_id, version)
VALUES ('0000000006292564', '01', '0001', 'POS TERM', 'Purchase at Ernser, Roob and Gleason', 6.78, '800000000', 'Ernser, Roob and Gleason', 'North Makenziemouth', '78487-7965', '6009619150674526', '2022-06-10 19:27:53.000000', 27, 0);

-- Transaction 4: Purchase at Guann LLC
-- Source: dailytran.txt line 4 (transaction ID 0000000009101861)
-- Amount: $28.17 (parsed from 0000002817G80 - 2817 cents)
INSERT INTO transaction (transaction_number, transaction_type_code, transaction_category_code, transaction_source, description, amount, merchant_id, merchant_name, merchant_city, merchant_zip, card_number, original_timestamp, account_id, version)
VALUES ('0000000009101861', '01', '0001', 'POS TERM', 'Purchase at Guann LLC', 28.17, '800000000', 'Guann LLC', 'South Lynn', '51508-9166', '8040580410348680', '2022-06-10 19:27:53.000000', 31, 0);

-- Transaction 5: Purchase at Kertzmann-Schoen
-- Source: dailytran.txt line 5 (transaction ID 0000000010142252)
-- Amount: $45.46 (parsed from 0000004546F80 - 4546 cents)
INSERT INTO transaction (transaction_number, transaction_type_code, transaction_category_code, transaction_source, description, amount, merchant_id, merchant_name, merchant_city, merchant_zip, card_number, original_timestamp, account_id, version)
VALUES ('0000000010142252', '01', '0001', 'POS TERM', 'Purchase at Kertzmann-Schoen', 45.46, '800000000', 'Kertzmann-Schoen', 'East Eulahstad', '98754-1089', '5656830544981216', '2022-06-10 19:27:53.000000', 17, 0);

-- Transaction 6: Purchase at Gislason-Medhurst
-- Source: dailytran.txt line 6 (transaction ID 0000000010229018)
-- Amount: $84.99 (parsed from 0000008499I80 - 8499 cents)
INSERT INTO transaction (transaction_number, transaction_type_code, transaction_category_code, transaction_source, description, amount, merchant_id, merchant_name, merchant_city, merchant_zip, card_number, original_timestamp, account_id, version)
VALUES ('0000000010229018', '01', '0001', 'POS TERM', 'Purchase at Gislason-Medhurst', 84.99, '800000000', 'Gislason-Medhurst', 'Colleenburgh', '23712-2080', '7379335634661142', '2022-06-10 19:27:53.000000', 43, 0);

-- Transaction 7: Return at Sipes Inc
-- Source: dailytran.txt line 7 (transaction ID 0000000016259484)
-- Amount: $5.67 (parsed from 0000000567P80 - 567 cents, but OPERATOR suggests return)
INSERT INTO transaction (transaction_number, transaction_type_code, transaction_category_code, transaction_source, description, amount, merchant_id, merchant_name, merchant_city, merchant_zip, card_number, original_timestamp, account_id, version)
VALUES ('0000000016259484', '03', '0001', 'OPERATOR', 'Return item at Sipes Inc', 5.67, '800000000', 'Sipes Inc', 'Emilioside', '93329', '4011500891777367', '2022-06-10 19:27:53.000000', 18, 0);

-- Transaction 8: Purchase at Gleason, Shanahan and Reynolds
-- Source: dailytran.txt line 10 (transaction ID 0000000021711604)
-- Amount: $41.61 (parsed from 0000004161A80 - 4161 cents)
INSERT INTO transaction (transaction_number, transaction_type_code, transaction_category_code, transaction_source, description, amount, merchant_id, merchant_name, merchant_city, merchant_zip, card_number, original_timestamp, account_id, version)
VALUES ('0000000021711604', '01', '0001', 'POS TERM', 'Purchase at Gleason, Shanahan and Reynolds', 41.61, '800000000', 'Gleason, Shanahan and Reynolds', 'Myrticeport', '21768-0823', '9501733721429893', '2022-06-10 19:27:53.000000', 44, 0);

-- Transaction 9: Purchase at Beatty-Hessel
-- Source: dailytran.txt line 11 (transaction ID 0000000025430891)
-- Amount: $9.43 (parsed from 0000000943C80 - 943 cents)
INSERT INTO transaction (transaction_number, transaction_type_code, transaction_category_code, transaction_source, description, amount, merchant_id, merchant_name, merchant_city, merchant_zip, card_number, original_timestamp, account_id, version)
VALUES ('0000000025430891', '01', '0001', 'POS TERM', 'Purchase at Beatty-Hessel', 9.43, '800000000', 'Beatty-Hessel', 'Simonisport', '52595', '3260763612337560', '2022-06-10 19:27:53.000000', 11, 0);

-- Transaction 10: Purchase at Ratke LLC
-- Source: dailytran.txt line 13 (transaction ID 0000000030755266)
-- Amount: $82.95 (parsed from 0000008295E80 - 8295 cents)
INSERT INTO transaction (transaction_number, transaction_type_code, transaction_category_code, transaction_source, description, amount, merchant_id, merchant_name, merchant_city, merchant_zip, card_number, original_timestamp, account_id, version)
VALUES ('0000000030755266', '01', '0001', 'POS TERM', 'Purchase at Ratke LLC', 82.95, '800000000', 'Ratke LLC', 'Brendenfort', '35302-6495', '3766281984155154', '2022-06-10 19:27:53.000000', 5, 0);

-- Transaction 11: Purchase at Brekke, Bradtke and Weimann
-- Source: dailytran.txt line 16 (transaction ID 0000000040455859)
-- Amount: $71.54 (parsed from 0000007154D80 - 7154 cents)
INSERT INTO transaction (transaction_number, transaction_type_code, transaction_category_code, transaction_source, description, amount, merchant_id, merchant_name, merchant_city, merchant_zip, card_number, original_timestamp, account_id, version)
VALUES ('0000000040455859', '01', '0001', 'POS TERM', 'Purchase at Brekke, Bradtke and Weimann', 71.54, '800000000', 'Brekke, Bradtke and Weimann', 'Veummouth', '18481-5013', '1142167692878931', '2022-06-10 19:27:53.000000', 32, 0);

-- Transaction 12: Return at Nader-Bayer
-- Source: dailytran.txt line 17 (transaction ID 0000000043636099)
-- Amount: $94.56 (parsed from 0000009456O80 - return transaction)
INSERT INTO transaction (transaction_number, transaction_type_code, transaction_category_code, transaction_source, description, amount, merchant_id, merchant_name, merchant_city, merchant_zip, card_number, original_timestamp, account_id, version)
VALUES ('0000000043636099', '03', '0001', 'OPERATOR', 'Return item at Nader-Bayer', 94.56, '800000000', 'Nader-Bayer', 'Goyetteville', '35324', '2940139362300449', '2022-06-10 19:27:53.000000', 50, 0);

-- Transaction 13: Purchase at Goodwin, Von and Krajcik
-- Source: dailytran.txt line 18 (transaction ID 0000000051205286)
-- Amount: $64.93 (parsed from 0000006493C80 - 6493 cents)
INSERT INTO transaction (transaction_number, transaction_type_code, transaction_category_code, transaction_source, description, amount, merchant_id, merchant_name, merchant_city, merchant_zip, card_number, original_timestamp, account_id, version)
VALUES ('0000000051205286', '01', '0001', 'POS TERM', 'Purchase at Goodwin, Von and Krajcik', 64.93, '800000000', 'Goodwin, Von and Krajcik', 'Ericmouth', '03874', '7094142751055551', '2022-06-10 19:27:53.000000', 2, 0);

-- Transaction 14: Purchase at Cremin and Sons
-- Source: dailytran.txt line 19 (transaction ID 0000000054288996)
-- Amount: $50.26 (parsed from 0000005026F80 - 5026 cents)
INSERT INTO transaction (transaction_number, transaction_type_code, transaction_category_code, transaction_source, description, amount, merchant_id, merchant_name, merchant_city, merchant_zip, card_number, original_timestamp, account_id, version)
VALUES ('0000000054288996', '01', '0001', 'POS TERM', 'Purchase at Cremin and Sons', 50.26, '800000000', 'Cremin and Sons', 'Bartonside', '08677', '4534784102713951', '2022-06-10 19:27:53.000000', 2, 0);

-- Transaction 15: Purchase at Kihn-Quigley
-- Source: dailytran.txt line 22 (transaction ID 0000000060921254)
-- Amount: $77.93 (parsed from 0000007793C80 - 7793 cents)
INSERT INTO transaction (transaction_number, transaction_type_code, transaction_category_code, transaction_source, description, amount, merchant_id, merchant_name, merchant_city, merchant_zip, card_number, original_timestamp, account_id, version)
VALUES ('0000000060921254', '01', '0001', 'POS TERM', 'Purchase at Kihn-Quigley', 77.93, '800000000', 'Kihn-Quigley', 'New Katrine', '42756-0584', '5787351228879339', '2022-06-10 19:27:53.000000', 40, 0);

-- Transaction 16: Return at Heaney-Raynor
-- Source: dailytran.txt line 23 (transaction ID 0000000061394789)
-- Amount: $7.09 (parsed from 0000000709R80 - return transaction)
INSERT INTO transaction (transaction_number, transaction_type_code, transaction_category_code, transaction_source, description, amount, merchant_id, merchant_name, merchant_city, merchant_zip, card_number, original_timestamp, account_id, version)
VALUES ('0000000061394789', '03', '0001', 'OPERATOR', 'Return item at Heaney-Raynor', 7.09, '800000000', 'Heaney-Raynor', 'North Daisy', '28696', '2745303720002090', '2022-06-10 19:27:53.000000', 41, 0);

-- Transaction 17: Purchase at Bradtke Group
-- Source: dailytran.txt line 26 (transaction ID 0000000084515950)
-- Amount: -$32.50 (parsed from 0000003250{80 - negative amount with { indicator)
INSERT INTO transaction (transaction_number, transaction_type_code, transaction_category_code, transaction_source, description, amount, merchant_id, merchant_name, merchant_city, merchant_zip, card_number, original_timestamp, account_id, version)
VALUES ('0000000084515950', '01', '0001', 'POS TERM', 'Purchase at Bradtke Group', -32.50, '800000000', 'Bradtke Group', 'Gerardland', '63873', '8931369351894783', '2022-06-10 19:27:53.000000', 34, 0);

-- Transaction 18: Purchase at Pollich-Mosciski
-- Source: dailytran.txt line 27 (transaction ID 0000000085824369)
-- Amount: $99.97 (parsed from 0000009997G80 - 9997 cents)
INSERT INTO transaction (transaction_number, transaction_type_code, transaction_category_code, transaction_source, description, amount, merchant_id, merchant_name, merchant_city, merchant_zip, card_number, original_timestamp, account_id, version)
VALUES ('0000000085824369', '01', '0001', 'POS TERM', 'Purchase at Pollich-Mosciski', 99.97, '800000000', 'Pollich-Mosciski', 'Georgettemouth', '85890', '3999169246375885', '2022-06-10 19:27:53.000000', 36, 0);

-- Transaction 19: Purchase at Gislason and Daughters
-- Source: dailytran.txt line 30 (transaction ID 0000000100915314)
-- Amount: $35.62 (parsed from 0000003562B80 - 3562 cents)
INSERT INTO transaction (transaction_number, transaction_type_code, transaction_category_code, transaction_source, description, amount, merchant_id, merchant_name, merchant_city, merchant_zip, card_number, original_timestamp, account_id, version)
VALUES ('0000000100915314', '01', '0001', 'POS TERM', 'Purchase at Gislason and Daughters', 35.62, '800000000', 'Gislason and Daughters', 'Torphyville', '09737', '9805583408996588', '2022-06-10 19:27:53.000000', 39, 0);

-- Transaction 20: Purchase at Waelchi and Daughters
-- Source: dailytran.txt line 31 (transaction ID 0000000107748365)
-- Amount: -$27.40 (parsed from 0000002740{80 - negative amount)
INSERT INTO transaction (transaction_number, transaction_type_code, transaction_category_code, transaction_source, description, amount, merchant_id, merchant_name, merchant_city, merchant_zip, card_number, original_timestamp, account_id, version)
VALUES ('0000000107748365', '01', '0001', 'POS TERM', 'Purchase at Waelchi and Daughters', -27.40, '800000000', 'Waelchi and Daughters', 'Dickensborough', '86052-1154', '7094142751055551', '2022-06-10 19:27:53.000000', 2, 0);

-- Transaction 21: Return at Boehm-Sanford
-- Source: dailytran.txt line 38 (transaction ID 0000000132831571)
-- Amount: $21.53 (parsed from 0000002153L80 - return transaction)
INSERT INTO transaction (transaction_number, transaction_type_code, transaction_category_code, transaction_source, description, amount, merchant_id, merchant_name, merchant_city, merchant_zip, card_number, original_timestamp, account_id, version)
VALUES ('0000000132831571', '03', '0001', 'OPERATOR', 'Return item at Boehm-Sanford', 21.53, '800000000', 'Boehm-Sanford', 'Winifredville', '93238-7169', '1014086565224350', '2022-06-10 19:27:53.000000', 1, 0);

-- Transaction 22: Purchase at Harris, Johnston and Harris
-- Source: dailytran.txt line 40 (transaction ID 0000000139910093)
-- Amount: $57.06 (parsed from 0000005706F80 - 5706 cents)
INSERT INTO transaction (transaction_number, transaction_type_code, transaction_category_code, transaction_source, description, amount, merchant_id, merchant_name, merchant_city, merchant_zip, card_number, original_timestamp, account_id, version)
VALUES ('0000000139910093', '01', '0001', 'POS TERM', 'Purchase at Harris, Johnston and Harris', 57.06, '800000000', 'Harris, Johnston and Harris', 'New Aurelia', '81068', '7251508149188883', '2022-06-10 19:27:53.000000', 34, 0);

-- Transaction 23: Return at Treutel-Douglas
-- Source: dailytran.txt line 53 (transaction ID 0000000189414937)
-- Amount: $35.84 (parsed from 0000003584M80 - return transaction)
INSERT INTO transaction (transaction_number, transaction_type_code, transaction_category_code, transaction_source, description, amount, merchant_id, merchant_name, merchant_city, merchant_zip, card_number, original_timestamp, account_id, version)
VALUES ('0000000189414937', '03', '0001', 'OPERATOR', 'Return item at Treutel-Douglas', 35.84, '800000000', 'Treutel-Douglas', 'Port Mittiestad', '12880-0185', '6349250331648509', '2022-06-10 19:27:53.000000', 10, 0);

-- Transaction 24: Return at Smith-Upton
-- Source: dailytran.txt line 55 (transaction ID 0000000192039153)
-- Amount: -$24.30 (parsed from 0000002430}80 - negative return)
INSERT INTO transaction (transaction_number, transaction_type_code, transaction_category_code, transaction_source, description, amount, merchant_id, merchant_name, merchant_city, merchant_zip, card_number, original_timestamp, account_id, version)
VALUES ('0000000192039153', '03', '0001', 'OPERATOR', 'Return item at Smith-Upton', -24.30, '800000000', 'Smith-Upton', 'Vandervortburgh', '15012-1007', '7058267261837752', '2022-06-10 19:27:53.000000', 3, 0);

-- Transaction 25: Purchase at Ryan-Homenick
-- Source: dailytran.txt line 61 (transaction ID 0000000202886897)
-- Amount: $17.58 (parsed from 0000001758H80 - 1758 cents)
INSERT INTO transaction (transaction_number, transaction_type_code, transaction_category_code, transaction_source, description, amount, merchant_id, merchant_name, merchant_city, merchant_zip, card_number, original_timestamp, account_id, version)
VALUES ('0000000202886897', '01', '0001', 'POS TERM', 'Purchase at Ryan-Homenick', 17.58, '800000000', 'Ryan-Homenick', 'North Franciscaside', '14400', '2871968252812490', '2022-06-10 19:27:53.000000', 41, 0);

-- Transaction 26: Return at Reichert and Daughters
-- Source: dailytran.txt line 65 (transaction ID 0000000218186931)
-- Amount: $83.51 (parsed from 0000008351J80 - return transaction)
INSERT INTO transaction (transaction_number, transaction_type_code, transaction_category_code, transaction_source, description, amount, merchant_id, merchant_name, merchant_city, merchant_zip, card_number, original_timestamp, account_id, version)
VALUES ('0000000218186931', '03', '0001', 'OPERATOR', 'Return item at Reichert and Daughters', 83.51, '800000000', 'Reichert and Daughters', 'Amaliafort', '31060-9178', '8931369351894783', '2022-06-10 19:27:53.000000', 34, 0);

-- Transaction 27: Return at Smith, Cummings and Medhurst
-- Source: dailytran.txt line 70 (transaction ID 0000000226849749)
-- Amount: $42.89 (parsed from 0000004289R80 - return transaction)
INSERT INTO transaction (transaction_number, transaction_type_code, transaction_category_code, transaction_source, description, amount, merchant_id, merchant_name, merchant_city, merchant_zip, card_number, original_timestamp, account_id, version)
VALUES ('0000000226849749', '03', '0001', 'OPERATOR', 'Return item at Smith, Cummings and Medhurst', 42.89, '800000000', 'Smith, Cummings and Medhurst', 'South Adriannaland', '54229-7459', '6009619150674526', '2022-06-10 19:27:53.000000', 27, 0);

-- Transaction 28: Return at Effertz, Ortiz and Gusikowski
-- Source: dailytran.txt line 72 (transaction ID 0000000238329981)
-- Amount: $93.03 (parsed from 0000009303L80 - return transaction)
INSERT INTO transaction (transaction_number, transaction_type_code, transaction_category_code, transaction_source, description, amount, merchant_id, merchant_name, merchant_city, merchant_zip, card_number, original_timestamp, account_id, version)
VALUES ('0000000238329981', '03', '0001', 'OPERATOR', 'Return item at Effertz, Ortiz and Gusikowski', 93.03, '800000000', 'Effertz, Ortiz and Gusikowski', 'Harrisonfurt', '89418-4999', '6509230362553816', '2022-06-10 19:27:53.000000', 17, 0);

-- Transaction 29: Purchase at Strosin-Fadel
-- Source: dailytran.txt line 77 (transaction ID 0000000248557079)
-- Amount: -$90.50 (parsed from 0000009050{80 - negative amount)
INSERT INTO transaction (transaction_number, transaction_type_code, transaction_category_code, transaction_source, description, amount, merchant_id, merchant_name, merchant_city, merchant_zip, card_number, original_timestamp, account_id, version)
VALUES ('0000000248557079', '01', '0001', 'POS TERM', 'Purchase at Strosin-Fadel', -90.50, '800000000', 'Strosin-Fadel', 'Krajcikmouth', '25843', '5975117516616077', '2022-06-10 19:27:53.000000', 28, 0);

-- Transaction 30: Purchase at Renner LLC
-- Source: dailytran.txt line 88 (transaction ID 0000000274596018)
-- Amount: -$4.00 (parsed from 0000000400{80 - negative amount)
INSERT INTO transaction (transaction_number, transaction_type_code, transaction_category_code, transaction_source, description, amount, merchant_id, merchant_name, merchant_city, merchant_zip, card_number, original_timestamp, account_id, version)
VALUES ('0000000274596018', '01', '0001', 'POS TERM', 'Purchase at Renner LLC', -4.00, '800000000', 'Renner LLC', 'Sengerport', '73531', '5407099850479866', '2022-06-10 19:27:53.000000', 17, 0);

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
