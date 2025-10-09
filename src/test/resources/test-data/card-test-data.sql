-- ===================================================================================
-- Card Test Data Fixture
-- ===================================================================================
-- Purpose: SQL test data for card master records and card-to-account cross-references
-- Source Files: 
--   - app/data/ASCII/carddata.txt (50 card records)
--   - app/data/ASCII/cardxref.txt (50 cross-reference records)
-- Target Tables: card, card_xref
-- Used By: CardIntegrationTest for verifying functional equivalence with legacy COBOL
--          card operations (COCRDLIC.cbl, COCRDSLC.cbl, COCRDUPC.cbl)
--
-- PCI-DSS COMPLIANCE NOTE:
-- Card numbers in this file are TEST DATA ONLY and do not represent real PANs.
-- In production environments, all card data must be encrypted at rest and in transit,
-- and card numbers must be masked in logs and non-secure displays.
-- ===================================================================================

-- ===================================================================================
-- CARD TABLE INSERTS
-- ===================================================================================
-- Migrated from: app/data/ASCII/carddata.txt
-- Format: Fixed-width ASCII with 16-digit card number, name field, expiration date, active flag
-- Columns: card_id, card_number, account_id, embossed_name, expiration_date, 
--          active_status, created_at, version
-- ===================================================================================

-- Card 1: From carddata.txt line 1, cardxref.txt line 1
-- Card Number: 0500024453765740, Account: 50, Customer: 5
INSERT INTO card (card_id, card_number, account_id, embossed_name, expiration_date, active_status, created_at, updated_at, version)
VALUES (1, '0500024453765740', 50, 'Aniya Von', '2023-03-09', 'Y', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0);

-- Card 2: From carddata.txt line 2, cardxref.txt line 2
-- Card Number: 0683586198171516, Account: 27, Customer: 27
INSERT INTO card (card_id, card_number, account_id, embossed_name, expiration_date, active_status, created_at, updated_at, version)
VALUES (2, '0683586198171516', 27, 'Ward Jones', '2025-07-13', 'Y', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0);

-- Card 3: From carddata.txt line 3, cardxref.txt line 3
-- Card Number: 0923877193247330, Account: 2, Customer: 2
INSERT INTO card (card_id, card_number, account_id, embossed_name, expiration_date, active_status, created_at, updated_at, version)
VALUES (3, '0923877193247330', 2, 'Enrico Rosenbaum', '2024-08-11', 'Y', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0);

-- Card 4: From carddata.txt line 4, cardxref.txt line 4
-- Card Number: 0927987108636232, Account: 20, Customer: 20
INSERT INTO card (card_id, card_number, account_id, embossed_name, expiration_date, active_status, created_at, updated_at, version)
VALUES (4, '0927987108636232', 20, 'Carter Veum', '2024-03-13', 'Y', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0);

-- Card 5: From carddata.txt line 5, cardxref.txt line 5
-- Card Number: 0982496213629795, Account: 12, Customer: 12
INSERT INTO card (card_id, card_number, account_id, embossed_name, expiration_date, active_status, created_at, updated_at, version)
VALUES (5, '0982496213629795', 12, 'Maci Robel', '2023-07-07', 'Y', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0);

-- Card 6: From carddata.txt line 6, cardxref.txt line 6
-- Card Number: 1014086565224350, Account: 44, Customer: 44
INSERT INTO card (card_id, card_number, account_id, embossed_name, expiration_date, active_status, created_at, updated_at, version)
VALUES (6, '1014086565224350', 44, 'Irving Emard', '2024-01-17', 'Y', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0);

-- Card 7: From carddata.txt line 7, cardxref.txt line 7
-- Card Number: 1142167692878931, Account: 37, Customer: 37
INSERT INTO card (card_id, card_number, account_id, embossed_name, expiration_date, active_status, created_at, updated_at, version)
VALUES (7, '1142167692878931', 37, 'Shany Walker', '2023-10-24', 'Y', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0);

-- Card 8: From carddata.txt line 8, cardxref.txt line 8
-- Card Number: 1561409106491600, Account: 35, Customer: 35
INSERT INTO card (card_id, card_number, account_id, embossed_name, expiration_date, active_status, created_at, updated_at, version)
VALUES (8, '1561409106491600', 35, 'Angelica Dach', '2025-09-23', 'Y', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0);

