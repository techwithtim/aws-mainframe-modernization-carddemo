# VSAM to PostgreSQL Data Migration Guide

## Table of Contents
1. [Introduction](#1-introduction)
2. [Migration Overview](#2-migration-overview)
3. [Pre-Migration Prerequisites](#3-pre-migration-prerequisites)
4. [Phase 1: VSAM Data Export](#4-phase-1-vsam-data-export)
5. [Phase 2: Data Format Transformation](#5-phase-2-data-format-transformation)
6. [Phase 3: PostgreSQL Schema Creation](#6-phase-3-postgresql-schema-creation)
7. [Phase 4: Data Loading](#7-phase-4-data-loading)
8. [Phase 5: Post-Migration Validation](#8-phase-5-post-migration-validation)
9. [Data Type Mapping Reference](#9-data-type-mapping-reference)
10. [Performance Optimization](#10-performance-optimization)
11. [Rollback Procedures](#11-rollback-procedures)
12. [Troubleshooting](#12-troubleshooting)

---

## 1. Introduction

### 1.1 Purpose

This document provides comprehensive procedures for migrating CardDemo application data from legacy mainframe VSAM (Virtual Storage Access Method) datasets to a cloud-native PostgreSQL 15+ relational database. The migration transforms flat-file indexed sequential storage into normalized relational tables while preserving complete data integrity, business logic compatibility, and PCI-DSS security compliance.

### 1.2 Scope

This guide covers the migration of the following datasets:

| Legacy VSAM Dataset | Record Count | Target PostgreSQL Table | Migration Priority |
|:-------------------|:------------|:------------------------|:-------------------|
| **ACCTFILE** (Account Master) | 50 | `account` | Critical - Phase 1 |
| **CARDFILE** (Card Master) | 50 | `card` | Critical - Phase 1 |
| **CUSTFILE** (Customer Master) | 50 | `customer` | Critical - Phase 1 |
| **TRANSACT** (Transaction History) | Variable | `transaction` | High - Phase 2 |
| **DALYTRAN** (Daily Transaction Feed) | Variable | `daily_transaction` | High - Phase 2 |
| **CARDXREF** (Card Cross-Reference) | 50 | `card_xref` | High - Phase 2 |
| **TCATBAL** (Category Balances) | 50 | `transaction_category_balance` | Medium - Phase 3 |
| **DISCGRP** (Disclosure Groups) | 51 | `disclosure_group` | Medium - Phase 3 |
| **USRSEC** (User Security) | Variable | `app_user` | High - Phase 1 |

**Reference Data Files** (ASCII format in `app/data/ASCII/`):
- `trantype.txt` → `transaction_type` (7 records)
- `trancatg.txt` → `transaction_category` (18 records)

### 1.3 Audience

This guide is intended for:
- **Database Administrators**: Responsible for PostgreSQL database setup and migration execution
- **Data Engineers**: Managing ETL pipelines and data transformation scripts
- **DevOps Engineers**: Orchestrating migration workflows and monitoring
- **Application Developers**: Understanding data structures for JPA entity mapping validation

### 1.4 Document Conventions

- **COBOL Field Names**: Uppercase with hyphens (e.g., `ACCT-ID`, `CARD-NUM`)
- **PostgreSQL Column Names**: Lowercase with underscores (e.g., `account_id`, `card_number`)
- **Java Entity Fields**: CamelCase (e.g., `accountId`, `cardNumber`)
- **File Paths**: Relative to repository root unless specified as absolute

---

## 2. Migration Overview

### 2.1 High-Level Migration Architecture

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                       MAINFRAME SOURCE ENVIRONMENT                          │
├─────────────────────────────────────────────────────────────────────────────┤
│  VSAM KSDS Datasets          COBOL Copybook Structures                     │
│  ├── ACCTFILE.DAT           ├── CVACT01Y.cpy (Account layout)              │
│  ├── CARDFILE.DAT           ├── CVACT02Y.cpy (Card layout)                 │
│  ├── CUSTFILE.DAT           ├── CVCUS01Y.cpy (Customer layout)             │
│  ├── TRANSACT.DAT           ├── CVTRA05Y.cpy (Transaction layout)          │
│  └── ...                    └── ...                                         │
└─────────────────────────────────────────────────────────────────────────────┘
                                     │
                                     │ EXPORT PHASE
                                     │ (IDCAMS REPRO / COBOL batch)
                                     ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                    INTERMEDIATE FLAT FILE STORAGE                           │
├─────────────────────────────────────────────────────────────────────────────┤
│  ASCII Fixed-Width Files (app/data/ASCII/)                                 │
│  ├── acctdata.txt           (50 records, 300 bytes/record)                 │
│  ├── carddata.txt           (50 records, 150 bytes/record)                 │
│  ├── custdata.txt           (50 records, 500 bytes/record)                 │
│  └── ...                                                                    │
│                                                                             │
│  EBCDIC → UTF-8 conversion applied                                         │
│  COMP-3 packed decimal → ASCII decimal conversion applied                  │
└─────────────────────────────────────────────────────────────────────────────┘
                                     │
                                     │ TRANSFORMATION PHASE
                                     │ (Python/Java ETL scripts)
                                     ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                    CSV/SQL FORMAT CONVERSION                                │
├─────────────────────────────────────────────────────────────────────────────┤
│  CSV Files (PostgreSQL COPY format)         SQL INSERT Scripts             │
│  ├── account.csv                            ├── V4__load_account_data.sql  │
│  ├── card.csv                               ├── V4__load_card_data.sql     │
│  ├── customer.csv                           ├── V4__load_customer_data.sql │
│  └── ...                                    └── ...                         │
│                                                                             │
│  Data validation and cleansing applied                                     │
│  Referential integrity checks performed                                    │
└─────────────────────────────────────────────────────────────────────────────┘
                                     │
                                     │ LOADING PHASE
                                     │ (Flyway migration / PostgreSQL COPY)
                                     ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                    POSTGRESQL 15+ TARGET DATABASE                           │
├─────────────────────────────────────────────────────────────────────────────┤
│  Relational Tables (AWS RDS PostgreSQL)                                    │
│  ├── customer (50 rows)          ├── transaction_type (7 rows)             │
│  ├── account (50 rows)           ├── transaction_category (18 rows)        │
│  ├── card (50 rows)              ├── disclosure_group (51 rows)            │
│  ├── card_xref (50 rows)         ├── transaction_category_balance (50 rows)│
│  ├── transaction (variable)      └── app_user (variable)                   │
│  └── daily_transaction (variable)                                          │
│                                                                             │
│  Indexes created (B-tree, composite, partial)                              │
│  Foreign key constraints enforced                                          │
│  Flyway schema version: V4 completed                                       │
└─────────────────────────────────────────────────────────────────────────────┘
```

### 2.2 Migration Phases Timeline

| Phase | Duration | Activities | Success Criteria |
|:------|:---------|:-----------|:----------------|
| **Phase 0: Preparation** | 1-2 days | Environment setup, tool installation, backup creation | PostgreSQL database provisioned, migration scripts tested in dev |
| **Phase 1: VSAM Export** | 4-8 hours | Execute IDCAMS REPRO or COBOL batch export jobs | All VSAM datasets exported to ASCII files, record counts match |
| **Phase 2: Transformation** | 8-16 hours | Data format conversion, validation, CSV generation | CSV files pass validation, referential integrity confirmed |
| **Phase 3: Schema Creation** | 1-2 hours | Execute Flyway migrations V1-V3 (DDL, indexes, reference data) | All tables, indexes, constraints created successfully |
| **Phase 4: Data Loading** | 2-6 hours | Execute Flyway migration V4 (INSERT statements or COPY commands) | All data loaded, row counts match source records |
| **Phase 5: Validation** | 4-8 hours | Post-migration reconciliation, integrity checks, performance testing | 100% record count match, all foreign keys satisfied, queries <50ms |
| **Total** | **~2-4 days** | End-to-end migration with validation | Production-ready PostgreSQL database with complete data |

### 2.3 Migration Strategy

**Approach**: **Big Bang Migration** - Complete cutover during maintenance window

**Rationale**:
- CardDemo test dataset size is small (50 accounts, 50 cards, 50 customers)
- Migration window fits within 4-6 hour maintenance period
- Parallel operation complexity outweighs benefits for demonstration application
- Clean cutover simplifies validation and rollback procedures

**Alternative for Production Systems**: **Phased Migration with Data Replication**
- Use AWS Database Migration Service (DMS) for continuous replication
- Maintain parallel operation for 30-90 days
- Gradual traffic cutover with rollback capability
- Not required for CardDemo demonstration scope

---

## 3. Pre-Migration Prerequisites

### 3.1 Infrastructure Requirements

#### 3.1.1 PostgreSQL Database Provisioning

**AWS RDS PostgreSQL Instance** (recommended configuration):

```bash
# Terraform configuration for RDS instance
resource "aws_db_instance" "carddemo_postgres" {
  identifier        = "carddemo-db"
  engine            = "postgres"
  engine_version    = "15.4"
  instance_class    = "db.t3.medium"  # 2 vCPU, 4 GB RAM
  allocated_storage = 50              # GB
  storage_type      = "gp3"
  storage_encrypted = true            # PCI-DSS requirement
  kms_key_id        = aws_kms_key.db_encryption.arn
  
  # Multi-AZ for high availability (production)
  multi_az = true
  
  # Database configuration
  db_name  = "carddemo"
  username = "postgres_admin"
  password = var.db_master_password  # Stored in AWS Secrets Manager
  port     = 5432
  
  # Backup configuration
  backup_retention_period = 30  # 30-day retention
  backup_window          = "03:00-04:00"  # UTC
  maintenance_window     = "sun:04:00-sun:05:00"
  
  # Enable SSL/TLS (PCI-DSS requirement)
  ca_cert_identifier = "rds-ca-2019"
  
  # Performance Insights enabled
  enabled_cloudwatch_logs_exports = ["postgresql", "upgrade"]
  performance_insights_enabled    = true
  
  # Parameter group for optimizations
  parameter_group_name = aws_db_parameter_group.carddemo.name
  
  # Deletion protection (production only)
  deletion_protection = true
  skip_final_snapshot = false
  final_snapshot_identifier = "carddemo-final-snapshot-${timestamp()}"
  
  tags = {
    Environment = "production"
    Application = "CardDemo"
    MigrationPhase = "target-database"
  }
}

# Custom parameter group for performance
resource "aws_db_parameter_group" "carddemo" {
  name   = "carddemo-postgres15"
  family = "postgres15"
  
  parameter {
    name  = "max_connections"
    value = "250"  # Support 10 pods × 20 connections + headroom
  }
  
  parameter {
    name  = "shared_buffers"
    value = "{DBInstanceClassMemory/4}"  # 1 GB for db.t3.medium
  }
  
  parameter {
    name  = "effective_cache_size"
    value = "{DBInstanceClassMemory*3/4}"  # 3 GB for db.t3.medium
  }
  
  parameter {
    name  = "work_mem"
    value = "16384"  # 16 MB per operation
  }
  
  parameter {
    name  = "maintenance_work_mem"
    value = "524288"  # 512 MB for index creation
  }
}
```

#### 3.1.2 Network Connectivity

**Security Group Configuration**:

```bash
# Allow PostgreSQL access from application VPC
resource "aws_security_group_rule" "postgres_ingress" {
  type              = "ingress"
  from_port         = 5432
  to_port           = 5432
  protocol          = "tcp"
  security_group_id = aws_security_group.rds.id
  source_security_group_id = aws_security_group.eks_nodes.id
  description       = "PostgreSQL access from EKS nodes"
}

# Allow admin access from bastion host (for migration execution)
resource "aws_security_group_rule" "postgres_admin_ingress" {
  type              = "ingress"
  from_port         = 5432
  to_port           = 5432
  protocol          = "tcp"
  security_group_id = aws_security_group.rds.id
  source_security_group_id = aws_security_group.bastion.id
  description       = "PostgreSQL admin access from bastion host"
}
```

### 3.2 Software Requirements

| Tool | Version | Purpose | Installation Command |
|:-----|:--------|:--------|:--------------------|
| **PostgreSQL Client** | 15.x | Database connectivity and SQL execution | `brew install postgresql@15` (macOS) or `apt install postgresql-client-15` (Ubuntu) |
| **Python** | 3.10+ | Data transformation scripts | `apt install python3.10` |
| **Python pandas** | 2.0+ | Fixed-width file parsing | `pip install pandas>=2.0.0` |
| **Python psycopg2** | 2.9+ | PostgreSQL Python driver | `pip install psycopg2-binary` |
| **AWS CLI** | 2.x | S3 data transfer, Secrets Manager access | `pip install awscli` |
| **Flyway** | 10.x | Database migration management | `brew install flyway` or download from https://flywaydb.org |

### 3.3 Access Credentials

**Required Credentials** (store in AWS Secrets Manager):

```json
{
  "db_host": "carddemo-db.cluster-xxxxx.us-east-1.rds.amazonaws.com",
  "db_port": 5432,
  "db_name": "carddemo",
  "db_admin_user": "postgres_admin",
  "db_admin_password": "<generated-secure-password>",
  "db_app_user": "postgres_app_user",
  "db_app_password": "<generated-secure-password>"
}
```

**Retrieve credentials**:

```bash
# Fetch credentials from AWS Secrets Manager
aws secretsmanager get-secret-value \
  --secret-id carddemo/database/credentials \
  --query SecretString \
  --output text | jq -r '.db_admin_password'
```

### 3.4 Pre-Migration Backup

**CRITICAL**: Create database snapshot before starting migration:

```bash
# Create RDS snapshot
aws rds create-db-snapshot \
  --db-instance-identifier carddemo-db \
  --db-snapshot-identifier carddemo-pre-migration-$(date +%Y%m%d-%H%M%S) \
  --tags Key=MigrationPhase,Value=pre-migration Key=CreatedBy,Value=migration-script

# Verify snapshot completion
aws rds describe-db-snapshots \
  --db-snapshot-identifier carddemo-pre-migration-20240615-120000 \
  --query 'DBSnapshots[0].Status'
# Expected output: "available"
```

---

## 4. Phase 1: VSAM Data Export

### 4.1 VSAM Export Strategy

**Two Export Approaches**:

1. **IDCAMS REPRO Utility** (mainframe-native, recommended for small datasets)
2. **COBOL Batch Export Program** (custom control, recommended for complex transformations)

### 4.2 IDCAMS REPRO Export Method

#### 4.2.1 Export ACCTFILE (Account Master)

**JCL for IDCAMS REPRO**:

```jcl
//EXPACCT JOB (ACCT),'EXPORT ACCTFILE',CLASS=A,MSGCLASS=X,
//        NOTIFY=&SYSUID
//******************************************************************
//* Export VSAM ACCTFILE to sequential file in ASCII format        *
//******************************************************************
//STEP01  EXEC PGM=IDCAMS
//SYSPRINT DD SYSOUT=*
//INFILE   DD DISP=SHR,DSN=CARDDEMO.ACCTFILE
//OUTFILE  DD DISP=(NEW,CATLG,DELETE),
//            DSN=CARDDEMO.EXPORT.ACCTDATA,
//            UNIT=SYSDA,
//            SPACE=(CYL,(1,1)),
//            DCB=(RECFM=FB,LRECL=300,BLKSIZE=3000)
//SYSIN    DD *
  REPRO INFILE(INFILE) -
        OUTFILE(OUTFILE) -
        COUNT(9999999)
/*
```

**Key Parameters**:
- `RECFM=FB`: Fixed-block record format
- `LRECL=300`: Logical record length = 300 bytes (from CVACT01Y.cpy copybook structure)
- `COUNT(9999999)`: Copy all records (no limit)

**Transfer to Cloud Storage**:

```bash
# Transfer exported file from mainframe to AWS S3
# (Requires z/OS Unix System Services and AWS CLI installation)
cd /z/export
ftp mainframe.example.com
> binary
> get CARDDEMO.EXPORT.ACCTDATA acctdata.dat
> quit

# Upload to S3 bucket
aws s3 cp acctdata.dat s3://carddemo-migration-data/vsam-exports/acctdata.txt \
  --storage-class STANDARD_IA \
  --metadata migration-phase=export,dataset=ACCTFILE
```

#### 4.2.2 Export CARDFILE (Card Master)

**JCL for CARDFILE Export**:

```jcl
//EXPCARD JOB (ACCT),'EXPORT CARDFILE',CLASS=A,MSGCLASS=X
//STEP01  EXEC PGM=IDCAMS
//SYSPRINT DD SYSOUT=*
//INFILE   DD DISP=SHR,DSN=CARDDEMO.CARDFILE
//OUTFILE  DD DISP=(NEW,CATLG,DELETE),
//            DSN=CARDDEMO.EXPORT.CARDDATA,
//            UNIT=SYSDA,
//            SPACE=(CYL,(1,1)),
//            DCB=(RECFM=FB,LRECL=150,BLKSIZE=1500)
//SYSIN    DD *
  REPRO INFILE(INFILE) -
        OUTFILE(OUTFILE) -
        COUNT(9999999)
/*
```

**Record Layout** (from CVACT02Y.cpy):
- Total length: 150 bytes
- Key field: `CARD-NUM` (PIC X(16)) at position 1-16
- Includes: card number, account ID, embossed name, expiration date, status

#### 4.2.3 Export CUSTFILE (Customer Master)

**JCL for CUSTFILE Export**:

```jcl
//EXPCUST JOB (ACCT),'EXPORT CUSTFILE',CLASS=A,MSGCLASS=X
//STEP01  EXEC PGM=IDCAMS
//SYSPRINT DD SYSOUT=*
//INFILE   DD DISP=SHR,DSN=CARDDEMO.CUSTFILE
//OUTFILE  DD DISP=(NEW,CATLG,DELETE),
//            DSN=CARDDEMO.EXPORT.CUSTDATA,
//            UNIT=SYSDA,
//            SPACE=(CYL,(2,1)),
//            DCB=(RECFM=FB,LRECL=500,BLKSIZE=5000)
//SYSIN    DD *
  REPRO INFILE(INFILE) -
        OUTFILE(OUTFILE) -
        COUNT(9999999)
/*
```

**Record Layout** (from CVCUS01Y.cpy):
- Total length: 500 bytes
- Includes: customer ID, name fields, address (3 lines), SSN, DOB, FICO score

### 4.3 COBOL Batch Export Method (Alternative)

**Custom COBOL Export Program** (for complex transformations):

```cobol
       IDENTIFICATION DIVISION.
       PROGRAM-ID. CBEXPDAT.
      *****************************************************************
      * Export VSAM ACCTFILE to ASCII flat file with format conversion*
      *****************************************************************
       ENVIRONMENT DIVISION.
       INPUT-OUTPUT SECTION.
       FILE-CONTROL.
           SELECT ACCTFILE
               ASSIGN TO ACCTFILE
               ORGANIZATION IS INDEXED
               ACCESS MODE IS SEQUENTIAL
               RECORD KEY IS ACCT-ID
               FILE STATUS IS WS-FILE-STATUS.
           
           SELECT OUTFILE
               ASSIGN TO OUTFILE
               ORGANIZATION IS LINE SEQUENTIAL
               FILE STATUS IS WS-OUT-STATUS.
       
       DATA DIVISION.
       FILE SECTION.
       FD  ACCTFILE.
       01  ACCOUNT-RECORD.
           COPY CVACT01Y.
       
       FD  OUTFILE.
       01  OUTPUT-RECORD          PIC X(300).
       
       WORKING-STORAGE SECTION.
       01  WS-FILE-STATUS         PIC XX.
       01  WS-OUT-STATUS          PIC XX.
       01  WS-RECORD-COUNT        PIC 9(09) COMP VALUE ZERO.
       01  WS-FORMATTED-RECORD.
           05  WS-ACCT-ID         PIC 9(11).
           05  FILLER             PIC X VALUE '|'.
           05  WS-ACCT-STATUS     PIC X.
           05  FILLER             PIC X VALUE '|'.
           05  WS-CURR-BAL        PIC -(10)9.99.
           05  FILLER             PIC X VALUE '|'.
           05  WS-CREDIT-LIMIT    PIC -(10)9.99.
           05  FILLER             PIC X VALUE '|'.
           05  WS-OPEN-DATE       PIC X(10).
           05  FILLER             PIC X(185) VALUE SPACES.
       
       PROCEDURE DIVISION.
       0000-MAIN.
           OPEN INPUT ACCTFILE
           OPEN OUTPUT OUTFILE
           
           PERFORM 1000-READ-ACCOUNT
               UNTIL WS-FILE-STATUS = '10'
           
           DISPLAY 'EXPORT COMPLETE. RECORDS WRITTEN: ' WS-RECORD-COUNT
           
           CLOSE ACCTFILE
           CLOSE OUTFILE
           STOP RUN.
       
       1000-READ-ACCOUNT.
           READ ACCTFILE
               AT END
                   MOVE '10' TO WS-FILE-STATUS
               NOT AT END
                   PERFORM 2000-FORMAT-RECORD
                   WRITE OUTPUT-RECORD FROM WS-FORMATTED-RECORD
                   ADD 1 TO WS-RECORD-COUNT
           END-READ.
       
       2000-FORMAT-RECORD.
      *    Convert COMP-3 to display format
           MOVE ACCT-ID TO WS-ACCT-ID
           MOVE ACCT-ACTIVE-STATUS TO WS-ACCT-STATUS
           MOVE ACCT-CURR-BAL TO WS-CURR-BAL
           MOVE ACCT-CREDIT-LIMIT TO WS-CREDIT-LIMIT
           MOVE ACCT-OPEN-DATE TO WS-OPEN-DATE.
```

**Advantages of COBOL Export**:
- Automatic COMP-3 to decimal conversion
- Date format standardization (YYYYMMDD → YYYY-MM-DD)
- Conditional field transformations
- Inline data validation

**Disadvantages**:
- Requires COBOL compiler on mainframe
- Additional development/testing effort
- JCL job orchestration complexity

### 4.4 Export Validation Checklist

After completing exports, validate:

✅ **Record Counts Match**:
```bash
# Count records in exported file
wc -l acctdata.txt
# Expected: 50 records for ACCTFILE

# Verify VSAM source record count
# (Run on mainframe)
LISTCAT ENTRIES(CARDDEMO.ACCTFILE) ALL
# Check "REC-TOTAL" field
```

✅ **File Size Reasonable**:
```bash
# Check file size
ls -lh acctdata.txt
# Expected: ~15 KB for 50 records × 300 bytes/record

# Validate no corruption
file acctdata.txt
# Expected: ASCII text
```

✅ **Sample Data Inspection**:
```bash
# View first 3 records
head -3 acctdata.txt

# Check for:
# - Fixed-width format (300 bytes per line)
# - No truncation or line breaks mid-record
# - Numeric fields contain valid numbers
# - Date fields in YYYY-MM-DD or YYYYMMDD format
```

---

## 5. Phase 2: Data Format Transformation

### 5.1 Transformation Requirements

**Key Transformations**:
1. **Character Encoding**: EBCDIC → UTF-8 (if not handled by IDCAMS REPRO)
2. **Packed Decimal**: COMP-3 → ASCII decimal with 2 decimal places
3. **Fixed-Width → Delimited**: Parse fixed positions, generate CSV with proper escaping
4. **Date Format**: YYYYMMDD → YYYY-MM-DD (ISO 8601)
5. **Data Validation**: Enforce NOT NULL, range checks, referential integrity

### 5.2 Python Transformation Script

**Complete ETL Script** (`scripts/transform_vsam_to_csv.py`):

```python
#!/usr/bin/env python3
"""
VSAM Fixed-Width File to PostgreSQL CSV Transformer
Converts mainframe VSAM export files to PostgreSQL-loadable CSV format
"""

import pandas as pd
import csv
import sys
from datetime import datetime
from decimal import Decimal, InvalidOperation
import logging

# Configure logging
logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s - %(levelname)s - %(message)s'
)
logger = logging.getLogger(__name__)

class VSAMTransformer:
    """Transform VSAM fixed-width files to PostgreSQL CSV"""
    
    def __init__(self, input_file, output_file, layout):
        self.input_file = input_file
        self.output_file = output_file
        self.layout = layout
        self.record_count = 0
        self.error_count = 0
    
    def transform(self):
        """Execute transformation"""
        logger.info(f"Starting transformation: {self.input_file} → {self.output_file}")
        
        try:
            # Read fixed-width file
            df = pd.read_fwf(
                self.input_file,
                colspecs=self.layout['colspecs'],
                names=self.layout['names'],
                dtype=str  # Read all as strings initially
            )
            
            logger.info(f"Read {len(df)} records from source file")
            
            # Apply transformations
            df = self._apply_transformations(df)
            
            # Validate data
            df = self._validate_data(df)
            
            # Write to CSV
            df.to_csv(
                self.output_file,
                index=False,
                quoting=csv.QUOTE_MINIMAL,
                escapechar='\\',
                na_rep='\\N'  # PostgreSQL NULL representation
            )
            
            self.record_count = len(df)
            logger.info(f"Transformation complete: {self.record_count} records written")
            logger.info(f"Errors encountered: {self.error_count}")
            
            return self.record_count, self.error_count
            
        except Exception as e:
            logger.error(f"Transformation failed: {str(e)}")
            raise
    
    def _apply_transformations(self, df):
        """Apply field-specific transformations"""
        for field, transform_func in self.layout.get('transforms', {}).items():
            if field in df.columns:
                logger.info(f"Transforming field: {field}")
                df[field] = df[field].apply(transform_func)
        return df
    
    def _validate_data(self, df):
        """Validate data quality"""
        validators = self.layout.get('validators', {})
        for field, validator_func in validators.items():
            if field in df.columns:
                invalid_mask = ~df[field].apply(validator_func)
                invalid_count = invalid_mask.sum()
                if invalid_count > 0:
                    logger.warning(f"Field '{field}': {invalid_count} invalid values")
                    self.error_count += invalid_count
                    # Log sample invalid values
                    logger.warning(f"Sample invalid values: {df[invalid_mask][field].head()}")
        return df

# Field transformation functions
def transform_comp3_to_decimal(value):
    """Convert COMP-3 packed decimal representation to decimal"""
    if pd.isna(value) or value.strip() == '':
        return None
    try:
        # Remove trailing '{' which indicates positive in COMP-3 ASCII representation
        cleaned = value.replace('{', '0').replace('}', '0')
        # Insert decimal point (assuming V99 = 2 decimal places)
        if len(cleaned) >= 3:
            decimal_value = cleaned[:-2] + '.' + cleaned[-2:]
            return Decimal(decimal_value)
        return Decimal(cleaned)
    except (InvalidOperation, ValueError) as e:
        logger.error(f"Failed to convert '{value}' to decimal: {e}")
        return None

def transform_date_yyyymmdd_to_iso(value):
    """Convert YYYYMMDD to YYYY-MM-DD"""
    if pd.isna(value) or value.strip() == '' or len(value) != 10:
        return None
    try:
        # Already in YYYY-MM-DD format from export
        datetime.strptime(value, '%Y-%m-%d')
        return value
    except ValueError:
        logger.error(f"Invalid date format: {value}")
        return None

def validate_not_null(value):
    """Validator: NOT NULL check"""
    return not pd.isna(value) and str(value).strip() != ''

def validate_positive_decimal(value):
    """Validator: Positive decimal check"""
    if pd.isna(value):
        return True  # NULL allowed unless NOT NULL constraint
    try:
        return Decimal(str(value)) >= 0
    except (InvalidOperation, ValueError):
        return False

def validate_date_format(value):
    """Validator: ISO date format check"""
    if pd.isna(value) or value == '':
        return True
    try:
        datetime.strptime(value, '%Y-%m-%d')
        return True
    except ValueError:
        return False

# Account file layout (from CVACT01Y.cpy)
ACCOUNT_LAYOUT = {
    'colspecs': [
        (0, 11),    # ACCT-ID (PIC 9(11))
        (11, 12),   # ACCT-ACTIVE-STATUS (PIC X)
        (12, 23),   # ACCT-CURR-BAL (PIC S9(09)V99 COMP-3)
        (23, 34),   # ACCT-CREDIT-LIMIT (PIC S9(09)V99 COMP-3)
        (34, 45),   # ACCT-CASH-CREDIT-LIMIT (PIC S9(09)V99 COMP-3)
        (45, 55),   # ACCT-OPEN-DATE (PIC X(10))
        (55, 65),   # ACCT-EXPIRAION-DATE (PIC X(10))
        (65, 75),   # ACCT-REISSUE-DATE (PIC X(10))
        (75, 86),   # ACCT-CURR-CYC-CREDIT (PIC S9(09)V99 COMP-3)
        (86, 97),   # ACCT-CURR-CYC-DEBIT (PIC S9(09)V99 COMP-3)
        (97, 107),  # ACCT-ADDR-ZIP (PIC X(10))
        (107, 117), # ACCT-GROUP-ID (PIC X(10))
    ],
    'names': [
        'account_number',
        'active_status',
        'current_balance',
        'credit_limit',
        'cash_credit_limit',
        'open_date',
        'expiration_date',
        'reissue_date',
        'current_cycle_credit',
        'current_cycle_debit',
        'address_zip',
        'group_id'
    ],
    'transforms': {
        'current_balance': transform_comp3_to_decimal,
        'credit_limit': transform_comp3_to_decimal,
        'cash_credit_limit': transform_comp3_to_decimal,
        'current_cycle_credit': transform_comp3_to_decimal,
        'current_cycle_debit': transform_comp3_to_decimal,
        'open_date': transform_date_yyyymmdd_to_iso,
        'expiration_date': transform_date_yyyymmdd_to_iso,
        'reissue_date': transform_date_yyyymmdd_to_iso,
    },
    'validators': {
        'account_number': validate_not_null,
        'active_status': validate_not_null,
        'current_balance': validate_positive_decimal,
        'credit_limit': validate_positive_decimal,
        'open_date': validate_date_format,
        'expiration_date': validate_date_format,
    }
}

# Card file layout (from CVACT02Y.cpy)
CARD_LAYOUT = {
    'colspecs': [
        (0, 16),    # CARD-NUM (PIC X(16))
        (16, 27),   # CARD-ACCT-ID (PIC 9(11))
        (27, 77),   # CARD-EMBOSSED-NAME (PIC X(50))
        (77, 87),   # CARD-EXPIRAION-DATE (PIC X(10))
        (87, 88),   # CARD-ACTIVE-STATUS (PIC X)
    ],
    'names': [
        'card_number',
        'account_id',
        'embossed_name',
        'expiration_date',
        'active_status'
    ],
    'transforms': {
        'expiration_date': transform_date_yyyymmdd_to_iso,
    },
    'validators': {
        'card_number': validate_not_null,
        'account_id': validate_not_null,
        'embossed_name': validate_not_null,
        'expiration_date': validate_date_format,
        'active_status': validate_not_null,
    }
}

# Customer file layout (from CVCUS01Y.cpy)
CUSTOMER_LAYOUT = {
    'colspecs': [
        (0, 9),     # CUST-ID (PIC 9(09))
        (9, 34),    # CUST-FIRST-NAME (PIC X(25))
        (34, 59),   # CUST-MIDDLE-NAME (PIC X(25))
        (59, 84),   # CUST-LAST-NAME (PIC X(25))
        (84, 134),  # CUST-ADDR-LINE-1 (PIC X(50))
        (134, 184), # CUST-ADDR-LINE-2 (PIC X(50))
        (184, 234), # CUST-ADDR-LINE-3 (PIC X(50))
        (234, 236), # CUST-ADDR-STATE-CD (PIC X(02))
        (236, 239), # CUST-ADDR-COUNTRY-CD (PIC X(03))
        (239, 249), # CUST-ADDR-ZIP (PIC X(10))
        (249, 264), # CUST-PHONE-NUM-1 (PIC X(15))
        (264, 279), # CUST-PHONE-NUM-2 (PIC X(15))
        (279, 288), # CUST-SSN (PIC 9(09))
        (288, 308), # CUST-GOVT-ISSUED-ID (PIC X(20))
        (308, 318), # CUST-DOB-YYYY-MM-DD (PIC X(10))
        (318, 328), # CUST-EFT-ACCOUNT-ID (PIC X(10))
        (328, 329), # CUST-PRI-CARD-HOLDER-IND (PIC X)
        (329, 332), # CUST-FICO-CREDIT-SCORE (PIC 9(03))
    ],
    'names': [
        'customer_id',
        'first_name',
        'middle_name',
        'last_name',
        'address_line_1',
        'address_line_2',
        'address_line_3',
        'state_code',
        'country_code',
        'zip_code',
        'phone_number_1',
        'phone_number_2',
        'ssn',
        'govt_issued_id',
        'date_of_birth',
        'eft_account_id',
        'primary_cardholder_indicator',
        'fico_credit_score'
    ],
    'transforms': {
        'date_of_birth': transform_date_yyyymmdd_to_iso,
    },
    'validators': {
        'customer_id': validate_not_null,
        'first_name': validate_not_null,
        'last_name': validate_not_null,
        'address_line_1': validate_not_null,
        'state_code': validate_not_null,
        'zip_code': validate_not_null,
        'ssn': validate_not_null,
        'date_of_birth': validate_date_format,
    }
}

def main():
    """Main transformation execution"""
    if len(sys.argv) < 4:
        print("Usage: python transform_vsam_to_csv.py <input_file> <output_file> <layout>")
        print("Layouts: account, card, customer")
        sys.exit(1)
    
    input_file = sys.argv[1]
    output_file = sys.argv[2]
    layout_name = sys.argv[3].lower()
    
    layouts = {
        'account': ACCOUNT_LAYOUT,
        'card': CARD_LAYOUT,
        'customer': CUSTOMER_LAYOUT,
    }
    
    if layout_name not in layouts:
        logger.error(f"Unknown layout: {layout_name}")
        sys.exit(1)
    
    transformer = VSAMTransformer(input_file, output_file, layouts[layout_name])
    record_count, error_count = transformer.transform()
    
    if error_count > 0:
        logger.warning(f"Transformation completed with {error_count} errors")
        sys.exit(2)  # Warning exit code
    else:
        logger.info("Transformation successful")
        sys.exit(0)

if __name__ == '__main__':
    main()
```

### 5.3 Execute Transformations

**Transform all datasets**:

```bash
# Download exported files from S3
aws s3 sync s3://carddemo-migration-data/vsam-exports/ ./vsam-exports/

# Create output directory
mkdir -p ./csv-output

# Transform Account data
python3 scripts/transform_vsam_to_csv.py \
  ./vsam-exports/acctdata.txt \
  ./csv-output/account.csv \
  account

# Transform Card data
python3 scripts/transform_vsam_to_csv.py \
  ./vsam-exports/carddata.txt \
  ./csv-output/card.csv \
  card

# Transform Customer data
python3 scripts/transform_vsam_to_csv.py \
  ./vsam-exports/custdata.txt \
  ./csv-output/customer.csv \
  customer

# Verify CSV files created
ls -lh ./csv-output/
# Expected output:
# account.csv (50 records)
# card.csv (50 records)
# customer.csv (50 records)
```

### 5.4 CSV Validation

**Validate generated CSV files**:

```bash
# Count records
wc -l ./csv-output/*.csv
# Expected: 51 lines per file (50 data + 1 header)

# Inspect first 5 records
head -6 ./csv-output/account.csv

# Check for data quality issues
# - Verify no missing required fields (empty cells in NOT NULL columns)
# - Confirm decimal formatting (XX.XX with 2 decimal places)
# - Validate date format (YYYY-MM-DD)
# - Check for special characters properly escaped
```

**Sample account.csv output**:

```csv
account_number,active_status,current_balance,credit_limit,cash_credit_limit,open_date,expiration_date,reissue_date,current_cycle_credit,current_cycle_debit,address_zip,group_id
00000000001,Y,1940.00,20200.00,10200.00,2014-11-20,2025-05-20,2025-05-20,0.00,0.00,\N,000000000
00000000002,Y,1580.00,61300.00,54480.00,2013-06-19,2024-08-11,2024-08-11,0.00,0.00,\N,000000000
00000000003,Y,1470.00,49090.00,5380.00,2013-08-23,2024-01-10,2024-01-10,0.00,0.00,\N,000000000
```

---

## 6. Phase 3: PostgreSQL Schema Creation

### 6.1 Flyway Migration Structure

**Migration Script Organization** (in `src/main/resources/db/migration/`):

```
db/migration/
├── V1__create_tables.sql           # Core table definitions (accounts, cards, customers, transactions)
├── V2__create_indexes.sql          # Performance indexes (B-tree, composite, partial)
├── V3__seed_reference_data.sql     # Reference data (transaction types, categories)
└── V4__load_test_data.sql          # Test data from VSAM export (accounts, cards, customers)
```

### 6.2 V1: Table Creation Script

**Complete DDL** (`V1__create_tables.sql`):

```sql
-- ============================================================================
-- Flyway Migration V1: Create Core Tables
-- Purpose: Initialize PostgreSQL schema with all tables, constraints, and
--          foreign key relationships migrated from VSAM structure
-- ============================================================================

-- Set schema
SET search_path TO public;

-- Enable UUID extension for future use
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- ============================================================================
-- CUSTOMER TABLE (from CVCUS01Y.cpy)
-- ============================================================================
CREATE TABLE customer (
    customer_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    first_name VARCHAR(25) NOT NULL,
    middle_name VARCHAR(25),
    last_name VARCHAR(25) NOT NULL,
    address_line_1 VARCHAR(50) NOT NULL,
    address_line_2 VARCHAR(50),
    address_line_3 VARCHAR(50),
    state_code CHAR(2) NOT NULL,
    country_code CHAR(3) NOT NULL DEFAULT 'USA',
    zip_code VARCHAR(10) NOT NULL,
    phone_number_1 VARCHAR(15),
    phone_number_2 VARCHAR(15),
    ssn VARCHAR(9) NOT NULL UNIQUE,
    govt_issued_id VARCHAR(20),
    date_of_birth DATE NOT NULL,
    eft_account_id VARCHAR(10),
    primary_cardholder_indicator CHAR(1) DEFAULT 'Y',
    fico_credit_score SMALLINT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version INTEGER NOT NULL DEFAULT 0,
    CONSTRAINT chk_valid_dob CHECK (date_of_birth < CURRENT_DATE),
    CONSTRAINT chk_fico_range CHECK (fico_credit_score BETWEEN 300 AND 850),
    CONSTRAINT chk_primary_holder CHECK (primary_cardholder_indicator IN ('Y', 'N'))
);

COMMENT ON TABLE customer IS 'Customer master data migrated from VSAM CUSTFILE';
COMMENT ON COLUMN customer.ssn IS 'PII: Social Security Number - must be encrypted at rest';
COMMENT ON COLUMN customer.date_of_birth IS 'PII: Date of birth - access logged for GDPR compliance';

-- ============================================================================
-- ACCOUNT TABLE (from CVACT01Y.cpy)
-- ============================================================================
CREATE TABLE account (
    account_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    account_number VARCHAR(11) NOT NULL UNIQUE,
    active_status CHAR(1) NOT NULL DEFAULT 'Y',
    current_balance NUMERIC(12,2) NOT NULL DEFAULT 0.00,
    credit_limit NUMERIC(12,2) NOT NULL,
    cash_credit_limit NUMERIC(12,2) NOT NULL,
    open_date DATE NOT NULL,
    expiration_date DATE NOT NULL,
    reissue_date DATE,
    current_cycle_credit NUMERIC(12,2) NOT NULL DEFAULT 0.00,
    current_cycle_debit NUMERIC(12,2) NOT NULL DEFAULT 0.00,
    address_zip VARCHAR(10),
    group_id VARCHAR(10),
    customer_id BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version INTEGER NOT NULL DEFAULT 0,
    CONSTRAINT fk_account_customer FOREIGN KEY (customer_id) 
        REFERENCES customer(customer_id) ON DELETE RESTRICT,
    CONSTRAINT chk_positive_balance CHECK (current_balance >= 0),
    CONSTRAINT chk_valid_expiration CHECK (expiration_date > open_date),
    CONSTRAINT chk_status_values CHECK (active_status IN ('Y', 'N'))
);

COMMENT ON TABLE account IS 'Account master data migrated from VSAM ACCTFILE';
COMMENT ON COLUMN account.current_balance IS 'Migrated from PIC S9(09)V99 COMP-3 - preserves exact decimal precision';

-- ============================================================================
-- CARD TABLE (from CVACT02Y.cpy)
-- ============================================================================
CREATE TABLE card (
    card_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    card_number VARCHAR(16) NOT NULL UNIQUE,
    account_id BIGINT NOT NULL,
    embossed_name VARCHAR(50) NOT NULL,
    expiration_date DATE NOT NULL,
    active_status CHAR(1) NOT NULL DEFAULT 'Y',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version INTEGER NOT NULL DEFAULT 0,
    CONSTRAINT fk_card_account FOREIGN KEY (account_id) 
        REFERENCES account(account_id) ON DELETE RESTRICT,
    CONSTRAINT chk_card_status CHECK (active_status IN ('Y', 'N')),
    CONSTRAINT chk_future_expiration CHECK (expiration_date > CURRENT_DATE)
);

COMMENT ON TABLE card IS 'Card master data migrated from VSAM CARDFILE';
COMMENT ON COLUMN card.card_number IS 'PCI-DSS: Full PAN stored for transaction processing - must be encrypted at rest and masked in logs';

-- ============================================================================
-- CARD_XREF TABLE (from CVACT03Y.cpy)
-- ============================================================================
CREATE TABLE card_xref (
    card_number VARCHAR(16) PRIMARY KEY,
    customer_id BIGINT NOT NULL,
    account_id BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_xref_customer FOREIGN KEY (customer_id) 
        REFERENCES customer(customer_id) ON DELETE RESTRICT,
    CONSTRAINT fk_xref_account FOREIGN KEY (account_id) 
        REFERENCES account(account_id) ON DELETE RESTRICT
);

COMMENT ON TABLE card_xref IS 'Card-to-account cross-reference - replaces VSAM AIX CXACAIX';

-- ============================================================================
-- TRANSACTION_TYPE TABLE (from CVTRA03Y.cpy)
-- ============================================================================
CREATE TABLE transaction_type (
    type_code VARCHAR(2) PRIMARY KEY,
    type_description VARCHAR(50) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_type_code_format CHECK (type_code ~ '^[0-9]{2}$')
);

COMMENT ON TABLE transaction_type IS 'Transaction type reference data - replaces VSAM TRANTYPE file';

-- ============================================================================
-- TRANSACTION_CATEGORY TABLE (from CVTRA04Y.cpy)
-- ============================================================================
CREATE TABLE transaction_category (
    transaction_type_code VARCHAR(2) NOT NULL,
    category_code CHAR(4) NOT NULL,
    category_description VARCHAR(50) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (transaction_type_code, category_code),
    CONSTRAINT fk_category_type FOREIGN KEY (transaction_type_code) 
        REFERENCES transaction_type(type_code) ON DELETE RESTRICT
);

COMMENT ON TABLE transaction_category IS 'Transaction category reference data - replaces VSAM TRANCATG file';

-- ============================================================================
-- TRANSACTION TABLE (from CVTRA05Y.cpy)
-- ============================================================================
CREATE TABLE transaction (
    transaction_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    transaction_number VARCHAR(16) NOT NULL UNIQUE,
    transaction_type_code VARCHAR(2) NOT NULL,
    transaction_category_code CHAR(4) NOT NULL,
    transaction_source VARCHAR(10) NOT NULL,
    description VARCHAR(100) NOT NULL,
    amount NUMERIC(11,2) NOT NULL,
    merchant_id VARCHAR(9),
    merchant_name VARCHAR(50),
    merchant_city VARCHAR(50),
    merchant_zip VARCHAR(10),
    card_number VARCHAR(16) NOT NULL,
    original_timestamp TIMESTAMP NOT NULL,
    processing_timestamp TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    account_id BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version INTEGER NOT NULL DEFAULT 0,
    CONSTRAINT fk_transaction_account FOREIGN KEY (account_id) 
        REFERENCES account(account_id) ON DELETE RESTRICT,
    CONSTRAINT fk_transaction_type FOREIGN KEY (transaction_type_code) 
        REFERENCES transaction_type(type_code),
    CONSTRAINT fk_transaction_category FOREIGN KEY (transaction_type_code, transaction_category_code) 
        REFERENCES transaction_category(transaction_type_code, category_code),
    CONSTRAINT chk_positive_amount CHECK (amount > 0)
);

COMMENT ON TABLE transaction IS 'Transaction history migrated from VSAM TRANSACT file';
COMMENT ON COLUMN transaction.card_number IS 'PCI-DSS: Full PAN for transaction processing - masked in application logs';

-- ============================================================================
-- DAILY_TRANSACTION TABLE (from CVTRA06Y.cpy)
-- ============================================================================
CREATE TABLE daily_transaction (
    daily_transaction_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    transaction_number VARCHAR(16) NOT NULL,
    card_number VARCHAR(16) NOT NULL,
    transaction_type_code VARCHAR(2) NOT NULL,
    transaction_category_code CHAR(4) NOT NULL,
    transaction_source VARCHAR(10) NOT NULL,
    description VARCHAR(100) NOT NULL,
    amount NUMERIC(11,2) NOT NULL,
    merchant_id VARCHAR(9),
    merchant_name VARCHAR(50),
    merchant_city VARCHAR(50),
    merchant_zip VARCHAR(10),
    original_timestamp TIMESTAMP NOT NULL,
    processed BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_daily_positive_amount CHECK (amount > 0)
);

COMMENT ON TABLE daily_transaction IS 'Daily transaction feed for batch processing - migrated from VSAM DALYTRAN';

-- ============================================================================
-- DISCLOSURE_GROUP TABLE (from CVTRA02Y.cpy)
-- ============================================================================
CREATE TABLE disclosure_group (
    account_group_id VARCHAR(10) NOT NULL,
    transaction_type_code VARCHAR(2) NOT NULL,
    transaction_category_code CHAR(4) NOT NULL,
    interest_rate NUMERIC(6,2) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (account_group_id, transaction_type_code, transaction_category_code),
    CONSTRAINT fk_disclosure_type FOREIGN KEY (transaction_type_code) 
        REFERENCES transaction_type(type_code),
    CONSTRAINT fk_disclosure_category FOREIGN KEY (transaction_type_code, transaction_category_code) 
        REFERENCES transaction_category(transaction_type_code, category_code),
    CONSTRAINT chk_interest_rate CHECK (interest_rate >= 0 AND interest_rate <= 99.99)
);

COMMENT ON TABLE disclosure_group IS 'Interest rate configurations by account group - migrated from VSAM DISCGRP';

-- ============================================================================
-- TRANSACTION_CATEGORY_BALANCE TABLE (from CVTRA01Y.cpy)
-- ============================================================================
CREATE TABLE transaction_category_balance (
    account_id BIGINT NOT NULL,
    transaction_type_code VARCHAR(2) NOT NULL,
    transaction_category_code CHAR(4) NOT NULL,
    category_balance NUMERIC(11,2) NOT NULL DEFAULT 0.00,
    last_updated TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version INTEGER NOT NULL DEFAULT 0,
    PRIMARY KEY (account_id, transaction_type_code, transaction_category_code),
    CONSTRAINT fk_catbal_account FOREIGN KEY (account_id) 
        REFERENCES account(account_id) ON DELETE CASCADE,
    CONSTRAINT fk_catbal_type FOREIGN KEY (transaction_type_code) 
        REFERENCES transaction_type(type_code),
    CONSTRAINT fk_catbal_category FOREIGN KEY (transaction_type_code, transaction_category_code) 
        REFERENCES transaction_category(transaction_type_code, category_code)
);

COMMENT ON TABLE transaction_category_balance IS 'Category-level balance tracking - migrated from VSAM TCATBAL';

-- ============================================================================
-- APP_USER TABLE (from CSUSR01Y.cpy)
-- ============================================================================
CREATE TABLE app_user (
    user_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    username VARCHAR(50) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    first_name VARCHAR(50) NOT NULL,
    last_name VARCHAR(50) NOT NULL,
    user_type CHAR(1) NOT NULL,
    last_login TIMESTAMP,
    account_locked BOOLEAN NOT NULL DEFAULT FALSE,
    failed_login_attempts INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version INTEGER NOT NULL DEFAULT 0,
    CONSTRAINT chk_user_type CHECK (user_type IN ('A', 'R')),
    CONSTRAINT chk_login_attempts CHECK (failed_login_attempts >= 0)
);

COMMENT ON TABLE app_user IS 'User security records - migrated from VSAM USRSEC with BCrypt password hashing';
COMMENT ON COLUMN app_user.password_hash IS 'BCrypt hashed password (60 characters) - replaces legacy 8-character plain-text passwords';

-- ============================================================================
-- Flyway Metadata
-- ============================================================================
COMMENT ON SCHEMA public IS 'CardDemo PostgreSQL schema v1.0 - Migrated from VSAM datasets';

-- Grant permissions to application user
GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO postgres_app_user;
GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA public TO postgres_app_user;
```

### 6.3 V2: Index Creation Script

**Performance Indexes** (`V2__create_indexes.sql`):

```sql
-- ============================================================================
-- Flyway Migration V2: Create Performance Indexes
-- Purpose: Optimize query performance for common access patterns
-- ============================================================================

-- Customer indexes
CREATE INDEX idx_customer_ssn ON customer(ssn);
CREATE INDEX idx_customer_name ON customer(last_name, first_name);
CREATE INDEX idx_customer_zip ON customer(zip_code);

-- Account indexes
CREATE INDEX idx_account_customer ON account(customer_id);
CREATE INDEX idx_account_group ON account(group_id);
CREATE INDEX idx_account_status ON account(active_status) WHERE active_status = 'Y';  -- Partial index
CREATE INDEX idx_account_expiration ON account(expiration_date) WHERE active_status = 'Y';

-- Card indexes
CREATE INDEX idx_card_account ON card(account_id);
CREATE INDEX idx_card_expiration ON card(expiration_date) WHERE active_status = 'Y';  -- Partial index

-- Card cross-reference indexes
CREATE INDEX idx_xref_account ON card_xref(account_id);
CREATE INDEX idx_xref_customer ON card_xref(customer_id);

-- Transaction indexes (composite for date-ordered queries)
CREATE INDEX idx_transaction_account_date ON transaction(account_id, processing_timestamp DESC);
CREATE INDEX idx_transaction_card ON transaction(card_number);
CREATE INDEX idx_transaction_merchant ON transaction(merchant_id);
CREATE INDEX idx_transaction_type_category ON transaction(transaction_type_code, transaction_category_code);
CREATE INDEX idx_transaction_timestamp ON transaction(processing_timestamp DESC);

-- Daily transaction indexes
CREATE INDEX idx_daily_transaction_card ON daily_transaction(card_number);
CREATE INDEX idx_daily_transaction_processed ON daily_transaction(processed) WHERE processed = FALSE;
CREATE INDEX idx_daily_transaction_timestamp ON daily_transaction(original_timestamp);

-- Category balance indexes
CREATE INDEX idx_catbal_account ON transaction_category_balance(account_id);

-- Disclosure group indexes
CREATE INDEX idx_disclosure_group ON disclosure_group(account_group_id);

-- Transaction category indexes
CREATE INDEX idx_category_type ON transaction_category(transaction_type_code);

-- App user indexes
CREATE INDEX idx_user_username ON app_user(username);
CREATE INDEX idx_user_type ON app_user(user_type);
CREATE INDEX idx_user_locked ON app_user(account_locked) WHERE account_locked = TRUE;

-- Analyze tables for query planner statistics
ANALYZE customer;
ANALYZE account;
ANALYZE card;
ANALYZE card_xref;
ANALYZE transaction;
ANALYZE daily_transaction;
ANALYZE transaction_type;
ANALYZE transaction_category;
ANALYZE disclosure_group;
ANALYZE transaction_category_balance;
ANALYZE app_user;
```

### 6.4 V3: Reference Data Seeding

**Reference Data Load** (`V3__seed_reference_data.sql`):

```sql
-- ============================================================================
-- Flyway Migration V3: Seed Reference Data
-- Purpose: Load transaction types, categories, and disclosure groups
-- ============================================================================

-- Transaction Type Reference Data (from trantype.txt)
INSERT INTO transaction_type (type_code, type_description) VALUES
    ('01', 'Purchase'),
    ('02', 'Cash Advance'),
    ('03', 'Balance Transfer'),
    ('04', 'Payment'),
    ('05', 'Refund'),
    ('06', 'Fee'),
    ('07', 'Interest Charge');

-- Transaction Category Reference Data (from trancatg.txt)
INSERT INTO transaction_category (transaction_type_code, category_code, category_description) VALUES
    ('01', '0001', 'Grocery'),
    ('01', '0002', 'Gas Station'),
    ('01', '0003', 'Restaurant'),
    ('01', '0004', 'Entertainment'),
    ('01', '0005', 'Travel'),
    ('01', '0006', 'Retail'),
    ('01', '0007', 'Online Purchase'),
    ('01', '0008', 'Utilities'),
    ('02', '0100', 'ATM Withdrawal'),
    ('02', '0101', 'Cash Advance Fee'),
    ('03', '0200', 'Balance Transfer'),
    ('03', '0201', 'Balance Transfer Fee'),
    ('04', '0300', 'Payment Received'),
    ('05', '0400', 'Refund'),
    ('06', '0500', 'Annual Fee'),
    ('06', '0501', 'Late Payment Fee'),
    ('06', '0502', 'Over Limit Fee'),
    ('07', '0600', 'Interest Charge');

-- Disclosure Group Reference Data (from discgrp.txt - 51 records)
-- Sample groups with APR configurations
INSERT INTO disclosure_group (account_group_id, transaction_type_code, transaction_category_code, interest_rate) VALUES
    ('000000000', '01', '0001', 16.99),  -- Standard group: Purchase-Grocery
    ('000000000', '01', '0002', 16.99),  -- Standard group: Purchase-Gas
    ('000000000', '01', '0003', 16.99),  -- Standard group: Purchase-Restaurant
    ('000000000', '01', '0004', 16.99),  -- Standard group: Purchase-Entertainment
    ('000000000', '01', '0005', 16.99),  -- Standard group: Purchase-Travel
    ('000000000', '02', '0100', 24.99),  -- Standard group: Cash Advance
    ('000000000', '03', '0200', 12.99),  -- Standard group: Balance Transfer
    ('PREMIUM01', '01', '0001', 14.99),  -- Premium group: Lower purchase APR
    ('PREMIUM01', '02', '0100', 19.99);  -- Premium group: Lower cash advance APR

-- Verify reference data load
DO $$
DECLARE
    v_transaction_types_count INTEGER;
    v_transaction_categories_count INTEGER;
    v_disclosure_groups_count INTEGER;
BEGIN
    SELECT COUNT(*) INTO v_transaction_types_count FROM transaction_type;
    SELECT COUNT(*) INTO v_transaction_categories_count FROM transaction_category;
    SELECT COUNT(*) INTO v_disclosure_groups_count FROM disclosure_group;
    
    RAISE NOTICE 'Reference data loaded:';
    RAISE NOTICE '  Transaction Types: %', v_transaction_types_count;
    RAISE NOTICE '  Transaction Categories: %', v_transaction_categories_count;
    RAISE NOTICE '  Disclosure Groups: %', v_disclosure_groups_count;
    
    IF v_transaction_types_count <> 7 THEN
        RAISE EXCEPTION 'Expected 7 transaction types, found %', v_transaction_types_count;
    END IF;
    
    IF v_transaction_categories_count < 18 THEN
        RAISE WARNING 'Expected at least 18 transaction categories, found %', v_transaction_categories_count;
    END IF;
END $$;
```

### 6.5 Execute Flyway Migrations

**Run Flyway Migration**:

```bash
# Configure Flyway connection
export FLYWAY_URL="jdbc:postgresql://carddemo-db.cluster-xxxxx.us-east-1.rds.amazonaws.com:5432/carddemo"
export FLYWAY_USER="postgres_admin"
export FLYWAY_PASSWORD="$(aws secretsmanager get-secret-value --secret-id carddemo/database/credentials --query SecretString --output text | jq -r '.db_admin_password')"
export FLYWAY_LOCATIONS="filesystem:./src/main/resources/db/migration"

# Execute migrations V1-V3
flyway migrate

# Expected output:
# Flyway version: 10.13.0
# Successfully validated 3 migrations
# Current schema version: None
# Migrating schema "public" to version "1 - create tables"
# Migrating schema "public" to version "2 - create indexes"
# Migrating schema "public" to version "3 - seed reference data"
# Successfully applied 3 migrations to schema "public"
# Schema version: 3

# Verify migrations
flyway info

# Expected output:
# +-----------+---------+---------------------+------+---------------------+----------+
# | Category  | Version | Description         | Type | Installed On        | State    |
# +-----------+---------+---------------------+------+---------------------+----------+
# | Versioned | 1       | create tables       | SQL  | 2024-06-15 12:30:00 | Success  |
# | Versioned | 2       | create indexes      | SQL  | 2024-06-15 12:30:15 | Success  |
# | Versioned | 3       | seed reference data | SQL  | 2024-06-15 12:30:20 | Success  |
# +-----------+---------+---------------------+------+---------------------+----------+
```

---

## 7. Phase 4: Data Loading

### 7.1 PostgreSQL COPY Command (High Performance)

**Bulk Load Using COPY** (fastest method - 10,000+ rows/second):

```sql
-- Connect to PostgreSQL
psql -h carddemo-db.cluster-xxxxx.us-east-1.rds.amazonaws.com \
     -U postgres_admin \
     -d carddemo \
     -p 5432

-- Load Customer data
\COPY customer (first_name, middle_name, last_name, address_line_1, address_line_2, address_line_3, state_code, country_code, zip_code, phone_number_1, phone_number_2, ssn, govt_issued_id, date_of_birth, eft_account_id, primary_cardholder_indicator, fico_credit_score) 
FROM './csv-output/customer.csv' 
WITH (FORMAT CSV, HEADER TRUE, DELIMITER ',', NULL '\\N');

-- Verify load
SELECT COUNT(*) FROM customer;
-- Expected: 50 rows

-- Load Account data (requires customer_id FK resolution)
-- Note: customer_id must be resolved from SSN or customer natural key
\COPY account (account_number, active_status, current_balance, credit_limit, cash_credit_limit, open_date, expiration_date, reissue_date, current_cycle_credit, current_cycle_debit, address_zip, group_id, customer_id)
FROM './csv-output/account.csv'
WITH (FORMAT CSV, HEADER TRUE, DELIMITER ',', NULL '\\N');

-- Verify load
SELECT COUNT(*) FROM account;
-- Expected: 50 rows

-- Load Card data (requires account_id FK resolution)
\COPY card (card_number, account_id, embossed_name, expiration_date, active_status)
FROM './csv-output/card.csv'
WITH (FORMAT CSV, HEADER TRUE, DELIMITER ',', NULL '\\N');

-- Verify load
SELECT COUNT(*) FROM card;
-- Expected: 50 rows

-- Load Card Cross-Reference
\COPY card_xref (card_number, customer_id, account_id)
FROM './csv-output/card_xref.csv'
WITH (FORMAT CSV, HEADER TRUE, DELIMITER ',', NULL '\\N');

-- Verify load
SELECT COUNT(*) FROM card_xref;
-- Expected: 50 rows
```

### 7.2 Flyway V4 Migration (SQL INSERT Statements)

**Alternative: SQL INSERT Script** (`V4__load_test_data.sql`):

```sql
-- ============================================================================
-- Flyway Migration V4: Load Test Data from VSAM Export
-- Purpose: Insert 50 accounts, 50 cards, 50 customers from migrated VSAM data
-- ============================================================================

-- Load Customer data (50 records from custdata.txt)
-- Note: This is a sample - full script would include all 50 customers
INSERT INTO customer (first_name, middle_name, last_name, address_line_1, address_line_2, address_line_3, state_code, country_code, zip_code, phone_number_1, phone_number_2, ssn, govt_issued_id, date_of_birth, eft_account_id, primary_cardholder_indicator, fico_credit_score) VALUES
    ('Immanuel', 'Madeline', 'Kessler', '1290 Beatrice Islands', 'Apt. 368', NULL, 'CA', 'USA', '90210', '5551234567', NULL, '020973888', 'DL-CA-X1234567', '1961-06-08', NULL, 'Y', 720),
    ('Adriane', 'Jolie', 'Anderson', '4253 Brian Creek', 'Suite 100', NULL, 'NY', 'USA', '10001', '5559876543', NULL, '154789632', 'DL-NY-Y2345678', '1975-03-15', NULL, 'Y', 680),
    ('Tremayne', 'Kane', 'Rutherford', '829 Welch Points', NULL, NULL, 'TX', 'USA', '75201', '5555551234', NULL, '987654321', 'DL-TX-Z3456789', '1982-11-20', NULL, 'Y', 750);
    -- ... (remaining 47 customers)

-- Verify customer load
SELECT COUNT(*) FROM customer;
-- Expected: 50 rows

-- Load Account data (50 records from acctdata.txt)
-- FK resolution: customer_id looked up by SSN
INSERT INTO account (account_number, active_status, current_balance, credit_limit, cash_credit_limit, open_date, expiration_date, reissue_date, current_cycle_credit, current_cycle_debit, address_zip, group_id, customer_id) VALUES
    ('00000000001', 'Y', 1940.00, 20200.00, 10200.00, '2014-11-20', '2025-05-20', '2025-05-20', 0.00, 0.00, NULL, '000000000', (SELECT customer_id FROM customer WHERE ssn = '020973888')),
    ('00000000002', 'Y', 1580.00, 61300.00, 54480.00, '2013-06-19', '2024-08-11', '2024-08-11', 0.00, 0.00, NULL, '000000000', (SELECT customer_id FROM customer WHERE ssn = '154789632')),
    ('00000000003', 'Y', 1470.00, 49090.00, 5380.00, '2013-08-23', '2024-01-10', '2024-01-10', 0.00, 0.00, NULL, '000000000', (SELECT customer_id FROM customer WHERE ssn = '987654321'));
    -- ... (remaining 47 accounts)

-- Verify account load
SELECT COUNT(*) FROM account;
-- Expected: 50 rows

-- Load Card data (50 records from carddata.txt)
-- FK resolution: account_id looked up by account_number
INSERT INTO card (card_number, account_id, embossed_name, expiration_date, active_status) VALUES
    ('4556737586899855', (SELECT account_id FROM account WHERE account_number = '00000000001'), 'IMMANUEL M KESSLER', '2027-12-31', 'Y'),
    ('4556737586899863', (SELECT account_id FROM account WHERE account_number = '00000000002'), 'ADRIANE J ANDERSON', '2026-08-31', 'Y'),
    ('4556737586899871', (SELECT account_id FROM account WHERE account_number = '00000000003'), 'TREMAYNE K RUTHERFORD', '2025-06-30', 'Y');
    -- ... (remaining 47 cards)

-- Verify card load
SELECT COUNT(*) FROM card;
-- Expected: 50 rows

-- Load Card Cross-Reference
INSERT INTO card_xref (card_number, customer_id, account_id)
SELECT c.card_number, a.customer_id, a.account_id
FROM card c
JOIN account a ON c.account_id = a.account_id;

-- Verify cross-reference load
SELECT COUNT(*) FROM card_xref;
-- Expected: 50 rows

-- Final verification
DO $$
DECLARE
    v_customer_count INTEGER;
    v_account_count INTEGER;
    v_card_count INTEGER;
    v_xref_count INTEGER;
BEGIN
    SELECT COUNT(*) INTO v_customer_count FROM customer;
    SELECT COUNT(*) INTO v_account_count FROM account;
    SELECT COUNT(*) INTO v_card_count FROM card;
    SELECT COUNT(*) INTO v_xref_count FROM card_xref;
    
    RAISE NOTICE 'Data migration V4 completed:';
    RAISE NOTICE '  Customers: %', v_customer_count;
    RAISE NOTICE '  Accounts: %', v_account_count;
    RAISE NOTICE '  Cards: %', v_card_count;
    RAISE NOTICE '  Card Cross-References: %', v_xref_count;
    
    IF v_customer_count <> 50 THEN
        RAISE EXCEPTION 'Expected 50 customers, found %', v_customer_count;
    END IF;
    
    IF v_account_count <> 50 THEN
        RAISE EXCEPTION 'Expected 50 accounts, found %', v_account_count;
    END IF;
    
    IF v_card_count <> 50 THEN
        RAISE EXCEPTION 'Expected 50 cards, found %', v_card_count;
    END IF;
    
    IF v_xref_count <> 50 THEN
        RAISE EXCEPTION 'Expected 50 card cross-references, found %', v_xref_count;
    END IF;
    
    RAISE NOTICE 'All data validation checks passed!';
END $$;
```

**Execute V4 Migration**:

```bash
# Run Flyway migration for data load
flyway migrate

# Expected output:
# Migrating schema "public" to version "4 - load test data"
# Successfully applied 1 migration to schema "public"
# Schema version: 4
```

---

## 8. Phase 5: Post-Migration Validation

### 8.1 Record Count Reconciliation

**SQL Validation Queries**:

```sql
-- ============================================================================
-- Post-Migration Validation Script
-- ============================================================================

-- Record count comparison
SELECT 'customer' AS table_name, COUNT(*) AS row_count FROM customer
UNION ALL
SELECT 'account', COUNT(*) FROM account
UNION ALL
SELECT 'card', COUNT(*) FROM card
UNION ALL
SELECT 'card_xref', COUNT(*) FROM card_xref
UNION ALL
SELECT 'transaction_type', COUNT(*) FROM transaction_type
UNION ALL
SELECT 'transaction_category', COUNT(*) FROM transaction_category
UNION ALL
SELECT 'disclosure_group', COUNT(*) FROM disclosure_group;

-- Expected output:
-- table_name               | row_count
-- -------------------------+-----------
-- customer                 |        50
-- account                  |        50
-- card                     |        50
-- card_xref                |        50
-- transaction_type         |         7
-- transaction_category     |        18
-- disclosure_group         |        51
```

### 8.2 Referential Integrity Validation

**Foreign Key Validation**:

```sql
-- Check for orphaned account records (missing customer FK)
SELECT a.account_id, a.customer_id
FROM account a
LEFT JOIN customer c ON a.customer_id = c.customer_id
WHERE c.customer_id IS NULL;
-- Expected: 0 rows

-- Check for orphaned card records (missing account FK)
SELECT c.card_id, c.account_id
FROM card c
LEFT JOIN account a ON c.account_id = a.account_id
WHERE a.account_id IS NULL;
-- Expected: 0 rows

-- Check for orphaned card_xref records
SELECT cx.card_number
FROM card_xref cx
LEFT JOIN card c ON cx.card_number = c.card_number
WHERE c.card_number IS NULL;
-- Expected: 0 rows

-- Verify card-to-account consistency
SELECT 
    (SELECT COUNT(*) FROM card) AS total_cards,
    (SELECT COUNT(*) FROM card_xref) AS total_xrefs,
    (SELECT COUNT(*) FROM card) - (SELECT COUNT(*) FROM card_xref) AS difference;
-- Expected difference: 0
```

### 8.3 Data Quality Validation

**Business Rule Validation**:

```sql
-- Check for negative account balances (should be prevented by CHECK constraint)
SELECT account_id, account_number, current_balance
FROM account
WHERE current_balance < 0;
-- Expected: 0 rows

-- Check for expired card expiration dates (should fail CHECK constraint)
SELECT card_id, card_number, expiration_date
FROM card
WHERE expiration_date <= CURRENT_DATE;
-- Expected: 0 rows (or address expiring cards)

-- Check for invalid FICO scores
SELECT customer_id, fico_credit_score
FROM customer
WHERE fico_credit_score NOT BETWEEN 300 AND 850;
-- Expected: 0 rows

-- Check for NULL in NOT NULL columns
SELECT 'customer' AS table_name, COUNT(*) AS null_count
FROM customer
WHERE first_name IS NULL OR last_name IS NULL OR ssn IS NULL
UNION ALL
SELECT 'account', COUNT(*)
FROM account
WHERE account_number IS NULL OR customer_id IS NULL
UNION ALL
SELECT 'card', COUNT(*)
FROM card
WHERE card_number IS NULL OR account_id IS NULL;
-- Expected: All null_count = 0
```

### 8.4 Numeric Precision Validation

**Verify Decimal Precision** (critical for financial data):

```sql
-- Sample account balance verification
-- Compare against known VSAM source values
SELECT 
    account_number,
    current_balance,
    credit_limit,
    CASE 
        WHEN account_number = '00000000001' AND current_balance = 1940.00 THEN 'PASS'
        WHEN account_number = '00000000002' AND current_balance = 1580.00 THEN 'PASS'
        ELSE 'FAIL'
    END AS validation_status
FROM account
WHERE account_number IN ('00000000001', '00000000002');

-- Check decimal scale (should be exactly 2 decimal places)
SELECT account_number, current_balance
FROM account
WHERE CAST(current_balance AS TEXT) NOT LIKE '%.%'
   OR LENGTH(SPLIT_PART(CAST(current_balance AS TEXT), '.', 2)) <> 2;
-- Expected: 0 rows (all balances should have .XX format)
```

### 8.5 Performance Validation

**Query Performance Benchmarks**:

```sql
-- Benchmark: Account lookup by account_number (should use unique index)
EXPLAIN ANALYZE
SELECT * FROM account WHERE account_number = '00000000001';
-- Expected execution time: <10ms
-- Expected plan: Index Scan using account_account_number_key

-- Benchmark: Card lookup by card_number (should use unique index)
EXPLAIN ANALYZE
SELECT * FROM card WHERE card_number = '4556737586899855';
-- Expected execution time: <10ms
-- Expected plan: Index Scan using card_card_number_key

-- Benchmark: Account-to-cards join (should use FK index)
EXPLAIN ANALYZE
SELECT a.account_number, c.card_number, c.embossed_name
FROM account a
JOIN card c ON a.account_id = c.account_id
WHERE a.account_number = '00000000001';
-- Expected execution time: <20ms
-- Expected plan: Nested Loop with Index Scans

-- Benchmark: Customer-to-accounts join (should use FK index)
EXPLAIN ANALYZE
SELECT cu.first_name, cu.last_name, a.account_number, a.current_balance
FROM customer cu
JOIN account a ON cu.customer_id = a.customer_id
WHERE cu.ssn = '020973888';
-- Expected execution time: <30ms
```

### 8.6 Security Validation

**PCI-DSS Compliance Checks**:

```sql
-- Verify card numbers are 16 digits
SELECT card_number
FROM card
WHERE LENGTH(card_number) <> 16 OR card_number !~ '^\d{16}$';
-- Expected: 0 rows

-- Verify SSNs are 9 digits
SELECT ssn
FROM customer
WHERE LENGTH(ssn) <> 9 OR ssn !~ '^\d{9}$';
-- Expected: 0 rows

-- Verify password hashes are BCrypt format (should be 60 characters)
SELECT username, LENGTH(password_hash) AS hash_length
FROM app_user
WHERE LENGTH(password_hash) <> 60;
-- Expected: 0 rows (if user data loaded)

-- Check for plain-text passwords (should not exist)
SELECT username
FROM app_user
WHERE password_hash NOT LIKE '$2a$%' AND password_hash NOT LIKE '$2b$%' AND password_hash NOT LIKE '$2y$%';
-- Expected: 0 rows
```

### 8.7 Validation Report Generation

**Generate Migration Report**:

```bash
#!/bin/bash
# generate_migration_report.sh

echo "=================================================="
echo " CardDemo Data Migration Validation Report"
echo "=================================================="
echo "Generated: $(date)"
echo ""

echo "=== Record Counts ==="
psql -h $DB_HOST -U $DB_USER -d carddemo -t -c "
SELECT table_name || ': ' || COUNT(*) 
FROM (
    SELECT 'customer' AS table_name, customer_id FROM customer
    UNION ALL SELECT 'account', account_id FROM account
    UNION ALL SELECT 'card', card_id FROM card
    UNION ALL SELECT 'card_xref', card_number FROM card_xref
    UNION ALL SELECT 'transaction_type', type_code FROM transaction_type
    UNION ALL SELECT 'transaction_category', category_code FROM transaction_category
    UNION ALL SELECT 'disclosure_group', account_group_id FROM disclosure_group
) counts
GROUP BY table_name;"

echo ""
echo "=== Referential Integrity ==="
ORPHANED_ACCOUNTS=$(psql -h $DB_HOST -U $DB_USER -d carddemo -t -c "SELECT COUNT(*) FROM account a LEFT JOIN customer c ON a.customer_id = c.customer_id WHERE c.customer_id IS NULL;")
ORPHANED_CARDS=$(psql -h $DB_HOST -U $DB_USER -d carddemo -t -c "SELECT COUNT(*) FROM card c LEFT JOIN account a ON c.account_id = a.account_id WHERE a.account_id IS NULL;")

echo "Orphaned Accounts: $ORPHANED_ACCOUNTS (expected: 0)"
echo "Orphaned Cards: $ORPHANED_CARDS (expected: 0)"

echo ""
echo "=== Data Quality ==="
NEGATIVE_BALANCES=$(psql -h $DB_HOST -U $DB_USER -d carddemo -t -c "SELECT COUNT(*) FROM account WHERE current_balance < 0;")
EXPIRED_CARDS=$(psql -h $DB_HOST -U $DB_USER -d carddemo -t -c "SELECT COUNT(*) FROM card WHERE expiration_date <= CURRENT_DATE;")

echo "Negative Balances: $NEGATIVE_BALANCES (expected: 0)"
echo "Expired Cards: $EXPIRED_CARDS (expected: 0)"

echo ""
echo "=== Migration Status ==="
if [[ $ORPHANED_ACCOUNTS -eq 0 && $ORPHANED_CARDS -eq 0 && $NEGATIVE_BALANCES -eq 0 ]]; then
    echo "✅ Migration PASSED all validation checks"
    exit 0
else
    echo "❌ Migration FAILED validation checks"
    exit 1
fi
```

**Run Validation Report**:

```bash
chmod +x generate_migration_report.sh
./generate_migration_report.sh

# Expected output:
# ==================================================
#  CardDemo Data Migration Validation Report
# ==================================================
# Generated: 2024-06-15 12:45:30
#
# === Record Counts ===
# customer: 50
# account: 50
# card: 50
# card_xref: 50
# transaction_type: 7
# transaction_category: 18
# disclosure_group: 51
#
# === Referential Integrity ===
# Orphaned Accounts: 0 (expected: 0)
# Orphaned Cards: 0 (expected: 0)
#
# === Data Quality ===
# Negative Balances: 0 (expected: 0)
# Expired Cards: 0 (expected: 0)
#
# === Migration Status ===
# ✅ Migration PASSED all validation checks
```

---

## 9. Data Type Mapping Reference

### 9.1 Complete COBOL to PostgreSQL Mapping Table

| COBOL Picture Clause | COBOL Example | PostgreSQL Type | Java Type | Bean Validation | Migration Notes |
|:--------------------|:--------------|:----------------|:----------|:----------------|:---------------|
| **PIC 9(n)** (n ≤ 9) | `PIC 9(09)` | `INTEGER` | `Integer` | `@Min`, `@Max` | Unsigned integer, leading zeros preserved if stored as VARCHAR |
| **PIC 9(n)** (n > 9) | `PIC 9(11)` | `BIGINT` or `VARCHAR(n)` | `Long` or `String` | `@Size(min=n, max=n)` | Use VARCHAR to preserve leading zeros (e.g., account numbers) |
| **PIC S9(n)** | `PIC S9(09)` | `INTEGER` or `BIGINT` | `Integer` or `Long` | `@Min`, `@Max` | Signed integer |
| **PIC S9(n)V9(m) COMP-3** | `PIC S9(09)V99 COMP-3` | `NUMERIC(n+m, m)` | `BigDecimal` | `@Digits(integer=n, fraction=m)` | **CRITICAL**: Use BigDecimal for exact precision. COMP-3 = packed decimal |
| **PIC X(n)** | `PIC X(50)` | `VARCHAR(n)` | `String` | `@Size(max=n)` | Variable-length character field |
| **PIC A(n)** | `PIC A(25)` | `VARCHAR(n)` | `String` | `@Size(max=n)`, `@Pattern` | Alphabetic only in COBOL - validate in application |
| **PIC X(10) (date)** | `PIC X(10)` with YYYY-MM-DD | `DATE` | `LocalDate` | `@Past`, `@Future`, `@PastOrPresent` | Convert YYYYMMDD → YYYY-MM-DD during transformation |
| **PIC 9(08) (date)** | `PIC 9(08)` with YYYYMMDD | `DATE` | `LocalDate` | `@Past`, `@Future` | Numeric date format - convert to ISO 8601 |
| **PIC X(26) (timestamp)** | `COBOL CURRENT-TIMESTAMP` | `TIMESTAMP` | `LocalDateTime` | `@PastOrPresent` | ISO 8601 timestamp |
| **88-level condition** | `88 ACCT-ACTIVE VALUE 'Y'` | `CHAR(1)` with `CHECK` | `String` or `Enum` | `@Pattern(regexp="[YN]")` | Boolean flag or enum representation |
| **REDEFINES** | `05 AMOUNT-NUM REDEFINES AMOUNT-CHAR` | Separate columns | Separate fields | Varies | Split into multiple columns or use application logic |
| **OCCURS n TIMES** | `05 MONTH-BAL OCCURS 12 TIMES` | Array type or separate table | `List<>` or separate entity | `@Size(min=n, max=n)` | Normalize to separate table with FK relationship |
| **COMP (binary)** | `PIC S9(04) COMP` | `SMALLINT` or `INTEGER` | `Short` or `Integer` | `@Min`, `@Max` | Binary integer (2 or 4 bytes) |
| **COMP-3 (packed)** | `PIC S9(07)V99 COMP-3` | `NUMERIC(9,2)` | `BigDecimal` | `@Digits` | **Packed decimal - use BigDecimal** |
| **PIC S9(n)V9(m)** (display) | `PIC S9(05)V99` | `NUMERIC(n+m, m)` | `BigDecimal` | `@Digits` | Implied decimal point |

### 9.2 Critical Financial Data Types

**ALWAYS Use BigDecimal for Monetary Values**:

```java
// ✅ CORRECT: Exact decimal precision
@Column(name = "current_balance", precision = 12, scale = 2)
@Digits(integer = 10, fraction = 2)
private BigDecimal currentBalance;

// ❌ INCORRECT: Float/Double lose precision
// private Double currentBalance;  // NEVER use for money!
```

**Example COMP-3 Conversion**:

```python
# COBOL: 05 ACCT-CURR-BAL PIC S9(09)V99 COMP-3.
# VSAM export (ASCII representation): "00000019400{"
# The trailing "{" indicates positive sign in COMP-3 ASCII dump

def convert_comp3_to_decimal(comp3_ascii):
    """Convert COMP-3 ASCII representation to decimal"""
    cleaned = comp3_ascii.replace('{', '0')  # Positive sign
    # Insert decimal point (V99 means 2 implied decimals)
    return cleaned[:-2] + '.' + cleaned[-2:]

# Example: "00000019400{" → "000000194.00" → Decimal('194.00')
```

### 9.3 Date Format Conversions

**COBOL Date Formats**:

| COBOL Format | Example | PostgreSQL | Java LocalDate |
|:-------------|:--------|:-----------|:--------------|
| `PIC X(10)` YYYY-MM-DD | `2024-06-15` | `DATE` | `LocalDate.parse("2024-06-15")` |
| `PIC 9(08)` YYYYMMDD | `20240615` | `DATE` | `LocalDate.parse("20240615", DateTimeFormatter.BASIC_ISO_DATE)` |
| `PIC 9(06)` YYMMDD | `240615` | `DATE` | Requires century calculation |

**Python Date Transformation**:

```python
from datetime import datetime

def transform_date_yyyymmdd(date_str):
    """Convert YYYYMMDD to YYYY-MM-DD"""
    if len(date_str) == 8:  # YYYYMMDD
        dt = datetime.strptime(date_str, '%Y%m%d')
        return dt.strftime('%Y-%m-%d')
    elif len(date_str) == 10 and '-' in date_str:  # Already YYYY-MM-DD
        return date_str
    else:
        raise ValueError(f"Invalid date format: {date_str}")
```

---

## 10. Performance Optimization

### 10.1 Bulk Loading Best Practices

**PostgreSQL COPY Performance**:

```bash
# Fastest method: Direct COPY from CSV
\COPY account FROM '/path/to/account.csv' WITH (FORMAT CSV, HEADER TRUE)
# Throughput: 10,000-50,000 rows/second

# Alternative: psql command line
psql -h $DB_HOST -U $DB_USER -d carddemo -c "\COPY account FROM 'account.csv' WITH (FORMAT CSV, HEADER TRUE)"

# For very large datasets: Disable indexes during load
DROP INDEX idx_account_customer;
DROP INDEX idx_account_group;
-- Load data
\COPY account FROM 'account.csv' WITH (FORMAT CSV, HEADER TRUE)
-- Recreate indexes
CREATE INDEX idx_account_customer ON account(customer_id);
CREATE INDEX idx_account_group ON account(group_id);
ANALYZE account;
```

### 10.2 Parallel Loading

**Load Multiple Tables Concurrently**:

```bash
#!/bin/bash
# parallel_load.sh - Load all tables in parallel

# Load customer data (no dependencies)
psql -h $DB_HOST -U $DB_USER -d carddemo -c "\COPY customer FROM 'customer.csv' WITH (FORMAT CSV, HEADER TRUE)" &
PID_CUSTOMER=$!

# Wait for customer load to complete
wait $PID_CUSTOMER

# Load account data (depends on customer)
psql -h $DB_HOST -U $DB_USER -d carddemo -c "\COPY account FROM 'account.csv' WITH (FORMAT CSV, HEADER TRUE)" &
PID_ACCOUNT=$!

# Wait for account load to complete
wait $PID_ACCOUNT

# Load card and card_xref in parallel (both depend on account)
psql -h $DB_HOST -U $DB_USER -d carddemo -c "\COPY card FROM 'card.csv' WITH (FORMAT CSV, HEADER TRUE)" &
PID_CARD=$!

psql -h $DB_HOST -U $DB_USER -d carddemo -c "\COPY card_xref FROM 'card_xref.csv' WITH (FORMAT CSV, HEADER TRUE)" &
PID_XREF=$!

# Wait for all loads to complete
wait $PID_CARD $PID_XREF

echo "All tables loaded successfully"
```

### 10.3 Monitoring Migration Progress

**Real-Time Progress Monitoring**:

```sql
-- Monitor active COPY operations
SELECT 
    pid,
    usename,
    application_name,
    state,
    query,
    query_start,
    NOW() - query_start AS duration
FROM pg_stat_activity
WHERE query LIKE '%COPY%'
    AND state = 'active';

-- Monitor table sizes during load
SELECT 
    schemaname,
    tablename,
    pg_size_pretty(pg_total_relation_size(schemaname||'.'||tablename)) AS size,
    n_live_tup AS row_count
FROM pg_stat_user_tables
WHERE schemaname = 'public'
ORDER BY pg_total_relation_size(schemaname||'.'||tablename) DESC;
```

---

## 11. Rollback Procedures

### 11.1 Database Snapshot Restore

**Restore from Pre-Migration Snapshot**:

```bash
# List available snapshots
aws rds describe-db-snapshots \
  --db-instance-identifier carddemo-db \
  --query 'DBSnapshots[*].[DBSnapshotIdentifier,SnapshotCreateTime,Status]' \
  --output table

# Restore database from snapshot (creates new instance)
aws rds restore-db-instance-from-db-snapshot \
  --db-instance-identifier carddemo-db-restored \
  --db-snapshot-identifier carddemo-pre-migration-20240615-120000 \
  --db-instance-class db.t3.medium \
  --multi-az \
  --publicly-accessible false

# Wait for restore to complete (10-30 minutes)
aws rds wait db-instance-available \
  --db-instance-identifier carddemo-db-restored

# Update application configuration to point to restored instance
# Update DNS or ConfigMap with new endpoint
```

### 11.2 Flyway Rollback

**Rollback Specific Migration Version**:

```bash
# Flyway does not support automatic rollback - manual intervention required

# Option 1: Drop and recreate database
psql -h $DB_HOST -U postgres_admin -d postgres -c "DROP DATABASE carddemo;"
psql -h $DB_HOST -U postgres_admin -d postgres -c "CREATE DATABASE carddemo;"

# Re-run migrations up to desired version
flyway migrate -target=2  # Migrate only to V2 (skip V3, V4)

# Option 2: Manual rollback SQL (for V4 data load only)
psql -h $DB_HOST -U postgres_admin -d carddemo <<EOF
-- Rollback V4: Delete migrated data
DELETE FROM card_xref;
DELETE FROM card;
DELETE FROM account;
DELETE FROM customer;

-- Update Flyway schema history
DELETE FROM flyway_schema_history WHERE version = '4';

-- Verify rollback
SELECT * FROM flyway_schema_history ORDER BY installed_rank;
EOF
```

### 11.3 Point-in-Time Recovery

**Restore to Specific Timestamp**:

```bash
# Restore database to specific point in time (within backup retention period)
aws rds restore-db-instance-to-point-in-time \
  --source-db-instance-identifier carddemo-db \
  --target-db-instance-identifier carddemo-db-pitr-20240615-143000 \
  --restore-time 2024-06-15T14:30:00Z \
  --db-instance-class db.t3.medium \
  --multi-az

# Wait for restore to complete
aws rds wait db-instance-available \
  --db-instance-identifier carddemo-db-pitr-20240615-143000

echo "Point-in-time restore complete"
```

---

## 12. Troubleshooting

### 12.1 Common Migration Issues

#### Issue 1: Foreign Key Constraint Violations

**Symptoms**:
```
ERROR: insert or update on table "account" violates foreign key constraint "fk_account_customer"
DETAIL: Key (customer_id)=(123) is not present in table "customer".
```

**Resolution**:
```sql
-- Identify orphaned records
SELECT a.account_id, a.customer_id
FROM account a
LEFT JOIN customer c ON a.customer_id = c.customer_id
WHERE c.customer_id IS NULL;

-- Fix: Load parent table first (customer before account)
-- Or: Temporarily disable foreign key constraints
SET CONSTRAINTS ALL DEFERRED;
-- Load data
\COPY customer FROM 'customer.csv' WITH (FORMAT CSV, HEADER TRUE);
\COPY account FROM 'account.csv' WITH (FORMAT CSV, HEADER TRUE);
COMMIT;
```

#### Issue 2: Decimal Precision Loss

**Symptoms**:
```
-- Expected: 1940.00
-- Actual: 1940.0 or 1940
```

**Resolution**:
```sql
-- Verify column definition
\d+ account
-- Ensure: current_balance NUMERIC(12,2)

-- Fix data:
UPDATE account
SET current_balance = ROUND(current_balance::NUMERIC, 2);

-- Validate:
SELECT account_number, current_balance
FROM account
WHERE CAST(current_balance AS TEXT) NOT LIKE '%.%';
```

#### Issue 3: Date Format Errors

**Symptoms**:
```
ERROR: invalid input syntax for type date: "20240615"
```

**Resolution**:
```python
# Update transformation script to convert YYYYMMDD → YYYY-MM-DD
def transform_date(date_str):
    if len(date_str) == 8 and date_str.isdigit():
        return f"{date_str[:4]}-{date_str[4:6]}-{date_str[6:8]}"
    return date_str
```

#### Issue 4: Character Encoding Issues (EBCDIC → UTF-8)

**Symptoms**:
```
-- Garbled characters or unexpected symbols in text fields
```

**Resolution**:
```bash
# Convert EBCDIC to ASCII during mainframe export
dd if=CARDDEMO.EXPORT.ACCTDATA of=acctdata.txt conv=ascii

# Or use iconv on Unix
iconv -f EBCDIC-US -t UTF-8 acctdata.ebcdic > acctdata.txt

# Verify encoding
file -bi acctdata.txt
# Expected: text/plain; charset=utf-8
```

### 12.2 Performance Troubleshooting

**Slow INSERT Performance**:

```sql
-- Check for missing indexes on FK columns
SELECT
    tc.table_name,
    kcu.column_name,
    CASE WHEN i.indexname IS NULL THEN 'MISSING INDEX' ELSE 'INDEXED' END AS index_status
FROM information_schema.table_constraints AS tc
JOIN information_schema.key_column_usage AS kcu
    ON tc.constraint_name = kcu.constraint_name
LEFT JOIN pg_indexes i
    ON i.tablename = tc.table_name AND i.indexdef LIKE '%' || kcu.column_name || '%'
WHERE tc.constraint_type = 'FOREIGN KEY'
    AND tc.table_schema = 'public';

-- Temporarily disable triggers during bulk load
ALTER TABLE account DISABLE TRIGGER ALL;
\COPY account FROM 'account.csv' WITH (FORMAT CSV, HEADER TRUE);
ALTER TABLE account ENABLE TRIGGER ALL;

-- Increase maintenance_work_mem for index creation
SET maintenance_work_mem = '512MB';
CREATE INDEX idx_account_customer ON account(customer_id);
```

### 12.3 Support Resources

**Documentation References**:
- PostgreSQL COPY Command: https://www.postgresql.org/docs/15/sql-copy.html
- AWS RDS Backup and Restore: https://docs.aws.amazon.com/AmazonRDS/latest/UserGuide/CHAP_CommonTasks.BackupRestore.html
- Flyway Documentation: https://flywaydb.org/documentation/
- COBOL to Java Migration Guide: See `docs/modernization.md`

**Logging and Monitoring**:
```bash
# Enable PostgreSQL query logging
psql -h $DB_HOST -U postgres_admin -d carddemo -c "ALTER SYSTEM SET log_statement = 'all';"
psql -h $DB_HOST -U postgres_admin -d carddemo -c "SELECT pg_reload_conf();"

# View PostgreSQL logs in CloudWatch
aws logs tail /aws/rds/instance/carddemo-db/postgresql --follow

# Monitor database metrics
aws cloudwatch get-metric-statistics \
  --namespace AWS/RDS \
  --metric-name DatabaseConnections \
  --dimensions Name=DBInstanceIdentifier,Value=carddemo-db \
  --start-time 2024-06-15T12:00:00Z \
  --end-time 2024-06-15T14:00:00Z \
  --period 300 \
  --statistics Average
```

---

## Appendix A: Complete Migration Checklist

### Pre-Migration

- [ ] AWS RDS PostgreSQL instance provisioned (db.t3.medium, 50GB, Multi-AZ)
- [ ] Network connectivity verified (bastion host, EKS nodes)
- [ ] Flyway installed and configured
- [ ] Python 3.10+ with pandas, psycopg2 installed
- [ ] Pre-migration database snapshot created
- [ ] Database credentials retrieved from AWS Secrets Manager
- [ ] Migration scripts tested in development environment

### Phase 1: VSAM Export

- [ ] IDCAMS REPRO JCL jobs submitted for all datasets
- [ ] Export files transferred to S3 bucket (9 files)
- [ ] Record counts validated (50 accounts, 50 cards, 50 customers)
- [ ] File integrity verified (no corruption, proper encoding)
- [ ] Sample records inspected for data quality

### Phase 2: Data Transformation

- [ ] Python transformation scripts executed for all datasets
- [ ] CSV files generated with proper formatting
- [ ] COMP-3 decimal conversions validated
- [ ] Date format conversions confirmed (YYYY-MM-DD)
- [ ] Referential integrity pre-validated (FK lookups)
- [ ] CSV files uploaded to S3 for archival

### Phase 3: Schema Creation

- [ ] Flyway migration V1 executed (table creation)
- [ ] Flyway migration V2 executed (index creation)
- [ ] Flyway migration V3 executed (reference data seeding)
- [ ] All tables created successfully (11 tables)
- [ ] All indexes created successfully (25+ indexes)
- [ ] Foreign key constraints verified
- [ ] CHECK constraints validated

### Phase 4: Data Loading

- [ ] Customer data loaded (50 records)
- [ ] Account data loaded (50 records)
- [ ] Card data loaded (50 records)
- [ ] Card cross-reference loaded (50 records)
- [ ] Flyway migration V4 completed
- [ ] All foreign key relationships satisfied
- [ ] No constraint violations

### Phase 5: Post-Migration Validation

- [ ] Record count reconciliation passed (all tables match expected counts)
- [ ] Referential integrity validation passed (no orphaned records)
- [ ] Data quality checks passed (no negative balances, valid dates)
- [ ] Numeric precision validated (BigDecimal values correct)
- [ ] Performance benchmarks met (queries <50ms)
- [ ] Security validation passed (card numbers encrypted, SSN protected)
- [ ] Migration validation report generated and reviewed

### Finalization

- [ ] Application configuration updated (database connection strings)
- [ ] Integration tests executed against migrated database
- [ ] User acceptance testing completed
- [ ] Post-migration database snapshot created
- [ ] Migration documentation finalized
- [ ] Rollback procedures tested and verified
- [ ] Production cutover scheduled
- [ ] Legacy VSAM datasets marked as read-only (archival status)

---

## Conclusion

This data migration guide provides a comprehensive, step-by-step procedure for transforming CardDemo application data from legacy mainframe VSAM datasets to a cloud-native PostgreSQL relational database. The migration preserves complete data integrity, maintains PCI-DSS security compliance, and establishes a foundation for modern application development with Java 21 and Spring Boot 3.

**Migration Success Criteria**:
✅ 100% record count match between VSAM source and PostgreSQL target  
✅ Zero referential integrity violations (all foreign keys satisfied)  
✅ Exact decimal precision preserved for all monetary values (BigDecimal)  
✅ Sub-50ms query performance for indexed lookups  
✅ PCI-DSS compliance maintained (card numbers encrypted, logs masked)  
✅ Complete audit trail of migration process  
✅ Validated rollback procedures available  

**Next Steps**:
- **Application Integration**: Update Spring Boot application.yml with PostgreSQL connection parameters
- **JPA Entity Validation**: Ensure all `@Entity` classes match database schema exactly
- **Integration Testing**: Execute Testcontainers-based integration tests against migrated data
- **Performance Tuning**: Monitor query performance, adjust indexes as needed
- **Production Deployment**: Schedule maintenance window for final cutover

For questions or issues encountered during migration, refer to the troubleshooting section or contact the database administration team.

**Document Version**: 1.0  
**Last Updated**: 2024-06-15  
**Author**: CardDemo Modernization Team  
**Related Documents**: 
- `docs/modernization.md` - COBOL to Java migration guide
- `docs/architecture.md` - System architecture overview
- `docs/deployment-guide.md` - Kubernetes deployment procedures
