# StrideX Public Spectator Product Strategy

> Snapshot note: this strategy document was written before the Phase 1 public spectator foundation was implemented. It should be read as a dated planning artifact, not as the current shipped-status source of truth.

## Executive Summary

StrideX is currently strongest as an authenticated admin and athlete operations platform, but the deployed public demo proves the product direction should be broader: a shareable spectator web experience plus a richer logged-in mobile/app experience. The immediate gap is not raw tracking capability. The gap is product packaging.

Today:
- `apps/web` exposes live tracking only inside the admin route tree.
- `apps/mobile` serves athletes after authentication, not public spectators.
- `apps/backend` supports GPS, checkpoint, results, notifications, and RFID-ready fields, but not a full public tracking surface or a production-grade RFID ingest environment.
- The deployed demo already shows the right public behavior model: race-first, athlete-follow-first, mobile-friendly, and zero-download.

Recommended direction:
- Build a free public spectator web layer in `apps/web`.
- Keep the mobile app as the higher-value logged-in experience for athletes, fans, and families.
- Monetize primarily through organizer plans and premium race-day follower features, not aggressive consumer paywalls.
- Treat RFID/bib timing as a first-class ingest system, not just a data field on participants/checkpoints.

If executed well, StrideX can compete by offering a hybrid of:
- public race-day access similar to race tracking platforms,
- personal utility similar to fitness/live-follow products,
- and organizer/timer operations depth that many consumer products do not serve well.

## Current-State Audit

### What exists now

**Web**
- `apps/web/src/App.tsx` gates the main live-tracking experience under `/admin/races/:raceId/live`.
- `apps/web/src/pages/LiveTrackingPage.tsx` already has a high-quality operator-style live map, participant sidebar, telemetry tray, organizer/spectator mode toggle, and websocket refresh loop.
- `apps/web/src/components/dashboard/*` already contains reusable primitives for a command bar, telemetry tray, participant strip, and selected-runner detail panel.
- `apps/web/src/pages/CheckpointsPage.tsx` already exposes RFID flags, QR flags, and manual checkpoint recording workflows for admins.
- Missing: a public race hub, public athlete tracking page, public leaderboard/splits routes, shareable tracking links, public alert subscriptions, sponsor inventory, and account-upgrade surfaces.

**Mobile**
- `apps/mobile/app/(tabs)/index.tsx` is a private athlete home focused on “Your Next Race”, quick actions, and recent results.
- `apps/mobile/app/race/[id].tsx` supports race detail browsing and registration.
- `apps/mobile/store/tracking.ts` already supports local GPS tracking, background sync, offline rehydrate, pace/distance estimation, and checkpoint recording.
- Missing: public spectator flows, favorites/following, live athlete watchlists, route replay, premium alerting, and family/fan packaging.

**Backend**
- `apps/backend/README.md` documents auth, races, registration, checkpoints, live tracking, results, notifications, and websocket support.
- `apps/backend/database/schema.sql` already includes:
  - `participants.bib_number`
  - `participants.rfid_tag`
  - `checkpoints.has_rfid_reader`
  - `checkpoint_records.record_method`
  - GPS history and result splits
- Missing: public read models and public auth policy, share tokens/track links, favorites/alerts entities, sponsor placement models, and dedicated RFID reader/device ingest models.

**Docs / contracts**
- `README.md`, `DEVELOPMENT_GUIDE.md`, and `marathon_race_tracking_master_prompt.md` all position the product around GPS, checkpoints, RFID/chip timing, QR scanning, and live results.
- `docs/GAP_ANALYSIS_2026_05_31.md` already calls out missing spectator depth and that RFID integration is still mostly readiness.
- `docs/api-contract.md` shows active contract/version management across web, mobile, and backend.
- Missing: explicit product packaging docs, public spectator architecture, and a real RFID integration environment spec.

### What the deployed demo has that the current repo does not fully expose

