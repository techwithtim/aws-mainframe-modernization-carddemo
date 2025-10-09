/*
 * CardDemo - Security Configuration
 * 
 * Configures Spring Security for JWT-based authentication and role-based authorization.
 * Replaces COBOL RACF security with modern Spring Security framework.
 * 
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.aws.carddemo.config;

import com.aws.carddemo.security.JwtAuthenticationFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Security configuration for CardDemo application.
 * 
 * <p>Enables JWT-based authentication and role-based authorization using Spring Security,
 * replacing legacy COBOL RACF security subsystem with modern security framework.</p>
 * 
 * <p>Key Features:</p>
 * <ul>
 *   <li>JWT token-based authentication (stateless)</li>
 *   <li>Method-level security with @PreAuthorize annotations</li>
 *   <li>Role-based access control (ROLE_USER, ROLE_ADMIN)</li>
 *   <li>BCrypt password encryption (replaces plain-text COBOL passwords)</li>
 * </ul>
 * 
 * <p>COBOL Legacy Mapping:</p>
 * <pre>
 * COSGN00C.cbl (RACF Authentication):
 *   EXEC CICS READ DATASET('USRSEC') → UserDetailsService.loadUserByUsername()
 *   IF SEC-USR-PWD = WS-USER-PWD → PasswordEncoder.matches()
 *   MOVE SEC-USR-TYPE TO CDEMO-USER-TYPE → Granted Authorities (ROLE_USER, ROLE_ADMIN)
 * </pre>
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
@RequiredArgsConstructor
@Profile("!test")
public class SecurityConfig {
    
    private final JwtAuthenticationFilter jwtAuthFilter;
    private final UserDetailsService userDetailsService;
    private final PasswordEncoder passwordEncoder;
    
    /**
     * Configures HTTP security filter chain.
     * 
     * <p>Defines which endpoints require authentication and configures JWT filter.</p>
     * 
     * @param http HttpSecurity to configure
     * @return Configured SecurityFilterChain
     * @throws Exception if configuration fails
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(auth -> auth
                        // Public endpoints (no authentication required)
                        .requestMatchers("/api/v1/auth/**").permitAll()
                        .requestMatchers("/actuator/health", "/actuator/info").permitAll()
                        .requestMatchers("/swagger-ui/**", "/v3/api-docs/**").permitAll()
                        // All other endpoints require authentication
                        .anyRequest().authenticated()
                )
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                )
                .authenticationProvider(authenticationProvider())
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);
        
        return http.build();
    }
    
    /**
     * Configures authentication provider with UserDetailsService and PasswordEncoder.
     * 
     * @return Configured AuthenticationProvider
     */
    @Bean
    public AuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider authProvider = new DaoAuthenticationProvider();
        authProvider.setUserDetailsService(userDetailsService);
        authProvider.setPasswordEncoder(passwordEncoder);
        return authProvider;
    }
    
    /**
     * Exposes AuthenticationManager bean for authentication operations.
     * 
     * @param config AuthenticationConfiguration
     * @return AuthenticationManager
     * @throws Exception if configuration fails
     */
    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }
}