-- Card 9: From carddata.txt line 9, cardxref.txt line 9
-- Card Number: 2745303720002090, Account: 39, Customer: 39
INSERT INTO card (card_id, card_number, account_id, embossed_name, expiration_date, active_status, created_at, updated_at, version)
VALUES (9, '2745303720002090', 39, 'Aliyah Berge', '2025-09-08', 'Y', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0);

-- Card 10: From carddata.txt line 10, cardxref.txt line 10
-- Card Number: 2760836797107565, Account: 24, Customer: 24
INSERT INTO card (card_id, card_number, account_id, embossed_name, expiration_date, active_status, created_at, updated_at, version)
VALUES (10, '2760836797107565', 24, 'Stefanie Dickinson', '2025-02-11', 'Y', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0);

-- Card 11: From carddata.txt line 11, cardxref.txt line 11
-- Card Number: 2871968252812490, Account: 6, Customer: 6
INSERT INTO card (card_id, card_number, account_id, embossed_name, expiration_date, active_status, created_at, updated_at, version)
VALUES (11, '2871968252812490', 6, 'Ignacio Douglas', '2025-10-08', 'Y', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0);

-- Card 12: From carddata.txt line 12, cardxref.txt line 12
-- Card Number: 2940139362300449, Account: 22, Customer: 22
INSERT INTO card (card_id, card_number, account_id, embossed_name, expiration_date, active_status, created_at, updated_at, version)
VALUES (12, '2940139362300449', 22, 'Allene Brown', '2025-12-28', 'Y', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0);

-- Card 13: From carddata.txt line 13, cardxref.txt line 13
-- Card Number: 2988091353094312, Account: 4, Customer: 4
INSERT INTO card (card_id, card_number, account_id, embossed_name, expiration_date, active_status, created_at, updated_at, version)
VALUES (13, '2988091353094312', 4, 'Delbert Parisian', '2023-12-16', 'Y', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0);

-- Card 14: From carddata.txt line 14, cardxref.txt line 14
-- Card Number: 3260763612337560, Account: 10, Customer: 10
INSERT INTO card (card_id, card_number, account_id, embossed_name, expiration_date, active_status, created_at, updated_at, version)
VALUES (14, '3260763612337560', 10, 'Maybell Mann', '2023-01-27', 'Y', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0);

-- Card 15: From carddata.txt line 15, cardxref.txt line 15
-- Card Number: 3766281984155154, Account: 41, Customer: 41
INSERT INTO card (card_id, card_number, account_id, embossed_name, expiration_date, active_status, created_at, updated_at, version)
VALUES (15, '3766281984155154', 41, 'Lucinda Dach', '2023-04-24', 'Y', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0);

-- Card 16: From carddata.txt line 16, cardxref.txt line 16
-- Card Number: 3940246016141489, Account: 19, Customer: 19
INSERT INTO card (card_id, card_number, account_id, embossed_name, expiration_date, active_status, created_at, updated_at, version)
VALUES (16, '3940246016141489', 19, 'Hadley Hamill', '2025-07-23', 'Y', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0);

-- Card 17: From carddata.txt line 17, cardxref.txt line 17
-- Card Number: 3999169246375885, Account: 3, Customer: 3
INSERT INTO card (card_id, card_number, account_id, embossed_name, expiration_date, active_status, created_at, updated_at, version)
VALUES (17, '3999169246375885', 3, 'Larry Homenick', '2024-01-10', 'Y', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0);

-- Card 18: From carddata.txt line 18, cardxref.txt line 18
-- Card Number: 4011500891777367, Account: 13, Customer: 13
INSERT INTO card (card_id, card_number, account_id, embossed_name, expiration_date, active_status, created_at, updated_at, version)
VALUES (18, '4011500891777367', 13, 'Mariane Fadel', '2024-08-04', 'Y', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0);

-- Card 19: From carddata.txt line 19, cardxref.txt line 19
-- Card Number: 4385271476627819, Account: 34, Customer: 34
INSERT INTO card (card_id, card_number, account_id, embossed_name, expiration_date, active_status, created_at, updated_at, version)
VALUES (19, '4385271476627819', 34, 'Faustino Schmidt', '2025-10-06', 'Y', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0);

