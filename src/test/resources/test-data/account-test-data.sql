-- =============================================================================
-- Account Test Data Fixture
-- =============================================================================
-- Source: app/data/ASCII/acctdata.txt and app/data/ASCII/custdata.txt
-- Purpose: Test data for integration testing of account and customer operations
-- Usage: Loaded by @SpringBootTest tests with Testcontainers PostgreSQL
-- 
-- This file contains deterministic test data derived from VSAM ASCII files
-- to verify functional equivalence with legacy COBOL account operations:
-- - COACTVWC.cbl: Account inquiry (GET /api/v1/accounts/{id})
-- - COACTUPC.cbl: Account update (PUT /api/v1/accounts/{id})
-- - CBTRN01C.cbl: Transaction posting batch operations
-- - CBACT04C.cbl: Interest calculation batch operations
--
-- Data Mapping:
-- - COBOL PIC 9(11) account_number → VARCHAR(11)
-- - COBOL PIC S9(10)V99 COMP-3 balances → NUMERIC(12,2)
-- - COBOL PIC X(10) dates → DATE
-- - COBOL PIC 9(09) SSN → VARCHAR(9)
-- - COBOL PIC X(n) text fields → VARCHAR(n)
-- =============================================================================

-- Clear existing test data
DELETE FROM account WHERE account_id BETWEEN 1 AND 50;
DELETE FROM customer WHERE customer_id BETWEEN 1 AND 50;

-- =============================================================================
-- CUSTOMER DATA (from custdata.txt)
-- 50 customer records with demographics for account testing
-- =============================================================================

-- Customer 1: Immanuel Madeline Kessler (custdata.txt line 1, account 00000000001)
INSERT INTO customer (customer_id, cust_id, first_name, middle_name, last_name, address_line_1, address_line_2, address_line_3, state_code, country_code, zip_code, phone_number_1, phone_number_2, ssn, date_of_birth, fico_credit_score, created_at, updated_at, version)
VALUES (1, '000000001', 'Immanuel', 'Madeline', 'Kessler', '618 Deshaun Route', 'Apt. 802', 'Altenwerthshire', 'NC', 'USA', '12546', '(908)119-8310', '(373)693-8684', '020973888', '1961-06-08', 753, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0);

-- Customer 2: Enrico April Rosenbaum (custdata.txt line 2, account 00000000002)
INSERT INTO customer (customer_id, cust_id, first_name, middle_name, last_name, address_line_1, address_line_2, address_line_3, state_code, country_code, zip_code, phone_number_1, phone_number_2, ssn, date_of_birth, fico_credit_score, created_at, updated_at, version)
VALUES (2, '000000002', 'Enrico', 'April', 'Rosenbaum', '4917 Myrna Flats', 'Apt. 453', 'West Bernita', 'IN', 'USA', '22770', '(429)706-9510', '(744)950-5272', '587518382', '1961-10-08', 769, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0);

-- Customer 3: Larry Cody Homenick (custdata.txt line 3, account 00000000003)
INSERT INTO customer (customer_id, cust_id, first_name, middle_name, last_name, address_line_1, address_line_2, address_line_3, state_code, country_code, zip_code, phone_number_1, phone_number_2, ssn, date_of_birth, fico_credit_score, created_at, updated_at, version)
VALUES (3, '000000003', 'Larry', 'Cody', 'Homenick', '362 Esta Parks', 'Apt. 390', 'New Gladys', 'GA', 'USA', '19852-6716', '(950)396-9024', '(685)168-8826', '317460867', '1987-11-30', 664, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0);

-- Customer 4: Delbert Kaia Parisian (custdata.txt line 4, account 00000000004)
INSERT INTO customer (customer_id, cust_id, first_name, middle_name, last_name, address_line_1, address_line_2, address_line_3, state_code, country_code, zip_code, phone_number_1, phone_number_2, ssn, date_of_birth, fico_credit_score, created_at, updated_at, version)
VALUES (4, '000000004', 'Delbert', 'Kaia', 'Parisian', '638 Blanda Gateway', 'Apt. 076', 'Lake Virginie', 'MI', 'USA', '39035-0455', '(801)603-4121', '(156)074-6837', '660354258', '1985-01-13', 740, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0);

-- Customer 5: Treva Manley Schowalter (custdata.txt line 5, account 00000000005)
INSERT INTO customer (customer_id, cust_id, first_name, middle_name, last_name, address_line_1, address_line_2, address_line_3, state_code, country_code, zip_code, phone_number_1, phone_number_2, ssn, date_of_birth, fico_credit_score, created_at, updated_at, version)
VALUES (5, '000000005', 'Treva', 'Manley', 'Schowalter', '5653 Legros Plaza', 'Apt. 968', 'Alvinaport', 'MI', 'USA', '02251-1698', '(978)775-4633', '(439)943-7644', '611264288', '1971-09-29', 663, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0);

-- Customer 6: Ignacio Emery Douglas (custdata.txt line 6, account 00000000006)
INSERT INTO customer (customer_id, cust_id, first_name, middle_name, last_name, address_line_1, address_line_2, address_line_3, state_code, country_code, zip_code, phone_number_1, phone_number_2, ssn, date_of_birth, fico_credit_score, created_at, updated_at, version)
VALUES (6, '000000006', 'Ignacio', 'Emery', 'Douglas', '3963 Yasmin Port', 'Suite 756', 'Port Josephstad', 'VI', 'USA', '46713-5148', '(277)743-4266', '(519)010-8739', '880329521', '1994-11-29', 671, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0);

-- Customer 7: Cooper Dennis Mayert (custdata.txt line 7, account 00000000007)
INSERT INTO customer (customer_id, cust_id, first_name, middle_name, last_name, address_line_1, address_line_2, address_line_3, state_code, country_code, zip_code, phone_number_1, phone_number_2, ssn, date_of_birth, fico_credit_score, created_at, updated_at, version)
VALUES (7, '000000007', 'Cooper', 'Dennis', 'Mayert', '6490 Zakary Locks', 'Apt. 765', 'Madieport', 'AL', 'USA', '34206-2974', '(698)282-4096', '(458)199-0016', '835138951', '1977-05-06', 300, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0);

-- Customer 8: Kelsie Jordyn Dicki (custdata.txt line 8, account 00000000008)
INSERT INTO customer (customer_id, cust_id, first_name, middle_name, last_name, address_line_1, address_line_2, address_line_3, state_code, country_code, zip_code, phone_number_1, phone_number_2, ssn, date_of_birth, fico_credit_score, created_at, updated_at, version)
VALUES (8, '000000008', 'Kelsie', 'Jordyn', 'Dicki', '0925 Welch Streets', 'Apt. 152', 'North Nanniestad', 'SC', 'USA', '27610', '(345)563-7159', '(443)197-1271', '295270759', '1964-03-25', 331, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0);

-- Customer 9: Melvin Regan Ondricka (custdata.txt line 9, account 00000000009)
INSERT INTO customer (customer_id, cust_id, first_name, middle_name, last_name, address_line_1, address_line_2, address_line_3, state_code, country_code, zip_code, phone_number_1, phone_number_2, ssn, date_of_birth, fico_credit_score, created_at, updated_at, version)
VALUES (9, '000000009', 'Melvin', 'Regan', 'Ondricka', '87893 Samson Flats', 'Apt. 135', 'New Braden', 'VI', 'USA', '21113', '(035)456-1404', '(412)440-3130', '842035847', '1975-11-07', 394, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0);

