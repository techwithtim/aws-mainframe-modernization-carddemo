/*
 * DataSourceConfig.java
 * 
 * PostgreSQL database connection configuration for CardDemo application.
 * Migrated from COBOL VSAM file access patterns to relational database access.
 * 
 * Original COBOL References:
 * - app/cbl/CBTRN01C.cbl: Transaction posting batch job (ACCTFILE, TRANFILE, XREFFILE access)
 * - app/cbl/CBACT04C.cbl: Interest calculation (TCATBAL, ACCTFILE, DISCGRP access)
 * - app/cbl/COACTVWC.cbl: Online account view (EXEC CICS READ DATASET operations)
 * 
 * This configuration replaces 8 VSAM datasets with PostgreSQL tables:
 * - ACCTFILE -> account table
 * - CARDFILE -> card table
 * - CUSTFILE -> customer table
 * - TRANFILE -> transaction table
 * - XREFFILE -> card_xref table
 * - DALYTRAN -> daily_transaction table
 * - TCATBAL -> transaction_category_balance table
 * - DISCGRP -> disclosure_group table
 * 
 * Copyright Amazon.com, Inc. or its affiliates.
 * All Rights Reserved.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License").
 * You may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *    http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific
 * language governing permissions and limitations under the License.
 */

package com.aws.carddemo.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import jakarta.persistence.EntityManagerFactory;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * DataSource configuration class for PostgreSQL database connectivity.
 * 
 * Configures HikariCP connection pool as the primary DataSource for:
 * - JPA EntityManager (for repository access to PostgreSQL tables)
 * - Spring Batch JobRepository (for batch job metadata)
 * - Transactional database operations throughout the application
 * 
 * This replaces COBOL VSAM file I/O operations:
 * - COBOL READ operations -> JPA findById/findByXxx
 * - COBOL WRITE operations -> JPA save() (insert)
 * - COBOL REWRITE operations -> JPA save() (update)
 * - COBOL DELETE operations -> JPA delete()
 * - EXEC CICS SYNCPOINT -> @Transactional commit
 * - EXEC CICS SYNCPOINT ROLLBACK -> @Transactional rollback on exception
 * 
 * Performance Characteristics:
 * - Connection pool: 10-20 connections per pod (sufficient for 1000+ concurrent users)
 * - Connection timeout: 30 seconds (fail fast on connection issues)
 * - Connection validation: SELECT 1 (validates connections before use)
 * - Leak detection: 60 seconds (detects connection leaks in development)
 * - Connection lifecycle: 30 minute max lifetime for connection recycling
 * 
 * @author AWS CardDemo Modernization Team
 * @version 1.0
 * @since 2024
 */
@Configuration
@EnableTransactionManagement
public class DataSourceConfig {
    
    private static final Logger logger = LoggerFactory.getLogger(DataSourceConfig.class);
    
    /**
     * Database connection URL.
     * Format: jdbc:postgresql://${DB_HOST:localhost}:5432/${DB_NAME:carddemo}
     * Injected from application.yml or environment variables.
     */
    @Value("${spring.datasource.url}")
    private String jdbcUrl;
    
    /**
     * Database username.
     * Injected from application.yml, Kubernetes Secret, or AWS Secrets Manager.
     */
    @Value("${spring.datasource.username}")
    private String username;
    
    /**
     * Database password.
     * Injected from application.yml, Kubernetes Secret, or AWS Secrets Manager.
     * SECURITY: Never log this value.
     */
    @Value("${spring.datasource.password}")
    private String password;
    
    /**
     * HikariCP maximum pool size.
     * Default: 20 connections (sufficient for high concurrency).
     */
    @Value("${spring.datasource.hikari.maximum-pool-size:20}")
    private int maximumPoolSize;
    
    /**
     * HikariCP minimum idle connections.
     * Default: 10 connections (baseline for quick response).
     */
    @Value("${spring.datasource.hikari.minimum-idle:10}")
    private int minimumIdle;
    
    /**
     * Connection timeout in milliseconds.
     * Default: 30000ms (30 seconds) - fail fast on connection issues.
     */
    @Value("${spring.datasource.hikari.connection-timeout:30000}")
    private long connectionTimeout;
    