-- Card 20: From carddata.txt line 20, cardxref.txt line 20
-- Card Number: 4534784102713951, Account: 36, Customer: 36
INSERT INTO card (card_id, card_number, account_id, embossed_name, expiration_date, active_status, created_at, updated_at, version)
VALUES (20, '4534784102713951', 36, 'Toney Gerhold', '2024-12-23', 'Y', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0);

-- Card 21: From carddata.txt line 21, cardxref.txt line 21
-- Card Number: 4859452612877065, Account: 7, Customer: 7
INSERT INTO card (card_id, card_number, account_id, embossed_name, expiration_date, active_status, created_at, updated_at, version)
VALUES (21, '4859452612877065', 7, 'Cooper Mayert', '2024-12-13', 'Y', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0);

-- Card 22: From carddata.txt line 22, cardxref.txt line 22
-- Card Number: 5407099850479866, Account: 21, Customer: 21
INSERT INTO card (card_id, card_number, account_id, embossed_name, expiration_date, active_status, created_at, updated_at, version)
VALUES (22, '5407099850479866', 21, 'Jerrold Maggio', '2023-01-06', 'Y', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0);

-- Card 23: From carddata.txt line 23, cardxref.txt line 23
-- Card Number: 5656830544981216, Account: 46, Customer: 46
INSERT INTO card (card_id, card_number, account_id, embossed_name, expiration_date, active_status, created_at, updated_at, version)
VALUES (23, '5656830544981216', 46, 'Cindy Cremin', '2025-06-20', 'Y', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0);

-- Card 24: From carddata.txt line 24, cardxref.txt line 24
-- Card Number: 5671184478505844, Account: 18, Customer: 18
INSERT INTO card (card_id, card_number, account_id, embossed_name, expiration_date, active_status, created_at, updated_at, version)
VALUES (24, '5671184478505844', 18, 'Emile White', '2023-09-10', 'Y', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0);

-- Card 25: From carddata.txt line 25, cardxref.txt line 25
-- Card Number: 5787351228879339, Account: 47, Customer: 47
INSERT INTO card (card_id, card_number, account_id, embossed_name, expiration_date, active_status, created_at, updated_at, version)
VALUES (25, '5787351228879339', 47, 'Rigoberto Hoeger', '2025-08-23', 'Y', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0);

-- Card 26: From carddata.txt line 26, cardxref.txt line 26
-- Card Number: 5975117516616077, Account: 42, Customer: 42
INSERT INTO card (card_id, card_number, account_id, embossed_name, expiration_date, active_status, created_at, updated_at, version)
VALUES (26, '5975117516616077', 42, 'Heather Nienow', '2025-09-19', 'Y', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0);

-- Card 27: From carddata.txt line 27, cardxref.txt line 27
-- Card Number: 6009619150674526, Account: 5, Customer: 5
INSERT INTO card (card_id, card_number, account_id, embossed_name, expiration_date, active_status, created_at, updated_at, version)
VALUES (27, '6009619150674526', 5, 'Treva Schowalter', '2025-03-09', 'Y', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0);

-- Card 28: From carddata.txt line 28, cardxref.txt line 28
-- Card Number: 6349250331648509, Account: 15, Customer: 15
INSERT INTO card (card_id, card_number, account_id, embossed_name, expiration_date, active_status, created_at, updated_at, version)
VALUES (28, '6349250331648509', 15, 'Aubree Hermann', '2025-06-09', 'Y', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0);

-- Card 29: From carddata.txt line 29, cardxref.txt line 29
-- Card Number: 6503535181795992, Account: 48, Customer: 48
INSERT INTO card (card_id, card_number, account_id, embossed_name, expiration_date, active_status, created_at, updated_at, version)
VALUES (29, '6503535181795992', 48, 'Lyric Pacocha', '2025-02-06', 'Y', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0);

-- Card 30: From carddata.txt line 30, cardxref.txt line 30
-- Card Number: 6509230362553816, Account: 30, Customer: 30
INSERT INTO card (card_id, card_number, account_id, embossed_name, expiration_date, active_status, created_at, updated_at, version)
VALUES (30, '6509230362553816', 30, 'Layla Ullrich', '2024-06-27', 'Y', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0);

-- Card 31: From carddata.txt line 31, cardxref.txt line 31
-- Card Number: 6723000463207764, Account: 28, Customer: 28
INSERT INTO card (card_id, card_number, account_id, embossed_name, expiration_date, active_status, created_at, updated_at, version)
VALUES (31, '6723000463207764', 28, 'Hester Hane', '2024-05-09', 'Y', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0);