-- Customer 10: Maybell Creola Mann (custdata.txt line 10, account 00000000010)
INSERT INTO customer (customer_id, cust_id, first_name, middle_name, last_name, address_line_1, address_line_2, address_line_3, state_code, country_code, zip_code, phone_number_1, phone_number_2, ssn, date_of_birth, fico_credit_score, created_at, updated_at, version)
VALUES (10, '000000010', 'Maybell', 'Creola', 'Mann', '77933 Adah Dale', 'Suite 343', 'Andersonfurt', 'CT', 'USA', '44803-4279', '(614)594-2619', '(667)057-0235', '754755746', '1980-06-11', 850, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0);

-- Customer 11: Hayden Ressie Pfannerstill (custdata.txt line 11, account 00000000011)
INSERT INTO customer (customer_id, cust_id, first_name, middle_name, last_name, address_line_1, address_line_2, address_line_3, state_code, country_code, zip_code, phone_number_1, phone_number_2, ssn, date_of_birth, fico_credit_score, created_at, updated_at, version)
VALUES (11, '000000011', 'Hayden', 'Ressie', 'Pfannerstill', '14895 Everette Ridges', 'Apt. 443', 'Julianneburgh', 'WA', 'USA', '24984', '(002)533-6980', '(553)586-7718', '493538586', '1986-11-03', 300, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0);

-- Customer 12: Maci Alan Robel (custdata.txt line 12, account 00000000012)
INSERT INTO customer (customer_id, cust_id, first_name, middle_name, last_name, address_line_1, address_line_2, address_line_3, state_code, country_code, zip_code, phone_number_1, phone_number_2, ssn, date_of_birth, fico_credit_score, created_at, updated_at, version)
VALUES (12, '000000012', 'Maci', 'Alan', 'Robel', '80501 Isac Cliffs', 'Suite 623', 'Predovicton', 'MN', 'USA', '78861', '(584)045-5200', '(610)244-0407', '666114218', '1984-02-18', 613, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0);

-- Customer 13: Mariane Oma Fadel (custdata.txt line 13, account 00000000013)
INSERT INTO customer (customer_id, cust_id, first_name, middle_name, last_name, address_line_1, address_line_2, address_line_3, state_code, country_code, zip_code, phone_number_1, phone_number_2, ssn, date_of_birth, fico_credit_score, created_at, updated_at, version)
VALUES (13, '000000013', 'Mariane', 'Oma', 'Fadel', '2689 Derick Mission', 'Suite 055', 'Bruenfurt', 'OR', 'USA', '02322', '(875)943-7287', '(075)550-6435', '757924569', '1999-03-09', 448, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0);

-- Customer 14: Chelsea Ignacio Marks (custdata.txt line 14, account 00000000014)
INSERT INTO customer (customer_id, cust_id, first_name, middle_name, last_name, address_line_1, address_line_2, address_line_3, state_code, country_code, zip_code, phone_number_1, phone_number_2, ssn, date_of_birth, fico_credit_score, created_at, updated_at, version)
VALUES (14, '000000014', 'Chelsea', 'Ignacio', 'Marks', '747 Dino Lodge', 'Apt. 850', 'West Chase', 'RI', 'USA', '12914-8465', '(141)807-6571', '(284)088-9052', '655128548', '1974-11-29', 483, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0);

-- Customer 15: Aubree Elliot Hermann (custdata.txt line 15, account 00000000015)
INSERT INTO customer (customer_id, cust_id, first_name, middle_name, last_name, address_line_1, address_line_2, address_line_3, state_code, country_code, zip_code, phone_number_1, phone_number_2, ssn, date_of_birth, fico_credit_score, created_at, updated_at, version)
VALUES (15, '000000015', 'Aubree', 'Elliot', 'Hermann', '36365 Ledner Drives', 'Suite 882', 'Port Efrainland', 'DE', 'USA', '63205-7014', '(769)100-7971', '(366)310-2061', '033922034', '1964-12-06', 300, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0);

-- Customer 16: Carroll Cicero Bergstrom (custdata.txt line 16, account 00000000016)
INSERT INTO customer (customer_id, cust_id, first_name, middle_name, last_name, address_line_1, address_line_2, address_line_3, state_code, country_code, zip_code, phone_number_1, phone_number_2, ssn, date_of_birth, fico_credit_score, created_at, updated_at, version)
VALUES (16, '000000016', 'Carroll', 'Cicero', 'Bergstrom', '06988 Thiel Falls', 'Suite 148', 'Concepcionland', 'VT', 'USA', '84390', '(631)343-8667', '(938)648-3716', '649827971', '1983-04-27', 300, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0);

-- Customer 17: Sigrid Angeline Mann (custdata.txt line 17, account 00000000017)
INSERT INTO customer (customer_id, cust_id, first_name, middle_name, last_name, address_line_1, address_line_2, address_line_3, state_code, country_code, zip_code, phone_number_1, phone_number_2, ssn, date_of_birth, fico_credit_score, created_at, updated_at, version)
VALUES (17, '000000017', 'Sigrid', 'Angeline', 'Mann', '95666 Dare Isle', 'Suite 286', 'New Presley', 'FM', 'USA', '56181-0584', '(087)314-2070', '(541)003-6606', '303334693', '1979-01-26', 523, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0);

-- Customer 18: Emile Jairo White (custdata.txt line 18, account 00000000018)
INSERT INTO customer (customer_id, cust_id, first_name, middle_name, last_name, address_line_1, address_line_2, address_line_3, state_code, country_code, zip_code, phone_number_1, phone_number_2, ssn, date_of_birth, fico_credit_score, created_at, updated_at, version)
VALUES (18, '000000018', 'Emile', 'Jairo', 'White', '133 Bergnaum Square', 'Apt. 328', 'Hansenville', 'AP', 'USA', '96003-5867', '(303)654-3323', '(520)186-2176', '385849271', '1987-03-25', 850, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0);

-- Customer 19: Hadley Sigrid Hamill (custdata.txt line 19, account 00000000019)
INSERT INTO customer (customer_id, cust_id, first_name, middle_name, last_name, address_line_1, address_line_2, address_line_3, state_code, country_code, zip_code, phone_number_1, phone_number_2, ssn, date_of_birth, fico_credit_score, created_at, updated_at, version)
VALUES (19, '000000019', 'Hadley', 'Sigrid', 'Hamill', '6273 Ondricka Meadows', 'Apt. 130', 'New Arturoshire', 'RI', 'USA', '48161', '(817)452-4986', '(724)901-6019', '439569907', '1991-01-07', 364, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0);

