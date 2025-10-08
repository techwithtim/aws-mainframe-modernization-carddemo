/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.carddemo.exception;

/**
 * Custom unchecked exception for authentication and authorization failures in the CardDemo application.
 * 
 * <p>This exception is thrown when authentication or authorization operations fail, including:
 * <ul>
 *   <li>Invalid username or password during login</li>
 *   <li>Expired or invalid JWT tokens</li>
 *   <li>Account lockout conditions after multiple failed attempts</li>
 *   <li>Password verification failures using BCrypt</li>
 *   <li>JWT signature validation failures</li>
 *   <li>User not found scenarios</li>
 * </ul>
 * 
 * <p><strong>Migrated from:</strong> app/cbl/COSGN00C.cbl authentication logic
 * 
 * <p>In the legacy COBOL implementation, authentication failures were handled through:
 * <pre>
 * Line 223: IF SEC-USR-PWD = WS-USER-PWD
 * Line 242: MOVE 'Wrong Password. Try again ...' TO WS-MESSAGE
 * Line 249: MOVE 'User not found. Try again ...' TO WS-MESSAGE
 * Line 254: MOVE 'Unable to verify the User ...' TO WS-MESSAGE
 * </pre>
 * 
 * <p><strong>PCI-DSS Compliance:</strong>
 * This exception implements security best practices to prevent credential enumeration attacks:
 * <ul>
 *   <li>Error responses to clients return generic "Authentication failed" message</li>
 *   <li>Detailed failure reason stored internally for security audit logging</li>
 *   <li>Actual failure reason logged securely to CloudWatch Logs with automatic sensitive data masking</li>
 *   <li>Error messages MUST NOT reveal whether username or password was incorrect</li>
 * </ul>
 * 
 * <p><strong>Spring Security Integration:</strong>
 * This exception extends {@link RuntimeException} for seamless integration with Spring Security
 * exception hierarchy. It serves as an application-specific alternative to
 * {@link org.springframework.security.authentication.BadCredentialsException} for scenarios
 * requiring custom error handling and audit logging.
 * 
 * <p><strong>Usage Examples:</strong>
 * <pre>
 * // Password verification failure
 * if (!passwordEncoder.matches(password, user.getPasswordHash())) {
 *     throw new AuthenticationFailedException("Invalid credentials");
 * }
 * 
 * // Account locked scenario
 * if (user.getAccountLocked()) {
 *     throw new AuthenticationFailedException("Account locked after 5 failed attempts");
 * }
 * 
 * // JWT token validation failure
 * if (!jwtTokenProvider.validateToken(token)) {
 *     throw new AuthenticationFailedException("Invalid or expired JWT token");
 * }
 * 
 * // User not found
 * if (user == null) {
 *     throw new AuthenticationFailedException("User not found");
 * }
 * </pre>
 * 
 * <p><strong>Error Handling:</strong>
 * This exception is mapped to HTTP 401 Unauthorized status in {@code GlobalExceptionHandler}
 * via {@code @ExceptionHandler(AuthenticationFailedException.class)} method. The handler ensures
 * that only generic error messages are returned to clients while detailed reasons are logged
 * for security monitoring.
 * 
 * @see org.springframework.security.core.AuthenticationException
 * @see org.springframework.security.authentication.BadCredentialsException
 * @author AWS CardDemo Modernization Team
 * @version 1.0
 * @since 1.0
 */
public class AuthenticationFailedException extends RuntimeException {

    /**
     * Serial version UID for serialization compatibility.
     */
    private static final long serialVersionUID = 1L;

    /**
     * Detailed reason for authentication failure.
     * 
     * <p>This field stores the specific reason for authentication failure for security audit
     * logging purposes. It is NOT exposed to end users in error responses to prevent credential
     * enumeration attacks.
     * 
     * <p>Common reason values include:
     * <ul>
     *   <li>"Invalid credentials" - Username or password incorrect</li>
     *   <li>"User not found" - Username does not exist in database</li>
     *   <li>"Account locked after 5 failed attempts" - Account temporarily locked</li>
     *   <li>"JWT token expired" - Token past expiration time</li>
     *   <li>"Invalid JWT signature" - Token signature validation failed</li>
     *   <li>"Password verification failed" - BCrypt password match failed</li>
     * </ul>
     */
    private final String reason;