-- Card 32: From carddata.txt line 32, cardxref.txt line 32
-- Card Number: 6727055190616014, Account: 16, Customer: 16
INSERT INTO card (card_id, card_number, account_id, embossed_name, expiration_date, active_status, created_at, updated_at, version)
VALUES (32, '6727055190616014', 16, 'Carroll Bergstrom', '2024-01-25', 'Y', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0);

-- Card 33: From carddata.txt line 33, cardxref.txt line 33
-- Card Number: 6832676047698087, Account: 33, Customer: 33
INSERT INTO card (card_id, card_number, account_id, embossed_name, expiration_date, active_status, created_at, updated_at, version)
VALUES (33, '6832676047698087', 33, 'Bernice Herman', '2025-10-07', 'Y', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0);

-- Card 34: From carddata.txt line 34, cardxref.txt line 34
-- Card Number: 7026637615032277, Account: 31, Customer: 31
INSERT INTO card (card_id, card_number, account_id, embossed_name, expiration_date, active_status, created_at, updated_at, version)
VALUES (34, '7026637615032277', 31, 'Lucious O''Connell', '2025-06-08', 'Y', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0);

-- Card 35: From carddata.txt line 35, cardxref.txt line 35
-- Card Number: 7058267261837752, Account: 43, Customer: 43
INSERT INTO card (card_id, card_number, account_id, embossed_name, expiration_date, active_status, created_at, updated_at, version)
VALUES (35, '7058267261837752', 43, 'Britney Waters', '2025-08-29', 'Y', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0);

-- Card 36: From carddata.txt line 36, cardxref.txt line 36
-- Card Number: 7094142751055551, Account: 32, Customer: 32
INSERT INTO card (card_id, card_number, account_id, embossed_name, expiration_date, active_status, created_at, updated_at, version)
VALUES (36, '7094142751055551', 32, 'Stephany Fisher', '2025-05-19', 'Y', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0);

-- Card 37: From carddata.txt line 37, cardxref.txt line 37
-- Card Number: 7251508149188883, Account: 29, Customer: 29
INSERT INTO card (card_id, card_number, account_id, embossed_name, expiration_date, active_status, created_at, updated_at, version)
VALUES (37, '7251508149188883', 29, 'Rickie Daugherty', '2024-06-04', 'Y', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0);

-- Card 38: From carddata.txt line 38, cardxref.txt line 38
-- Card Number: 7379335634661142, Account: 45, Customer: 45
INSERT INTO card (card_id, card_number, account_id, embossed_name, expiration_date, active_status, created_at, updated_at, version)
VALUES (38, '7379335634661142', 45, 'Dixie Beier', '2025-07-09', 'Y', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0);

-- Card 39: From carddata.txt line 39, cardxref.txt line 39
-- Card Number: 7427684863423209, Account: 11, Customer: 11
INSERT INTO card (card_id, card_number, account_id, embossed_name, expiration_date, active_status, created_at, updated_at, version)
VALUES (39, '7427684863423209', 11, 'Hayden Pfannerstill', '2025-03-12', 'Y', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0);

-- Card 40: From carddata.txt line 40, cardxref.txt line 40
-- Card Number: 7443870988897530, Account: 38, Customer: 38
INSERT INTO card (card_id, card_number, account_id, embossed_name, expiration_date, active_status, created_at, updated_at, version)
VALUES (40, '7443870988897530', 38, 'Angela Ankunding', '2023-07-23', 'Y', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0);

-- Card 41: From carddata.txt line 41, cardxref.txt line 41
-- Card Number: 8040580410348680, Account: 26, Customer: 26
INSERT INTO card (card_id, card_number, account_id, embossed_name, expiration_date, active_status, created_at, updated_at, version)
VALUES (41, '8040580410348680', 26, 'Marjory Stracke', '2024-12-19', 'Y', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0);

-- Card 42: From carddata.txt line 42, cardxref.txt line 42
-- Card Number: 8112545834239735, Account: 23, Customer: 23
INSERT INTO card (card_id, card_number, account_id, embossed_name, expiration_date, active_status, created_at, updated_at, version)
VALUES (42, '8112545834239735', 23, 'Johnson Ruecker', '2025-03-18', 'Y', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0);

