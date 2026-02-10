package com.bankleads.bank_leads_backend.service;

import com.bankleads.bank_leads_backend.model.CanonicalField;
import com.bankleads.bank_leads_backend.repository.CanonicalFieldRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;

/**
 * Performs deduplication driven by active canonical fields.
 * Only identifier-type canonical fields (email, phone_number, aadhar_number or types Email, Phone)
 * are used to build the deduplication config; then the core DeduplicationService is invoked.
 * Intended to be run automatically after lead upload.
 */
@Service
@RequiredArgsConstructor
public class CanonicalFieldDeduplicationService {

    private static final Logger log = LoggerFactory.getLogger(CanonicalFieldDeduplicationService.class);

    private static final Set<String> EMAIL_FIELD_NAMES = Set.of("email", "email_id", "emailid", "mail", "e_mail", "email_address");
    private static final Set<String> PHONE_FIELD_NAMES = Set.of("phone_number", "phone", "phonenumber", "mobile", "mobile_number", "contact", "contact_number");
    private static final Set<String> AADHAR_FIELD_NAMES = Set.of("aadhar_number", "aadhar", "aadhaar", "aadhaar_number", "aadhar_no");

    private final CanonicalFieldRepository canonicalFieldRepository;
    private final DeduplicationService deduplicationService;

    /**
     * Builds deduplication config from active canonical fields.
     * Uses field name (normalized) and field type to determine which identifiers to use.
     */
    public DeduplicationService.DeduplicationConfig buildConfigFromCanonicalFields() {
        List<CanonicalField> activeFields = canonicalFieldRepository
                .findAll(PageRequest.of(0, 500))
                .getContent()
                .stream()
                .filter(f -> f.getIsActive() != null && f.getIsActive())
                .toList();

        boolean useEmail = false;
        boolean usePhone = false;
        boolean useAadhar = false;

        for (CanonicalField f : activeFields) {
            String name = f.getFieldName() == null ? "" : f.getFieldName().trim().toLowerCase().replaceAll("\\s+", "_");
            CanonicalField.FieldType type = f.getFieldType();

            if (EMAIL_FIELD_NAMES.contains(name) || type == CanonicalField.FieldType.Email) {
                useEmail = true;
            }
            if (PHONE_FIELD_NAMES.contains(name) || type == CanonicalField.FieldType.Phone) {
                usePhone = true;
            }
            if (AADHAR_FIELD_NAMES.contains(name)) {
                useAadhar = true;
            }
        }

        // If no identifier fields are defined in canonical, default to all (current behaviour)
        if (!useEmail && !usePhone && !useAadhar) {
            useEmail = true;
            usePhone = true;
            useAadhar = true;
            log.debug("No identifier canonical fields found; using default deduplication config (email, phone, aadhar)");
        }

        return new DeduplicationService.DeduplicationConfig(useEmail, usePhone, useAadhar);
    }

    /**
     * Runs deduplication using config derived from active canonical fields.
     * Call this automatically after lead upload.
     */
    public DeduplicationService.DeduplicationStats runDeduplicationFromCanonicalFields() {
        DeduplicationService.DeduplicationConfig config = buildConfigFromCanonicalFields();
        log.info("Running automatic deduplication from canonical fields: useEmail={}, usePhone={}, useAadhar={}",
                config.isUseEmail(), config.isUsePhone(), config.isUseAadhar());
        DeduplicationService.DeduplicationStats stats = deduplicationService.executeDeduplication(config);
        log.info("Automatic deduplication completed: totalLeads={}, duplicatesFound={}, mergedCount={}, finalCount={}",
                stats.getTotalLeads(), stats.getDuplicatesFound(), stats.getMergedCount(), stats.getFinalCount());
        return stats;
    }
}
