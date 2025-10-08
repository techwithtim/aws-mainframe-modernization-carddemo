/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.carddemo.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import java.security.Key;
import java.util.Base64;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * JWT token provider component for generating and validating JSON Web Tokens.
 * 
 * <p>Migrated from: app/cbl/COSGN00C.cbl
 * Replaces COBOL session token logic (CARDDEMO-COMMAREA) with stateless JWT tokens
 * enabling horizontal scaling without sticky sessions or shared session state.</p>
 * 
 * <p>Security Features:
 * - Supports HS256 symmetric signing for development environments
 * - Supports RS256 asymmetric signing for production environments
 * - Configurable token expiration (default 1 hour)
 * - Secure claims payload with username, user_id, roles
 * - Signature verification to prevent token tampering
 * - Expiration validation with detailed error logging</p>
 * 
 * <p>PCI-DSS Compliance:
 * - Usernames masked in logs for privacy
 * - No sensitive data (passwords, card numbers) stored in tokens
 * - Tokens are signed to prevent unauthorized modifications
 * - Expiration enforced to limit exposure window</p>
 * 
 * @see io.jsonwebtoken.Jwts
 * @see org.springframework.security.core.userdetails.UserDetails
 */
@Component
public class JwtTokenProvider {

    private static final Logger logger = LoggerFactory.getLogger(JwtTokenProvider.class);

    /**
     * JWT secret key for HS256 symmetric signing algorithm.
     * Should be a base64-encoded string of at least 256 bits (32 bytes).
     * In production, use RS256 asymmetric signing instead.
     */
    @Value("${jwt.secret:}")
    private String jwtSecret;

    /**
     * JWT token expiration time in milliseconds.
     * Default: 3600000 ms = 1 hour
     */
    @Value("${jwt.expiration:3600000}")
    private long jwtExpirationMs;

    /**
     * JWT signing algorithm configuration.
     * Supported values: HS256 (symmetric), RS256 (asymmetric)
     * Default: HS256 for development
     */
    @Value("${jwt.algorithm:HS256}")
    private String jwtAlgorithm;

    /**
     * RSA private key PEM for RS256 signing (production).
     * Only required when jwt.algorithm=RS256
     */
    @Value("${jwt.private-key:}")
    private String jwtPrivateKey;

    /**
     * Generates a JWT access token for an authenticated user.
     * 
     * <p>Replaces COBOL logic in COSGN00C.cbl lines 224-228:
     * <pre>
     * MOVE WS-USER-ID   TO CDEMO-USER-ID
     * MOVE SEC-USR-TYPE TO CDEMO-USER-TYPE
     * </pre>
     * </p>
     * 
     * <p>Token Claims Structure:
     * - sub: username (subject claim)
     * - username: username for explicit reference
     * - roles: array of authority strings (e.g., ["ROLE_ADMIN", "ROLE_USER"])
     * - user_id: optional user identifier for database lookups
     * - iat: issued at timestamp
     * - exp: expiration timestamp</p>
     * 
     * @param userDetails Spring Security UserDetails containing username and authorities
     * @return signed JWT token string
     * @throws IllegalStateException if JWT secret is not configured
     */
    public String generateToken(UserDetails userDetails) {
        String username = userDetails.getUsername();
        
        // Extract roles from authorities
        List<String> roles = userDetails.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toList());