-- Card 43: From carddata.txt line 43, cardxref.txt line 43
-- Card Number: 8262593602473076, Account: 49, Customer: 49
INSERT INTO card (card_id, card_number, account_id, embossed_name, expiration_date, active_status, created_at, updated_at, version)
VALUES (43, '8262593602473076', 49, 'Immanuel Bednar', '2023-09-17', 'Y', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0);

-- Card 44: From carddata.txt line 44, cardxref.txt line 44
-- Card Number: 8517866958206008, Account: 14, Customer: 14
INSERT INTO card (card_id, card_number, account_id, embossed_name, expiration_date, active_status, created_at, updated_at, version)
VALUES (44, '8517866958206008', 14, 'Chelsea Marks', '2025-12-11', 'Y', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0);

-- Card 45: From carddata.txt line 45, cardxref.txt line 45
-- Card Number: 8931369351894783, Account: 8, Customer: 8
INSERT INTO card (card_id, card_number, account_id, embossed_name, expiration_date, active_status, created_at, updated_at, version)
VALUES (45, '8931369351894783', 8, 'Kelsie Dicki', '2024-05-20', 'Y', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0);

-- Card 46: From carddata.txt line 46, cardxref.txt line 46
-- Card Number: 9056297931664011, Account: 25, Customer: 25
INSERT INTO card (card_id, card_number, account_id, embossed_name, expiration_date, active_status, created_at, updated_at, version)
VALUES (46, '9056297931664011', 25, 'Elliott Howell', '2025-07-10', 'Y', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0);

-- Card 47: From carddata.txt line 47, cardxref.txt line 47
-- Card Number: 9349107475869214, Account: 17, Customer: 17
INSERT INTO card (card_id, card_number, account_id, embossed_name, expiration_date, active_status, created_at, updated_at, version)
VALUES (47, '9349107475869214', 17, 'Sigrid Mann', '2025-03-01', 'Y', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0);

-- Card 48: From carddata.txt line 48, cardxref.txt line 48
-- Card Number: 9501733721429893, Account: 9, Customer: 9
INSERT INTO card (card_id, card_number, account_id, embossed_name, expiration_date, active_status, created_at, updated_at, version)
VALUES (48, '9501733721429893', 9, 'Melvin Ondricka', '2024-12-27', 'Y', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0);

-- Card 49: From carddata.txt line 49, cardxref.txt line 49
-- Card Number: 9680294154603697, Account: 1, Customer: 1
INSERT INTO card (card_id, card_number, account_id, embossed_name, expiration_date, active_status, created_at, updated_at, version)
VALUES (49, '9680294154603697', 1, 'Immanuel Kessler', '2025-05-20', 'Y', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0);

-- Card 50: From carddata.txt line 50, cardxref.txt line 50
-- Card Number: 9805583408996588, Account: 40, Customer: 40
INSERT INTO card (card_id, card_number, account_id, embossed_name, expiration_date, active_status, created_at, updated_at, version)
VALUES (50, '9805583408996588', 40, 'Davon Emmerich', '2023-10-27', 'Y', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0);

-- ===================================================================================
-- CARD CROSS-REFERENCE TABLE INSERTS
-- ===================================================================================
-- Migrated from: app/data/ASCII/cardxref.txt
-- Format: 33 ASCII digits (16-digit card_number + 11-digit customer_id + 6-digit account_id)
-- Purpose: Maps card numbers to customer and account IDs for cross-reference lookups
-- Columns: card_number (PK), customer_id, account_id, created_at
-- Replaces: CARDXREF VSAM file lookups in COBOL programs
-- ===================================================================================

-- Cross-reference 1: Card 0500024453765740 → Customer 5, Account 50
INSERT INTO card_xref (card_number, customer_id, account_id, created_at, updated_at, version)
VALUES ('0500024453765740', '000000005', '00000000050', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0);

-- Cross-reference 2: Card 0683586198171516 → Customer 27, Account 27
INSERT INTO card_xref (card_number, customer_id, account_id, created_at, updated_at, version)
VALUES ('0683586198171516', '000000027', '00000000027', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0);

-- Cross-reference 3: Card 0923877193247330 → Customer 2, Account 2
INSERT INTO card_xref (card_number, customer_id, account_id, created_at, updated_at, version)
VALUES ('0923877193247330', '000000002', '00000000002', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0);

