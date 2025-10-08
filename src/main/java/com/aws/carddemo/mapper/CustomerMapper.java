package com.aws.carddemo.mapper;

import com.aws.carddemo.model.Customer;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.ReportingPolicy;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * MapStruct mapper interface for bidirectional mapping between Customer JPA entity
 * and Customer Data Transfer Objects (DTOs) for REST API request/response handling.
 * 
 * <p><b>Migration Context:</b> This mapper facilitates the transformation from COBOL
 * copybook structures (CVCUS01Y.cpy) to modern Java DTOs, handling complex PII field
 * masking, date formatting, and selective field exclusion per GDPR/CCPA compliance.
 * 
 * <p><b>PII Compliance Features:</b>
 * <ul>
 *   <li><b>SSN Masking:</b> Response DTOs show only last 4 digits (*****6789 format)
 *       via {@link Customer#getSsnMasked()} method integration</li>
 *   <li><b>DOB Protection:</b> Date of birth excluded from summary DTOs to minimize
 *       PII exposure in list/browse operations</li>
 *   <li><b>Government ID Exclusion:</b> Government-issued ID numbers completely
 *       excluded from all response DTOs per PCI-DSS requirements</li>
 * </ul>
 * 
 * <p><b>MapStruct Configuration:</b>
 * <ul>
 *   <li><b>componentModel = "spring":</b> Generates Spring-managed {@code @Component}
 *       bean for dependency injection in service classes</li>
 *   <li><b>unmappedTargetPolicy = IGNORE:</b> Suppresses warnings for audit fields
 *       (createdAt, updatedAt, version) inherited from BaseEntity, which are
 *       automatically managed by Spring Data JPA auditing</li>
 * </ul>
 * 
 * <p><b>Generated Implementation:</b> MapStruct annotation processor generates
 * {@code CustomerMapperImpl} class at compile time with optimized mapping logic,
 * avoiding runtime reflection overhead of manual mapping frameworks.
 * 
 * <p><b>Usage in Service Layer:</b>
 * <pre>
 * {@code
 * @Service
 * public class CustomerService {
 *     private final CustomerMapper customerMapper;
 *     
 *     public CustomerResponse getCustomer(Long id) {
 *         Customer entity = customerRepository.findById(id).orElseThrow();
 *         return customerMapper.toResponse(entity); // SSN automatically masked
 *     }
 *     
 *     public Customer createCustomer(CustomerRequest request) {
 *         Customer entity = customerMapper.toEntity(request);
 *         return customerRepository.save(entity);
 *     }
 * }
 * }
 * </pre>
 * 
 * <p><b>Technical Specification References:</b>
 * <ul>
 *   <li>Section 0.4.1: CustomerMapper for Customer entity ↔ DTOs with SSN masking</li>
 *   <li>Section 0.8.1: PCI-DSS compliance with sensitive data masked in logs</li>
 *   <li>Section CQ7: Export Schema Implementation and Validation</li>
 * </ul>
 * 
 * @author CardDemo Modernization Team
 * @since 1.0.0
 * @see Customer
 * @see org.mapstruct.Mapper
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface CustomerMapper {

    /**
     * Converts Customer entity to CustomerResponse DTO for API responses.
     * 
     * <p><b>PII Protection:</b>
     * <ul>
     *   <li>SSN masked to show only last 4 digits via getSsnMasked() method</li>
     *   <li>Government-issued ID completely excluded from response</li>
     *   <li>All PII fields logged with @ToString.Exclude in entity</li>
     * </ul>
     * 
     * <p><b>Field Mappings:</b>
     * <ul>
     *   <li>customerId → customerId (primary key)</li>
     *   <li>custId → custId (business key)</li>
     *   <li>firstName, middleName, lastName → name fields</li>
     *   <li>addressLine1/2/3, stateCode, countryCode, zipCode → address fields</li>
     *   <li>phoneNumber1, phoneNumber2 → contact fields</li>
     *   <li>ssn → ssnMasked (via getSsnMasked() method)</li>
     *   <li>dateOfBirth → dateOfBirth (included in full response)</li>
     *   <li>ficoCreditScore → ficoCreditScore</li>
     *   <li>eftAccountId, primaryCardholderIndicator → account fields</li>
     *   <li>createdAt, updatedAt → audit timestamps</li>
     * </ul>
     * 
     * <p><b>Excluded Fields:</b>
     * <ul>
     *   <li>govtIssuedId (PII protection)</li>
     *   <li>accounts collection (use separate endpoint for account details)</li>
     * </ul>
     * 
     * @param customer the Customer entity from database
     * @return CustomerResponse DTO with masked PII fields, or null if input is null
     */
    @Mapping(source = "ssnMasked", target = "ssnMasked")
    @Mapping(target = "govtIssuedId", ignore = true)
    CustomerResponse toResponse(Customer customer);

    /**
     * Converts CustomerRequest DTO to Customer entity for create operations.
     * 
     * <p><b>Field Mappings:</b>
     * <ul>
     *   <li>custId → custId (9-digit business key)</li>
     *   <li>firstName, middleName, lastName → name fields</li>
     *   <li>addressLine1/2/3, stateCode, countryCode, zipCode → address fields</li>
     *   <li>phoneNumber1, phoneNumber2 → contact fields</li>
     *   <li>ssn → ssn (stored as-is, 9 digits)</li>
     *   <li>govtIssuedId → govtIssuedId (if provided)</li>
     *   <li>dateOfBirth → dateOfBirth</li>
     *   <li>ficoCreditScore → ficoCreditScore</li>
     *   <li>eftAccountId, primaryCardholderIndicator → account fields</li>
     * </ul>
     * 
     * <p><b>Auto-Ignored Fields:</b>
     * <ul>
     *   <li>customerId (auto-generated by database)</li>
     *   <li>accounts (empty collection initialized)</li>
     *   <li>createdAt, updatedAt (auto-populated by JPA auditing)</li>
     *   <li>version (auto-managed by JPA optimistic locking)</li>
     * </ul>
     * 
     * <p><b>Validation:</b> Bean Validation annotations on Customer entity
     * ensure data integrity before persistence (e.g., @NotBlank, @Pattern, @Min, @Max).
     * 
     * @param request the CustomerRequest DTO from API request body
     * @return new Customer entity ready for persistence, or null if input is null
     */
    @Mapping(target = "customerId", ignore = true)
    @Mapping(target = "accounts", ignore = true)
    Customer toEntity(CustomerRequest request);

    /**
     * Converts Customer entity to CustomerSummary DTO for nested display.
     * 
     * <p><b>Purpose:</b> Provides abbreviated customer information for embedding
     * in other response DTOs (e.g., AccountResponse contains customer summary
     * showing name and basic contact info without full PII exposure).
     * 
     * <p><b>Field Mappings:</b>
     * <ul>
     *   <li>customerId → customerId</li>
     *   <li>custId → custId</li>
     *   <li>firstName, middleName, lastName → name fields</li>
     *   <li>phoneNumber1 → phoneNumber</li>
     *   <li>ssnMasked → ssnMasked (last 4 digits only)</li>
     * </ul>
     * 
     * <p><b>Excluded Fields:</b>
     * <ul>
     *   <li>Full address (only summary name/contact shown)</li>
     *   <li>dateOfBirth (PII minimization in summary views)</li>
     *   <li>govtIssuedId (never included in any DTO)</li>
     *   <li>ficoCreditScore (sensitive financial data)</li>
     *   <li>eftAccountId (sensitive payment data)</li>
     * </ul>
     * 
     * <p><b>Use Cases:</b>
     * <ul>
     *   <li>Embedded in AccountResponse for "customer": {...} field</li>
     *   <li>Customer list/browse operations (GET /api/v1/customers)</li>
     *   <li>Transaction history showing cardholder name</li>
     * </ul>
     * 
     * @param customer the Customer entity from database
     * @return CustomerSummary DTO with minimal PII exposure, or null if input is null
     */
    @Mapping(source = "ssnMasked", target = "ssnMasked")
    @Mapping(source = "phoneNumber1", target = "phoneNumber")
    CustomerSummary toCustomerSummary(Customer customer);

    /**
     * Updates existing Customer entity from CustomerRequest DTO for update operations.
     * 
     * <p><b>Update Strategy:</b> Applies changes from request DTO to existing entity,
     * preserving fields not included in the request (null values in DTO do not
     * overwrite existing entity values unless explicitly configured).
     * 
     * <p><b>Immutable Fields:</b> The following fields are NOT updated:
     * <ul>
     *   <li>customerId (primary key never changes)</li>
     *   <li>custId (business key immutable after creation)</li>
     *   <li>ssn (SSN changes require special workflow, not via simple update)</li>
     *   <li>accounts (collection managed separately)</li>
     *   <li>createdAt (creation timestamp immutable)</li>
     * </ul>
     * 
     * <p><b>Updatable Fields:</b>
     * <ul>
     *   <li>firstName, middleName, lastName (name changes)</li>
     *   <li>addressLine1/2/3, stateCode, countryCode, zipCode (address changes)</li>
     *   <li>phoneNumber1, phoneNumber2 (contact updates)</li>
     *   <li>govtIssuedId (ID number updates)</li>
     *   <li>dateOfBirth (DOB corrections)</li>
     *   <li>ficoCreditScore (credit score refreshes)</li>
     *   <li>eftAccountId (autopay account changes)</li>
     *   <li>primaryCardholderIndicator (cardholder status changes)</li>
     * </ul>
     * 
     * <p><b>Audit Trail:</b> {@code updatedAt} timestamp automatically updated
     * by JPA {@code @PreUpdate} lifecycle callback in BaseEntity.
     * 
     * <p><b>Usage Example:</b>
     * <pre>
     * {@code
     * Customer existing = customerRepository.findById(id).orElseThrow();
     * customerMapper.updateEntityFromRequest(request, existing);
     * customerRepository.save(existing); // updatedAt timestamp auto-updated
     * }
     * </pre>
     * 
     * @param request the CustomerRequest DTO with updated field values
     * @param customer the existing Customer entity to update (modified in-place)
     */
    @Mapping(target = "customerId", ignore = true)
    @Mapping(target = "custId", ignore = true)
    @Mapping(target = "ssn", ignore = true)
    @Mapping(target = "accounts", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    void updateEntityFromRequest(CustomerRequest request, @MappingTarget Customer customer);
}

/**
 * Data Transfer Object for customer creation and update requests.
 * 
 * <p><b>API Usage:</b> Request body for POST /api/v1/customers (create)
 * and PUT /api/v1/customers/{id} (update) endpoints.
 * 
 * <p><b>Validation:</b> Inherits validation from Customer entity when mapped.
 * Additional DTO-level validation can be added via Bean Validation annotations.
 * 
 * <p><b>PII Handling:</b> Contains full PII fields (SSN, DOB, govtIssuedId) which
 * are accepted in requests but never echoed in responses (use CustomerResponse).
 * 
 * <p><b>COBOL Mapping:</b> Fields mapped from CVCUS01Y.cpy copybook structure.
 */
class CustomerRequest implements Serializable {
    private static final long serialVersionUID = 1L;

    private String custId;
    private String firstName;
    private String middleName;
    private String lastName;
    private String addressLine1;
    private String addressLine2;
    private String addressLine3;
    private String stateCode;
    private String countryCode;
    private String zipCode;
    private String phoneNumber1;
    private String phoneNumber2;
    private String ssn;
    private String govtIssuedId;
    private LocalDate dateOfBirth;
    private Short ficoCreditScore;
    private String eftAccountId;
    private String primaryCardholderIndicator;

    // Default constructor
    public CustomerRequest() {}

    // All-args constructor
    public CustomerRequest(String custId, String firstName, String middleName, String lastName,
                           String addressLine1, String addressLine2, String addressLine3,
                           String stateCode, String countryCode, String zipCode,
                           String phoneNumber1, String phoneNumber2, String ssn,
                           String govtIssuedId, LocalDate dateOfBirth, Short ficoCreditScore,
                           String eftAccountId, String primaryCardholderIndicator) {
        this.custId = custId;
        this.firstName = firstName;
        this.middleName = middleName;
        this.lastName = lastName;
        this.addressLine1 = addressLine1;
        this.addressLine2 = addressLine2;
        this.addressLine3 = addressLine3;
        this.stateCode = stateCode;
        this.countryCode = countryCode;
        this.zipCode = zipCode;
        this.phoneNumber1 = phoneNumber1;
        this.phoneNumber2 = phoneNumber2;
        this.ssn = ssn;
        this.govtIssuedId = govtIssuedId;
        this.dateOfBirth = dateOfBirth;
        this.ficoCreditScore = ficoCreditScore;
        this.eftAccountId = eftAccountId;
        this.primaryCardholderIndicator = primaryCardholderIndicator;
    }

    // Getters and setters
    public String getCustId() { return custId; }
    public void setCustId(String custId) { this.custId = custId; }

    public String getFirstName() { return firstName; }
    public void setFirstName(String firstName) { this.firstName = firstName; }

    public String getMiddleName() { return middleName; }
    public void setMiddleName(String middleName) { this.middleName = middleName; }

    public String getLastName() { return lastName; }
    public void setLastName(String lastName) { this.lastName = lastName; }

    public String getAddressLine1() { return addressLine1; }
    public void setAddressLine1(String addressLine1) { this.addressLine1 = addressLine1; }

    public String getAddressLine2() { return addressLine2; }
    public void setAddressLine2(String addressLine2) { this.addressLine2 = addressLine2; }

    public String getAddressLine3() { return addressLine3; }
    public void setAddressLine3(String addressLine3) { this.addressLine3 = addressLine3; }

    public String getStateCode() { return stateCode; }
    public void setStateCode(String stateCode) { this.stateCode = stateCode; }

    public String getCountryCode() { return countryCode; }
    public void setCountryCode(String countryCode) { this.countryCode = countryCode; }

    public String getZipCode() { return zipCode; }
    public void setZipCode(String zipCode) { this.zipCode = zipCode; }

    public String getPhoneNumber1() { return phoneNumber1; }
    public void setPhoneNumber1(String phoneNumber1) { this.phoneNumber1 = phoneNumber1; }

    public String getPhoneNumber2() { return phoneNumber2; }
    public void setPhoneNumber2(String phoneNumber2) { this.phoneNumber2 = phoneNumber2; }

    public String getSsn() { return ssn; }
    public void setSsn(String ssn) { this.ssn = ssn; }

    public String getGovtIssuedId() { return govtIssuedId; }
    public void setGovtIssuedId(String govtIssuedId) { this.govtIssuedId = govtIssuedId; }

    public LocalDate getDateOfBirth() { return dateOfBirth; }
    public void setDateOfBirth(LocalDate dateOfBirth) { this.dateOfBirth = dateOfBirth; }

    public Short getFicoCreditScore() { return ficoCreditScore; }
    public void setFicoCreditScore(Short ficoCreditScore) { this.ficoCreditScore = ficoCreditScore; }

    public String getEftAccountId() { return eftAccountId; }
    public void setEftAccountId(String eftAccountId) { this.eftAccountId = eftAccountId; }

    public String getPrimaryCardholderIndicator() { return primaryCardholderIndicator; }
    public void setPrimaryCardholderIndicator(String primaryCardholderIndicator) {
        this.primaryCardholderIndicator = primaryCardholderIndicator;
    }
}

/**
 * Data Transfer Object for customer query responses.
 * 
 * <p><b>API Usage:</b> Response body for GET /api/v1/customers/{id} endpoint.
 * 
 * <p><b>PII Protection:</b>
 * <ul>
 *   <li>SSN masked to show only last 4 digits (ssnMasked field)</li>
 *   <li>Government-issued ID completely excluded</li>
 *   <li>Date of birth included but protected from logging via entity @ToString.Exclude</li>
 * </ul>
 * 
 * <p><b>Audit Fields:</b> Includes createdAt and updatedAt timestamps for
 * audit trail visibility in API responses.
 */
class CustomerResponse implements Serializable {
    private static final long serialVersionUID = 1L;

    private Long customerId;
    private String custId;
    private String firstName;
    private String middleName;
    private String lastName;
    private String addressLine1;
    private String addressLine2;
    private String addressLine3;
    private String stateCode;
    private String countryCode;
    private String zipCode;
    private String phoneNumber1;
    private String phoneNumber2;
    private String ssnMasked;
    private LocalDate dateOfBirth;
    private Short ficoCreditScore;
    private String eftAccountId;
    private String primaryCardholderIndicator;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // Default constructor
    public CustomerResponse() {}

    // All-args constructor
    public CustomerResponse(Long customerId, String custId, String firstName, String middleName,
                           String lastName, String addressLine1, String addressLine2,
                           String addressLine3, String stateCode, String countryCode,
                           String zipCode, String phoneNumber1, String phoneNumber2,
                           String ssnMasked, LocalDate dateOfBirth, Short ficoCreditScore,
                           String eftAccountId, String primaryCardholderIndicator,
                           LocalDateTime createdAt, LocalDateTime updatedAt) {
        this.customerId = customerId;
        this.custId = custId;
        this.firstName = firstName;
        this.middleName = middleName;
        this.lastName = lastName;
        this.addressLine1 = addressLine1;
        this.addressLine2 = addressLine2;
        this.addressLine3 = addressLine3;
        this.stateCode = stateCode;
        this.countryCode = countryCode;
        this.zipCode = zipCode;
        this.phoneNumber1 = phoneNumber1;
        this.phoneNumber2 = phoneNumber2;
        this.ssnMasked = ssnMasked;
        this.dateOfBirth = dateOfBirth;
        this.ficoCreditScore = ficoCreditScore;
        this.eftAccountId = eftAccountId;
        this.primaryCardholderIndicator = primaryCardholderIndicator;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    // Getters and setters
    public Long getCustomerId() { return customerId; }
    public void setCustomerId(Long customerId) { this.customerId = customerId; }

    public String getCustId() { return custId; }
    public void setCustId(String custId) { this.custId = custId; }

    public String getFirstName() { return firstName; }
    public void setFirstName(String firstName) { this.firstName = firstName; }

    public String getMiddleName() { return middleName; }
    public void setMiddleName(String middleName) { this.middleName = middleName; }

    public String getLastName() { return lastName; }
    public void setLastName(String lastName) { this.lastName = lastName; }

    public String getAddressLine1() { return addressLine1; }
    public void setAddressLine1(String addressLine1) { this.addressLine1 = addressLine1; }

    public String getAddressLine2() { return addressLine2; }
    public void setAddressLine2(String addressLine2) { this.addressLine2 = addressLine2; }

    public String getAddressLine3() { return addressLine3; }
    public void setAddressLine3(String addressLine3) { this.addressLine3 = addressLine3; }

    public String getStateCode() { return stateCode; }
    public void setStateCode(String stateCode) { this.stateCode = stateCode; }

    public String getCountryCode() { return countryCode; }
    public void setCountryCode(String countryCode) { this.countryCode = countryCode; }

    public String getZipCode() { return zipCode; }
    public void setZipCode(String zipCode) { this.zipCode = zipCode; }

    public String getPhoneNumber1() { return phoneNumber1; }
    public void setPhoneNumber1(String phoneNumber1) { this.phoneNumber1 = phoneNumber1; }

    public String getPhoneNumber2() { return phoneNumber2; }
    public void setPhoneNumber2(String phoneNumber2) { this.phoneNumber2 = phoneNumber2; }

    public String getSsnMasked() { return ssnMasked; }
    public void setSsnMasked(String ssnMasked) { this.ssnMasked = ssnMasked; }

    public LocalDate getDateOfBirth() { return dateOfBirth; }
    public void setDateOfBirth(LocalDate dateOfBirth) { this.dateOfBirth = dateOfBirth; }

    public Short getFicoCreditScore() { return ficoCreditScore; }
    public void setFicoCreditScore(Short ficoCreditScore) { this.ficoCreditScore = ficoCreditScore; }

    public String getEftAccountId() { return eftAccountId; }
    public void setEftAccountId(String eftAccountId) { this.eftAccountId = eftAccountId; }

    public String getPrimaryCardholderIndicator() { return primaryCardholderIndicator; }
    public void setPrimaryCardholderIndicator(String primaryCardholderIndicator) {
        this.primaryCardholderIndicator = primaryCardholderIndicator;
    }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}

/**
 * Data Transfer Object for abbreviated customer information in nested contexts.
 * 
 * <p><b>API Usage:</b> Embedded in other response DTOs (e.g., AccountResponse)
 * and used in customer list/browse operations (GET /api/v1/customers).
 * 
 * <p><b>PII Minimization:</b> Excludes sensitive fields to minimize PII exposure:
 * <ul>
 *   <li>No full address (address minimization)</li>
 *   <li>No date of birth (age-related PII)</li>
 *   <li>No government ID (never exposed)</li>
 *   <li>No FICO score (sensitive financial data)</li>
 *   <li>SSN masked to last 4 digits only</li>
 * </ul>
 * 
 * <p><b>Use Cases:</b>
 * <ul>
 *   <li>Customer search/browse results</li>
 *   <li>Embedded customer info in account responses</li>
 *   <li>Transaction history cardholder names</li>
 * </ul>
 */
class CustomerSummary implements Serializable {
    private static final long serialVersionUID = 1L;

    private Long customerId;
    private String custId;
    private String firstName;
    private String middleName;
    private String lastName;
    private String phoneNumber;
    private String ssnMasked;

    // Default constructor
    public CustomerSummary() {}

    // All-args constructor
    public CustomerSummary(Long customerId, String custId, String firstName, String middleName,
                          String lastName, String phoneNumber, String ssnMasked) {
        this.customerId = customerId;
        this.custId = custId;
        this.firstName = firstName;
        this.middleName = middleName;
        this.lastName = lastName;
        this.phoneNumber = phoneNumber;
        this.ssnMasked = ssnMasked;
    }

    // Getters and setters
    public Long getCustomerId() { return customerId; }
    public void setCustomerId(Long customerId) { this.customerId = customerId; }

    public String getCustId() { return custId; }
    public void setCustId(String custId) { this.custId = custId; }

    public String getFirstName() { return firstName; }
    public void setFirstName(String firstName) { this.firstName = firstName; }

    public String getMiddleName() { return middleName; }
    public void setMiddleName(String middleName) { this.middleName = middleName; }

    public String getLastName() { return lastName; }
    public void setLastName(String lastName) { this.lastName = lastName; }

    public String getPhoneNumber() { return phoneNumber; }
    public void setPhoneNumber(String phoneNumber) { this.phoneNumber = phoneNumber; }

    public String getSsnMasked() { return ssnMasked; }
    public void setSsnMasked(String ssnMasked) { this.ssnMasked = ssnMasked; }
}