    /**
     * Idle timeout in milliseconds.
     * Default: 600000ms (10 minutes) - close idle connections.
     */
    @Value("${spring.datasource.hikari.idle-timeout:600000}")
    private long idleTimeout;
    
    /**
     * Maximum connection lifetime in milliseconds.
     * Default: 1800000ms (30 minutes) - force connection recycling.
     */
    @Value("${spring.datasource.hikari.max-lifetime:1800000}")
    private long maxLifetime;
    
    /**
     * Connection test query.
     * Default: SELECT 1 (lightweight validation query).
     */
    @Value("${spring.datasource.hikari.connection-test-query:SELECT 1}")
    private String connectionTestQuery;
    
    /**
     * Leak detection threshold in milliseconds.
     * Default: 60000ms (60 seconds) - detect connection leaks in development.
     * Set to 0 in production to disable overhead.
     */
    @Value("${spring.datasource.hikari.leak-detection-threshold:60000}")
    private long leakDetectionThreshold;
    
    /**
     * Pool name for monitoring and logging.
     */
    @Value("${spring.datasource.hikari.pool-name:CardDemoHikariPool}")
    private String poolName;
    
    /**
     * Creates and configures the primary DataSource bean using HikariCP.
     * 
     * HikariCP is chosen for:
     * - High performance (fastest connection pool in Java ecosystem)
     * - Low overhead (minimal memory and CPU usage)
     * - Reliability (battle-tested in production environments)
     * - Monitoring (built-in metrics and health checks)
     * 
     * Configuration details:
     * - Driver: org.postgresql.Driver (PostgreSQL 15+ JDBC driver)
     * - Pool size: 10-20 connections (optimized for Kubernetes pod deployment)
     * - Timeouts: 30s connection, 10m idle, 30m max lifetime
     * - Validation: SELECT 1 query before connection use
     * - Prepared statement cache: 256 queries, 5MB size
     * - Leak detection: 60s threshold (development only)
     * 
     * Connection lifecycle:
     * 1. Pool initialized with minimumIdle connections on startup
     * 2. Connections borrowed from pool on demand (up to maximumPoolSize)
     * 3. Connections validated with connectionTestQuery before use
     * 4. Idle connections closed after idleTimeout (10 minutes)
     * 5. All connections recycled after maxLifetime (30 minutes)
     * 6. Leak detection logs warning if connection not returned in 60 seconds
     * 
     * This DataSource replaces COBOL VSAM file access patterns:
     * - COBOL FILE-CONTROL SELECT statements -> DataSource configuration
     * - COBOL OPEN/CLOSE operations -> Connection pool management
     * - COBOL FILE STATUS checks -> SQLException handling
     * 
     * @return HikariDataSource configured for PostgreSQL connectivity
     * @throws RuntimeException if DataSource initialization fails
     */
    @Bean
    @Primary
    public DataSource dataSource() {
        logger.info("Initializing HikariCP DataSource for PostgreSQL connectivity");
        logger.info("JDBC URL: {}", maskPassword(jdbcUrl));
        logger.info("Maximum pool size: {}", maximumPoolSize);
        logger.info("Minimum idle connections: {}", minimumIdle);
        
        try {
            // Create HikariCP configuration object
            HikariConfig hikariConfig = new HikariConfig();
            
            // Database connection properties
            hikariConfig.setJdbcUrl(jdbcUrl);
            hikariConfig.setUsername(username);
            hikariConfig.setPassword(password);
            hikariConfig.setDriverClassName("org.postgresql.Driver");
            
            // Connection pool sizing
            hikariConfig.setMaximumPoolSize(maximumPoolSize);
            hikariConfig.setMinimumIdle(minimumIdle);
            
            // Connection timeout configuration
            hikariConfig.setConnectionTimeout(connectionTimeout);
            hikariConfig.setIdleTimeout(idleTimeout);
            hikariConfig.setMaxLifetime(maxLifetime);
            
            // Connection validation
            hikariConfig.setConnectionTestQuery(connectionTestQuery);
            
            // Pool identification
            hikariConfig.setPoolName(poolName);
            
            // Leak detection (development/testing only)
            if (leakDetectionThreshold > 0) {
                hikariConfig.setLeakDetectionThreshold(leakDetectionThreshold);
                logger.warn("Connection leak detection enabled with {}ms threshold", 
                           leakDetectionThreshold);
            }
            
            // PostgreSQL-specific optimizations
            // Prepared statement caching for performance
            hikariConfig.addDataSourceProperty("prepareThreshold", "3");
            hikariConfig.addDataSourceProperty("preparedStatementCacheQueries", "256");
            hikariConfig.addDataSourceProperty("preparedStatementCacheSizeMiB", "5");
            
            // Connection validation settings
            hikariConfig.addDataSourceProperty("tcpKeepAlive", "true");
            hikariConfig.addDataSourceProperty("socketTimeout", "30");
            
            // Application name for PostgreSQL monitoring
            hikariConfig.addDataSourceProperty("ApplicationName", "CardDemo-Modernized");
            
            // Create and return HikariDataSource
            HikariDataSource dataSource = new HikariDataSource(hikariConfig);
            
            logger.info("HikariCP DataSource initialized successfully");
            logger.info("Pool name: {}", poolName);
            logger.info("Connection pool ready with {} minimum idle connections", minimumIdle);
            
            return dataSource;
            
        } catch (Exception e) {
            logger.error("Failed to initialize HikariCP DataSource", e);
            throw new RuntimeException("DataSource initialization failed: " + e.getMessage(), e);
        }
    }
    