-- Cross-reference 4: Card 0927987108636232 → Customer 20, Account 20
INSERT INTO card_xref (card_number, customer_id, account_id, created_at, updated_at, version)
VALUES ('0927987108636232', '000000020', '00000000020', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0);

-- Cross-reference 5: Card 0982496213629795 → Customer 12, Account 12
INSERT INTO card_xref (card_number, customer_id, account_id, created_at, updated_at, version)
VALUES ('0982496213629795', '000000012', '00000000012', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0);

-- Cross-reference 6: Card 1014086565224350 → Customer 44, Account 44
INSERT INTO card_xref (card_number, customer_id, account_id, created_at, updated_at, version)
VALUES ('1014086565224350', '000000044', '00000000044', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0);

-- Cross-reference 7: Card 1142167692878931 → Customer 37, Account 37
INSERT INTO card_xref (card_number, customer_id, account_id, created_at, updated_at, version)
VALUES ('1142167692878931', '000000037', '00000000037', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0);

-- Cross-reference 8: Card 1561409106491600 → Customer 35, Account 35
INSERT INTO card_xref (card_number, customer_id, account_id, created_at, updated_at, version)
VALUES ('1561409106491600', '000000035', '00000000035', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0);

-- Cross-reference 9: Card 2745303720002090 → Customer 39, Account 39
INSERT INTO card_xref (card_number, customer_id, account_id, created_at, updated_at, version)
VALUES ('2745303720002090', '000000039', '00000000039', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0);

-- Cross-reference 10: Card 2760836797107565 → Customer 24, Account 24
INSERT INTO card_xref (card_number, customer_id, account_id, created_at, updated_at, version)
VALUES ('2760836797107565', '000000024', '00000000024', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0);

-- Cross-reference 11: Card 2871968252812490 → Customer 6, Account 6
INSERT INTO card_xref (card_number, customer_id, account_id, created_at, updated_at, version)
VALUES ('2871968252812490', '000000006', '00000000006', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0);

-- Cross-reference 12: Card 2940139362300449 → Customer 22, Account 22
INSERT INTO card_xref (card_number, customer_id, account_id, created_at, updated_at, version)
VALUES ('2940139362300449', '000000022', '00000000022', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0);

-- Cross-reference 13: Card 2988091353094312 → Customer 4, Account 4
INSERT INTO card_xref (card_number, customer_id, account_id, created_at, updated_at, version)
VALUES ('2988091353094312', '000000004', '00000000004', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0);

-- Cross-reference 14: Card 3260763612337560 → Customer 10, Account 10
INSERT INTO card_xref (card_number, customer_id, account_id, created_at, updated_at, version)
VALUES ('3260763612337560', '000000010', '00000000010', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0);

-- Cross-reference 15: Card 3766281984155154 → Customer 41, Account 41
INSERT INTO card_xref (card_number, customer_id, account_id, created_at, updated_at, version)
VALUES ('3766281984155154', '000000041', '00000000041', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0);

-- Cross-reference 16: Card 3940246016141489 → Customer 19, Account 19
INSERT INTO card_xref (card_number, customer_id, account_id, created_at, updated_at, version)
VALUES ('3940246016141489', '000000019', '00000000019', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0);

-- Cross-reference 17: Card 3999169246375885 → Customer 3, Account 3
INSERT INTO card_xref (card_number, customer_id, account_id, created_at, updated_at, version)
VALUES ('3999169246375885', '000000003', '00000000003', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0);

-- Cross-reference 18: Card 4011500891777367 → Customer 13, Account 13
INSERT INTO card_xref (card_number, customer_id, account_id, created_at, updated_at, version)
VALUES ('4011500891777367', '000000013', '00000000013', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0);

-- Cross-reference 19: Card 4385271476627819 → Customer 34, Account 34
INSERT INTO card_xref (card_number, customer_id, account_id, created_at, updated_at, version)
VALUES ('4385271476627819', '000000034', '00000000034', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0);

-- Cross-reference 20: Card 4534784102713951 → Customer 36, Account 36
INSERT INTO card_xref (card_number, customer_id, account_id, created_at, updated_at, version)
VALUES ('4534784102713951', '000000036', '00000000036', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0);

-- Cross-reference 21: Card 4859452612877065 → Customer 7, Account 7
INSERT INTO card_xref (card_number, customer_id, account_id, created_at, updated_at, version)
VALUES ('4859452612877065', '000000007', '00000000007', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0);

