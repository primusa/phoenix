package com.example.phoenix.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.regex.Pattern;

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

    private static final Map<Pattern, String> REDACTION_RULES = Map.of(
            Pattern.compile("\\b\\d{3}-\\d{2}-\\d{4}\\b|\\b\\d{9}\\b"), "[REDACTED_SSN]",
            Pattern.compile("[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,6}"), "[REDACTED_EMAIL]",
            Pattern.compile("(?i)\\b(POL|POLICY)-\\d{4,10}\\b"), "[REDACTED_POLICY_ID]"
    );

    /**
     * Redacts sensitive data from claim text.
     */
    public String redactSensitiveData(String input) {
        if (input == null || input.isEmpty()) {
            return input;
        }

        log.debug("Running governance scrub on input (length: {})", input.length());

        String[] redacted = {input};
        boolean changed = false;

        for (Map.Entry<Pattern, String> rule : REDACTION_RULES.entrySet()) {
            String result = rule.getKey().matcher(redacted[0]).replaceAll(rule.getValue());
            if (!result.equals(redacted[0])) {
                redacted[0] = result;
                changed = true;
            }
        }

        if (changed) {
            log.info("Governance: PII detected and redacted from claim stream.");
        }

        return redacted[0];
    }
}