-- Customer 20: Carter Oren Veum (custdata.txt line 20, account 00000000020)
INSERT INTO customer (customer_id, cust_id, first_name, middle_name, last_name, address_line_1, address_line_2, address_line_3, state_code, country_code, zip_code, phone_number_1, phone_number_2, ssn, date_of_birth, fico_credit_score, created_at, updated_at, version)
VALUES (20, '000000020', 'Carter', 'Oren', 'Veum', '5845 Allison Valleys', 'Suite 934', 'Mitchellmouth', 'MH', 'USA', '72362', '(618)994-0531', '(571)695-4136', '717778238', '1996-04-14', 367, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0);

-- Customer 21: Jerrold Adolphus Maggio (custdata.txt line 21, account 00000000021)
INSERT INTO customer (customer_id, cust_id, first_name, middle_name, last_name, address_line_1, address_line_2, address_line_3, state_code, country_code, zip_code, phone_number_1, phone_number_2, ssn, date_of_birth, fico_credit_score, created_at, updated_at, version)
VALUES (21, '000000021', 'Jerrold', 'Adolphus', 'Maggio', '401 Haylie Crest', 'Apt. 320', 'North Myrnaton', 'CA', 'USA', '72407', '(399)526-3254', '(326)193-1118', '336490822', '1977-11-15', 300, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0);

-- Customer 22: Allene Icie Brown (custdata.txt line 22, account 00000000022)
INSERT INTO customer (customer_id, cust_id, first_name, middle_name, last_name, address_line_1, address_line_2, address_line_3, state_code, country_code, zip_code, phone_number_1, phone_number_2, ssn, date_of_birth, fico_credit_score, created_at, updated_at, version)
VALUES (22, '000000022', 'Allene', 'Icie', 'Brown', '4467 Donnie Crossroad', 'Apt. 437', 'Anabelton', 'MD', 'USA', '01993-9116', '(231)251-5792', '(494)652-0009', '292059024', '1994-02-20', 300, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0);

-- Customer 23: Johnson Blanca Ruecker (custdata.txt line 23, account 00000000023)
INSERT INTO customer (customer_id, cust_id, first_name, middle_name, last_name, address_line_1, address_line_2, address_line_3, state_code, country_code, zip_code, phone_number_1, phone_number_2, ssn, date_of_birth, fico_credit_score, created_at, updated_at, version)
VALUES (23, '000000023', 'Johnson', 'Blanca', 'Ruecker', '2433 Jacobi Forks', 'Apt. 845', 'Hendersonbury', 'KS', 'USA', '78239-9466', '(981)873-1589', '(131)638-5974', '944154289', '1998-12-07', 751, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0);

-- Customer 24: Stefanie Verla Dickinson (custdata.txt line 24, account 00000000024)
INSERT INTO customer (customer_id, cust_id, first_name, middle_name, last_name, address_line_1, address_line_2, address_line_3, state_code, country_code, zip_code, phone_number_1, phone_number_2, ssn, date_of_birth, fico_credit_score, created_at, updated_at, version)
VALUES (24, '000000024', 'Stefanie', 'Verla', 'Dickinson', '6367 Stracke River', 'Apt. 444', 'East Otho', 'KS', 'USA', '15414', '(617)348-9142', '(330)116-5634', '017590544', '1996-01-24', 300, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0);

-- Customer 25: Elliott Fermin Howell (custdata.txt line 25, account 00000000025)
INSERT INTO customer (customer_id, cust_id, first_name, middle_name, last_name, address_line_1, address_line_2, address_line_3, state_code, country_code, zip_code, phone_number_1, phone_number_2, ssn, date_of_birth, fico_credit_score, created_at, updated_at, version)
VALUES (25, '000000025', 'Elliott', 'Fermin', 'Howell', '9524 McKenzie Lakes', 'Suite 245', 'West Alexa', 'NH', 'USA', '75721-7382', '(092)336-8599', '(311)969-1460', '788820436', '1989-03-27', 322, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0);

-- Customer 26: Marjory Damien Stracke (custdata.txt line 26, account 00000000026)
INSERT INTO customer (customer_id, cust_id, first_name, middle_name, last_name, address_line_1, address_line_2, address_line_3, state_code, country_code, zip_code, phone_number_1, phone_number_2, ssn, date_of_birth, fico_credit_score, created_at, updated_at, version)
VALUES (26, '000000026', 'Marjory', 'Damien', 'Stracke', '30161 Bogan Canyon', 'Suite 916', 'Walshberg', 'IL', 'USA', '59945', '(584)772-2867', '(819)733-9809', '840478806', '1990-03-17', 608, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0);

-- Customer 27: Ward Henri Jones (custdata.txt line 27, account 00000000027)
INSERT INTO customer (customer_id, cust_id, first_name, middle_name, last_name, address_line_1, address_line_2, address_line_3, state_code, country_code, zip_code, phone_number_1, phone_number_2, ssn, date_of_birth, fico_credit_score, created_at, updated_at, version)
VALUES (27, '000000027', 'Ward', 'Henri', 'Jones', '210 Amaya Turnpike', 'Suite 180', 'Port Dwight', 'GU', 'USA', '07923-8822', '(935)027-1145', '(103)537-5007', '980161210', '1986-11-08', 500, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0);

-- Customer 28: Hester Vesta Hane (custdata.txt line 28, account 00000000028)
INSERT INTO customer (customer_id, cust_id, first_name, middle_name, last_name, address_line_1, address_line_2, address_line_3, state_code, country_code, zip_code, phone_number_1, phone_number_2, ssn, date_of_birth, fico_credit_score, created_at, updated_at, version)
VALUES (28, '000000028', 'Hester', 'Vesta', 'Hane', '06816 Ursula Meadows', 'Suite 605', 'South Aurore', 'AS', 'USA', '77442-7954', '(122)357-7257', '(050)352-6579', '677986013', '1991-06-05', 300, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0);

-- Customer 29: Rickie Otho Daugherty (custdata.txt line 29, account 00000000029)
INSERT INTO customer (customer_id, cust_id, first_name, middle_name, last_name, address_line_1, address_line_2, address_line_3, state_code, country_code, zip_code, phone_number_1, phone_number_2, ssn, date_of_birth, fico_credit_score, created_at, updated_at, version)
VALUES (29, '000000029', 'Rickie', 'Otho', 'Daugherty', '676 Funk Curve', 'Apt. 375', 'Hayesstad', 'NH', 'USA', '01226', '(418)291-9023', '(795)634-7776', '015027332', '1973-04-05', 677, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0);

-- Customer 30: Layla Dannie Ullrich (custdata.txt line 30, account 00000000030)
INSERT INTO customer (customer_id, cust_id, first_name, middle_name, last_name, address_line_1, address_line_2, address_line_3, state_code, country_code, zip_code, phone_number_1, phone_number_2, ssn, date_of_birth, fico_credit_score, created_at, updated_at, version)
VALUES (30, '000000030', 'Layla', 'Dannie', 'Ullrich', '269 Eleazar Circle', 'Apt. 817', 'Kutchland', 'AK', 'USA', '64266', '(330)408-6966', '(413)347-7306', '866102152', '1965-11-28', 505, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0);

