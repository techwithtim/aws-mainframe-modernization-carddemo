/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.carddemo.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Documentation class for BCrypt password encoding implementation in the CardDemo application.
 * 
 * <p><strong>NOTE:</strong> The actual {@code passwordEncoder} bean is defined in 
 * {@link com.aws.carddemo.config.SecurityConfig} with configurable BCrypt strength.
 * This class is retained for comprehensive documentation of the COBOL-to-Java password 
 * migration strategy and BCrypt implementation details.</p>
 * 
 * <p>This documentation explains how the CardDemo modernization replaces the legacy COBOL 
 * plain-text password storage and comparison mechanism with modern BCrypt cryptographic 
 * hashing to meet PCI-DSS compliance requirements.</p>
 * 
 * <h2>Legacy COBOL Implementation (INSECURE - Replaced)</h2>
 * <p>The original CardDemo application stored passwords as 8-character plain-text values:</p>
 * <ul>
 *   <li><b>Source:</b> {@code app/cpy/CSUSR01Y.cpy} line 21</li>
 *   <li><b>Field Definition:</b> {@code 05 SEC-USR-PWD PIC X(08)}</li>
 *   <li><b>Storage:</b> Unencrypted 8-character strings in USRSEC VSAM file</li>
 *   <li><b>Authentication:</b> Direct string comparison in {@code app/cbl/COSGN00C.cbl} line 223:
 *       {@code IF SEC-USR-PWD = WS-USER-PWD}</li>
 *   <li><b>Security Issues:</b> Passwords exposed in memory, logs, file dumps, and vulnerable to 
 *       theft if storage media compromised</li>
 * </ul>
 * 
 * <h2>Modern BCrypt Implementation</h2>
 * <p>The modernized system uses BCrypt password hashing with the following characteristics:</p>
 * <ul>
 *   <li><b>Algorithm:</b> BCrypt with 10 salt rounds (2^10 = 1,024 iterations)</li>
 *   <li><b>Hash Format:</b> 60-character BCrypt string 
 *       (e.g., {@code $2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy})</li>
 *   <li><b>Storage:</b> Hash stored in {@code app_user.password_hash} VARCHAR(255) column</li>
 *   <li><b>Salt:</b> Unique random salt embedded in each hash (prevents rainbow table attacks)</li>
 *   <li><b>One-way:</b> Cryptographically infeasible to reverse hash to original password</li>
 *   <li><b>Performance:</b> ~100ms verification time balances security and user experience</li>
 * </ul>
 * 
 * <h2>PCI-DSS Compliance</h2>
 * <p>This implementation satisfies PCI-DSS Requirement 8.2.1:</p>
 * <ul>
 *   <li>"Render all authentication credentials unreadable during transmission and storage on all 
 *       system components using strong cryptography"</li>
 *   <li>BCrypt uses Blowfish cipher with adaptive key derivation</li>
 *   <li>Work factor (10 rounds) can be increased as hardware improves</li>
 *   <li>Passwords never logged, transmitted, or stored in plain text</li>
 * </ul>
 * 
 * <h2>Migration Path</h2>
 * <p><b>On User Creation (equivalent to COUSR01C.cbl):</b></p>
 * <pre>
 * String hashedPassword = passwordEncoder.encode(plainPassword);
 * user.setPasswordHash(hashedPassword); // 60-character BCrypt hash
 * userRepository.save(user);
 * </pre>
 * 
 * <p><b>On Authentication (equivalent to COSGN00C.cbl):</b></p>
 * <pre>
 * boolean matches = passwordEncoder.matches(providedPassword, user.getPasswordHash());
 * if (matches) {
 *     // Authentication successful - grant access
 * } else {
 *     // Authentication failed - deny access
 * }
 * </pre>
 * 
 * <p><b>On Password Change (equivalent to COUSR02C.cbl):</b></p>
 * <pre>
 * if (passwordEncoder.matches(oldPassword, user.getPasswordHash())) {
 *     String newHashedPassword = passwordEncoder.encode(newPassword);
 *     user.setPasswordHash(newHashedPassword);
 *     userRepository.save(user);
 * }
 * </pre>
 * 
 * <h2>Security Benefits</h2>
 * <ul>
 *   <li><b>Brute-force Resistance:</b> 2^10 iterations make password cracking computationally expensive</li>
 *   <li><b>Rainbow Table Immunity:</b> Unique per-password salt prevents pre-computed hash lookups</li>
 *   <li><b>Database Breach Protection:</b> Even if database compromised, passwords remain secure</li>
 *   <li><b>Memory Safety:</b> Plain-text passwords exist only briefly during authentication</li>
 *   <li><b>Future-proof:</b> Work factor can be increased without algorithm change</li>
 * </ul>
 * 
 * <h2>Actual Bean Location</h2>
 * <p>The {@code passwordEncoder} bean is defined in {@link com.aws.carddemo.config.SecurityConfig#passwordEncoder()}
 * and is automatically injected into:</p>
 * <ul>
 *   <li>{@code AuthenticationService} - For login credential validation</li>
 *   <li>{@code UserService} - For password hashing during user creation and updates</li>
 *   <li>{@code UserDetailsServiceImpl} - For Spring Security authentication provider</li>
 * </ul>
 * 
 * @see com.aws.carddemo.config.SecurityConfig#passwordEncoder()
 * @see org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
 * @see org.springframework.security.crypto.password.PasswordEncoder
 * @author AWS CardDemo Modernization Team
 * @version 1.0
 * @since 1.0
 */
public class PasswordEncoderConfig {

    /**
     * BCrypt work factor (log2 of hashing iterations).
     * 
     * <p>A strength of 10 means 2^10 = 1,024 iterations of the BCrypt key derivation function.
     * This provides strong security while maintaining acceptable authentication performance 
     * (~100ms per password verification).</p>
     * 
     * <p><b>Recommended Values:</b></p>
     * <ul>
     *   <li>Strength 10: ~100ms verification (current setting - balances security and UX)</li>
     *   <li>Strength 12: ~400ms verification (high security applications)</li>
     *   <li>Strength 14: ~1.6s verification (maximum security, impacts user experience)</li>
     * </ul>
     * 
     * <p><b>Security Note:</b> As computing power increases, this value should be periodically 
     * reviewed and increased to maintain security margin. BCrypt is designed to support work 
     * factor increases without requiring algorithm migration.</p>
     */
    private static final int BCRYPT_STRENGTH = 10;

    /**
     * Creates and configures a BCrypt password encoder bean for the Spring Security framework.
     * 
     * <p>This bean is managed by the Spring container and automatically injected into any component
     * requiring password encoding/verification functionality via constructor injection or 
     * {@code @Autowired} annotation.</p>
     * 
     * <p><b>Bean Lifecycle:</b></p>
     * <ol>
     *   <li>Spring creates singleton instance during application context initialization</li>
     *   <li>Bean is registered in application context with type {@code PasswordEncoder}</li>
     *   <li>Dependency injection framework provides this instance to requesting components</li>
     *   <li>Single instance is reused throughout application lifetime (thread-safe)</li>
     * </ol>
     * 
     * <p><b>Thread Safety:</b> BCryptPasswordEncoder is thread-safe and can be safely used
     * concurrently by multiple threads without external synchronization. The encoder handles
     * thread-local random salt generation internally.</p>
     * 
     * <p><b>Key Methods Provided by PasswordEncoder Interface:</b></p>
     * <ul>
     *   <li>{@code encode(CharSequence rawPassword)}: 
     *       Hashes a plain-text password and returns a 60-character BCrypt string.
     *       Each invocation produces a different hash due to unique salt generation.
     *       <br><b>Example:</b> {@code encode("Password123")} → 
     *       {@code "$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy"}
     *   </li>
     *   <li>{@code matches(CharSequence rawPassword, String encodedPassword)}: 
     *       Verifies a plain-text password against a BCrypt hash. Returns {@code true} if 
     *       password matches, {@code false} otherwise. Constant-time comparison prevents 
     *       timing attacks.
     *       <br><b>Example:</b> {@code matches("Password123", user.getPasswordHash())} → {@code true}
     *   </li>
     * </ul>
     * 
     * <p><b>Implementation Details:</b></p>
     * <ul>
     *   <li><b>Hash Format:</b> {@code $2a$[rounds]$[22-char salt][31-char hash]}</li>
     *   <li><b>Character Set:</b> Base64 encoding (./0-9A-Za-z)</li>
     *   <li><b>Salt Length:</b> 128 bits (22 base64 characters)</li>
     *   <li><b>Hash Length:</b> 184 bits (31 base64 characters)</li>
     *   <li><b>Total Length:</b> 60 characters (constant length regardless of input)</li>
     * </ul>
     * 
     * <p><b>Security Guarantees:</b></p>
     * <ul>
     *   <li>Cryptographically secure random salt generation using {@code SecureRandom}</li>
     *   <li>Constant-time comparison in {@code matches()} method prevents timing side-channels</li>
     *   <li>No password length limitation (any length string can be hashed)</li>
     *   <li>Resistant to GPU-accelerated attacks due to memory-hard algorithm design</li>
     * </ul>
     * 
     * <p><b>Performance Characteristics:</b></p>
     * <ul>
     *   <li><b>Encoding (one-time):</b> ~100ms per password on modern hardware</li>
     *   <li><b>Verification (per login):</b> ~100ms per authentication attempt</li>
     *   <li><b>Scalability:</b> CPU-bound operation, scales linearly with concurrent requests</li>
     *   <li><b>Recommendation:</b> Consider caching authenticated sessions to minimize 
     *       repeated password verifications</li>
     * </ul>
     * 
     * <p><b>Migration Considerations:</b></p>
     * <ul>
     *   <li>Existing COBOL 8-character passwords must be migrated during first login:
     *       <ol>
     *         <li>User authenticates with legacy 8-char password (force password reset)</li>
     *         <li>System prompts for new password meeting modern complexity requirements</li>
     *         <li>New password hashed with BCrypt and stored in password_hash column</li>
     *         <li>Legacy plain-text password column marked NULL or removed</li>
     *       </ol>
     *   </li>
     *   <li>Password policy enforcement (minimum length, complexity) handled separately 
     *       by validation annotations in User entity and UserService business logic</li>
     * </ul>
     * 
     * @return A thread-safe {@link PasswordEncoder} implementation using BCrypt algorithm
     *         with 10 salt rounds, suitable for production use in the CardDemo application.
     *         The returned encoder is fully compliant with PCI-DSS Requirement 8.2.1 for
     *         strong cryptographic password storage.
     * 
     * @see BCryptPasswordEncoder#encode(CharSequence)
     * @see BCryptPasswordEncoder#matches(CharSequence, String)
     * 
     * <p><strong>NOTE:</strong> This is now a documentation/example method. The actual 
     * {@code passwordEncoder} bean is defined in {@link com.aws.carddemo.config.SecurityConfig}.</p>
     */
    public PasswordEncoder examplePasswordEncoder() {
        return new BCryptPasswordEncoder(BCRYPT_STRENGTH);
    }
}
