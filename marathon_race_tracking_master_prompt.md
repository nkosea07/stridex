# Enterprise Marathon & Race Tracking Platform — AI Engineering Master Prompt

You are a senior enterprise software architect, product designer, DevOps engineer, backend engineer, frontend engineer, mobile engineer, QA engineer, security engineer, and AI systems reviewer.

Your task is to REVIEW, AUDIT, REFINE, REFACTOR, and COMPLETE an existing marathon/race tracking platform that has already been partially developed from earlier requirements.

You must deeply analyze the current implementation and compare it against the complete specification below.

The goal is to produce a production-grade, scalable, secure, modular, cloud-ready, enterprise-level race management and live athlete tracking ecosystem.

You must identify:
- Missing features
- Weak architecture decisions
- Poor scalability patterns
- Security flaws
- Performance bottlenecks
- Incomplete workflows
- Missing APIs
- UI/UX gaps
- Data inconsistencies
- Missing integrations
- Technical debt
- Mobile limitations
- Poor database design
- Refactoring opportunities
- DevOps and deployment gaps
- Testing deficiencies

Then fully implement or refactor everything necessary.

---

# PRIMARY OBJECTIVE

Build a COMPLETE END-TO-END marathon and race management ecosystem capable of handling:

- Marathon races
- Road races
- Cycling races
- Fun runs
- Ultra marathons
- Multi-stage races
- Charity races
- School races
- International sporting events

The platform must support:
- Web platform
- Mobile applications
- Live athlete tracking
- Real-time race operations
- Registration management
- Race analytics
- Timing systems
- Race administration
- Spectator viewing
- Event management
- Notifications
- Reporting
- Payments
- Media sharing
- AI insights

The system must be scalable enough to support races with:
- 100 participants
- 1,000 participants
- 10,000 participants
- 50,000+ participants concurrently

Examples:
- Old Mutual Marathon
- Two Oceans Marathon
- Comrades Marathon
- Joshua Nkomo Peace Marathon
- CBZ Marathon
- Any dynamically created future race

---

# REQUIRED TECH STACK

## Backend
Use:
- Java Spring Boot (latest stable version)
- Spring Security
- Spring Data JPA
- PostgreSQL
- Redis caching
- WebSockets
- JWT Authentication
- REST APIs
- GraphQL optionally
- Microservice-ready architecture
- Docker support

## Web Frontend
Use:
- React JS
- TypeScript
- Redux Toolkit or Zustand
- Material UI or Tailwind CSS
- Responsive design
- Progressive Web App support

## Mobile App
Preferred:
- Flutter

Alternative:
- React Native

Must support:
- Android
- iOS

Must include:
- Background GPS tracking
- Push notifications
- Offline mode
- Battery optimization
- Real-time map updates

---

# SYSTEM MODULES

The system must contain the following fully functional modules.

---

# 1. AUTHENTICATION & USER MANAGEMENT

Implement:
- Admin accounts
- Super admin
- Race organizers
- Officials
- Volunteers
- Athletes
- Spectators

Features:
- JWT authentication
- OAuth login
- Google login
- Apple login
- Role-based access control
- Multi-factor authentication
- Password reset
- Email verification
- Session management
- Account locking
- Audit logging

---

# 2. RACE MANAGEMENT MODULE

Allow admins to:
- Create races dynamically
- Edit races
- Clone existing races
- Archive races
- Cancel races

Race configuration:
- Race name
- Description
- Categories
- Distances
- Routes
- Start points
- Finish points
- Checkpoints
- Water points
- Medical points
- Sponsors
- Branding
- Posters
- Race schedules
- Maximum participants
- Registration windows

Support:
- Multiple race types
- Multi-day events
- Multi-stage races
- International timezone handling

---

# 3. PARTICIPANT REGISTRATION SYSTEM

Implement:
- Manual registration
- Bulk registration
- Self-service athlete registration portal
- Team registration
- Club registration

Features:
- Dynamic registration forms
- Waivers
- Consent forms
- Emergency contacts
- Medical information
- Shirt sizes
- Category selection
- Payment verification
- Registration approval workflows

Include:
- Automatic bib allocation
- QR codes
- Unique athlete IDs
- RFID/chip integration readiness

---

# 4. CSV IMPORT / EXPORT

Implement:
- Bulk CSV upload
- CSV validation
- Error handling
- Duplicate detection
- Import preview
- Export participants
- Export results
- Export checkpoints
- Export analytics

Support:
- Excel import
- Large file uploads
- Background processing queues

---

# 5. PRINTABLE BIB / RACE NUMBER GENERATION

Generate:
- Printable race bibs
- QR codes
- Barcodes
- Emergency details
- Sponsor branding
- Category coloring

Support:
- PDF generation
- Batch printing
- Custom templates
- Dynamic layouts

---

# 6. LIVE ATHLETE TRACKING SYSTEM

This is a CORE FEATURE.

Implement real-time athlete tracking using:
- GPS tracking
- Mobile device tracking
- RFID/chip timing integration capability
- Checkpoint scanning
- WebSockets
- Real-time synchronization

Features:
- Live map tracking
- Athlete movement updates
- Geo-location updates
- Checkpoint progress
- Estimated finish times
- Pace calculations
- Distance remaining
- Heat maps
- Route playback

Map support:
- Google Maps
- OpenStreetMap
- Mapbox

Spectator features:
- Search athlete
- Follow athlete
- Favorite athletes
- Live leaderboard
- Live pace charts

Optimize for:
- Battery usage
- Low network environments
- Offline buffering
- GPS drift correction

---