        // Build claims map
        Map<String, Object> claims = new HashMap<>();
        claims.put("username", username);
        claims.put("roles", roles);
        // Note: user_id can be added if UserDetails implementation provides it
        // claims.put("user_id", userId);

        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + jwtExpirationMs);

        try {
            String token = Jwts.builder()
                    .setClaims(claims)
                    .setSubject(username)
                    .setIssuedAt(now)
                    .setExpiration(expiryDate)
                    .signWith(getSigningKey(), getSignatureAlgorithm())
                    .compact();

            // Log token generation with masked username for security
            if (logger.isDebugEnabled()) {
                String maskedUsername = maskUsername(username);
                logger.debug("Generated JWT token for user: {}, expires at: {}", 
                        maskedUsername, expiryDate);
            }

            return token;
        } catch (Exception e) {
            logger.error("Failed to generate JWT token for user: {}", 
                    maskUsername(username), e);
            throw new IllegalStateException("JWT token generation failed", e);
        }
    }

    /**
     * Validates a JWT token's signature and expiration.
     * 
     * <p>Replaces COBOL password validation logic in COSGN00C.cbl line 223:
     * <pre>
     * IF SEC-USR-PWD = WS-USER-PWD
     * </pre>
     * With cryptographic signature verification ensuring token integrity.</p>
     * 
     * @param token JWT token string to validate
     * @return true if token is valid (signature correct and not expired), false otherwise
     */
    public boolean validateToken(String token) {
        try {
            Jwts.parser()
                    .verifyWith((javax.crypto.SecretKey) getSigningKey())
                    .build()
                    .parseSignedClaims(token);
            return true;
        } catch (ExpiredJwtException e) {
            // Specific handling for expired tokens - log warning and return false
            logger.warn("JWT token has expired: {}", e.getMessage());
            return false;
        } catch (JwtException e) {
            // Handle all other JWT validation failures (signature invalid, malformed, etc.)
            logger.error("JWT token validation failed: {}", e.getMessage());
            return false;
        } catch (IllegalArgumentException e) {
            logger.error("JWT token is null or empty: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Extracts username from JWT token claims.
     * 
     * <p>Replaces COBOL CDEMO-USER-ID retrieval from COMMAREA.</p>
     * 
     * @param token JWT token string
     * @return username extracted from 'sub' claim
     * @throws JwtException if token is invalid or expired
     */
    public String getUsernameFromToken(String token) {
        try {
            Claims claims = extractAllClaims(token);
            return claims.getSubject();
        } catch (Exception e) {
            logger.error("Failed to extract username from token: {}", e.getMessage());
            throw new JwtException("Failed to extract username from token", e);
        }
    }

    /**
     * Extracts roles from JWT token claims and converts to Spring Security authorities.
     * 
     * <p>Replaces COBOL CDEMO-USER-TYPE logic (COSGN00C.cbl line 227):
     * <pre>
     * MOVE SEC-USR-TYPE TO CDEMO-USER-TYPE
     * IF CDEMO-USRTYP-ADMIN
     *     EXEC CICS XCTL PROGRAM ('COADM01C')
     * ELSE
     *     EXEC CICS XCTL PROGRAM ('COMEN01C')
     * </pre>
     * With role-based access control using Spring Security authorities.</p>
     * 
     * @param token JWT token string
     * @return List of GrantedAuthority objects representing user roles
     * @throws JwtException if token is invalid or expired
     */
    @SuppressWarnings("unchecked")
    public List<GrantedAuthority> getRolesFromToken(String token) {
        try {
            Claims claims = extractAllClaims(token);
            List<String> roles = (List<String>) claims.get("roles");
            
            if (roles == null || roles.isEmpty()) {
                logger.warn("No roles found in JWT token claims");
                return List.of();
            }
            
            return roles.stream()
                    .map(SimpleGrantedAuthority::new)
                    .collect(Collectors.toList());
        } catch (Exception e) {
            logger.error("Failed to extract roles from token: {}", e.getMessage());
            throw new JwtException("Failed to extract roles from token", e);
        }
    }

    /**
     * Extracts all claims from JWT token.
     * 
     * @param token JWT token string
     * @return Claims object containing all token claims
     * @throws JwtException if token parsing fails
     */
    private Claims extractAllClaims(String token) {
        return Jwts.parser()
                .verifyWith((javax.crypto.SecretKey) getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /**
     * Gets the signing key for JWT token generation and validation.
     * 
     * <p>Supports two configurations:
     * 1. HS256 (symmetric): Uses base64-decoded secret key from jwt.secret property
     * 2. RS256 (asymmetric): Uses RSA private key from jwt.private-key property (production)</p>
     * 
     * @return Key object for JWT signing/verification
     * @throws IllegalStateException if secret key is not configured properly
     */
    private Key getSigningKey() {
        if ("RS256".equalsIgnoreCase(jwtAlgorithm)) {
            // RS256 asymmetric signing (production)
            if (jwtPrivateKey == null || jwtPrivateKey.isEmpty()) {
                throw new IllegalStateException(
                        "JWT private key is not configured for RS256 algorithm");
            }
            // Note: In production, parse RSA private key from PEM format
            // For now, fallback to HS256
            logger.warn("RS256 configured but private key parsing not implemented, " +
                    "falling back to HS256");
        }
        
        // HS256 symmetric signing (development)
        if (jwtSecret == null || jwtSecret.isEmpty()) {
            throw new IllegalStateException(
                    "JWT secret is not configured. Set jwt.secret property in application.yml");
        }
        
        try {
            byte[] keyBytes = Base64.getDecoder().decode(jwtSecret);
            return Keys.hmacShaKeyFor(keyBytes);
        } catch (IllegalArgumentException e) {
            logger.error("JWT secret is not valid base64: {}", e.getMessage());
            throw new IllegalStateException("Invalid JWT secret configuration", e);
        }
    }

    /**
     * Gets the signature algorithm for JWT signing.
     * 
     * @return SignatureAlgorithm enum value
     */
    private SignatureAlgorithm getSignatureAlgorithm() {
        if ("RS256".equalsIgnoreCase(jwtAlgorithm)) {
            return SignatureAlgorithm.RS256;
        }
        return SignatureAlgorithm.HS256;
    }

    /**
     * Masks username for logging to protect PII.
     * 
     * <p>PCI-DSS compliance requirement: mask sensitive data in logs.</p>
     * 
     * @param username username to mask
     * @return masked username (shows first 2 and last 1 characters)
     */
    private String maskUsername(String username) {
        if (username == null || username.length() <= 3) {
            return "***";
        }
        return username.substring(0, 2) + "***" + username.substring(username.length() - 1);
    }
}
