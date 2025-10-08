package com.aws.carddemo.model;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * JPA entity representing customer demographics and personal information.
 * 
 * <p><b>Legacy Mapping:</b> Migrated from COBOL copybook {@code app/cpy/CVCUS01Y.cpy}
 * (CUSTOMER-RECORD structure, 500-byte fixed-length record with 23 fields).
 * 
 * <p><b>Business Purpose:</b> The Customer entity serves as the master record for
 * individual cardholders, containing complete demographic information, contact details,
 * and financial attributes required for credit card account management. Each customer
 * can have multiple credit card accounts linked through the {@code accounts} collection.
 * 
 * <p><b>PCI-DSS Compliance:</b> This entity contains Personally Identifiable Information
 * (PII) and sensitive financial data that require special handling per PCI-DSS and GDPR:
 * <ul>
 *   <li><b>SSN Protection:</b> {@code @ToString.Exclude} prevents SSN exposure in logs.
 *       Use {@link #getSsnMasked()} for display purposes (shows "****1234" format).</li>
 *   <li><b>Date of Birth:</b> {@code @ToString.Exclude} protects DOB from log exposure,
 *       as DOB combined with name is considered PII under GDPR.</li>
 *   <li><b>Government ID:</b> {@code @ToString.Exclude} prevents exposure of driver
 *       license, passport, or other government-issued identification numbers.</li>
 *   <li><b>Audit Trail:</b> Extends {@link BaseEntity} to inherit automatic
 *       {@code createdAt} and {@code updatedAt} timestamps for compliance reporting.</li>
 * </ul>
 * 
 * <p><b>Data Validation:</b> Comprehensive Bean Validation constraints ensure data
 * integrity at entity level before persistence:
 * <ul>
 *   <li>Name fields: {@code @NotBlank} for required fields, {@code @Size(max=25)}</li>
 *   <li>Address fields: {@code @Size(max=50)} for address lines,
 *       {@code @Pattern} for state/country codes</li>
 *   <li>Phone numbers: {@code @Pattern(regexp="\\d{10,15}")} for numeric validation</li>
 *   <li>SSN: {@code @Pattern(regexp="\\d{9}")} for 9-digit format,
 *       {@code unique=true} to prevent duplicates</li>
 *   <li>FICO score: {@code @Min(300) @Max(850)} for valid credit score range</li>
 *   <li>Date of Birth: {@code @Past} ensures realistic birthdates</li>
 * </ul>
 * 
 * <p><b>Database Schema:</b> Maps to {@code CUSTOMER} table with indexes:
 * <pre>
 * CREATE TABLE customer (
 *   customer_id BIGINT PRIMARY KEY GENERATED ALWAYS AS IDENTITY,
 *   first_name VARCHAR(25) NOT NULL,
 *   middle_name VARCHAR(25),
 *   last_name VARCHAR(25) NOT NULL,
 *   address_line_1 VARCHAR(50),
 *   address_line_2 VARCHAR(50),
 *   address_line_3 VARCHAR(50),
 *   state_code CHAR(2),
 *   country_code CHAR(3),
 *   zip_code VARCHAR(10),
 *   phone_number_1 VARCHAR(15),
 *   phone_number_2 VARCHAR(15),
 *   ssn CHAR(9) UNIQUE NOT NULL,
 *   govt_issued_id VARCHAR(20),
 *   date_of_birth DATE,
 *   eft_account_id VARCHAR(10),
 *   primary_cardholder_indicator CHAR(1),
 *   fico_credit_score SMALLINT,
 *   created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
 *   updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
 *   version INTEGER NOT NULL DEFAULT 0,
 *   CONSTRAINT chk_fico_range CHECK (fico_credit_score BETWEEN 300 AND 850),
 *   CONSTRAINT chk_pri_card_holder CHECK (primary_cardholder_indicator IN ('Y', 'N'))
 * );
 * 
 * CREATE UNIQUE INDEX idx_customer_ssn ON customer(ssn);
 * CREATE INDEX idx_customer_name ON customer(last_name, first_name);
 * </pre>
 * 
 * <p><b>Relationships:</b>
 * <ul>
 *   <li><b>One-to-Many with Account:</b> {@code @OneToMany(mappedBy="customer")}
 *       allows a single customer to have multiple credit card accounts. Cascade
 *       operations propagate to child accounts (e.g., deleting customer deletes accounts).</li>
 * </ul>
 * 
 * <p><b>COBOL to Java Data Type Mappings:</b>
 * <table border="1">
 *   <tr><th>COBOL Field</th><th>COBOL Type</th><th>Java Field</th><th>Java Type</th></tr>
 *   <tr><td>CUST-ID</td><td>PIC 9(09)</td><td>customerId</td><td>Long (auto-increment)</td></tr>
 *   <tr><td>CUST-FIRST-NAME</td><td>PIC X(25)</td><td>firstName</td><td>String (max 25)</td></tr>
 *   <tr><td>CUST-MIDDLE-NAME</td><td>PIC X(25)</td><td>middleName</td><td>String (max 25)</td></tr>
 *   <tr><td>CUST-LAST-NAME</td><td>PIC X(25)</td><td>lastName</td><td>String (max 25)</td></tr>
 *   <tr><td>CUST-ADDR-LINE-1</td><td>PIC X(50)</td><td>addressLine1</td><td>String (max 50)</td></tr>
 *   <tr><td>CUST-ADDR-LINE-2</td><td>PIC X(50)</td><td>addressLine2</td><td>String (max 50)</td></tr>
 *   <tr><td>CUST-ADDR-LINE-3</td><td>PIC X(50)</td><td>addressLine3</td><td>String (max 50)</td></tr>
 *   <tr><td>CUST-ADDR-STATE-CD</td><td>PIC X(02)</td><td>stateCode</td><td>String (2 uppercase)</td></tr>
 *   <tr><td>CUST-ADDR-COUNTRY-CD</td><td>PIC X(03)</td><td>countryCode</td><td>String (3 uppercase)</td></tr>
 *   <tr><td>CUST-ADDR-ZIP</td><td>PIC X(10)</td><td>zipCode</td><td>String (5 or 9 digits)</td></tr>
 *   <tr><td>CUST-PHONE-NUM-1</td><td>PIC X(15)</td><td>phoneNumber1</td><td>String (10-15 digits)</td></tr>
 *   <tr><td>CUST-PHONE-NUM-2</td><td>PIC X(15)</td><td>phoneNumber2</td><td>String (10-15 digits)</td></tr>
 *   <tr><td>CUST-SSN</td><td>PIC 9(09)</td><td>ssn</td><td>String (9 digits, unique)</td></tr>
 *   <tr><td>CUST-GOVT-ISSUED-ID</td><td>PIC X(20)</td><td>govtIssuedId</td><td>String (max 20)</td></tr>
 *   <tr><td>CUST-DOB-YYYY-MM-DD</td><td>PIC X(10)</td><td>dateOfBirth</td><td>LocalDate</td></tr>
 *   <tr><td>CUST-EFT-ACCOUNT-ID</td><td>PIC X(10)</td><td>eftAccountId</td><td>String (max 10)</td></tr>
 *   <tr><td>CUST-PRI-CARD-HOLDER-IND</td><td>PIC X(01)</td><td>primaryCardholderIndicator</td><td>String (Y/N)</td></tr>
 *   <tr><td>CUST-FICO-CREDIT-SCORE</td><td>PIC 9(03)</td><td>ficoCreditScore</td><td>Short (300-850)</td></tr>
 * </table>
 * 
 * <p><b>Usage Example:</b>
 * <pre>
 * Customer customer = Customer.builder()
 *     .firstName("John")
 *     .lastName("Doe")
 *     .ssn("123456789")
 *     .dateOfBirth(LocalDate.of(1980, 5, 15))
 *     .ficoCreditScore((short) 720)
 *     .primaryCardholderIndicator("Y")
 *     .build();
 * 
 * customerRepository.save(customer);
 * 
 * // For display, use masked SSN
 * String displaySsn = customer.getSsnMasked(); // Returns "*****6789"
 * </pre>
 * 
 * <p><b>Technical Specification References:</b>
 * <ul>
 *   <li>Section 0.4.1: File Transformation Mapping - Customer entity from CVCUS01Y.cpy</li>
 *   <li>Section 0.8.1: Critical Directive #3 - PCI-DSS compliance with sensitive data masking</li>
 *   <li>Section 0.8.3: Data Type Mapping Standards - PIC 9(09) → String for SSN format preservation</li>
 *   <li>Section 6.2.2.1: Customer Master Table - PII protection requirements</li>
 * </ul>
 * 
 * @see BaseEntity
 * @see Account
 * @author CardDemo Modernization Team
 * @since 1.0.0
 */
@Entity
@Table(
    name = "customer",
    indexes = {
        @Index(name = "idx_customer_ssn", columnList = "ssn", unique = true),
        @Index(name = "idx_customer_name", columnList = "last_name, first_name")
    }
)
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class Customer extends BaseEntity {

    /**
     * Serialization version UID for distributed system compatibility.
     */
    private static final long serialVersionUID = 1L;

    /**
     * Unique customer identifier, auto-generated primary key.
     * 
     * <p><b>Legacy Mapping:</b> CUST-ID PIC 9(09) from CVCUS01Y.cpy.
     * In COBOL, this was a 9-digit numeric field that served as the primary key
     * in the VSAM KSDS customer file. Converted to database-generated IDENTITY
     * for automatic sequence management in PostgreSQL.
     * 
     * <p><b>Generation Strategy:</b> Uses {@code IDENTITY} strategy for PostgreSQL
     * compatibility (equivalent to {@code SERIAL} or {@code BIGSERIAL} column type).
     * Database generates sequential values automatically on INSERT.
     * 
     * <p><b>Business Rule:</b> Customer IDs are permanent and never reused after
     * customer record deletion, ensuring audit trail integrity and preventing
     * accidental association of historical data with new customers.
     * 
     * @see GeneratedValue
     * @see GenerationType#IDENTITY
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "customer_id", nullable = false)
    private Long customerId;

    /**
     * Customer's legal first name (given name).
     * 
     * <p><b>Legacy Mapping:</b> CUST-FIRST-NAME PIC X(25) from CVCUS01Y.cpy.
     * 
     * <p><b>Validation:</b>
     * <ul>
     *   <li>{@code @NotBlank}: Required field, cannot be null, empty, or whitespace-only</li>
     *   <li>{@code @Size(max=25)}: Maximum 25 characters per COBOL source field length</li>
     * </ul>
     * 
     * <p><b>Business Rule:</b> Must match legal name on government-issued ID for
     * Know Your Customer (KYC) compliance and fraud prevention.
     */
    @NotBlank(message = "First name is required")
    @Size(max = 25, message = "First name cannot exceed 25 characters")
    @Column(name = "first_name", length = 25, nullable = false)
    private String firstName;

    /**
     * Customer's middle name or middle initial (optional).
     * 
     * <p><b>Legacy Mapping:</b> CUST-MIDDLE-NAME PIC X(25) from CVCUS01Y.cpy.
     * 
     * <p><b>Validation:</b>
     * <ul>
     *   <li>{@code @Size(max=25)}: Maximum 25 characters per COBOL source field length</li>
     *   <li>Optional field: Can be null or empty</li>
     * </ul>
     * 
     * <p><b>Business Rule:</b> Middle names are optional but recommended for
     * reducing false positives in fraud detection and credit bureau matching.
     */
    @Size(max = 25, message = "Middle name cannot exceed 25 characters")
    @Column(name = "middle_name", length = 25)
    private String middleName;

    /**
     * Customer's legal last name (surname/family name).
     * 
     * <p><b>Legacy Mapping:</b> CUST-LAST-NAME PIC X(25) from CVCUS01Y.cpy.
     * 
     * <p><b>Validation:</b>
     * <ul>
     *   <li>{@code @NotBlank}: Required field, cannot be null, empty, or whitespace-only</li>
     *   <li>{@code @Size(max=25)}: Maximum 25 characters per COBOL source field length</li>
     * </ul>
     * 
     * <p><b>Database Index:</b> Part of composite index {@code idx_customer_name}
     * for efficient customer name searches. Indexed as (last_name, first_name) to
     * optimize "Smith, John" lookup patterns common in customer service workflows.
     * 
     * <p><b>Business Rule:</b> Must match legal name on government-issued ID for KYC.
     */
    @NotBlank(message = "Last name is required")
    @Size(max = 25, message = "Last name cannot exceed 25 characters")
    @Column(name = "last_name", length = 25, nullable = false)
    private String lastName;

    /**
     * Primary street address line (street number and name).
     * 
     * <p><b>Legacy Mapping:</b> CUST-ADDR-LINE-1 PIC X(50) from CVCUS01Y.cpy.
     * 
     * <p><b>Validation:</b>
     * <ul>
     *   <li>{@code @Size(max=50)}: Maximum 50 characters per COBOL source field length</li>
     *   <li>Optional field: Can be null, but typically populated for billing purposes</li>
     * </ul>
     * 
     * <p><b>Business Rule:</b> Used for billing statement mailing and cardholder
     * verification. Must be a valid US postal address for domestic customers.
     */
    @Size(max = 50, message = "Address line 1 cannot exceed 50 characters")
    @Column(name = "address_line_1", length = 50)
    private String addressLine1;

    /**
     * Secondary street address line (apartment, suite, unit number).
     * 
     * <p><b>Legacy Mapping:</b> CUST-ADDR-LINE-2 PIC X(50) from CVCUS01Y.cpy.
     * 
     * <p><b>Validation:</b>
     * <ul>
     *   <li>{@code @Size(max=50)}: Maximum 50 characters per COBOL source field length</li>
     *   <li>Optional field: Can be null</li>
     * </ul>
     */
    @Size(max = 50, message = "Address line 2 cannot exceed 50 characters")
    @Column(name = "address_line_2", length = 50)
    private String addressLine2;

    /**
     * Tertiary street address line (additional address details).
     * 
     * <p><b>Legacy Mapping:</b> CUST-ADDR-LINE-3 PIC X(50) from CVCUS01Y.cpy.
     * 
     * <p><b>Validation:</b>
     * <ul>
     *   <li>{@code @Size(max=50)}: Maximum 50 characters per COBOL source field length</li>
     *   <li>Optional field: Can be null, rarely used</li>
     * </ul>
     */
    @Size(max = 50, message = "Address line 3 cannot exceed 50 characters")
    @Column(name = "address_line_3", length = 50)
    private String addressLine3;

    /**
     * Two-letter US state code (uppercase).
     * 
     * <p><b>Legacy Mapping:</b> CUST-ADDR-STATE-CD PIC X(02) from CVCUS01Y.cpy.
     * 
     * <p><b>Validation:</b>
     * <ul>
     *   <li>{@code @Pattern(regexp="[A-Z]{2}")}: Exactly 2 uppercase letters</li>
     *   <li>{@code @Size(min=2, max=2)}: Enforces 2-character length</li>
     * </ul>
     * 
     * <p><b>Business Rule:</b> Must be valid US state/territory code per USPS
     * standards (e.g., "CA", "NY", "TX"). Validated against reference data in
     * {@code ValidationUtil} (migrated from COBOL CSLKPCDY.cpy lookups).
     * 
     * <p><b>Examples:</b> "CA" (California), "TX" (Texas), "NY" (New York)
     */
    @Pattern(regexp = "[A-Z]{2}", message = "State code must be 2 uppercase letters")
    @Size(min = 2, max = 2, message = "State code must be exactly 2 characters")
    @Column(name = "state_code", length = 2)
    private String stateCode;

    /**
     * Three-letter ISO 3166-1 alpha-3 country code (uppercase).
     * 
     * <p><b>Legacy Mapping:</b> CUST-ADDR-COUNTRY-CD PIC X(03) from CVCUS01Y.cpy.
     * 
     * <p><b>Validation:</b>
     * <ul>
     *   <li>{@code @Pattern(regexp="[A-Z]{3}")}: Exactly 3 uppercase letters</li>
     *   <li>{@code @Size(min=3, max=3)}: Enforces 3-character length</li>
     * </ul>
     * 
     * <p><b>Business Rule:</b> Should be "USA" for domestic customers. International
     * country codes follow ISO 3166-1 alpha-3 standard (e.g., "CAN" for Canada,
     * "MEX" for Mexico, "GBR" for United Kingdom).
     * 
     * <p><b>Examples:</b> "USA" (United States), "CAN" (Canada), "MEX" (Mexico)
     */
    @Pattern(regexp = "[A-Z]{3}", message = "Country code must be 3 uppercase letters")
    @Size(min = 3, max = 3, message = "Country code must be exactly 3 characters")
    @Column(name = "country_code", length = 3)
    private String countryCode;

    /**
     * ZIP code or postal code (5-digit or 9-digit ZIP+4 format).
     * 
     * <p><b>Legacy Mapping:</b> CUST-ADDR-ZIP PIC X(10) from CVCUS01Y.cpy.
     * 
     * <p><b>Validation:</b>
     * <ul>
     *   <li>{@code @Pattern}: Accepts "12345" or "12345-6789" formats</li>
     *   <li>{@code @Size(max=10)}: Maximum 10 characters (ZIP+4 with hyphen)</li>
     * </ul>
     * 
     * <p><b>Business Rule:</b> Must be valid US ZIP code for domestic addresses.
     * ZIP+4 format preferred for more accurate address verification and lower
     * mail return rates.
     * 
     * <p><b>Examples:</b> "90210", "10001-1234"
     */
    @Pattern(regexp = "\\d{5}(-\\d{4})?", message = "ZIP code must be 5 digits or 9 digits with hyphen (12345 or 12345-6789)")
    @Size(max = 10, message = "ZIP code cannot exceed 10 characters")
    @Column(name = "zip_code", length = 10)
    private String zipCode;

    /**
     * Primary contact phone number (10-15 digits, no formatting).
     * 
     * <p><b>Legacy Mapping:</b> CUST-PHONE-NUM-1 PIC X(15) from CVCUS01Y.cpy.
     * 
     * <p><b>Validation:</b>
     * <ul>
     *   <li>{@code @Pattern}: 10-15 consecutive digits, no spaces/hyphens/parentheses</li>
     *   <li>{@code @Size(max=15)}: Maximum 15 characters</li>
     * </ul>
     * 
     * <p><b>Storage Format:</b> Stored without formatting (e.g., "5551234567" not
     * "(555) 123-4567"). Application layer handles display formatting.
     * 
     * <p><b>Business Rule:</b> Used for customer service contact and fraud verification.
     * Must be reachable phone number for account security purposes.
     * 
     * <p><b>Examples:</b> "5551234567" (10-digit US), "15551234567" (11-digit with country code)
     */
    @Pattern(regexp = "\\d{10,15}", message = "Phone number must be 10-15 digits without formatting")
    @Size(max = 15, message = "Phone number cannot exceed 15 characters")
    @Column(name = "phone_number_1", length = 15)
    private String phoneNumber1;

    /**
     * Secondary contact phone number (optional, 10-15 digits).
     * 
     * <p><b>Legacy Mapping:</b> CUST-PHONE-NUM-2 PIC X(15) from CVCUS01Y.cpy.
     * 
     * <p><b>Validation:</b>
     * <ul>
     *   <li>{@code @Pattern}: 10-15 consecutive digits, no spaces/hyphens/parentheses</li>
     *   <li>{@code @Size(max=15)}: Maximum 15 characters</li>
     *   <li>Optional field: Can be null</li>
     * </ul>
     * 
     * <p><b>Business Rule:</b> Alternate contact number for fraud alerts and
     * customer service escalations.
     */
    @Pattern(regexp = "\\d{10,15}", message = "Phone number must be 10-15 digits without formatting")
    @Size(max = 15, message = "Phone number cannot exceed 15 characters")
    @Column(name = "phone_number_2", length = 15)
    private String phoneNumber2;

    /**
     * Social Security Number (SSN), 9-digit unique identifier (PII - PROTECTED).
     * 
     * <p><b>Legacy Mapping:</b> CUST-SSN PIC 9(09) from CVCUS01Y.cpy.
     * 
     * <p><b>PCI-DSS/GDPR COMPLIANCE WARNING:</b> This field contains highly sensitive
     * Personally Identifiable Information (PII) that requires strict protection:
     * <ul>
     *   <li><b>Logging:</b> {@code @ToString.Exclude} prevents SSN from appearing in
     *       application logs, error messages, or debug output.</li>
     *   <li><b>Display:</b> Use {@link #getSsnMasked()} method to show masked format
     *       ("*****1234" with last 4 digits visible).</li>
     *   <li><b>Database:</b> Consider column-level encryption for SSN storage in
     *       production environments (e.g., PostgreSQL pgcrypto extension).</li>
     *   <li><b>Access Control:</b> Restrict SSN access to authorized roles only
     *       (e.g., ROLE_ADMIN, ROLE_COMPLIANCE).</li>
     * </ul>
     * 
     * <p><b>Validation:</b>
     * <ul>
     *   <li>{@code @Pattern(regexp="\\d{9}")}: Exactly 9 digits, no hyphens</li>
     *   <li>{@code @Size(min=9, max=9)}: Enforces 9-character length</li>
     *   <li>{@code unique=true}: Database constraint prevents duplicate SSNs</li>
     *   <li>{@code nullable=false}: Required field for identity verification</li>
     * </ul>
     * 
     * <p><b>Data Type Mapping:</b> COBOL PIC 9(09) → Java String (not Long) to:
     * <ul>
     *   <li>Preserve leading zeros (SSN "000123456" not 123456)</li>
     *   <li>Support pattern validation without numeric conversion</li>
     *   <li>Align with credit card industry standards (PCI-DSS)</li>
     * </ul>
     * 
     * <p><b>Database Index:</b> Unique index {@code idx_customer_ssn} enables fast
     * customer lookup by SSN for identity verification during login and fraud checks.
     * 
     * <p><b>Business Rule:</b> SSN must be verified against Social Security
     * Administration (SSA) validation service for new account applications to
     * prevent identity theft and comply with USA PATRIOT Act requirements.
     * 
     * @see #getSsnMasked()
     */
    @ToString.Exclude
    @NotBlank(message = "SSN is required")
    @Pattern(regexp = "\\d{9}", message = "SSN must be exactly 9 digits")
    @Size(min = 9, max = 9, message = "SSN must be exactly 9 characters")
    @Column(name = "ssn", length = 9, unique = true, nullable = false)
    private String ssn;

    /**
     * Government-issued identification number (driver license, passport, etc.) (PII - PROTECTED).
     * 
     * <p><b>Legacy Mapping:</b> CUST-GOVT-ISSUED-ID PIC X(20) from CVCUS01Y.cpy.
     * 
     * <p><b>PII Protection:</b> {@code @ToString.Exclude} prevents exposure in logs.
     * Contains sensitive identification numbers that could facilitate identity theft.
     * 
     * <p><b>Validation:</b>
     * <ul>
     *   <li>{@code @Size(max=20)}: Maximum 20 characters per COBOL source</li>
     *   <li>Optional field: Can be null if SSN is primary identifier</li>
     * </ul>
     * 
     * <p><b>Business Rule:</b> Stores driver license number, passport number, or
     * other government ID for secondary identity verification. Format varies by
     * state/country of issuance. Consider storing ID type separately for validation.
     * 
     * <p><b>Examples:</b> "D1234567" (driver license), "123456789" (passport)
     */
    @ToString.Exclude
    @Size(max = 20, message = "Government issued ID cannot exceed 20 characters")
    @Column(name = "govt_issued_id", length = 20)
    private String govtIssuedId;

    /**
     * Customer's date of birth in ISO 8601 format (YYYY-MM-DD) (PII - PROTECTED).
     * 
     * <p><b>Legacy Mapping:</b> CUST-DOB-YYYY-MM-DD PIC X(10) from CVCUS01Y.cpy.
     * In COBOL, this was stored as a 10-character string "YYYY-MM-DD". Converted to
     * {@code LocalDate} for type-safe date arithmetic and validation.
     * 
     * <p><b>PII Protection:</b> {@code @ToString.Exclude} prevents DOB exposure in logs.
     * Combined with name and address, DOB is considered PII under GDPR and requires
     * protection from unauthorized access.
     * 
     * <p><b>Validation:</b>
     * <ul>
     *   <li>{@code @Past}: Ensures date of birth is in the past, not future</li>
     *   <li>Optional field: Can be null, but typically required for credit applications</li>
     * </ul>
     * 
     * <p><b>Business Rule:</b> Used for:
     * <ul>
     *   <li>Age verification (must be 18+ for credit card applications)</li>
     *   <li>Fraud detection (DOB matching during identity verification)</li>
     *   <li>Regulatory compliance (COPPA for under-13 restrictions)</li>
     * </ul>
     * 
     * <p><b>Data Type Benefits:</b> {@code LocalDate} provides:
     * <ul>
     *   <li>Automatic age calculation: {@code Period.between(dateOfBirth, LocalDate.now()).getYears()}</li>
     *   <li>Timezone-agnostic date representation (no time component)</li>
     *   <li>Immutable and thread-safe</li>
     * </ul>
     */
    @ToString.Exclude
    @Past(message = "Date of birth must be in the past")
    @Column(name = "date_of_birth")
    private LocalDate dateOfBirth;

    /**
     * FICO credit score (range: 300-850).
     * 
     * <p><b>Legacy Mapping:</b> CUST-FICO-CREDIT-SCORE PIC 9(03) from CVCUS01Y.cpy.
     * 
     * <p><b>Validation:</b>
     * <ul>
     *   <li>{@code @Min(300)}: FICO scores start at 300</li>
     *   <li>{@code @Max(850)}: FICO scores cap at 850 (perfect credit)</li>
     * </ul>
     * 
     * <p><b>Data Type:</b> {@code Short} (16-bit signed integer) is sufficient for
     * range 300-850 and saves memory compared to {@code Integer}.
     * 
     * <p><b>Business Rule:</b> Credit score determines:
     * <ul>
     *   <li>Credit limit approval (higher score → higher limit)</li>
     *   <li>Interest rate tier (higher score → lower APR)</li>
     *   <li>Application auto-approval threshold (typically 680+)</li>
     * </ul>
     * 
     * <p><b>Credit Score Interpretation:</b>
     * <ul>
     *   <li>800-850: Exceptional credit</li>
     *   <li>740-799: Very good credit</li>
     *   <li>670-739: Good credit</li>
     *   <li>580-669: Fair credit</li>
     *   <li>300-579: Poor credit</li>
     * </ul>
     * 
     * <p><b>Data Source:</b> Typically obtained from credit bureaus (Experian,
     * Equifax, TransUnion) during credit application process. Must be refreshed
     * periodically (e.g., annually) for credit limit reviews.
     */
    @Min(value = 300, message = "FICO credit score must be at least 300")
    @Max(value = 850, message = "FICO credit score cannot exceed 850")
    @Column(name = "fico_credit_score")
    private Short ficoCreditScore;

    /**
     * Electronic Funds Transfer (EFT) account identifier for autopay.
     * 
     * <p><b>Legacy Mapping:</b> CUST-EFT-ACCOUNT-ID PIC X(10) from CVCUS01Y.cpy.
     * 
     * <p><b>Validation:</b>
     * <ul>
     *   <li>{@code @Size(max=10)}: Maximum 10 characters per COBOL source</li>
     *   <li>Optional field: Can be null if customer does not use autopay</li>
     * </ul>
     * 
     * <p><b>Business Rule:</b> Links to external bank account for automatic payment
     * processing via ACH (Automated Clearing House) network. Required for customers
     * enrolled in autopay for monthly statement balances.
     * 
     * <p><b>Security Note:</b> This is a reference ID, not the actual bank account
     * number. Actual account/routing numbers are stored in separate encrypted vault.
     */
    @Size(max = 10, message = "EFT account ID cannot exceed 10 characters")
    @Column(name = "eft_account_id", length = 10)
    private String eftAccountId;

    /**
     * Primary cardholder indicator ("Y" = primary, "N" = additional cardholder).
     * 
     * <p><b>Legacy Mapping:</b> CUST-PRI-CARD-HOLDER-IND PIC X(01) from CVCUS01Y.cpy.
     * 
     * <p><b>Validation:</b>
     * <ul>
     *   <li>{@code @Pattern(regexp="[YN]")}: Must be exactly "Y" or "N"</li>
     *   <li>{@code @Size(min=1, max=1)}: Enforces single-character length</li>
     * </ul>
     * 
     * <p><b>Business Rule:</b>
     * <ul>
     *   <li><b>"Y" (Primary):</b> The main account holder with full account privileges,
     *       receives billing statements, and is legally responsible for all charges.</li>
     *   <li><b>"N" (Additional):</b> Authorized user with limited privileges (e.g.,
     *       cannot close account, change credit limit, or add other users).</li>
     * </ul>
     * 
     * <p><b>Credit Reporting:</b> Only primary cardholder's credit score is affected
     * by account payment history. Additional cardholders may or may not have activity
     * reported to credit bureaus depending on card issuer policy.
     * 
     * <p><b>Design Note:</b> Could be refactored to {@code Boolean} or enum, but
     * preserved as String to maintain exact COBOL field semantics during migration.
     */
    @Pattern(regexp = "[YN]", message = "Primary cardholder indicator must be 'Y' or 'N'")
    @Size(min = 1, max = 1, message = "Primary cardholder indicator must be exactly 1 character")
    @Column(name = "primary_cardholder_indicator", length = 1)
    private String primaryCardholderIndicator;

    /**
     * Collection of credit card accounts associated with this customer.
     * 
     * <p><b>Relationship:</b> One-to-Many bidirectional relationship with {@link Account}.
     * A single customer can have multiple credit card accounts (e.g., personal card,
     * business card, supplementary cards for family members).
     * 
     * <p><b>Cascade Operations:</b> {@code CascadeType.ALL} means:
     * <ul>
     *   <li><b>PERSIST:</b> Saving customer automatically saves associated accounts</li>
     *   <li><b>MERGE:</b> Updating customer updates associated accounts</li>
     *   <li><b>REMOVE:</b> Deleting customer deletes all associated accounts</li>
     *   <li><b>REFRESH:</b> Refreshing customer refreshes accounts from database</li>
     *   <li><b>DETACH:</b> Detaching customer detaches accounts</li>
     * </ul>
     * 
     * <p><b>Mapped By:</b> {@code mappedBy="customer"} indicates the owning side
     * of the relationship is the {@code customer} field in the {@code Account} entity.
     * This means the foreign key {@code customer_id} exists in the {@code account} table.
     * 
     * <p><b>Lazy Loading:</b> By default, {@code @OneToMany} uses lazy loading.
     * Accounts are not fetched until explicitly accessed via {@code getAccounts()}.
     * For eager loading in specific queries, use {@code JOIN FETCH} in JPQL.
     * 
     * <p><b>Initialization:</b> Initialized to {@code new ArrayList<>()} to prevent
     * {@code NullPointerException} when accessing collection before JPA initialization.
     * Allows safe operations like {@code customer.getAccounts().add(account)} on
     * transient entities.
     * 
     * <p><b>Business Rule:</b> Most customers have 1-3 accounts. High-value customers
     * may have 5+ accounts. Typical account types:
     * <ul>
     *   <li>Personal credit card (rewards, cashback)</li>
     *   <li>Business credit card (expense tracking)</li>
     *   <li>Secured credit card (credit building)</li>
     *   <li>Store credit card (retail-specific)</li>
     * </ul>
     * 
     * <p><b>Query Performance:</b> Loading all accounts for all customers in a list
     * query causes N+1 problem. Use {@code JOIN FETCH} or {@code @EntityGraph} to
     * optimize bulk queries:
     * <pre>
     * {@code
     * @Query("SELECT c FROM Customer c LEFT JOIN FETCH c.accounts WHERE c.customerId IN :ids")
     * List<Customer> findByIdWithAccounts(@Param("ids") List<Long> ids);
     * }
     * </pre>
     * 
     * @see Account
     * @see CascadeType
     */
    @OneToMany(mappedBy = "customer", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<Account> accounts = new ArrayList<>();

    /**
     * Returns a PII-compliant masked version of the Social Security Number.
     * 
     * <p><b>Masking Format:</b> Shows "*****" followed by last 4 digits of SSN.
     * Example: SSN "123456789" returns "*****6789".
     * 
     * <p><b>PCI-DSS Compliance:</b> This method provides a safe way to display SSN
     * in user interfaces, reports, and logs without exposing the full number.
     * Per PCI-DSS requirement 3.3, only the last 4 digits of SSN can be displayed
     * in UI or printed reports.
     * 
     * <p><b>Usage:</b>
     * <pre>
     * Customer customer = customerRepository.findById(1L).orElseThrow();
     * String displaySsn = customer.getSsnMasked(); // "*****6789"
     * System.out.println("SSN: " + displaySsn); // Safe for logging
     * </pre>
     * 
     * <p><b>JPA Note:</b> {@code @Transient} annotation marks this method as
     * non-persistent, meaning:
     * <ul>
     *   <li>Not mapped to any database column</li>
     *   <li>Not included in SQL SELECT/INSERT/UPDATE statements</li>
     *   <li>Computed on-the-fly from the {@code ssn} field</li>
     * </ul>
     * 
     * <p><b>Null Safety:</b> Returns null if SSN is null. Returns "*****" if SSN
     * is less than 5 characters (defensive programming for invalid data).
     * 
     * <p><b>Alternative Display Formats:</b> Some organizations prefer different
     * masking patterns:
     * <ul>
     *   <li>XXX-XX-6789 (preserves SSN format)</li>
     *   <li>***-**-6789 (preserves SSN format with asterisks)</li>
     *   <li>•••-••-6789 (bullet character masking)</li>
     * </ul>
     * 
     * @return masked SSN with format "*****NNNN", or null if SSN is null
     */
    @Transient
    public String getSsnMasked() {
        if (ssn == null || ssn.length() < 5) {
            return ssn != null ? "*****" : null;
        }
        return "*****" + ssn.substring(5);
    }

    /**
     * Helper method to add an account to the customer's account collection.
     * 
     * <p><b>Bidirectional Relationship Management:</b> This method ensures both
     * sides of the bidirectional relationship are properly maintained:
     * <ol>
     *   <li>Adds account to this customer's {@code accounts} collection</li>
     *   <li>Sets this customer as the account's {@code customer} reference</li>
     * </ol>
     * 
     * <p><b>Usage:</b>
     * <pre>
     * Customer customer = new Customer();
     * Account account = new Account();
     * customer.addAccount(account); // Sets up bidirectional link
     * customerRepository.save(customer); // Cascades to save account
     * </pre>
     * 
     * <p><b>Cascade Behavior:</b> Since {@code accounts} has {@code CascadeType.ALL},
     * calling {@code customerRepository.save(customer)} will automatically persist
     * the associated account without explicit {@code accountRepository.save(account)}.
     * 
     * <p><b>Duplicate Prevention:</b> If account is already in collection, this
     * method is idempotent (no duplicate entries).
     * 
     * @param account the account to add to this customer
     * @throws NullPointerException if account is null
     */
    public void addAccount(Account account) {
        if (account != null) {
            accounts.add(account);
            account.setCustomer(this);
        }
    }

    /**
     * Helper method to remove an account from the customer's account collection.
     * 
     * <p><b>Bidirectional Relationship Management:</b> This method ensures both
     * sides of the bidirectional relationship are properly cleared:
     * <ol>
     *   <li>Removes account from this customer's {@code accounts} collection</li>
     *   <li>Clears the account's {@code customer} reference (sets to null)</li>
     * </ol>
     * 
     * <p><b>Orphan Removal:</b> Since {@code accounts} has {@code orphanRemoval=true},
     * removing an account from the collection and flushing the persistence context
     * will automatically DELETE the account from the database.
     * 
     * <p><b>Usage:</b>
     * <pre>
     * Customer customer = customerRepository.findById(1L).orElseThrow();
     * Account account = customer.getAccounts().get(0);
     * customer.removeAccount(account); // Removes bidirectional link
     * customerRepository.save(customer); // Account is deleted from database
     * </pre>
     * 
     * @param account the account to remove from this customer
     */
    public void removeAccount(Account account) {
        if (account != null) {
            accounts.remove(account);
            account.setCustomer(null);
        }
    }
}