    /**
     * Creates and configures the JPA transaction manager.
     * 
     * The JpaTransactionManager provides:
     * - ACID transaction guarantees for database operations
     * - Declarative transaction management via @Transactional annotations
     * - Transaction propagation and isolation level control
     * - Automatic rollback on unchecked exceptions
     * - Commit/rollback coordination with JPA EntityManager
     * 
     * This replaces COBOL CICS transaction semantics:
     * - EXEC CICS SYNCPOINT -> @Transactional commit (successful method completion)
     * - EXEC CICS SYNCPOINT ROLLBACK -> @Transactional rollback (exception thrown)
     * - CICS transaction boundaries -> @Transactional method boundaries
     * - CICS unit of work -> Spring transaction scope
     * 
     * Transaction behavior:
     * - Default propagation: REQUIRED (join existing transaction or create new)
     * - Default isolation: READ_COMMITTED (prevent dirty reads)
     * - Rollback on: RuntimeException and Error (unchecked exceptions)
     * - Commit on: Successful method completion (no exceptions)
     * 
     * Example usage in service layer:
     * <pre>
     * {@code
     * @Service
     * @Transactional
     * public class TransactionService {
     *     public void postTransaction(TransactionRequest request) {
     *         // Multiple repository operations in single transaction
     *         accountRepository.updateBalance(accountId, newBalance);
     *         transactionRepository.save(transaction);
     *         // Automatic commit if successful
     *         // Automatic rollback if exception thrown
     *     }
     * }
     * }
     * </pre>
     * 
     * Batch job integration:
     * Spring Batch jobs also use this transaction manager for:
     * - Chunk-oriented processing (commit after each chunk)
     * - Skip/retry logic with transaction boundaries
     * - Job restart with transaction rollback to last successful chunk
     * 
     * @param entityManagerFactory JPA EntityManagerFactory for entity lifecycle management
     * @return PlatformTransactionManager configured for JPA transactions
     */
    @Bean
    public PlatformTransactionManager transactionManager(EntityManagerFactory entityManagerFactory) {
        logger.info("Initializing JpaTransactionManager for declarative transaction management");
        
        JpaTransactionManager transactionManager = new JpaTransactionManager();
        transactionManager.setEntityManagerFactory(entityManagerFactory);
        
        logger.info("JpaTransactionManager initialized successfully");
        logger.info("Transaction manager ready for @Transactional annotations");
        logger.info("ACID transaction guarantees enabled (replaces COBOL CICS SYNCPOINT semantics)");
        
        return transactionManager;
    }
    
    /**
     * Masks password in JDBC URL for secure logging.
     * 
     * Security best practice: Never log passwords or sensitive credentials.
     * This method removes password parameter from JDBC URL before logging.
     * 
     * @param url JDBC URL potentially containing password parameter
     * @return Masked JDBC URL with password replaced by "***"
     */
    private String maskPassword(String url) {
        if (url == null) {
            return null;
        }
        // Replace password parameter with masked value
        return url.replaceAll("password=[^&]*", "password=***");
    }
}