-- Customer 31: Lucious Otto O'Connell (custdata.txt line 31, account 00000000031)
INSERT INTO customer (customer_id, cust_id, first_name, middle_name, last_name, address_line_1, address_line_2, address_line_3, state_code, country_code, zip_code, phone_number_1, phone_number_2, ssn, date_of_birth, fico_credit_score, created_at, updated_at, version)
VALUES (31, '000000031', 'Lucious', 'Otto', 'O''Connell', '919 Swift Valleys', 'Suite 548', 'Hermanborough', 'MS', 'USA', '56133-5636', '(259)414-9625', '(118)946-9264', '357462348', '1976-08-03', 850, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0);

-- Customer 32: Stephany Meda Fisher (custdata.txt line 32, account 00000000032)
INSERT INTO customer (customer_id, cust_id, first_name, middle_name, last_name, address_line_1, address_line_2, address_line_3, state_code, country_code, zip_code, phone_number_1, phone_number_2, ssn, date_of_birth, fico_credit_score, created_at, updated_at, version)
VALUES (32, '000000032', 'Stephany', 'Meda', 'Fisher', '63452 Kenny Streets', 'Apt. 116', 'Predovicburgh', 'AK', 'USA', '85943-7605', '(202)436-5156', '(246)296-3533', '146204208', '1980-11-19', 359, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0);

-- Customer 33: Bernice Norbert Herman (custdata.txt line 33, account 00000000033)
INSERT INTO customer (customer_id, cust_id, first_name, middle_name, last_name, address_line_1, address_line_2, address_line_3, state_code, country_code, zip_code, phone_number_1, phone_number_2, ssn, date_of_birth, fico_credit_score, created_at, updated_at, version)
VALUES (33, '000000033', 'Bernice', 'Norbert', 'Herman', '877 Kassandra Ranch', 'Suite 956', 'Haleyport', 'AR', 'USA', '19113-4329', '(836)743-5487', '(640)208-1176', '144195105', '1988-05-19', 652, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0);

-- Customer 34: Faustino Jess Schmidt (custdata.txt line 34, account 00000000034)
INSERT INTO customer (customer_id, cust_id, first_name, middle_name, last_name, address_line_1, address_line_2, address_line_3, state_code, country_code, zip_code, phone_number_1, phone_number_2, ssn, date_of_birth, fico_credit_score, created_at, updated_at, version)
VALUES (34, '000000034', 'Faustino', 'Jess', 'Schmidt', '44132 Michel Square', 'Suite 007', 'South Margarettaburgh', 'ME', 'USA', '49544-2869', '(179)036-5135', '(986)905-0112', '548088300', '1994-03-21', 674, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0);

-- Customer 35: Angelica Damaris Dach (custdata.txt line 35, account 00000000035)
INSERT INTO customer (customer_id, cust_id, first_name, middle_name, last_name, address_line_1, address_line_2, address_line_3, state_code, country_code, zip_code, phone_number_1, phone_number_2, ssn, date_of_birth, fico_credit_score, created_at, updated_at, version)
VALUES (35, '000000035', 'Angelica', 'Damaris', 'Dach', '396 Pearl Loop', 'Suite 383', 'Pfefferhaven', 'LA', 'USA', '46142', '(303)480-9098', '(637)710-7367', '220547115', '1987-06-23', 474, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0);

-- Customer 36: Toney Emerald Gerhold (custdata.txt line 36, account 00000000036)
INSERT INTO customer (customer_id, cust_id, first_name, middle_name, last_name, address_line_1, address_line_2, address_line_3, state_code, country_code, zip_code, phone_number_1, phone_number_2, ssn, date_of_birth, fico_credit_score, created_at, updated_at, version)
VALUES (36, '000000036', 'Toney', 'Emerald', 'Gerhold', '35943 Raleigh Harbor', 'Apt. 116', 'Lake Derekburgh', 'AL', 'USA', '10932-0480', '(034)271-9180', '(507)529-4523', '420360688', '1991-03-31', 664, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0);

-- Customer 37: Shany Darby Walker (custdata.txt line 37, account 00000000037)
INSERT INTO customer (customer_id, cust_id, first_name, middle_name, last_name, address_line_1, address_line_2, address_line_3, state_code, country_code, zip_code, phone_number_1, phone_number_2, ssn, date_of_birth, fico_credit_score, created_at, updated_at, version)
VALUES (37, '000000037', 'Shany', 'Darby', 'Walker', '91196 Heaney Turnpike', 'Suite 814', 'Lubowitzberg', 'NV', 'USA', '11857-8177', '(052)759-5167', '(706)896-1282', '891897974', '1984-12-09', 661, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0);

-- Customer 38: Angela Ceasar Ankunding (custdata.txt line 38, account 00000000038)
INSERT INTO customer (customer_id, cust_id, first_name, middle_name, last_name, address_line_1, address_line_2, address_line_3, state_code, country_code, zip_code, phone_number_1, phone_number_2, ssn, date_of_birth, fico_credit_score, created_at, updated_at, version)
VALUES (38, '000000038', 'Angela', 'Ceasar', 'Ankunding', '65482 Zoila Skyway', 'Apt. 054', 'East Malachi', 'VA', 'USA', '63928-0008', '(316)640-2650', '(148)111-1148', '764307306', '1990-05-28', 300, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0);

-- Customer 39: Aliyah Horace Berge (custdata.txt line 39, account 00000000039)
INSERT INTO customer (customer_id, cust_id, first_name, middle_name, last_name, address_line_1, address_line_2, address_line_3, state_code, country_code, zip_code, phone_number_1, phone_number_2, ssn, date_of_birth, fico_credit_score, created_at, updated_at, version)
VALUES (39, '000000039', 'Aliyah', 'Horace', 'Berge', '5761 Pasquale Trail', 'Apt. 616', 'New Sabryna', 'IA', 'USA', '74267', '(089)096-3287', '(768)959-4733', '510793388', '1972-08-26', 618, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0);

-- Customer 40: Davon Demond Emmerich (custdata.txt line 40, account 00000000040)
INSERT INTO customer (customer_id, cust_id, first_name, middle_name, last_name, address_line_1, address_line_2, address_line_3, state_code, country_code, zip_code, phone_number_1, phone_number_2, ssn, date_of_birth, fico_credit_score, created_at, updated_at, version)
VALUES (40, '000000040', 'Davon', 'Demond', 'Emmerich', '23499 Beer Views', 'Suite 816', 'Erniechester', 'TX', 'USA', '87156-8689', '(463)762-3017', '(419)414-2177', '054960660', '1992-01-26', 850, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0);

-- Customer 41: Lucinda Kiana Dach (custdata.txt line 41, account 00000000041)
INSERT INTO customer (customer_id, cust_id, first_name, middle_name, last_name, address_line_1, address_line_2, address_line_3, state_code, country_code, zip_code, phone_number_1, phone_number_2, ssn, date_of_birth, fico_credit_score, created_at, updated_at, version)
VALUES (41, '000000041', 'Lucinda', 'Kiana', 'Dach', '3220 Yolanda Corner', 'Suite 649', 'East Harmonystad', 'VT', 'USA', '72971-7481', '(284)052-5831', '(091)234-2144', '643942675', '1967-02-20', 300, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0);