# 7. CHECKPOINT & TIMING SYSTEM

Implement:
- Checkpoint management
- Timing officials interface
- QR scanning
- Manual timing entry
- RFID integration-ready architecture

Features:
- Split timing
- Automatic ranking
- Penalty management
- DNF handling
- DNS handling
- Time corrections
- Race completion verification

---

# 8. RESULTS MANAGEMENT

Features:
- Live results
- Official results
- Rankings
- Gender rankings
- Age group rankings
- Team rankings
- Country rankings

Include:
- Downloadable certificates
- Medals allocation
- Qualification status
- Historical records
- Personal bests

---

# 9. PAYMENT SYSTEM

Implement:
- Online payments
- Mobile money integration
- Stripe
- PayPal
- Flutterwave
- Paynow
- EcoCash
- Visa/Mastercard

Features:
- Refund handling
- Coupons
- Promo codes
- Discounts
- Team pricing
- Early bird pricing

---

# 10. EMAIL & NOTIFICATION SYSTEM

Implement:
- Email templates
- SMS notifications
- Push notifications
- WhatsApp integration optionally

Send:
- Registration confirmations
- Payment confirmations
- Race reminders
- Live race alerts
- Checkpoint alerts
- Finish notifications
- Emergency alerts

Include:
- Template management
- Multi-language support

---

# 11. ANALYTICS & REPORTING

Implement dashboards showing:
- Registrations
- Revenue
- Race participation
- Athlete demographics
- Heat maps
- Race completion stats
- Checkpoint analytics
- Device tracking quality

Export:
- PDF reports
- Excel reports
- CSV reports

Add:
- AI-powered insights
- Predictive analytics
- Dropout prediction
- Performance forecasting

---

# 12. MEDIA MODULE

Allow:
- Race photos
- Video uploads
- Athlete galleries
- AI facial recognition tagging optionally

Features:
- Athlete photo matching
- Social sharing
- Media moderation

---

# 13. MOBILE APPLICATION FEATURES

The mobile app must include:

Athlete app:
- Registration
- GPS tracking
- Route viewing
- Race updates
- Emergency SOS
- Pace tracking
- Offline support

Spectator app:
- Live athlete tracking
- Notifications
- Leaderboards
- Maps

Admin app:
- Checkpoint scanning
- Timing entry
- Race management
- Incident reporting

---

# 14. SECURITY REQUIREMENTS

Implement:
- OWASP best practices
- Rate limiting
- SQL injection prevention
- XSS protection
- CSRF protection
- Encryption
- Secure JWT handling
- API throttling
- Audit logs

Ensure:
- GDPR readiness
- Data privacy compliance
- Secure GPS data handling

---

# 15. PERFORMANCE & SCALABILITY

The system must support:
- Tens of thousands of concurrent users
- Real-time tracking updates
- Horizontal scaling
- High availability

Implement:
- Redis caching
- Queue systems
- Event-driven architecture
- Lazy loading
- CDN readiness

Optimize:
- Database queries
- API performance
- WebSocket throughput
- Mobile battery efficiency

---

# 16. DEVOPS & DEPLOYMENT

Implement:
- Docker containers
- CI/CD pipelines
- Kubernetes readiness
- Environment configs
- Production deployment scripts

Support:
- AWS
- Azure
- Google Cloud

Include:
- Logging
- Monitoring
- Health checks
- Alerting
- Backup strategies

---

# 17. TESTING

Implement:
- Unit tests
- Integration tests
- End-to-end tests
- Load testing
- Security testing

Use:
- JUnit
- Mockito
- Cypress
- Postman collections

---

# 18. DOCUMENTATION

Generate complete:
- API documentation
- Swagger/OpenAPI docs
- Architecture diagrams
- ER diagrams
- Deployment documentation
- Developer onboarding docs
- User manuals
- Admin manuals

---

# 19. UI/UX REQUIREMENTS

Design modern enterprise-grade interfaces.

Requirements:
- Responsive
- Dark mode
- Accessibility support
- Clean dashboards
- Real-time updating UI
- Map-heavy visualizations
- Interactive analytics

---

# 20. AI-POWERED FEATURES (CREATIVE ADDITIONS)

Add innovative features such as:
- AI race predictions
- Athlete performance forecasting
- Injury risk analysis
- Smart pacing suggestions
- Crowd density prediction
- AI chatbot support
- AI-generated race summaries
- Fraud detection
- Suspicious GPS activity detection

---

# IMPORTANT AUDIT TASK

FIRST:
1. Analyze the CURRENT implementation.
2. Compare every implemented feature against this specification.
3. Identify:
   - Missing components
   - Broken workflows
   - Poor architecture
   - Refactor opportunities
   - Security issues
   - Scalability risks
   - Performance issues
   - UI/UX inconsistencies
   - Technical debt

THEN:
4. Produce a detailed GAP ANALYSIS REPORT.
5. Produce a REFACTORING PLAN.
6. Produce an IMPLEMENTATION ROADMAP.
7. Then COMPLETE the missing functionality.

---

# OUTPUT REQUIREMENTS

Generate:
- Full backend implementation
- Full frontend implementation
- Full mobile implementation
- Database schema
- API endpoints
- Docker configs
- Deployment configs
- CI/CD configs
- Complete documentation
- Production-ready code
- Tests
- Architecture diagrams

Ensure all code is:
- Clean
- Modular
- Scalable
- Maintainable
- Production-ready
- Enterprise-grade
- Fully documented

Do NOT provide pseudo-code.
Provide complete working implementations.

The final platform should be capable of becoming a commercial-grade marathon and race management ecosystem.
