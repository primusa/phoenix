package com.example.phoenix.service;

import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Enterprise Governance Service
 * Handles data privacy, PII redaction, and compliance checks before external AI
 * processing.
 * 
 * NOTE: This is a development-mode implementation providing a subset of core
 * protections.
 * In a Production environment, this service is part of an enforced governance
 * gateway
 * with comprehensive PII/PHI scrubbing and regulatory auditing.
 */
@Service
public class GovernanceService {

    private static final Logger log = LoggerFactory.getLogger(GovernanceService.class);

    // Simple SSN Pattern: XXX-XX-XXXX or XXXXXXXXX
    private static final Pattern SSN_PATTERN = Pattern.compile("\\b\\d{3}-\\d{2}-\\d{4}\\b|\\b\\d{9}\\b");

    // Simple Email Pattern
    private static final Pattern EMAIL_PATTERN = Pattern.compile("[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,6}");

    // Simple Policy ID Pattern: POL-XXXXXX or POLICY-XXXXXX
    private static final Pattern POLICY_PATTERN = Pattern.compile("(?i)\\b(POL|POLICY)-\\d{4,10}\\b");

    /**
     * Redacts sensitive data from claim text.
     * Currently supports SSN, Email, and Policy ID masking.
     */
    public String redactSensitiveData(String input) {
        if (input == null || input.isEmpty()) {
            return input;
        }

        log.debug("Running governance scrub on input (length: {})", input.length());

        String redacted = input;

        // 1. Scrub SSNs
        redacted = SSN_PATTERN.matcher(redacted).replaceAll("[REDACTED_SSN]");

        // 2. Scrub Emails
        redacted = EMAIL_PATTERN.matcher(redacted).replaceAll("[REDACTED_EMAIL]");

        // 3. Scrub Policy IDs
        redacted = POLICY_PATTERN.matcher(redacted).replaceAll("[REDACTED_POLICY_ID]");

        if (!redacted.equals(input)) {
            log.info("Governance: PII detected and redacted from claim stream.");
        }

        return redacted;
    }
}