-- Customer 42: Heather Ericka Nienow (custdata.txt line 42, account 00000000042)
INSERT INTO customer (customer_id, cust_id, first_name, middle_name, last_name, address_line_1, address_line_2, address_line_3, state_code, country_code, zip_code, phone_number_1, phone_number_2, ssn, date_of_birth, fico_credit_score, created_at, updated_at, version)
VALUES (42, '000000042', 'Heather', 'Ericka', 'Nienow', '5523 Archibald Club', 'Apt. 358', 'Reillyland', 'FM', 'USA', '83589', '(640)954-4538', '(565)873-6897', '800455633', '1964-11-03', 792, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0);

-- Customer 43: Britney Jermain Waters (custdata.txt line 43, account 00000000043)
INSERT INTO customer (customer_id, cust_id, first_name, middle_name, last_name, address_line_1, address_line_2, address_line_3, state_code, country_code, zip_code, phone_number_1, phone_number_2, ssn, date_of_birth, fico_credit_score, created_at, updated_at, version)
VALUES (43, '000000043', 'Britney', 'Jermain', 'Waters', '97765 Bernhard Fort', 'Apt. 666', 'South Marisaview', 'OK', 'USA', '10050-7980', '(407)042-6952', '(438)659-6397', '262568593', '1966-10-16', 530, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0);

-- Customer 44: Irving Kiera Emard (custdata.txt line 44, account 00000000044)
INSERT INTO customer (customer_id, cust_id, first_name, middle_name, last_name, address_line_1, address_line_2, address_line_3, state_code, country_code, zip_code, phone_number_1, phone_number_2, ssn, date_of_birth, fico_credit_score, created_at, updated_at, version)
VALUES (44, '000000044', 'Irving', 'Kiera', 'Emard', '978 Fatima Stream', 'Apt. 110', 'Lake King', 'ID', 'USA', '05704-0501', '(703)484-5840', '(537)392-5569', '318104527', '1984-04-04', 320, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0);

-- Customer 45: Dixie Norris Beier (custdata.txt line 45, account 00000000045)
INSERT INTO customer (customer_id, cust_id, first_name, middle_name, last_name, address_line_1, address_line_2, address_line_3, state_code, country_code, zip_code, phone_number_1, phone_number_2, ssn, date_of_birth, fico_credit_score, created_at, updated_at, version)
VALUES (45, '000000045', 'Dixie', 'Norris', 'Beier', '441 Levi Prairie', 'Suite 749', 'Abbottshire', 'NV', 'USA', '09048', '(697)143-3221', '(499)287-7255', '352819961', '2001-12-12', 300, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0);

-- Customer 46: Cindy Kira Cremin (custdata.txt line 46, account 00000000046)
INSERT INTO customer (customer_id, cust_id, first_name, middle_name, last_name, address_line_1, address_line_2, address_line_3, state_code, country_code, zip_code, phone_number_1, phone_number_2, ssn, date_of_birth, fico_credit_score, created_at, updated_at, version)
VALUES (46, '000000046', 'Cindy', 'Kira', 'Cremin', '494 Lang Avenue', 'Apt. 937', 'Alexandroview', 'PW', 'USA', '63082-4520', '(358)349-2574', '(077)525-9966', '656405528', '1987-12-14', 300, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0);

-- Customer 47: Rigoberto Savanna Hoeger (custdata.txt line 47, account 00000000047)
INSERT INTO customer (customer_id, cust_id, first_name, middle_name, last_name, address_line_1, address_line_2, address_line_3, state_code, country_code, zip_code, phone_number_1, phone_number_2, ssn, date_of_birth, fico_credit_score, created_at, updated_at, version)
VALUES (47, '000000047', 'Rigoberto', 'Savanna', 'Hoeger', '00097 Gleichner Spur', 'Apt. 932', 'Port Aidanborough', 'GU', 'USA', '31329-6973', '(946)322-6160', '(973)443-8438', '029222192', '1979-02-25', 300, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0);

-- Customer 48: Lyric Mackenzie Pacocha (custdata.txt line 48, account 00000000048)
INSERT INTO customer (customer_id, cust_id, first_name, middle_name, last_name, address_line_1, address_line_2, address_line_3, state_code, country_code, zip_code, phone_number_1, phone_number_2, ssn, date_of_birth, fico_credit_score, created_at, updated_at, version)
VALUES (48, '000000048', 'Lyric', 'Mackenzie', 'Pacocha', '453 Rosina Mountain', 'Apt. 011', 'Albertville', 'OR', 'USA', '83985-4937', '(950)497-1005', '(004)244-7955', '635734407', '1986-08-17', 463, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0);

-- Customer 49: Immanuel Ellie Bednar (custdata.txt line 49, account 00000000049)
INSERT INTO customer (customer_id, cust_id, first_name, middle_name, last_name, address_line_1, address_line_2, address_line_3, state_code, country_code, zip_code, phone_number_1, phone_number_2, ssn, date_of_birth, fico_credit_score, created_at, updated_at, version)
VALUES (49, '000000049', 'Immanuel', 'Ellie', 'Bednar', '5423 Esther Locks', 'Apt. 142', 'Langoshstad', 'GA', 'USA', '12288-3495', '(843)095-2553', '(615)988-9038', '813044111', '2000-01-05', 587, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0);

-- Customer 50: Aniya Alba Von (custdata.txt line 50, account 00000000050)
INSERT INTO customer (customer_id, cust_id, first_name, middle_name, last_name, address_line_1, address_line_2, address_line_3, state_code, country_code, zip_code, phone_number_1, phone_number_2, ssn, date_of_birth, fico_credit_score, created_at, updated_at, version)
VALUES (50, '000000050', 'Aniya', 'Alba', 'Von', '1588 Nienow Cape', 'Suite 187', 'New Aricchester', 'OR', 'USA', '04257', '(325)301-0827', '(493)985-9283', '931248469', '1960-12-01', 748, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0);

-- =============================================================================
-- ACCOUNT DATA (from acctdata.txt)
-- 50 account records with balances, limits, and dates
-- Note: Balances converted from COBOL COMP-3 format (packed decimal)
-- Format: Original "00000001940{" = 1940.0 cents = 19.40 dollars
-- =============================================================================

-- Account 1: Customer 1 (acctdata.txt line 1)
INSERT INTO account (account_id, acct_id, account_number, active_status, current_balance, credit_limit, cash_credit_limit, open_date, expiration_date, reissue_date, customer_id, created_at, updated_at, version)
VALUES (1, '00000000001', '00000000001', 'Y', 19.40, 202.00, 102.00, '2014-11-20', '2025-05-20', '2025-05-20', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0);

-- Account 2: Customer 2 (acctdata.txt line 2)
INSERT INTO account (account_id, acct_id, account_number, active_status, current_balance, credit_limit, cash_credit_limit, open_date, expiration_date, reissue_date, customer_id, created_at, updated_at, version)
VALUES (2, '00000000002', '00000000002', 'Y', 15.80, 613.00, 544.80, '2013-06-19', '2024-08-11', '2024-08-11', 2, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0);

-- Account 3: Customer 3 (acctdata.txt line 3)
INSERT INTO account (account_id, acct_id, account_number, active_status, current_balance, credit_limit, cash_credit_limit, open_date, expiration_date, reissue_date, customer_id, created_at, updated_at, version)
VALUES (3, '00000000003', '00000000003', 'Y', 14.70, 490.90, 53.80, '2013-08-23', '2024-01-10', '2024-01-10', 3, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0);

