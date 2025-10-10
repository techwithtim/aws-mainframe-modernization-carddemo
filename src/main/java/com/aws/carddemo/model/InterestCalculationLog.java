package com.aws.carddemo.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Interest Calculation Log Entity
 * 
 * <p>Audit log for monthly interest calculation batch job execution.
 * Tracks interest posting runs with summary statistics for reconciliation,
 * reporting, and operational monitoring.
 * 
 * <p><strong>Business Function:</strong> Provides audit trail for interest posting batch jobs,
 * enabling monthly reconciliation reports and operational monitoring of batch job execution.
 * 
 * <p><strong>COBOL Equivalent:</strong> N/A (new audit capability for batch job monitoring)
 * 
 * <p><strong>Database Mapping:</strong> interest_calculation_log table (12 columns, IDENTITY primary key)
 * 
 * <p><strong>Data Integrity:</strong>
 * <ul>
 *   <li>All numeric fields enforce non-negative constraints (CHECK >= 0)</li>
 *   <li>Processing date is required for monthly reconciliation</li>
 *   <li>Processing timestamp defaults to CURRENT_TIMESTAMP</li>
 *   <li>Indexed on processing_date and processing_timestamp for reporting queries</li>
 * </ul>
 * 
 * <p><strong>Usage Example:</strong>
 * <pre>{@code
 * InterestCalculationLog log = new InterestCalculationLog();
 * log.setProcessingDate(LocalDate.now());
 * log.setTotalAccountsProcessed(1000);
 * log.setTotalInterestCharged(new BigDecimal("125000.00"));
 * log.setAverageInterest(new BigDecimal("125.00"));
 * log.setMinInterest(new BigDecimal("0.50"));
 * log.setMaxInterest(new BigDecimal("500.00"));
 * log.setProcessingTimestamp(LocalDateTime.now());
 * interestCalculationLogRepository.save(log);
 * }</pre>
 * 
 * @see com.aws.carddemo.batch.writer.AccountWriter#writeAuditSummary AccountWriter.writeAuditSummary
 * @see com.aws.carddemo.batch.config.InterestCalculationJobConfig InterestCalculationJobConfig
 * @since 1.0.0
 * @version 1.0.0
 */
@Entity
@Table(name = "interest_calculation_log",
       indexes = {
           @Index(name = "idx_interest_log_date", columnList = "processing_date"),
           @Index(name = "idx_interest_log_timestamp", columnList = "processing_timestamp")
       })
@Data
@NoArgsConstructor
@AllArgsConstructor
public class InterestCalculationLog {

    /**
     * Primary key - Auto-generated identity column
     * 
     * <p>Database type: BIGINT GENERATED ALWAYS AS IDENTITY
     * <p>Auto-incremented by PostgreSQL for each audit log entry
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "log_id", nullable = false)
    private Long logId;

    /**
     * Business date for which interest was calculated
     * 
     * <p>Typically set to month-end date (e.g., 2024-01-31) for monthly interest posting.
     * Used for monthly reconciliation reports and filtering audit logs by billing cycle.
     * 
     * <p>Database type: DATE
     * <p>Required: YES (NOT NULL constraint)
     * 
     * <p>Example: LocalDate.of(2024, 1, 31) for January 2024 interest calculation
     */
    @NotNull(message = "Processing date is required for interest calculation audit")
    @Column(name = "processing_date", nullable = false)
    private LocalDate processingDate;

    /**
     * Number of accounts charged interest in this batch run
     * 
     * <p>Should match the count of interest transaction records created during the batch job.
     * Used for reconciliation and verification that all eligible accounts were processed.
     * 
     * <p>Database type: INTEGER
     * <p>Required: YES (NOT NULL constraint)
     * <p>Constraint: Must be >= 0 (CHECK constraint)
     * 
     * <p>Example: 1,000 (if 1,000 accounts received interest charges)
     */
    @NotNull(message = "Total accounts processed count is required")
    @Min(value = 0, message = "Total accounts processed cannot be negative")
    @Column(name = "total_accounts_processed", nullable = false)
    private Integer totalAccountsProcessed;

    /**
     * Sum of all interest charges posted in this batch run
     * 
     * <p>Should reconcile with the sum of all interest transaction amounts created during
     * the batch job. Provides high-level financial reconciliation for the interest posting run.
     * 
     * <p>Database type: NUMERIC(15,2) - up to $999,999,999,999.99
     * <p>Required: YES (NOT NULL constraint)
     * <p>Constraint: Must be >= 0 (CHECK constraint)
     * <p>Precision: 2 decimal places (financial precision)
     * 
     * <p>Example: new BigDecimal("125000.00") for $125,000.00 total interest charged
     */
    @NotNull(message = "Total interest charged is required")
    @DecimalMin(value = "0.00", inclusive = true, message = "Total interest charged cannot be negative")
    @Column(name = "total_interest_charged", nullable = false, precision = 15, scale = 2)
    private BigDecimal totalInterestCharged;