-- Cross-reference 22: Card 5407099850479866 → Customer 21, Account 21
INSERT INTO card_xref (card_number, customer_id, account_id, created_at, updated_at, version)
VALUES ('5407099850479866', '000000021', '00000000021', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0);

-- Cross-reference 23: Card 5656830544981216 → Customer 46, Account 46
INSERT INTO card_xref (card_number, customer_id, account_id, created_at, updated_at, version)
VALUES ('5656830544981216', '000000046', '00000000046', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0);

-- Cross-reference 24: Card 5671184478505844 → Customer 18, Account 18
INSERT INTO card_xref (card_number, customer_id, account_id, created_at, updated_at, version)
VALUES ('5671184478505844', '000000018', '00000000018', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0);

-- Cross-reference 25: Card 5787351228879339 → Customer 47, Account 47
INSERT INTO card_xref (card_number, customer_id, account_id, created_at, updated_at, version)
VALUES ('5787351228879339', '000000047', '00000000047', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0);

-- Cross-reference 26: Card 5975117516616077 → Customer 42, Account 42
INSERT INTO card_xref (card_number, customer_id, account_id, created_at, updated_at, version)
VALUES ('5975117516616077', '000000042', '00000000042', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0);

-- Cross-reference 27: Card 6009619150674526 → Customer 5, Account 5
INSERT INTO card_xref (card_number, customer_id, account_id, created_at, updated_at, version)
VALUES ('6009619150674526', '000000005', '00000000005', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0);

-- Cross-reference 28: Card 6349250331648509 → Customer 15, Account 15
INSERT INTO card_xref (card_number, customer_id, account_id, created_at, updated_at, version)
VALUES ('6349250331648509', '000000015', '00000000015', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0);

-- Cross-reference 29: Card 6503535181795992 → Customer 48, Account 48
INSERT INTO card_xref (card_number, customer_id, account_id, created_at, updated_at, version)
VALUES ('6503535181795992', '000000048', '00000000048', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0);

-- Cross-reference 30: Card 6509230362553816 → Customer 30, Account 30
INSERT INTO card_xref (card_number, customer_id, account_id, created_at, updated_at, version)
VALUES ('6509230362553816', '000000030', '00000000030', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0);

-- Cross-reference 31: Card 6723000463207764 → Customer 28, Account 28
INSERT INTO card_xref (card_number, customer_id, account_id, created_at, updated_at, version)
VALUES ('6723000463207764', '000000028', '00000000028', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0);

-- Cross-reference 32: Card 6727055190616014 → Customer 16, Account 16
INSERT INTO card_xref (card_number, customer_id, account_id, created_at, updated_at, version)
VALUES ('6727055190616014', '000000016', '00000000016', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0);

-- Cross-reference 33: Card 6832676047698087 → Customer 33, Account 33
INSERT INTO card_xref (card_number, customer_id, account_id, created_at, updated_at, version)
VALUES ('6832676047698087', '000000033', '00000000033', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0);

-- Cross-reference 34: Card 7026637615032277 → Customer 31, Account 31
INSERT INTO card_xref (card_number, customer_id, account_id, created_at, updated_at, version)
VALUES ('7026637615032277', '000000031', '00000000031', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0);

-- Cross-reference 35: Card 7058267261837752 → Customer 43, Account 43
INSERT INTO card_xref (card_number, customer_id, account_id, created_at, updated_at, version)
VALUES ('7058267261837752', '000000043', '00000000043', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0);

-- Cross-reference 36: Card 7094142751055551 → Customer 32, Account 32
INSERT INTO card_xref (card_number, customer_id, account_id, created_at, updated_at, version)
VALUES ('7094142751055551', '000000032', '00000000032', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0);

-- Cross-reference 37: Card 7251508149188883 → Customer 29, Account 29
INSERT INTO card_xref (card_number, customer_id, account_id, created_at, updated_at, version)
VALUES ('7251508149188883', '000000029', '00000000029', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0);

-- Cross-reference 38: Card 7379335634661142 → Customer 45, Account 45
INSERT INTO card_xref (card_number, customer_id, account_id, created_at, updated_at, version)
VALUES ('7379335634661142', '000000045', '00000000045', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0);

-- Cross-reference 39: Card 7427684863423209 → Customer 11, Account 11
INSERT INTO card_xref (card_number, customer_id, account_id, created_at, updated_at, version)
VALUES ('7427684863423209', '000000011', '00000000011', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0);

