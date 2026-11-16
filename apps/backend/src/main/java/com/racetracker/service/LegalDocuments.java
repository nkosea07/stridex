package com.racetracker.service;

/**
 * Current versions of the documents every user must accept. They mirror
 * {@code packages/contracts/contract.json} ({@code legal.privacyPolicyVersion} and
 * {@code legal.termsVersion}); {@code pnpm contracts:validate} fails when they diverge.
 */
public final class LegalDocuments {

    public static final String CURRENT_PRIVACY_POLICY_VERSION = "2026-11-10";
    public static final String CURRENT_TERMS_VERSION = "2026-11-10";

    private LegalDocuments() {
    }

    public static boolean acceptanceRequired(String acceptedPrivacyPolicyVersion, String acceptedTermsVersion) {
        return !CURRENT_PRIVACY_POLICY_VERSION.equals(acceptedPrivacyPolicyVersion)
            || !CURRENT_TERMS_VERSION.equals(acceptedTermsVersion);
    }
}