-- Account 4: Customer 4 (acctdata.txt line 4)
INSERT INTO account (account_id, acct_id, account_number, active_status, current_balance, credit_limit, cash_credit_limit, open_date, expiration_date, reissue_date, customer_id, created_at, updated_at, version)
VALUES (4, '00000000004', '00000000004', 'Y', 4.00, 350.30, 278.90, '2012-11-17', '2023-12-16', '2023-12-16', 4, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0);

-- Account 5: Customer 5 (acctdata.txt line 5)
INSERT INTO account (account_id, acct_id, account_number, active_status, current_balance, credit_limit, cash_credit_limit, open_date, expiration_date, reissue_date, customer_id, created_at, updated_at, version)
VALUES (5, '00000000005', '00000000005', 'Y', 34.50, 381.90, 243.00, '2012-10-03', '2025-03-09', '2025-03-09', 5, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0);

-- Account 6: Customer 6 (acctdata.txt line 6)
INSERT INTO account (account_id, acct_id, account_number, active_status, current_balance, credit_limit, cash_credit_limit, open_date, expiration_date, reissue_date, customer_id, created_at, updated_at, version)
VALUES (6, '00000000006', '00000000006', 'Y', 21.80, 358.40, 294.80, '2017-12-23', '2025-10-08', '2025-10-08', 6, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0);

-- Account 7: Customer 7 (acctdata.txt line 7)
INSERT INTO account (account_id, acct_id, account_number, active_status, current_balance, credit_limit, cash_credit_limit, open_date, expiration_date, reissue_date, customer_id, created_at, updated_at, version)
VALUES (7, '00000000007', '00000000007', 'Y', 19.30, 206.50, 26.40, '2012-10-12', '2024-12-13', '2024-12-13', 7, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0);

-- Account 8: Customer 8 (acctdata.txt line 8)
INSERT INTO account (account_id, acct_id, account_number, active_status, current_balance, credit_limit, cash_credit_limit, open_date, expiration_date, reissue_date, customer_id, created_at, updated_at, version)
VALUES (8, '00000000008', '00000000008', 'Y', 60.50, 610.40, 131.80, '2012-01-04', '2024-05-20', '2024-05-20', 8, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0);

-- Account 9: Customer 9 (acctdata.txt line 9)
INSERT INTO account (account_id, acct_id, account_number, active_status, current_balance, credit_limit, cash_credit_limit, open_date, expiration_date, reissue_date, customer_id, created_at, updated_at, version)
VALUES (9, '00000000009', '00000000009', 'Y', 56.00, 820.10, 206.50, '2016-08-27', '2024-12-27', '2024-12-27', 9, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0);

-- Account 10: Customer 10 (acctdata.txt line 10)
INSERT INTO account (account_id, acct_id, account_number, active_status, current_balance, credit_limit, cash_credit_limit, open_date, expiration_date, reissue_date, customer_id, created_at, updated_at, version)
VALUES (10, '00000000010', '00000000010', 'Y', 15.90, 540.10, 444.20, '2015-09-13', '2023-01-27', '2023-01-27', 10, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0);

-- Account 11: Customer 11 (acctdata.txt line 11)
INSERT INTO account (account_id, acct_id, account_number, active_status, current_balance, credit_limit, cash_credit_limit, open_date, expiration_date, reissue_date, customer_id, created_at, updated_at, version)
VALUES (11, '00000000011', '00000000011', 'Y', 21.20, 499.80, 317.50, '2014-09-12', '2025-03-12', '2025-03-12', 11, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0);

-- Account 12: Customer 12 (acctdata.txt line 12)
INSERT INTO account (account_id, acct_id, account_number, active_status, current_balance, credit_limit, cash_credit_limit, open_date, expiration_date, reissue_date, customer_id, created_at, updated_at, version)
VALUES (12, '00000000012', '00000000012', 'Y', 17.60, 463.60, 38.80, '2009-06-17', '2023-07-07', '2023-07-07', 12, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0);

-- Account 13: Customer 13 (acctdata.txt line 13)
INSERT INTO account (account_id, acct_id, account_number, active_status, current_balance, credit_limit, cash_credit_limit, open_date, expiration_date, reissue_date, customer_id, created_at, updated_at, version)
VALUES (13, '00000000013', '00000000013', 'Y', 4.10, 754.20, 492.20, '2017-10-01', '2024-08-04', '2024-08-04', 13, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0);

-- Account 14: Customer 14 (acctdata.txt line 14)
INSERT INTO account (account_id, acct_id, account_number, active_status, current_balance, credit_limit, cash_credit_limit, open_date, expiration_date, reissue_date, customer_id, created_at, updated_at, version)
VALUES (14, '00000000014', '00000000014', 'Y', 1.50, 225.40, 21.20, '2010-12-04', '2025-12-11', '2025-12-11', 14, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0);

-- Account 15: Customer 15 (acctdata.txt line 15)
INSERT INTO account (account_id, acct_id, account_number, active_status, current_balance, credit_limit, cash_credit_limit, open_date, expiration_date, reissue_date, customer_id, created_at, updated_at, version)
VALUES (15, '00000000015', '00000000015', 'Y', 48.90, 844.10, 383.30, '2009-10-06', '2025-06-09', '2025-06-09', 15, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0);

-- Account 16: Customer 16 (acctdata.txt line 16)
INSERT INTO account (account_id, acct_id, account_number, active_status, current_balance, credit_limit, cash_credit_limit, open_date, expiration_date, reissue_date, customer_id, created_at, updated_at, version)
VALUES (16, '00000000016', '00000000016', 'Y', 73.30, 892.20, 263.20, '2014-09-11', '2024-01-25', '2024-01-25', 16, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0);

-- Account 17: Customer 17 (acctdata.txt line 17)
INSERT INTO account (account_id, acct_id, account_number, active_status, current_balance, credit_limit, cash_credit_limit, open_date, expiration_date, reissue_date, customer_id, created_at, updated_at, version)
VALUES (17, '00000000017', '00000000017', 'Y', 3.30, 56.80, 51.00, '2014-05-17', '2025-03-01', '2025-03-01', 17, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0);

-- Account 18: Customer 18 (acctdata.txt line 18)
INSERT INTO account (account_id, acct_id, account_number, active_status, current_balance, credit_limit, cash_credit_limit, open_date, expiration_date, reissue_date, customer_id, created_at, updated_at, version)
VALUES (18, '00000000018', '00000000018', 'Y', 14.40, 290.30, 149.60, '2018-11-15', '2023-09-10', '2023-09-10', 18, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0);

-- Account 19: Customer 19 (acctdata.txt line 19)
INSERT INTO account (account_id, acct_id, account_number, active_status, current_balance, credit_limit, cash_credit_limit, open_date, expiration_date, reissue_date, customer_id, created_at, updated_at, version)
VALUES (19, '00000000019', '00000000019', 'Y', 48.00, 698.60, 372.30, '2011-12-14', '2025-07-23', '2025-07-23', 19, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0);

