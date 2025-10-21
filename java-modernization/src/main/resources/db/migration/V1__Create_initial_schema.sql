CREATE TABLE accounts (
    acct_id BIGINT PRIMARY KEY,
    acct_active_status VARCHAR(1) NOT NULL,
    acct_curr_bal DECIMAL(12, 2),
    acct_credit_limit DECIMAL(12, 2),
    acct_cash_credit_limit DECIMAL(12, 2),
    acct_open_date DATE,
    acct_expiration_date DATE,
    acct_reissue_date DATE,
    acct_curr_cyc_credit DECIMAL(12, 2),
    acct_curr_cyc_debit DECIMAL(12, 2),
    acct_addr_zip VARCHAR(10),
    acct_group_id VARCHAR(10)
);

CREATE TABLE customers (
    cust_id BIGINT PRIMARY KEY,
    cust_first_name VARCHAR(25),
    cust_middle_name VARCHAR(25),
    cust_last_name VARCHAR(25),
    cust_addr_line_1 VARCHAR(50),
    cust_addr_line_2 VARCHAR(50),
    cust_addr_line_3 VARCHAR(50),
    cust_addr_state_cd VARCHAR(2),
    cust_addr_country_cd VARCHAR(3),
    cust_addr_zip VARCHAR(10),
    cust_phone_num_1 VARCHAR(15),
    cust_phone_num_2 VARCHAR(15),
    cust_ssn BIGINT,
    cust_govt_issued_id VARCHAR(20),
    cust_dob DATE,
    cust_eft_account_id VARCHAR(10),
    cust_pri_card_holder_ind VARCHAR(1),
    cust_fico_credit_score INTEGER
);

CREATE TABLE cards (
    card_num VARCHAR(16) PRIMARY KEY,
    card_acct_id BIGINT NOT NULL,
    card_cvv_cd INTEGER,
    card_embossed_name VARCHAR(50),
    card_expiration_date DATE,
    card_active_status VARCHAR(1) NOT NULL,
    FOREIGN KEY (card_acct_id) REFERENCES accounts(acct_id)
);

CREATE TABLE transactions (
    tran_id VARCHAR(16) PRIMARY KEY,
    tran_type_cd VARCHAR(2) NOT NULL,
    tran_cat_cd INTEGER NOT NULL,
    tran_source VARCHAR(10),
    tran_desc VARCHAR(100),
    tran_amt DECIMAL(11, 2),
    tran_merchant_id BIGINT,
    tran_merchant_name VARCHAR(50),
    tran_merchant_city VARCHAR(50),
    tran_merchant_zip VARCHAR(10),
    tran_card_num VARCHAR(16) NOT NULL,
    tran_orig_ts TIMESTAMP,
    tran_proc_ts TIMESTAMP,
    FOREIGN KEY (tran_card_num) REFERENCES cards(card_num)
);

CREATE TABLE card_xref (
    card_num VARCHAR(16) PRIMARY KEY,
    cust_id BIGINT NOT NULL,
    acct_id BIGINT NOT NULL,
    FOREIGN KEY (card_num) REFERENCES cards(card_num),
    FOREIGN KEY (cust_id) REFERENCES customers(cust_id),
    FOREIGN KEY (acct_id) REFERENCES accounts(acct_id)
);

CREATE TABLE users (
    user_id VARCHAR(8) PRIMARY KEY,
    first_name VARCHAR(20),
    last_name VARCHAR(20),
    password VARCHAR(8) NOT NULL,
    user_type VARCHAR(1) NOT NULL
);

CREATE INDEX idx_accounts_status ON accounts(acct_active_status);
CREATE INDEX idx_accounts_zip ON accounts(acct_addr_zip);
CREATE INDEX idx_accounts_group ON accounts(acct_group_id);

CREATE INDEX idx_cards_account ON cards(card_acct_id);
CREATE INDEX idx_cards_status ON cards(card_active_status);

CREATE INDEX idx_transactions_card ON transactions(tran_card_num);
CREATE INDEX idx_transactions_type ON transactions(tran_type_cd);
CREATE INDEX idx_transactions_category ON transactions(tran_cat_cd);
CREATE INDEX idx_transactions_orig_ts ON transactions(tran_orig_ts);

CREATE INDEX idx_customers_last_name ON customers(cust_last_name);
CREATE INDEX idx_customers_ssn ON customers(cust_ssn);

CREATE INDEX idx_card_xref_cust ON card_xref(cust_id);
CREATE INDEX idx_card_xref_acct ON card_xref(acct_id);

CREATE INDEX idx_users_type ON users(user_type);
