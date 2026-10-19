# Production OAuth Identity Decision Implementation Plan

> **For agentic workers:** Execute this plan inline with focused verification. Do not commit until explicitly approved.

**Goal:** Make production password-plus-MFA-only while preserving the existing Google OAuth implementation for non-production environments.

**Architecture:** Use one `racetracker.oauth.enabled` backend property, defaulting enabled in the shared configuration and explicitly disabled in the `prod` profile. The controller rejects disabled OAuth requests with the existing safe API-exception path, and the production validator fails startup if OAuth is enabled. The current web login has no Google control, so no frontend build flag or new control is introduced; a UI regression test and README note document that state.

**Verification:** Run focused backend Maven tests for the OAuth controller and production validator, then the focused web test suite for the login surface plus web typecheck/lint as practical. Do not commit.