-- Cross-reference 40: Card 7443870988897530 → Customer 38, Account 38
INSERT INTO card_xref (card_number, customer_id, account_id, created_at, updated_at, version)
VALUES ('7443870988897530', '000000038', '00000000038', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0);

-- Cross-reference 41: Card 8040580410348680 → Customer 26, Account 26
INSERT INTO card_xref (card_number, customer_id, account_id, created_at, updated_at, version)
VALUES ('8040580410348680', '000000026', '00000000026', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0);

-- Cross-reference 42: Card 8112545834239735 → Customer 23, Account 23
INSERT INTO card_xref (card_number, customer_id, account_id, created_at, updated_at, version)
VALUES ('8112545834239735', '000000023', '00000000023', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0);

-- Cross-reference 43: Card 8262593602473076 → Customer 49, Account 49
INSERT INTO card_xref (card_number, customer_id, account_id, created_at, updated_at, version)
VALUES ('8262593602473076', '000000049', '00000000049', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0);

-- Cross-reference 44: Card 8517866958206008 → Customer 14, Account 14
INSERT INTO card_xref (card_number, customer_id, account_id, created_at, updated_at, version)
VALUES ('8517866958206008', '000000014', '00000000014', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0);

-- Cross-reference 45: Card 8931369351894783 → Customer 8, Account 8
INSERT INTO card_xref (card_number, customer_id, account_id, created_at, updated_at, version)
VALUES ('8931369351894783', '000000008', '00000000008', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0);

-- Cross-reference 46: Card 9056297931664011 → Customer 25, Account 25
INSERT INTO card_xref (card_number, customer_id, account_id, created_at, updated_at, version)
VALUES ('9056297931664011', '000000025', '00000000025', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0);

-- Cross-reference 47: Card 9349107475869214 → Customer 17, Account 17
INSERT INTO card_xref (card_number, customer_id, account_id, created_at, updated_at, version)
VALUES ('9349107475869214', '000000017', '00000000017', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0);

-- Cross-reference 48: Card 9501733721429893 → Customer 9, Account 9
INSERT INTO card_xref (card_number, customer_id, account_id, created_at, updated_at, version)
VALUES ('9501733721429893', '000000009', '00000000009', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0);

-- Cross-reference 49: Card 9680294154603697 → Customer 1, Account 1
INSERT INTO card_xref (card_number, customer_id, account_id, created_at, updated_at, version)
VALUES ('9680294154603697', '000000001', '00000000001', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0);

-- Cross-reference 50: Card 9805583408996588 → Customer 40, Account 40
INSERT INTO card_xref (card_number, customer_id, account_id, created_at, updated_at, version)
VALUES ('9805583408996588', '000000040', '00000000040', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0);

-- ===================================================================================
-- NOTE: Card IDs 1-50 are reserved by the test data above
-- Ad-hoc tests should NOT use these IDs to avoid primary key collisions
-- Use IDs 51+ for ad-hoc test cases
-- ===================================================================================

-- ===================================================================================
-- End of Card Test Data
-- ===================================================================================
-- Total Records:
--   - 50 card records inserted into card table
--   - 50 cross-reference records inserted into card_xref table
--
-- Referential Integrity Notes:
--   - card.account_id references account table (accounts 1-50 must exist)
--   - card_xref.card_number is primary key and references card.card_number
--   - card_xref.account_id references account table
--   - This test data must be loaded AFTER account-test-data.sql
--
-- Usage in Integration Tests:
--   - CardIntegrationTest.testCardList() - Verifies GET /api/v1/accounts/{id}/cards
--   - CardIntegrationTest.testCardDetail() - Verifies GET /api/v1/cards/{cardNumber}
--   - CardIntegrationTest.testCardUpdate() - Verifies PUT /api/v1/cards/{id}
--   - Tests verify functional equivalence with COBOL programs:
--     * COCRDLIC.cbl (Card List Browse)
--     * COCRDSLC.cbl (Card Detail View)
--     * COCRDUPC.cbl (Card Update)
-- ===================================================================================

-- Reset sequence to avoid primary key collisions in ad-hoc tests
-- H2 syntax for restarting IDENTITY sequence after bulk data loading (IDs 1-50)
ALTER TABLE card ALTER COLUMN card_id RESTART WITH 51;
