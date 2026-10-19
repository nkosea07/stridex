# AMS Platform Gap Analysis Report (2026-05-31)

> Historical snapshot from 2026-05-31. Superseded by
> `docs/IMPLEMENTATION_GAP_ANALYSIS_2026_10_11.md` and the current register in
> `docs/PENDING_WORK.md`; the findings below are retained for audit history.

This report provides an updated audit of the AMS (Athlete Management System) against the requirements specified in `marathon_race_tracking_master_prompt.md`.

## 1. Executive Summary
The AMS platform has made significant progress since the last audit (2026-05-19). The UI/UX has been modernized with a modular hybrid dashboard, and core tracking features are functional. However, several enterprise-level modules and scalability improvements remain outstanding.

## 2. Status of Key Modules

| Module | Status | Gaps / Observations |
| :--- | :--- | :--- |
| **1. Auth & User Management** | PARTIAL | Roles (OFFICIAL, SPECTATOR, etc.) added to model. MFA, Google/Apple login, and password reset flows are still missing. |
| **2. Race Management** | PARTIAL | Core CRUD exists. Cloning, archiving, and branding/media pipelines are missing. |
| **3. Registration System** | PARTIAL | Basic self-reg and admin-reg exist. Dynamic forms, waivers, and team registrations are missing. |
| **4. CSV Import / Export** | PARTIAL | Implemented in `ParticipantService` but lacks background processing for large datasets (50k+). |
| **5. Printable Bibs** | MISSING | Dependencies (iText, ZXing) in `pom.xml` but no implementation in Java. |
| **6. Live Tracking** | PARTIAL | WebSocket sync, map views, and predictive finish times exist. GPS drift correction and route playback are missing. |
| **7. Checkpoint & Timing** | PARTIAL | Scanning and manual entry exist. DNF/DNS/Penalty management is weak. |
| **8. Results Management** | PARTIAL | Ranking by category/gender exists. Certificates and historical records are missing. |
| **9. Payment System** | MISSING | No payment gateway integrations (Stripe, PayPal, etc.) found. |
| **10. Notifications** | PARTIAL | In-app notifications exist. No Push (FCM), SMS, or template management. |
| **11. Analytics & Reporting** | PARTIAL | Basic stats and CSV export exist. No rich BI dashboards or PDF/Excel reporting. |
| **12. Media Module** | MISSING | No athlete photo matching or social sharing. |
| **15. Performance & Scale** | PARTIAL | Redis integrated for some caching. No message queue (RabbitMQ/Kafka) for long-running tasks. |
| **17. Testing** | PARTIAL | Good backend unit/integration tests. No automated Web (Cypress) or Mobile tests. |
| **20. AI-Powered Features**| MISSING | Predictive finish is basic; no anomaly detection or crowd density prediction. |

## 3. High-Priority Technical Debt
- **Background Processing:** Long-running tasks like CSV import and email sending are currently handled synchronously or with simple `@Async`, which lacks durability and visibility.
- **GPS Data Volume:** The `gps_locations` table will grow rapidly. Partitioning or cold storage strategies are not yet implemented.
- **Frontend State Management:** The web app uses local state heavily; as complexity grows, a robust state management solution (Zustand/Redux) might be needed for the Live Tracking dashboard.
- **Mobile Background Robustness:** True background tracking on Android/iOS requires significant hardening and testing across different OS versions.

## 4. Immediate Roadmap (Proposed)

### Phase 1: Core Operations & Reliability
1. **Implement Module 5 (Printable Bibs):** Create PDF generation service using iText and ZXing.
2. **Harden Module 4 (CSV):** Implement background processing using Spring Batch or a simple Task Queue.
3. **Enhance Module 10 (Notifications):** Integrate a transactional email provider (SendGrid/AWS SES) with template support.

### Phase 2: Commercialization
1. **Implement Module 9 (Payments):** Integrate Stripe/PayPal for race registrations.
2. **Complete Module 3 (Registration):** Add waiver/consent workflows and dynamic fields.

### Phase 3: Enterprise & AI
1. **Module 12 (Media):** Basic image upload and athlete tagging.
2. **Module 20 (AI):** Simple GPS anomaly detection (to prevent cheating) and improved finish time forecasting.

## 5. Security & Compliance
- **GDPR:** Needs "Right to be forgotten" and Data Export features for athletes.
- **Rate Limiting:** Implement Spring Cloud Gateway or Bucket4j for API rate limiting.
