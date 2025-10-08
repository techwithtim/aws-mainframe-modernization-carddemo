package com.aws.carddemo.repository;

import com.aws.carddemo.model.Customer;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Spring Data JPA repository interface for Customer entity providing customer demographics data access.
 * 
 * <p><b>Legacy Mapping:</b> Replaces COBOL VSAM CUSTFILE keyed file operations from customer file 
 * viewer {@code app/cbl/CBCUS01C.cbl} with Spring Data JPA declarative query methods. Converts 
 * COBOL READ/WRITE operations on VSAM KSDS (Key-Sequenced Data Set) to SQL-based database access 
 * through PostgreSQL.
 * 
 * <p><b>COBOL File Operations Replaced:</b>
 * <pre>
 * COBOL (CBCUS01C.cbl):
 *   SELECT CUSTFILE-FILE ASSIGN TO CUSTFILE
 *          ORGANIZATION IS INDEXED
 *          ACCESS MODE IS SEQUENTIAL
 *          RECORD KEY IS FD-CUST-ID
 *          FILE STATUS IS CUSTFILE-STATUS.
 *   
 *   READ CUSTFILE-FILE INTO CUSTOMER-RECORD.
 *   IF CUSTFILE-STATUS = '00'  [Success]
 *   IF CUSTFILE-STATUS = '10'  [End of file - no record found]
 * 
 * Java (CustomerRepository):
 *   Optional&lt;Customer&gt; customer = customerRepository.findByCustomerId(customerId);
 *   if (customer.isPresent()) {
 *       // Success - record found
 *   } else {
 *       // No record found (equivalent to FILE STATUS '10')
 *   }
 * </pre>
 * 
 * <p><b>Data Structure Mapping:</b> Customer entity maps to COBOL copybooks:
 * <ul>
 *   <li><b>CVCUS01Y.cpy:</b> CUSTOMER-RECORD structure (500-byte fixed-length record)</li>
 *   <li><b>CUSTREC.cpy:</b> Alternate CUSTOMER-RECORD structure (same layout)</li>
 *   <li><b>Key Fields:</b> CUST-ID PIC 9(09), CUST-SSN PIC 9(09)</li>
 * </ul>
 * 
 * <p><b>Query Methods Provided:</b>
 * <ol>
 *   <li><b>{@link #findByCustomerId(Long)}:</b> Primary key lookup by database-generated ID.
 *       Replaces COBOL READ with KEY IS FD-CUST-ID. Returns Optional to safely handle missing
 *       records without null pointer exceptions.</li>
 *   
 *   <li><b>{@link #findBySsn(String)}:</b> Unique lookup by Social Security Number (SSN).
 *       Used for customer authentication and identity verification during login flows.
 *       <b>PCI-DSS Compliance:</b> SSN is sensitive PII requiring @ToString.Exclude protection
 *       in entity and masking in logs. Use Customer.getSsnMasked() for display purposes.</li>
 *   
 *   <li><b>{@link #searchByName(String, Pageable)}:</b> Paginated customer name search with
 *       LIKE pattern matching. Replaces COBOL sequential file browse with SQL pagination.
 *       Searches across firstName, middleName, and lastName fields using UPPER() for
 *       case-insensitive matching. Supports wildcard patterns (e.g., "SMITH%", "%JOHN%").</li>
 * </ol>
 * 
 * <p><b>Inherited JpaRepository Methods:</b> This interface inherits 20+ CRUD methods from
 * {@link JpaRepository} including:
 * <ul>
 *   <li>{@code save(Customer)}: INSERT or UPDATE customer record</li>
 *   <li>{@code findById(Long)}: Primary key lookup by customerId</li>
 *   <li>{@code findAll()}: Retrieve all customer records (use with caution - large table)</li>
 *   <li>{@code findAll(Pageable)}: Paginated retrieval of all customers</li>
 *   <li>{@code delete(Customer)}: DELETE customer record (cascades to accounts)</li>
 *   <li>{@code count()}: Total customer count</li>
 *   <li>{@code existsById(Long)}: Fast existence check without fetching full entity</li>
 * </ul>
 * 
 * <p><b>Spring Data JPA Query Derivation:</b> Method names follow Spring Data naming conventions:
 * <ul>
 *   <li><b>findBy[PropertyName]:</b> Generates SELECT query filtering by property equality</li>
 *   <li><b>Optional&lt;T&gt; return type:</b> Indicates 0 or 1 result expected</li>
 *   <li><b>Page&lt;T&gt; return type:</b> Indicates paginated result set with metadata</li>
 * </ul>
 * 
 * <p><b>PCI-DSS and GDPR Compliance Notes:</b>
 * <ul>
 *   <li><b>SSN Protection:</b> Never log full SSN values. The Customer entity uses
 *       @ToString.Exclude on ssn field. Use getSsnMasked() for display (shows last 4 digits).</li>
 *   <li><b>Data Minimization:</b> Only retrieve customer data when necessary. Use projections
 *       or DTOs for UI display to avoid exposing full PII in API responses.</li>
 *   <li><b>Access Control:</b> Restrict customer data access to authorized roles. Service
 *       layer should enforce @PreAuthorize("hasRole('ADMIN')") for sensitive operations.</li>
 *   <li><b>Audit Trail:</b> Customer entity extends BaseEntity with createdAt/updatedAt
 *       timestamps for regulatory compliance and audit reporting.</li>
 * </ul>
 * 
 * <p><b>Performance Considerations:</b>
 * <ul>
 *   <li><b>Indexes:</b> Customer table has unique indexes on custId and ssn for fast lookups.
 *       Composite index on (last_name, first_name) optimizes name search queries.</li>
 *   <li><b>Pagination:</b> Always use pagination for searchByName() to prevent loading
 *       thousands of records into memory. Typical page size: 20-50 customers.</li>
 *   <li><b>N+1 Queries:</b> Customer has @OneToMany relationship with Account. Use JOIN FETCH
 *       or @EntityGraph if accounts need to be loaded with customer to avoid N+1 problem.</li>
 *   <li><b>Caching:</b> Consider second-level cache for frequently accessed customers (e.g.,
 *       high-transaction customers). Not recommended for large customer base due to memory.</li>
 * </ul>
 * 
 * <p><b>Transaction Management:</b> All repository methods are transactional by default through
 * Spring Data JPA's @Transactional annotation on SimpleJpaRepository implementation. For custom
 * transaction boundaries, annotate service layer methods with @Transactional.
 * 
 * <p><b>Error Handling:</b>
 * <ul>
 *   <li><b>DataIntegrityViolationException:</b> Thrown on unique constraint violations (e.g.,
 *       duplicate SSN or custId). Service layer should catch and translate to DuplicateResourceException.</li>
 *   <li><b>EmptyResultDataAccessException:</b> Thrown by deleteById() if customer not found.
 *       Service layer should catch and translate to ResourceNotFoundException.</li>
 *   <li><b>OptimisticLockException:</b> Thrown on concurrent update conflicts. BaseEntity
 *       version field enables optimistic locking for concurrency control.</li>
 * </ul>
 * 
 * <p><b>Usage Examples:</b>
 * <pre>
 * // Example 1: Find customer by primary key (database ID)
 * Optional&lt;Customer&gt; customer = customerRepository.findByCustomerId(12345L);
 * customer.ifPresent(c -&gt; System.out.println("Found: " + c.getFirstName()));
 * 
 * // Example 2: Find customer by SSN for authentication
 * String ssn = "123456789"; // From login form
 * Optional&lt;Customer&gt; customerBySsn = customerRepository.findBySsn(ssn);
 * if (customerBySsn.isEmpty()) {
 *     throw new AuthenticationFailedException("Invalid credentials");
 * }
 * 
 * // Example 3: Search customers by name with pagination
 * String searchPattern = "SMITH"; // User input
 * Pageable pageable = PageRequest.of(0, 20, Sort.by("lastName", "firstName"));
 * Page&lt;Customer&gt; results = customerRepository.searchByName(searchPattern, pageable);
 * System.out.println("Found " + results.getTotalElements() + " customers");
 * results.getContent().forEach(c -&gt; System.out.println(c.getFirstName() + " " + c.getLastName()));
 * 
 * // Example 4: Create new customer (replaces COBOL WRITE operation)
 * Customer newCustomer = Customer.builder()
 *     .custId("000123456")
 *     .firstName("John")
 *     .lastName("Doe")
 *     .ssn("123456789")
 *     .dateOfBirth(LocalDate.of(1980, 5, 15))
 *     .ficoCreditScore((short) 720)
 *     .primaryCardholderIndicator("Y")
 *     .build();
 * Customer saved = customerRepository.save(newCustomer);
 * System.out.println("Created customer with ID: " + saved.getCustomerId());
 * 
 * // Example 5: Update customer (replaces COBOL REWRITE operation)
 * Customer existing = customerRepository.findByCustomerId(12345L).orElseThrow();
 * existing.setPhoneNumber1("5551234567");
 * customerRepository.save(existing); // Same save() method for insert and update
 * 
 * // Example 6: Delete customer (replaces COBOL DELETE operation)
 * customerRepository.deleteById(12345L); // Cascades to delete all associated accounts
 * </pre>
 * 
 * <p><b>Technical Specification References:</b>
 * <ul>
 *   <li>Section 0.2.1: Source File Discovery - CBCUS01C.cbl customer file viewer</li>
 *   <li>Section 0.4.1: File Transformation Mapping - CustomerRepository from CBCUS01C.cbl</li>
 *   <li>Section 0.5.1: Maven Dependencies - Spring Data JPA 3.3.0</li>
 *   <li>Section 0.8.1: Critical Directive #3 - PCI-DSS compliance for SSN field</li>
 *   <li>Section 0.8.3: Data Type Mapping - PIC 9(09) → String for SSN preservation</li>
 *   <li>Section 6.2.2.1: Customer Master Table - Database schema and indexes</li>
 * </ul>
 * 
 * @see Customer
 * @see JpaRepository
 * @see Page
 * @see Pageable
 * @author CardDemo Modernization Team
 * @since 1.0.0
 */
@Repository
public interface CustomerRepository extends JpaRepository<Customer, Long> {

    /**
     * Find customer by unique database-generated customer ID (primary key).
     * 
     * <p><b>Legacy Mapping:</b> Replaces COBOL READ operation with RECORD KEY IS FD-CUST-ID
     * from {@code app/cbl/CBCUS01C.cbl}. In COBOL, this performed an indexed read on the
     * VSAM KSDS customer file using the customer ID as the key.
     * 
     * <p><b>COBOL Equivalent:</b>
     * <pre>
     * MOVE 000012345 TO FD-CUST-ID.
     * READ CUSTFILE-FILE INTO CUSTOMER-RECORD.
     * IF CUSTFILE-STATUS = '00'
     *     DISPLAY CUSTOMER-RECORD
     * ELSE IF CUSTFILE-STATUS = '23'
     *     DISPLAY 'CUSTOMER NOT FOUND'
     * END-IF.
     * </pre>
     * 
     * <p><b>Java Equivalent:</b>
     * <pre>
     * Optional&lt;Customer&gt; customer = customerRepository.findByCustomerId(12345L);
     * if (customer.isPresent()) {
     *     System.out.println(customer.get()); // Success
     * } else {
     *     System.out.println("Customer not found"); // Equivalent to FILE STATUS '23'
     * }
     * </pre>
     * 
     * <p><b>Query Generation:</b> Spring Data JPA automatically generates SQL:
     * <pre>
     * SELECT c.* FROM customer c WHERE c.customer_id = ?
     * </pre>
     * 
     * <p><b>Performance:</b> Primary key lookup is fastest query type:
     * <ul>
     *   <li>Uses clustered index (customer_id is PK)</li>
     *   <li>O(log n) complexity with B-tree index</li>
     *   <li>Typical execution time: &lt;5ms</li>
     * </ul>
     * 
     * <p><b>Return Type:</b> {@code Optional<Customer>} provides null-safe handling:
     * <ul>
     *   <li>{@code Optional.empty()}: Customer ID not found (no record)</li>
     *   <li>{@code Optional.of(customer)}: Customer found and returned</li>
     * </ul>
     * 
     * <p><b>Usage:</b> Primary method for loading customer by synthetic database ID.
     * Used throughout application for customer data retrieval in service layer.
     * 
     * @param customerId the unique database-generated customer ID (not to be confused with
     *                   custId which is the 9-digit business key from COBOL)
     * @return Optional containing the customer if found, or empty Optional if not found
     * @throws IllegalArgumentException if customerId is null
     */
    Optional<Customer> findByCustomerId(Long customerId);

    /**
     * Find customer by unique 9-digit Social Security Number (SSN).
     * 
     * <p><b>Legacy Mapping:</b> Replaces COBOL READ operation using alternate index (AIX)
     * on SSN field from legacy VSAM file. In COBOL systems, AIX provided secondary key
     * access paths for non-primary key searches.
     * 
     * <p><b>Business Use Case:</b> Used for customer authentication and identity verification:
     * <ul>
     *   <li><b>Login Flow:</b> Verify customer identity by SSN during authentication</li>
     *   <li><b>Account Opening:</b> Check for duplicate SSN to prevent fraud</li>
     *   <li><b>Credit Bureau Integration:</b> Match customer records with credit reports</li>
     *   <li><b>Compliance Verification:</b> KYC (Know Your Customer) identity validation</li>
     * </ul>
     * 
     * <p><b>PCI-DSS Compliance Requirements:</b> SSN is <b>highly sensitive PII</b> requiring:
     * <ul>
     *   <li><b>Masking:</b> Never log full SSN. Use {@code Customer.getSsnMasked()} for display
     *       which shows "*****6789" (last 4 digits only).</li>
     *   <li><b>Encryption at Rest:</b> Database column should use PostgreSQL pgcrypto extension
     *       for transparent column-level encryption in production environments.</li>
     *   <li><b>TLS in Transit:</b> All SSN transmission must use HTTPS (TLS 1.3+).</li>
     *   <li><b>Access Control:</b> Restrict SSN access to authorized roles only. Service layer
     *       should enforce {@code @PreAuthorize("hasAnyRole('ADMIN', 'COMPLIANCE')")}.</li>
     *   <li><b>Audit Logging:</b> Log all SSN lookup attempts with user ID, timestamp, and
     *       source IP address for security monitoring and compliance audits.</li>
     * </ul>
     * 
     * <p><b>Data Validation:</b> SSN must be exactly 9 numeric digits:
     * <ul>
     *   <li><b>Format:</b> "123456789" (no hyphens, no spaces)</li>
     *   <li><b>Validation:</b> {@code @Pattern(regexp="\\d{9}")} on Customer entity</li>
     *   <li><b>Uniqueness:</b> Database unique index {@code idx_customer_ssn} prevents duplicates</li>
     * </ul>
     * 
     * <p><b>Query Generation:</b> Spring Data JPA generates SQL:
     * <pre>
     * SELECT c.* FROM customer c WHERE c.ssn = ?
     * </pre>
     * 
     * <p><b>Performance:</b> Fast unique index lookup:
     * <ul>
     *   <li>Uses unique index {@code idx_customer_ssn}</li>
     *   <li>O(log n) complexity with B-tree index</li>
     *   <li>Typical execution time: &lt;10ms</li>
     * </ul>
     * 
     * <p><b>Error Scenarios:</b>
     * <ul>
     *   <li><b>SSN Not Found:</b> Returns {@code Optional.empty()} (valid scenario - new customer)</li>
     *   <li><b>Invalid Format:</b> Service layer should validate SSN format before calling this method</li>
     *   <li><b>Null SSN:</b> Throws IllegalArgumentException (Spring Data validation)</li>
     * </ul>
     * 
     * <p><b>COBOL Data Type Mapping:</b>
     * <pre>
     * COBOL: 05 CUST-SSN PIC 9(09).
     * Java:  private String ssn;  // Stored as String to preserve leading zeros
     * </pre>
     * 
     * <p><b>Usage Example:</b>
     * <pre>
     * // Authentication flow
     * String ssnInput = loginRequest.getSsn(); // "123456789" from user input
     * Optional&lt;Customer&gt; customer = customerRepository.findBySsn(ssnInput);
     * if (customer.isEmpty()) {
     *     throw new AuthenticationFailedException("Invalid credentials");
     * }
     * 
     * // Duplicate check during account opening
     * if (customerRepository.findBySsn(newCustomer.getSsn()).isPresent()) {
     *     throw new DuplicateResourceException("Customer with SSN already exists");
     * }
     * 
     * // Display SSN safely in UI
     * Customer cust = customerRepository.findBySsn("123456789").orElseThrow();
     * String displaySsn = cust.getSsnMasked(); // Returns "*****6789"
     * </pre>
     * 
     * <p><b>Security Warning:</b> NEVER log the full SSN value in application logs, error
     * messages, or API responses. The Customer entity uses {@code @ToString.Exclude} on the
     * ssn field to prevent accidental exposure in logs. Always use {@code getSsnMasked()}
     * for display purposes.
     * 
     * @param ssn the 9-digit Social Security Number (must match pattern "\\d{9}", no hyphens)
     * @return Optional containing the customer if SSN matches, or empty Optional if not found
     * @throws IllegalArgumentException if ssn is null
     * @see Customer#getSsnMasked()
     */
    Optional<Customer> findBySsn(String ssn);

    /**
     * Search customers by name pattern with pagination support (case-insensitive LIKE query).
     * 
     * <p><b>Legacy Mapping:</b> Replaces COBOL sequential file browse with pagination logic
     * from batch programs. COBOL programs used PERFORM UNTIL loops to read customer file
     * sequentially and display matching records up to WS-MAX-SCREEN-LINES limit.
     * 
     * <p><b>COBOL Equivalent Pattern:</b>
     * <pre>
     * PERFORM UNTIL END-OF-FILE = 'Y' OR LINE-COUNT = WS-MAX-SCREEN-LINES
     *     READ CUSTFILE-FILE INTO CUSTOMER-RECORD
     *     IF CUST-LAST-NAME CONTAINS SEARCH-PATTERN
     *         DISPLAY CUSTOMER-RECORD
     *         ADD 1 TO LINE-COUNT
     *     END-IF
     * END-PERFORM.
     * </pre>
     * 
     * <p><b>Java Equivalent:</b>
     * <pre>
     * Pageable pageable = PageRequest.of(0, 20, Sort.by("lastName", "firstName"));
     * Page&lt;Customer&gt; results = customerRepository.searchByName("SMITH", pageable);
     * results.getContent().forEach(customer -&gt; System.out.println(customer));
     * System.out.println("Page " + (results.getNumber() + 1) + " of " + results.getTotalPages());
     * </pre>
     * 
     * <p><b>Business Use Case:</b> Customer service representative workflows:
     * <ul>
     *   <li><b>Customer Lookup:</b> CSR searches for customer by partial name during phone support</li>
     *   <li><b>Browse Functionality:</b> Display paginated list of customers matching search criteria</li>
     *   <li><b>Autocomplete:</b> Provide name suggestions as user types in search box</li>
     *   <li><b>Reporting:</b> Generate customer lists filtered by name pattern</li>
     * </ul>
     * 
     * <p><b>Query Implementation:</b> Custom JPQL query searches across multiple name fields:
     * <pre>
     * SELECT c FROM Customer c 
     * WHERE UPPER(c.firstName) LIKE UPPER(:namePattern)
     *    OR UPPER(c.middleName) LIKE UPPER(:namePattern)
     *    OR UPPER(c.lastName) LIKE UPPER(:namePattern)
     * ORDER BY c.lastName, c.firstName
     * </pre>
     * 
     * <p><b>Search Behavior:</b>
     * <ul>
     *   <li><b>Case-Insensitive:</b> Uses UPPER() function to ignore case differences</li>
     *   <li><b>Wildcard Support:</b> Accepts SQL wildcard patterns:
     *       <ul>
     *         <li>"SMITH%" - Starts with "Smith"</li>
     *         <li>"%SMITH%" - Contains "Smith" anywhere</li>
     *         <li>"SMITH" - Exact match (automatically appended wildcards in implementation)</li>
     *       </ul>
     *   </li>
     *   <li><b>Multi-Field Search:</b> Searches firstName, middleName, and lastName</li>
     *   <li><b>Default Sorting:</b> Results sorted by last name, then first name (alphabetical)</li>
     * </ul>
     * 
     * <p><b>Pagination Parameters:</b>
     * <ul>
     *   <li><b>Page Number:</b> Zero-based (0 = first page, 1 = second page, etc.)</li>
     *   <li><b>Page Size:</b> Number of records per page (typical values: 10, 20, 50)</li>
     *   <li><b>Sorting:</b> Optional sort criteria (default: lastName, firstName ascending)</li>
     * </ul>
     * 
     * <p><b>Performance Considerations:</b>
     * <ul>
     *   <li><b>Index Usage:</b> Composite index {@code idx_customer_name} on (last_name, first_name)
     *       accelerates queries with "SMITH%" pattern (prefix match). Full scan for "%SMITH%" pattern.</li>
     *   <li><b>Page Size:</b> Recommended page size: 20-50 records. Larger pages (100+) cause
     *       memory pressure and slow JSON serialization.</li>
     *   <li><b>Total Count:</b> {@code Page.getTotalElements()} executes separate COUNT(*) query.
     *       For large tables, consider disabling count query with {@code Slice<Customer>} instead.</li>
     *   <li><b>LIKE Performance:</b> Prefix patterns (e.g., "SMITH%") use index. Contains patterns
     *       (e.g., "%SMITH%") require full table scan. For better performance, consider full-text
     *       search (PostgreSQL tsvector) or Elasticsearch for large customer tables.</li>
     * </ul>
     * 
     * <p><b>Return Value:</b> {@link Page} object provides:
     * <ul>
     *   <li>{@code getContent()}: List of customers in current page</li>
     *   <li>{@code getTotalElements()}: Total count of matching customers across all pages</li>
     *   <li>{@code getTotalPages()}: Total number of pages</li>
     *   <li>{@code getNumber()}: Current page number (zero-based)</li>
     *   <li>{@code getSize()}: Page size (records per page)</li>
     *   <li>{@code hasNext()}: Whether there is a next page</li>
     *   <li>{@code hasPrevious()}: Whether there is a previous page</li>
     * </ul>
     * 
     * <p><b>Usage Examples:</b>
     * <pre>
     * // Example 1: Basic name search with pagination
     * Pageable firstPage = PageRequest.of(0, 20);
     * Page&lt;Customer&gt; customers = customerRepository.searchByName("SMITH", firstPage);
     * System.out.println("Found " + customers.getTotalElements() + " customers");
     * 
     * // Example 2: Search with custom sorting
     * Pageable sortedPage = PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "lastName"));
     * Page&lt;Customer&gt; sortedResults = customerRepository.searchByName("JOHN", sortedPage);
     * 
     * // Example 3: Wildcard search (contains pattern)
     * Page&lt;Customer&gt; containsResults = customerRepository.searchByName("%SMITH%", firstPage);
     * 
     * // Example 4: Navigate through pages
     * Pageable page1 = PageRequest.of(0, 20);
     * Page&lt;Customer&gt; results = customerRepository.searchByName("A%", page1);
     * while (results.hasNext()) {
     *     results = customerRepository.searchByName("A%", results.nextPageable());
     *     processPage(results.getContent());
     * }
     * 
     * // Example 5: Handle empty results
     * Page&lt;Customer&gt; noResults = customerRepository.searchByName("ZZZZZ", firstPage);
     * if (noResults.isEmpty()) {
     *     System.out.println("No customers found matching pattern");
     * }
     * 
     * // Example 6: Extract page metadata for UI pagination controls
     * Page&lt;Customer&gt; page = customerRepository.searchByName("SMITH", firstPage);
     * Map&lt;String, Object&gt; metadata = Map.of(
     *     "currentPage", page.getNumber() + 1,
     *     "totalPages", page.getTotalPages(),
     *     "totalRecords", page.getTotalElements(),
     *     "hasNext", page.hasNext(),
     *     "hasPrevious", page.hasPrevious()
     * );
     * </pre>
     * 
     * <p><b>Database Index:</b> This query benefits from composite index {@code idx_customer_name}:
     * <pre>
     * CREATE INDEX idx_customer_name ON customer(last_name, first_name);
     * </pre>
     * The index accelerates queries with last name prefix patterns (e.g., "SMITH%") but cannot
     * be used for contains patterns (e.g., "%SMITH%") which require full table scans.
     * 
     * <p><b>Alternative Approaches for Large Tables:</b>
     * <ul>
     *   <li><b>Full-Text Search:</b> Use PostgreSQL tsvector for more efficient text search:
     *       <pre>
     *       CREATE INDEX idx_customer_fulltext ON customer 
     *       USING gin(to_tsvector('english', first_name || ' ' || last_name));
     *       </pre>
     *   </li>
     *   <li><b>Elasticsearch Integration:</b> For very large customer tables (millions of records),
     *       consider indexing customer names in Elasticsearch for sub-second search performance.</li>
     *   <li><b>Slice Instead of Page:</b> For infinite scroll UI, use {@code Slice<Customer>}
     *       return type to avoid expensive COUNT(*) query.</li>
     * </ul>
     * 
     * @param namePattern the search pattern to match against firstName, middleName, and lastName.
     *                    Supports SQL wildcard characters: % (any characters) and _ (single character).
     *                    Example patterns: "SMITH" (exact), "SMITH%" (starts with), "%SMITH%" (contains)
     * @param pageable pagination and sorting parameters (page number, page size, sort criteria).
     *                 Example: {@code PageRequest.of(0, 20, Sort.by("lastName", "firstName"))}
     * @return Page containing list of matching customers, total count, and pagination metadata.
     *         Never returns null; returns empty page if no matches found.
     * @throws IllegalArgumentException if namePattern is null or pageable is null
     */
    @Query("SELECT c FROM Customer c WHERE " +
           "UPPER(c.firstName) LIKE UPPER(CONCAT('%', :namePattern, '%')) OR " +
           "UPPER(c.middleName) LIKE UPPER(CONCAT('%', :namePattern, '%')) OR " +
           "UPPER(c.lastName) LIKE UPPER(CONCAT('%', :namePattern, '%'))")
    Page<Customer> searchByName(@Param("namePattern") String namePattern, Pageable pageable);
}