-- Account 20: Customer 20 (acctdata.txt line 20)
INSERT INTO account (account_id, acct_id, account_number, active_status, current_balance, credit_limit, cash_credit_limit, open_date, expiration_date, reissue_date, customer_id, created_at, updated_at, version)
VALUES (20, '00000000020', '00000000020', 'Y', 36.90, 376.70, 104.00, '2014-02-27', '2024-03-13', '2024-03-13', 20, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0);

-- Account 21: Customer 21 (acctdata.txt line 21)
INSERT INTO account (account_id, acct_id, account_number, active_status, current_balance, credit_limit, cash_credit_limit, open_date, expiration_date, reissue_date, customer_id, created_at, updated_at, version)
VALUES (21, '00000000021', '00000000021', 'Y', 11.20, 126.40, 18.00, '2011-10-19', '2023-01-06', '2023-01-06', 21, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0);

-- Account 22: Customer 22 (acctdata.txt line 22)
INSERT INTO account (account_id, acct_id, account_number, active_status, current_balance, credit_limit, cash_credit_limit, open_date, expiration_date, reissue_date, customer_id, created_at, updated_at, version)
VALUES (22, '00000000022', '00000000022', 'Y', 5.50, 859.90, 471.20, '2016-11-21', '2025-12-28', '2025-12-28', 22, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0);

-- Account 23: Customer 23 (acctdata.txt line 23)
INSERT INTO account (account_id, acct_id, account_number, active_status, current_balance, credit_limit, cash_credit_limit, open_date, expiration_date, reissue_date, customer_id, created_at, updated_at, version)
VALUES (23, '00000000023', '00000000023', 'Y', 10.40, 337.70, 290.40, '2012-03-15', '2025-03-18', '2025-03-18', 23, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0);

-- Account 24: Customer 24 (acctdata.txt line 24)
INSERT INTO account (account_id, acct_id, account_number, active_status, current_balance, credit_limit, cash_credit_limit, open_date, expiration_date, reissue_date, customer_id, created_at, updated_at, version)
VALUES (24, '00000000024', '00000000024', 'Y', 40.00, 517.40, 412.90, '2015-08-08', '2025-02-11', '2025-02-11', 24, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0);

-- Account 25: Customer 25 (acctdata.txt line 25)
INSERT INTO account (account_id, acct_id, account_number, active_status, current_balance, credit_limit, cash_credit_limit, open_date, expiration_date, reissue_date, customer_id, created_at, updated_at, version)
VALUES (25, '00000000025', '00000000025', 'Y', 6.10, 819.40, 658.20, '2012-10-26', '2025-07-10', '2025-07-10', 25, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0);

-- Account 26: Customer 26 (acctdata.txt line 26)
INSERT INTO account (account_id, acct_id, account_number, active_status, current_balance, credit_limit, cash_credit_limit, open_date, expiration_date, reissue_date, customer_id, created_at, updated_at, version)
VALUES (26, '00000000026', '00000000026', 'Y', 4.60, 218.10, 137.50, '2009-04-20', '2024-12-19', '2024-12-19', 26, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0);

-- Account 27: Customer 27 (acctdata.txt line 27)
INSERT INTO account (account_id, acct_id, account_number, active_status, current_balance, credit_limit, cash_credit_limit, open_date, expiration_date, reissue_date, customer_id, created_at, updated_at, version)
VALUES (27, '00000000027', '00000000027', 'Y', 28.40, 557.20, 207.50, '2012-09-30', '2025-07-13', '2025-07-13', 27, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0);

-- Account 28: Customer 28 (acctdata.txt line 28)
INSERT INTO account (account_id, acct_id, account_number, active_status, current_balance, credit_limit, cash_credit_limit, open_date, expiration_date, reissue_date, customer_id, created_at, updated_at, version)
VALUES (28, '00000000028', '00000000028', 'Y', 6.80, 86.80, 54.70, '2015-05-20', '2024-05-09', '2024-05-09', 28, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0);

-- Account 29: Customer 29 (acctdata.txt line 29)
INSERT INTO account (account_id, acct_id, account_number, active_status, current_balance, credit_limit, cash_credit_limit, open_date, expiration_date, reissue_date, customer_id, created_at, updated_at, version)
VALUES (29, '00000000029', '00000000029', 'Y', 33.90, 551.10, 436.10, '2015-11-03', '2024-06-04', '2024-06-04', 29, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0);

-- Account 30: Customer 30 (acctdata.txt line 30)
INSERT INTO account (account_id, acct_id, account_number, active_status, current_balance, credit_limit, cash_credit_limit, open_date, expiration_date, reissue_date, customer_id, created_at, updated_at, version)
VALUES (30, '00000000030', '00000000030', 'Y', 0.20, 12.00, 9.30, '2011-08-26', '2024-06-27', '2024-06-27', 30, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0);

-- Account 31: Customer 31 (acctdata.txt line 31)
INSERT INTO account (account_id, acct_id, account_number, active_status, current_balance, credit_limit, cash_credit_limit, open_date, expiration_date, reissue_date, customer_id, created_at, updated_at, version)
VALUES (31, '00000000031', '00000000031', 'Y', 3.10, 114.00, 107.70, '2017-02-25', '2025-06-08', '2025-06-08', 31, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0);

-- Account 32: Customer 32 (acctdata.txt line 32)
INSERT INTO account (account_id, acct_id, account_number, active_status, current_balance, credit_limit, cash_credit_limit, open_date, expiration_date, reissue_date, customer_id, created_at, updated_at, version)
VALUES (32, '00000000032', '00000000032', 'Y', 3.00, 117.50, 84.60, '2013-11-10', '2025-05-19', '2025-05-19', 32, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0);

-- Account 33: Customer 33 (acctdata.txt line 33)
INSERT INTO account (account_id, acct_id, account_number, active_status, current_balance, credit_limit, cash_credit_limit, open_date, expiration_date, reissue_date, customer_id, created_at, updated_at, version)
VALUES (33, '00000000033', '00000000033', 'Y', 41.00, 640.40, 95.10, '2012-10-11', '2025-10-07', '2025-10-07', 33, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0);

-- Account 34: Customer 34 (acctdata.txt line 34)
INSERT INTO account (account_id, acct_id, account_number, active_status, current_balance, credit_limit, cash_credit_limit, open_date, expiration_date, reissue_date, customer_id, created_at, updated_at, version)
VALUES (34, '00000000034', '00000000034', 'Y', 25.30, 364.20, 277.00, '2009-05-10', '2025-10-06', '2025-10-06', 34, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0);

-- Account 35: Customer 35 (acctdata.txt line 35)
INSERT INTO account (account_id, acct_id, account_number, active_status, current_balance, credit_limit, cash_credit_limit, open_date, expiration_date, reissue_date, customer_id, created_at, updated_at, version)
VALUES (35, '00000000035', '00000000035', 'Y', 16.60, 194.70, 152.50, '2018-02-02', '2025-09-23', '2025-09-23', 35, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0);

-- Account 36: Customer 36 (acctdata.txt line 36)
INSERT INTO account (account_id, acct_id, account_number, active_status, current_balance, credit_limit, cash_credit_limit, open_date, expiration_date, reissue_date, customer_id, created_at, updated_at, version)
VALUES (36, '00000000036', '00000000036', 'Y', 11.00, 332.80, 83.90, '2018-07-18', '2024-12-23', '2024-12-23', 36, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0);