    /**
     * Mean interest amount per account
     * 
     * <p>Calculated as totalInterestCharged / totalAccountsProcessed.
     * Provides average interest charge metric for reporting and trend analysis.
     * 
     * <p>Database type: NUMERIC(15,2)
     * <p>Required: YES (NOT NULL constraint)
     * <p>Constraint: Must be >= 0 (CHECK constraint)
     * <p>Precision: 2 decimal places (financial precision)
     * 
     * <p>Example: new BigDecimal("125.00") if total interest is $125,000 for 1,000 accounts
     */
    @NotNull(message = "Average interest is required")
    @DecimalMin(value = "0.00", inclusive = true, message = "Average interest cannot be negative")
    @Column(name = "average_interest", nullable = false, precision = 15, scale = 2)
    private BigDecimal averageInterest;

    /**
     * Minimum interest charge in this batch run
     * 
     * <p>Useful for identifying accounts with low balances or detecting potential data
     * anomalies (e.g., unexpectedly low interest charges that may indicate errors).
     * 
     * <p>Database type: NUMERIC(15,2)
     * <p>Required: YES (NOT NULL constraint)
     * <p>Constraint: Must be >= 0 (CHECK constraint)
     * <p>Precision: 2 decimal places (financial precision)
     * 
     * <p>Example: new BigDecimal("0.50") for minimum interest of $0.50
     */
    @NotNull(message = "Minimum interest is required")
    @DecimalMin(value = "0.00", inclusive = true, message = "Minimum interest cannot be negative")
    @Column(name = "min_interest", nullable = false, precision = 15, scale = 2)
    private BigDecimal minInterest;

    /**
     * Maximum interest charge in this batch run
     * 
     * <p>Useful for identifying high-balance accounts or detecting potential data anomalies
     * (e.g., unexpectedly high interest charges that may indicate calculation errors or
     * fraudulent balances).
     * 
     * <p>Database type: NUMERIC(15,2)
     * <p>Required: YES (NOT NULL constraint)
     * <p>Constraint: Must be >= 0 (CHECK constraint)
     * <p>Precision: 2 decimal places (financial precision)
     * 
     * <p>Example: new BigDecimal("500.00") for maximum interest of $500.00
     */
    @NotNull(message = "Maximum interest is required")
    @DecimalMin(value = "0.00", inclusive = true, message = "Maximum interest cannot be negative")
    @Column(name = "max_interest", nullable = false, precision = 15, scale = 2)
    private BigDecimal maxInterest;

    /**
     * Timestamp when batch job completed
     * 
     * <p>Used for operational monitoring (tracking batch job execution times) and performance
     * analysis (detecting long-running jobs or scheduling issues).
     * 
     * <p>Database type: TIMESTAMP
     * <p>Required: YES (NOT NULL constraint)
     * <p>Default: CURRENT_TIMESTAMP (set by database if not provided)
     * 
     * <p>Example: LocalDateTime.now() at job completion
     */
    @NotNull(message = "Processing timestamp is required")
    @Column(name = "processing_timestamp", nullable = false)
    private LocalDateTime processingTimestamp;

    /**
     * Constructor for creating audit log with all required fields
     * 
     * <p>Typically called by AccountWriter after completing a chunk of interest calculations.
     * 
     * @param processingDate Business date for interest calculation (month-end)
     * @param totalAccountsProcessed Number of accounts charged interest
     * @param totalInterestCharged Sum of all interest charges
     * @param averageInterest Mean interest per account
     * @param minInterest Minimum interest charge in batch
     * @param maxInterest Maximum interest charge in batch
     * @param processingTimestamp Timestamp of job completion
     */
    public InterestCalculationLog(
        LocalDate processingDate,
        Integer totalAccountsProcessed,
        BigDecimal totalInterestCharged,
        BigDecimal averageInterest,
        BigDecimal minInterest,
        BigDecimal maxInterest,
        LocalDateTime processingTimestamp
    ) {
        this.processingDate = processingDate;
        this.totalAccountsProcessed = totalAccountsProcessed;
        this.totalInterestCharged = totalInterestCharged;
        this.averageInterest = averageInterest;
        this.minInterest = minInterest;
        this.maxInterest = maxInterest;
        this.processingTimestamp = processingTimestamp;
    }

    /**
     * Pre-persist callback to set default timestamp if not provided
     * 
     * <p>Ensures processingTimestamp is always set, either by caller or automatically.
     */
    @PrePersist
    protected void onCreate() {
        if (processingTimestamp == null) {
            processingTimestamp = LocalDateTime.now();
        }
    }
}