The deployed demo at [stridex-722039414864.europe-west2.run.app](https://stridex-722039414864.europe-west2.run.app/) behaves like a public race-day product:
- public entry with no sign-in barrier,
- race clock and active-race framing,
- mobile-like live tracking presentation,
- athlete selection by bib/name-like cards,
- leaderboard view,
- splits/timing slip view,
- timing alerts view,
- obvious spectator orientation.

The current repo has many of the raw building blocks, but they are hidden inside admin/athlete surfaces instead of exposed as a public product.

### What is missing for public spectators

- Public race landing page
- Public race tracker route
- Athlete search by bib/name on a public route
- Shareable athlete-follow links
- Public leaderboard page
- Public split timeline page
- Public “latest checkpoint / predicted finish / current pace” detail page
- Limited spectator alerts without app install
- Sponsor-safe placements and event branding
- Public access controls that limit depth without making the product useless

### What is missing for athlete premium value

- Favorites/follows
- Unlimited athlete following
- Push-based race-day alerts
- Smarter split analysis and route replay
- Multi-race history and richer performance summaries
- Family/friend sharing controls
- Race-day premium utility that goes beyond “track yourself”

### What is missing for organizer/timer value

- Public race hub builder
- Announcer mode
- Timing incident queue
- Reader/device health monitoring
- RFID ingest observability
- Public sponsor and branding controls
- White-label/public race microsite configuration
- Role-specific race-day operations views for timer, announcer, volunteer, medic

### What is missing for RFID/bib tracking implementation readiness

The schema proves intent, but not an end-to-end ingest system. Missing pieces:
- RFID reader/station registry
- Device credentials and authentication model
- Raw timing event table
- Dedupe and out-of-order handling
- Reader-to-checkpoint mapping
- Import/bridge service for third-party timing systems
- Replay/simulator tooling for test environments
- Operational dashboards for ingest failures and reconciliation

## Competitive Benchmark

### Feature Comparison

| Capability | Current StrideX | Deployed Demo | RaceJoy / RunSignup | RTRT.me | Strava | Garmin LiveTrack |
| --- | --- | --- | --- | --- | --- | --- |
| Public race tracking without install | No | Yes | Yes | Yes | Limited | Share-based |
| Public live map | Partial | Yes | Yes | Yes | Limited to Beacon-style share | Yes |
| Public leaderboard | Partial | Yes | Yes | Yes | No race-specific event board | No |
| Public splits view | Partial | Yes | Yes | Yes | Limited | Limited |
| Athlete search by bib/name | Admin-oriented | Yes | Yes | Yes | No bib model | No bib model |
| Follow-specific athlete page | No | Partial | Yes | Yes | Partial | Yes |
| Spectator alerts | No | Partial | Yes | Yes | Partial | Limited |
| GPS + RFID hybrid story | Partial | Simulated | Yes | Yes | GPS-centric | GPS-centric |
| Sponsor/event branding inventory | Minimal | Minimal | Strong | Strong | Subscription-first | Minimal |
| Replay/history | Minimal | No | Partial | Partial | Strong | Minimal |
| Organizer/timer operations | Partial | No | Strong | Strong | Weak | Weak |
| Announcer/command center tooling | No | No | Partial | Strong | No | No |
| Monetization clarity | No | No | Event/B2B + add-ons | Event/B2B | Consumer subscription | Device ecosystem |

### Competitor notes

**RaceJoy / RunSignup**
- Strong on race-day tracking, progress alerts, cheers, bib lookup, event branding, and organizer controls.
- Important pattern: public utility remains strong while event organizers pay for the operational layer and advanced integrations.
- Sources: [RaceJoy Organizer](https://racejoy.com/organizer.html), [RunSignup RaceJoy](https://info.runsignup.com/products/raceday/racejoy/)

**RTRT.me**
- Strong on event command center, live leaderboards, maps, operations overlays, APIs, remote racing, and hybrid timing/event tooling.
- Important pattern: organizer/timer value is a serious revenue engine when the operations surface is good enough.
- Source: [RTRT.me](https://rtrt.me/)

**Strava**
- Strong on premium recurring consumer value: Beacon/live sharing, route/history, training context, personal analytics.
- Important pattern: subscription works when the product improves identity, progress, and community over time, not only on race day.
- Source: [Strava Subscription Features](https://support.strava.com/hc/en-us/articles/216917657-Strava-Subscription-Features)

**Garmin LiveTrack**
- Strong on simple shareable live-follow links and high-trust personal telemetry.
- Important pattern: “send a link and follow me live” is a baseline expectation now.
- Source: [Garmin LiveTrack](https://support.garmin.com/en-US/?faq=oPPijumqU23KHBCZk2wlc9)

### Biggest product gaps

- No public spectator web surface
- No productized athlete-follow/share flow
- No premium consumer packaging
- No event-branding/sponsor layer
- No operator-facing RFID ingest environment
- No differentiated organizer command center beyond core admin CRUD

### Biggest opportunities

- Convert the existing web live-tracking shell into both an operator console and public race hub
- Combine GPS + RFID + QR + manual timing into a stronger hybrid story than fitness-only apps
- Use free public web as acquisition and upgrade funnel
- Sell organizers a branded race experience instead of only internal tooling

## Product and Monetization Strategy

### Product structure

**1. Public Spectator Website**
- Target user: spectators, family, friends, casual fans, media, sponsors
- Job to be done: “Let me follow the race and find a specific athlete fast”
- Value promise: instant, no-install race-day visibility

**2. Logged-in Athlete/Fan App**
- Target user: athletes, repeat spectators, families following multiple runners
- Job to be done: “Make race day and my race history more useful to me personally”
- Value promise: personalized tracking, alerts, history, and premium race-day tools

**3. Organizer/Timer Console**
- Target user: race directors, timing teams, volunteers, announcers, operations staff
- Job to be done: “Run the event reliably, brand it professionally, and resolve timing issues fast”
- Value promise: command-and-control plus monetizable public race surfaces

### Monetization model

**Free public web**
- Public race hub
- Athlete search
- Live leaderboard
- Basic athlete page
- Limited splits and checkpoint view
- Shareable public links

**Registered free account**
- Save favorite races
- Follow a limited number of athletes
- Email alerts only
- Personalized watchlist
- Basic post-race recap

**Premium athlete/fan subscription**
- Unlimited athlete follows
- Push alerts
- Advanced split analytics
- Predictive finish and pacing insights
- Route replay
- Family/friend alert bundles
- Multi-race history and premium recap

**Organizer / timer plans**
- Charged per event or participant tier
- Public race hub branding
- Sponsor inventory controls
- Announcer mode
- Timing command center
- RFID bridge / device monitor
- Exports, APIs, white-label, support tiers

**Sponsor / branding revenue**
- Branded race hub header/footer
- Sponsored checkpoint cards
- Sponsored recap modules
- Event partner placements in non-intrusive locations

### Monetization principles

- Do not paywall basic race following.
- Do not interrupt live race use with popups.
- Put upgrade prompts at natural moments:
  - after saving favorites,
  - after subscribing to more than the free follow limit,
  - after race recap,
  - in notification settings,
  - in organizer branding/config screens.

## Recommended Feature Tiers

### A. Public Spectator Web

Must-have:
- Public race landing page
- Countdown / status badge
- Course map
- Athlete search by bib/name
- Live leaderboard
- Athlete detail page
- Latest checkpoint and last-seen state
- Split timeline
- Limited timing alerts
- Shareable tracking links
- Event branding and sponsor placements

Limitations:
- Fewer followed athletes
- No push notifications
- No advanced replay
- Limited recap/history
- Limited predictive analysis depth

### B. Registered Free Account

- Save favorite races
- Follow up to 3 athletes live
- Email alerts for start / finish / key checkpoints
- Personal race watchlist
- Basic recap
- Account-based notification preferences

### C. Premium Athlete/Fan Features

- Unlimited live follows
- Push notifications
- Predictive finish bands
- Advanced split deltas and pacing analysis
- Route replay
- “Near finish” and “near checkpoint” alerts
- Family tracking bundles
- Premium post-race recap and history
- Personal best / progression insights across races

### D. Organizer / Timer / Event Director Features

- Branded public race tracker
- Sponsor module placement
- Announcer mode
- Heatmap and congestion view
- Checkpoint health and freshness monitoring
- Timing incident queue
- RFID reader/device health
- Manual corrections with audit trail
- Volunteer and medic operations views
- Export/API/white-label controls

## RFID / Bib Tracking Integration Architecture

### Product requirement

StrideX should move from “RFID-ready” to “RFID-integrated”. That means reader devices and timing feeds become a real subsystem with observable ingest, normalization, and correction workflows.

### Recommended domain additions

Add backend models for:
- `timing_devices`
  - device id
  - vendor
  - station name
  - checkpoint id
  - status
  - auth credential / key id
  - last heartbeat
- `timing_ingest_events`
  - raw payload
  - source device
  - source timestamp
  - received timestamp
  - tag id
  - mapped participant id
  - dedupe hash
  - processing status
  - error reason
- `timing_sessions`
  - race id
  - vendor mode
  - simulation/live mode
  - ingest status

### Event flow

1. RFID reader or bridge posts raw read event
2. Backend authenticates device
3. Event stored immutably in raw ingest table
4. Processor maps reader to checkpoint and tag to participant
5. Dedupe window removes repeated scans
6. Out-of-order logic flags suspicious events
7. Valid event becomes normalized `checkpoint_record`
8. Updated participant state is broadcast to websocket consumers
9. Operator dashboards surface failures, duplicates, and manual review cases

### Hybrid timing model

StrideX should normalize all tracking into one event model:
- GPS location
- RFID checkpoint read
- QR checkpoint scan
- Manual checkpoint entry

Each event should carry:
- source type
- source confidence
- device/user source
- race id
- participant id
- checkpoint id if applicable
- event timestamp
- received timestamp
- verification state

### Operational requirements

- Device registration UI for timers/admins
- Reader-to-checkpoint mapping
- Heartbeat and stale-reader alerts
- Offline batch ingest support
- Retry-safe idempotent endpoints
- Simulation mode for dev and demos
- CSV/import bridge for external timer systems where direct reader integration is not feasible

### Environment setup requirements

Add explicit environment/docs support for:
- `RFID_BRIDGE_ENABLED`
- `RFID_BRIDGE_API_KEY`
- `RFID_BRIDGE_ALLOWED_IPS`
- `RFID_SIMULATOR_MODE`
- `TIMING_DEDUPE_WINDOW_MS`
- `TIMING_HEARTBEAT_TIMEOUT_MS`

### Documentation requirements

Update docs so README and implementation guides explicitly describe:
- bib/tag assignment workflow
- reader setup workflow
- test/simulator workflow
- public tracking + timing data flow
- reconciliation and correction workflow

## UX Direction

### Public spectator web

Should feel:
- fast,
- trustworthy,
- race-centric,
- easy to share,
- optimized for glanceable mobile use.

Information hierarchy:
1. race status and headline
2. search/follow athlete
3. map / leaderboard / splits tabs
4. athlete card
5. sponsor and event content

### Athlete app

Should feel:
- personal,
- premium,
- useful before, during, and after race day.

Information hierarchy:
1. upcoming race
2. live status / tracking controls
3. favorites and alerts
4. results / recap / progression
5. premium upgrades in settings and recap moments

### Organizer console

Should feel:
- operational,
- high-trust,
- dense but controlled,
- optimized for race-day decisions.

Information hierarchy:
1. active race health
2. participant freshness / checkpoint issues
3. timing ingest status
4. incident queue
5. public hub controls and sponsor modules

## Phased Roadmap

### Phase 1: Public Spectator Foundation

**Goal**
- Launch a shareable public race-tracking surface and create the first real acquisition funnel.

**Likely systems affected**
- `apps/web/src/App.tsx`
- new public pages under `apps/web/src/pages`
- public API endpoints in `apps/backend`
- docs and contract package

**Backend**
- Add public race summary/read endpoints
- Add public leaderboard and public athlete-tracking endpoints
- Add share-token or public access policy

**Web**
- Build public race hub
- Build public athlete tracking page
- Expose public leaderboard and split views

**Mobile**
- No major spectator build yet
- Add deep-link/open-in-app strategy for premium handoff

**Data model**
- Public share token / public race settings
- Favorites/follow seeds if needed

**Integration**
- Reuse existing websocket/live endpoints where safe

**Success metrics**
- public sessions per race
- athlete searches per session
- share-link open rate
- account-creation conversion from public users

**Rollout risks**
- access control mistakes
- overexposing admin data
- public caching/performance under race spikes

### Phase 2: Premium App Differentiation

**Goal**
- Make the mobile app clearly more valuable than the free public web.

**Likely systems affected**
- `apps/mobile/app`
- `apps/mobile/store`
- `apps/mobile/lib/api.ts`
- backend notifications/favorites/preferences

**Backend**
- favorites/follows
- alert subscriptions
- recap/history endpoints

**Web**
- upgrade prompts from public race hub
- account save/follow actions

**Mobile**
- live follows
- push alerts
- premium recap/history
- premium settings and entitlements

**Data model**
- saved athletes
- alert subscriptions
- user feature entitlements

**Success metrics**
- free-to-account conversion
- account-to-premium conversion
- retained monthly race-day users

**Rollout risks**
- premium features that feel too thin
- notification fatigue

### Phase 3: Organizer / Timer + RFID Integration Maturity

**Goal**
- Turn StrideX into an event operations platform, not only a spectator utility.

**Likely systems affected**
- backend timing models/controllers/services
- admin web pages and dashboards
- schema and migration set

**Backend**
- raw timing ingest
- device auth
- dedupe/reconciliation
- heartbeat monitoring

**Web**
- reader/station setup
- ingest monitor
- incident queue
- announcer mode

**Mobile**
- optional volunteer/mobile ops surfaces later

**Data model**
- timing devices
- ingest events
- timing sessions

**Success metrics**
- ingest reliability
- event-to-leaderboard latency
- correction turnaround time

**Rollout risks**
- vendor integration variability
- noisy RFID data
- race-day failure modes

### Phase 4: Competitive Polish and Growth Loops

**Goal**
- Add features that improve retention, sponsor value, and market differentiation.

**Likely systems affected**
- public web recap/replay
- mobile premium analytics
- sponsor CMS/config
- analytics/reporting

**Backend**
- replay/history aggregation
- sponsor placement config
- advanced analytics

**Web**
- replay pages
- richer public recaps
- sponsor analytics surfaces

**Mobile**
- deeper history/progression
- family bundle and sharing controls

**Success metrics**
- repeat event adoption
- sponsor renewal
- premium renewal

**Rollout risks**
- building too much polish before operational reliability

## Concrete Next 10 Implementation Tasks

1. Add public race routes in `apps/web/src/App.tsx` for a race hub, leaderboard, and athlete tracker.
2. Create `apps/web/src/pages/PublicRacePage.tsx` using existing dashboard/live-tracking components in a public-safe composition.
3. Create `apps/web/src/pages/PublicAthleteTrackingPage.tsx` with bib/name search, last checkpoint, pace, ETA, and limited split history.
4. Add public race tracking endpoints in `apps/backend` that return only safe spectator fields.
5. Add public shareable athlete tracking link support in backend and web.
6. Add favorites/following models and endpoints for registered users.
7. Add email alert subscriptions first, then push alerts in a second pass.
8. Design and add timing-device, ingest-event, and reader-to-checkpoint schema extensions plus migrations.
9. Build an admin timing ingest monitor page in `apps/web` using the existing operations shell.
10. Update `README.md`, `DEVELOPMENT_GUIDE.md`, `apps/backend/README.md`, `docs/api-contract.md`, and `marathon_race_tracking_master_prompt.md` so public tracking and RFID ingest are explicit first-class capabilities.

## Documentation Updates Required

Update these files:
- `/Users/nkosinxumalo/WebProjects/AMS/README.md`
  - add product surfaces: public spectator web, athlete app, organizer console
  - add RFID/bib integration environment section
- `/Users/nkosinxumalo/WebProjects/AMS/DEVELOPMENT_GUIDE.md`
  - add public tracking architecture and timing ingest architecture
- `/Users/nkosinxumalo/WebProjects/AMS/apps/backend/README.md`
  - document public tracking endpoints and timing device ingest endpoints
- `/Users/nkosinxumalo/WebProjects/AMS/docs/api-contract.md`
  - add contract expectations for public endpoints and tracking share links
- `/Users/nkosinxumalo/WebProjects/AMS/marathon_race_tracking_master_prompt.md`
  - explicitly distinguish public spectator product, premium athlete value, organizer monetization, and RFID integration maturity expectations

## Open Risks / Assumptions

- Assumption: `apps/web` should become both the public spectator surface and the organizer web surface. This is the fastest path because the live-tracking components already exist there.
- Assumption: mobile remains the premium logged-in experience, not the first public surface.
- Risk: the current API/version story is documented across `apps/backend/README.md`, `packages/contracts/contract.json`, and client code, but public rollout should first verify route consistency and environment defaults.
- Risk: the deployed demo currently shows timing inconsistencies between global race progression and per-athlete timing details. The public product should not inherit that trust problem.
- Risk: RFID integrations vary by vendor. StrideX should plan for both direct device ingest and bridge/import modes.
- Risk: sponsor monetization can damage trust if placed too aggressively in the live race path.

## Sources

- Deployed demo observation: [StrideX Demo](https://stridex-722039414864.europe-west2.run.app/)
- Official market references:
  - [RaceJoy Organizer](https://racejoy.com/organizer.html)
  - [RunSignup RaceJoy](https://info.runsignup.com/products/raceday/racejoy/)
  - [RTRT.me](https://rtrt.me/)
  - [Strava Subscription Features](https://support.strava.com/hc/en-us/articles/216917657-Strava-Subscription-Features)
  - [Garmin LiveTrack](https://support.garmin.com/en-US/?faq=oPPijumqU23KHBCZk2wlc9)