    /**
     * Constructs a new authentication failed exception with the specified detail message.
     * 
     * <p>This constructor creates an exception with a generic message for client responses
     * and no detailed reason for audit logging. Use {@link #AuthenticationFailedException(String, String)}
     * for cases requiring audit trail.
     * 
     * @param message the detail message (which is saved for later retrieval by the
     *                {@link #getMessage()} method). This message is returned to clients
     *                in error responses.
     */
    public AuthenticationFailedException(String message) {
        super(message);
        this.reason = null;
    }

    /**
     * Constructs a new authentication failed exception with the specified detail message
     * and failure reason for audit logging.
     * 
     * <p>This is the recommended constructor for most authentication failures. It allows
     * specifying a generic message for client responses while capturing detailed failure
     * reasons for security audit logging.
     * 
     * <p><strong>Security Note:</strong> The {@code message} parameter is returned to clients
     * and should be generic (e.g., "Authentication failed"). The {@code reason} parameter
     * is for internal logging only and may contain specific details.
     * 
     * <p><strong>Example Usage:</strong>
     * <pre>
     * // Client receives "Authentication failed"
     * // Security logs receive "Invalid credentials for user: alice"
     * throw new AuthenticationFailedException(
     *     "Authentication failed",
     *     "Invalid credentials"
     * );
     * </pre>
     * 
     * @param message the detail message for client responses (saved for later retrieval
     *                by the {@link #getMessage()} method). Should be generic to prevent
     *                credential enumeration.
     * @param reason  the specific reason for authentication failure, used for security
     *                audit logging. May contain detailed information not exposed to clients.
     */
    public AuthenticationFailedException(String message, String reason) {
        super(message);
        this.reason = reason;
    }

    /**
     * Constructs a new authentication failed exception with the specified detail message
     * and cause.
     * 
     * <p>This constructor is useful when authentication failure is caused by an underlying
     * exception (e.g., database connection failure, cryptographic operation failure).
     * 
     * @param message the detail message (saved for later retrieval by the
     *                {@link #getMessage()} method)
     * @param cause   the cause (which is saved for later retrieval by the
     *                {@link #getCause()} method). A {@code null} value is permitted,
     *                and indicates that the cause is nonexistent or unknown.
     */
    public AuthenticationFailedException(String message, Throwable cause) {
        super(message, cause);
        this.reason = null;
    }

    /**
     * Constructs a new authentication failed exception with the specified detail message,
     * failure reason, and cause.
     * 
     * <p>This is the most comprehensive constructor, capturing both the cause of the failure
     * and a detailed reason for audit logging.
     * 
     * @param message the detail message for client responses (saved for later retrieval
     *                by the {@link #getMessage()} method)
     * @param reason  the specific reason for authentication failure, used for security
     *                audit logging
     * @param cause   the cause (saved for later retrieval by the {@link #getCause()} method).
     *                A {@code null} value is permitted.
     */
    public AuthenticationFailedException(String message, String reason, Throwable cause) {
        super(message, cause);
        this.reason = reason;
    }

    /**
     * Returns the detailed reason for authentication failure.
     * 
     * <p>This method is used by {@code GlobalExceptionHandler} to log authentication failures
     * for security monitoring and audit purposes. The reason is NOT included in error responses
     * sent to clients to prevent credential enumeration attacks.
     * 
     * <p><strong>Security Logging:</strong>
     * The returned reason is logged to CloudWatch Logs with automatic masking of sensitive
     * data (passwords, tokens, etc.) by the logging framework. Security teams can analyze
     * these logs to detect:
     * <ul>
     *   <li>Brute force attack attempts (multiple "Invalid credentials" for same user)</li>
     *   <li>Account enumeration attempts (multiple "User not found" errors)</li>
     *   <li>Token manipulation attempts (multiple "Invalid JWT signature" errors)</li>
     * </ul>
     * 
     * @return the detailed reason for authentication failure, or {@code null} if no
     *         specific reason was provided. The reason should be treated as sensitive
     *         information and not exposed to end users.
     */
    public String getReason() {
        return reason;
    }

    /**
     * Returns a string representation of this exception for debugging purposes.
     * 
     * <p><strong>Security Note:</strong> This method includes the reason field in the output
     * for debugging. Ensure that stack traces containing this information are not exposed
     * to end users in production environments.
     * 
     * @return a string representation including the class name, message, and reason
     */
    @Override
    public String toString() {
        String className = getClass().getName();
        String message = getMessage();
        if (reason != null) {
            return className + ": " + message + " (reason: " + reason + ")";
        } else {
            return className + ": " + message;
        }
    }
}