-- Account 37: Customer 37 (acctdata.txt line 37)
INSERT INTO account (account_id, acct_id, account_number, active_status, current_balance, credit_limit, cash_credit_limit, open_date, expiration_date, reissue_date, customer_id, created_at, updated_at, version)
VALUES (37, '00000000037', '00000000037', 'Y', 0.70, 44.60, 16.60, '2016-09-10', '2023-10-24', '2023-10-24', 37, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0);

-- Account 38: Customer 38 (acctdata.txt line 38)
INSERT INTO account (account_id, acct_id, account_number, active_status, current_balance, credit_limit, cash_credit_limit, open_date, expiration_date, reissue_date, customer_id, created_at, updated_at, version)
VALUES (38, '00000000038', '00000000038', 'Y', 61.20, 650.50, 347.60, '2010-08-12', '2023-07-23', '2023-07-23', 38, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0);

-- Account 39: Customer 39 (acctdata.txt line 39)
INSERT INTO account (account_id, acct_id, account_number, active_status, current_balance, credit_limit, cash_credit_limit, open_date, expiration_date, reissue_date, customer_id, created_at, updated_at, version)
VALUES (39, '00000000039', '00000000039', 'Y', 84.30, 975.00, 621.20, '2018-08-26', '2025-09-08', '2025-09-08', 39, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0);

-- Account 40: Customer 40 (acctdata.txt line 40)
INSERT INTO account (account_id, acct_id, account_number, active_status, current_balance, credit_limit, cash_credit_limit, open_date, expiration_date, reissue_date, customer_id, created_at, updated_at, version)
VALUES (40, '00000000040', '00000000040', 'Y', 4.30, 582.30, 167.40, '2010-02-13', '2023-10-27', '2023-10-27', 40, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0);

-- Account 41: Customer 41 (acctdata.txt line 41)
INSERT INTO account (account_id, acct_id, account_number, active_status, current_balance, credit_limit, cash_credit_limit, open_date, expiration_date, reissue_date, customer_id, created_at, updated_at, version)
VALUES (41, '00000000041', '00000000041', 'Y', 37.50, 672.10, 342.90, '2015-02-07', '2023-04-24', '2023-04-24', 41, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0);

-- Account 42: Customer 42 (acctdata.txt line 42)
INSERT INTO account (account_id, acct_id, account_number, active_status, current_balance, credit_limit, cash_credit_limit, open_date, expiration_date, reissue_date, customer_id, created_at, updated_at, version)
VALUES (42, '00000000042', '00000000042', 'Y', 30.20, 656.30, 510.30, '2016-09-19', '2025-09-19', '2025-09-19', 42, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0);

-- Account 43: Customer 43 (acctdata.txt line 43)
INSERT INTO account (account_id, acct_id, account_number, active_status, current_balance, credit_limit, cash_credit_limit, open_date, expiration_date, reissue_date, customer_id, created_at, updated_at, version)
VALUES (43, '00000000043', '00000000043', 'Y', 61.00, 616.80, 120.60, '2012-04-09', '2025-08-29', '2025-08-29', 43, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0);

-- Account 44: Customer 44 (acctdata.txt line 44)
INSERT INTO account (account_id, acct_id, account_number, active_status, current_balance, credit_limit, cash_credit_limit, open_date, expiration_date, reissue_date, customer_id, created_at, updated_at, version)
VALUES (44, '00000000044', '00000000044', 'Y', 26.30, 689.90, 443.20, '2018-12-01', '2024-01-17', '2024-01-17', 44, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0);

-- Account 45: Customer 45 (acctdata.txt line 45)
INSERT INTO account (account_id, acct_id, account_number, active_status, current_balance, credit_limit, cash_credit_limit, open_date, expiration_date, reissue_date, customer_id, created_at, updated_at, version)
VALUES (45, '00000000045', '00000000045', 'Y', 18.60, 271.90, 68.80, '2010-12-31', '2025-07-09', '2025-07-09', 45, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0);

-- Account 46: Customer 46 (acctdata.txt line 46)
INSERT INTO account (account_id, acct_id, account_number, active_status, current_balance, credit_limit, cash_credit_limit, open_date, expiration_date, reissue_date, customer_id, created_at, updated_at, version)
VALUES (46, '00000000046', '00000000046', 'Y', 39.60, 700.70, 543.80, '2013-09-06', '2025-06-20', '2025-06-20', 46, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0);

-- Account 47: Customer 47 (acctdata.txt line 47)
INSERT INTO account (account_id, acct_id, account_number, active_status, current_balance, credit_limit, cash_credit_limit, open_date, expiration_date, reissue_date, customer_id, created_at, updated_at, version)
VALUES (47, '00000000047', '00000000047', 'Y', 3.20, 233.80, 15.90, '2014-04-03', '2025-08-23', '2025-08-23', 47, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0);

-- Account 48: Customer 48 (acctdata.txt line 48)
INSERT INTO account (account_id, acct_id, account_number, active_status, current_balance, credit_limit, cash_credit_limit, open_date, expiration_date, reissue_date, customer_id, created_at, updated_at, version)
VALUES (48, '00000000048', '00000000048', 'Y', 22.60, 230.60, 61.20, '2017-03-18', '2025-02-06', '2025-02-06', 48, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0);

-- Account 49: Customer 49 (acctdata.txt line 49)
INSERT INTO account (account_id, acct_id, account_number, active_status, current_balance, credit_limit, cash_credit_limit, open_date, expiration_date, reissue_date, customer_id, created_at, updated_at, version)
VALUES (49, '00000000049', '00000000049', 'Y', 10.00, 904.80, 480.70, '2019-04-06', '2023-09-17', '2023-09-17', 49, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0);

-- Account 50: Customer 50 (acctdata.txt line 50)
INSERT INTO account (account_id, acct_id, account_number, active_status, current_balance, credit_limit, cash_credit_limit, open_date, expiration_date, reissue_date, customer_id, created_at, updated_at, version)
VALUES (50, '00000000050', '00000000050', 'Y', 49.20, 616.90, 458.70, '2011-04-22', '2023-03-09', '2023-03-09', 50, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0);

-- =============================================================================
-- Test Data Summary
-- =============================================================================
-- Customers: 50 records (IDs 1-50)
-- Accounts: 50 records (IDs 1-50)
-- Each account linked to corresponding customer via customer_id FK
-- Account numbers: 00000000001 through 00000000050
-- All accounts active (status 'Y')
-- Balances range from $0.20 to $84.30
-- Credit limits range from $12.00 to $975.00
-- Test data enables comprehensive integration testing of:
--   - Account inquiry operations (COACTVWC.cbl → GET /api/v1/accounts/{id})
--   - Account update operations (COACTUPC.cbl → PUT /api/v1/accounts/{id})
--   - Transaction posting batch jobs (CBTRN01C.cbl)
--   - Interest calculation batch jobs (CBACT04C.cbl)
--   - Customer relationship queries and validations
-- =============================================================================
