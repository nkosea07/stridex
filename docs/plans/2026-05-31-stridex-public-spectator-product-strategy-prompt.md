# StrideX Public Spectator Product Strategy Prompt

Use this prompt when you want an agent to review the current StrideX codebase, compare it against the deployed demo and market leaders, and define a commercially credible product roadmap.

## Prompt

```text
You are acting as a senior product strategist, race-tech domain expert, UX architect, monetization strategist, backend architect, frontend architect, mobile architect, and systems integration engineer.

Your job is to review the current StrideX codebase and define the next product version so it becomes a competitive race-tracking business, not just a technical demo.

Primary goal:
Design a dual-surface StrideX platform that serves both:
1. Public spectators who do not want to download the app but still want to follow races and track specific athletes on the web
2. Registered app users who create accounts, download the app, and receive richer premium features and benefits

This product must create real user value and also support monetization without overwhelming users or making the free experience useless.

Context to inspect first:
- Repo root: /Users/nkosinxumalo/WebProjects/AMS
- Deployed reference app: https://stridex-722039414864.europe-west2.run.app/

Required local files to inspect before making recommendations:
- /Users/nkosinxumalo/WebProjects/AMS/README.md
- /Users/nkosinxumalo/WebProjects/AMS/DEVELOPMENT_GUIDE.md
- /Users/nkosinxumalo/WebProjects/AMS/marathon_race_tracking_master_prompt.md
- /Users/nkosinxumalo/WebProjects/AMS/docs/GAP_ANALYSIS_2026_05_31.md
- /Users/nkosinxumalo/WebProjects/AMS/apps/backend/README.md
- /Users/nkosinxumalo/WebProjects/AMS/apps/web/src/App.tsx
- /Users/nkosinxumalo/WebProjects/AMS/apps/web/src/pages/LiveTrackingPage.tsx
- /Users/nkosinxumalo/WebProjects/AMS/apps/mobile/README.md
- /Users/nkosinxumalo/WebProjects/AMS/apps/mobile/app
- /Users/nkosinxumalo/WebProjects/AMS/apps/web/src/components/dashboard
- /Users/nkosinxumalo/WebProjects/AMS/apps/backend/database/schema.sql

Critical context you must recognize from the code/docs:
- The current web live tracking experience is admin-oriented and route-gated, while the deployed demo behaves more like a public spectator product
- README and master prompt already position the platform around GPS tracking, checkpoint timing, RFID/chip timing, QR scanning, real-time results, and race operations
- Existing docs indicate RFID integration is mostly “readiness” and not yet a complete production integration environment
- The solution must explicitly mention bib tracking / RFID timing integration setup in the architecture, plan, and docs recommendations

External benchmark requirement:
Research how comparable products handle public tracking, premium features, athlete value, spectator value, organizer value, sponsorship, and monetization.
Use official/current sources where possible, especially:
- RaceJoy / RunSignup
- RTRT.me
- Strava
- Garmin LiveTrack
Also identify any other relevant race tracking or endurance event products if they materially improve the benchmark.

What to investigate in competitors:
- Public spectator tracking flows
- Athlete tracking and sharing flows
- Live leaderboard and split experiences
- Push/email/SMS/progress alerts
- GPS tracking vs bib/RFID timing vs hybrid predictive tracking
- Sponsor and brand placements
- Safety features
- Replay/history features
- Organizer dashboards / command center / announcer tools
- Free vs premium packaging
- Per-event, per-athlete, per-spectator, and B2B monetization patterns

Business/product direction:
We want BOTH a strong public web experience and a stronger logged-in app experience.

The public web experience should be useful, polished, and easy to share, but intentionally limited so it drives upgrades and account creation.

The app experience should clearly feel more valuable than the public website.

You must recommend a packaging model that is commercially credible and not annoying.

Recommended packaging model to evaluate:
- Public spectator web: free
- Registered free account: limited enhanced features
- Premium athlete/fan subscription: richer personal and live-follow features
- Organizer / timer / event director plans: B2B monetization
- Sponsor/white-label/event branding inventory: secondary revenue layer

Non-negotiable product goals:
- A spectator should be able to follow a race from the web without downloading the app
- A spectator should be able to search and track a specific athlete by name or bib
- A race should have a public-facing shareable experience
- The app should still offer noticeably better value than the public site
- The system must support bib/RFID timing integration alongside GPS
- The platform must feel competitive with the race-tech market, not just functional

You must identify what the current StrideX version is missing compared to:
1. The deployed demo
2. The current repo’s intended architecture
3. Comparable products in the market

Focus areas for the new product design:
1. Public spectator web
2. Mobile premium experience
3. Organizer/timer operations
4. RFID/bib tracking integration environment
5. Monetization and packaging
6. Competitive differentiation
7. Documentation and implementation readiness

Expected outputs:

1. Current-State Audit
Produce a concise but concrete audit of:
- What exists now in web, mobile, backend, and docs
- What the deployed demo has that the current repo does not fully expose
- What is missing for public spectators
- What is missing for athlete premium value
- What is missing for organizer/timer value
- What is missing for RFID/bib tracking implementation readiness

2. Competitive Benchmark
Create a feature comparison table across:
- Current StrideX
- Deployed demo
- RaceJoy / RunSignup
- RTRT.me
- Strava
- Garmin LiveTrack
Include:
- public tracking
- live map
- leaderboard
- splits
- alerts
- hybrid GPS + RFID support
- sponsor inventory
- replay/history
- safety features
- announcer/operations tooling
- monetization model
Then summarize the biggest product gaps and the biggest opportunities.

3. Product Strategy
Define a clear product structure with these surfaces:
- Public spectator website
- Logged-in athlete/fan app
- Organizer/timer console
For each surface, specify:
- target user
- core jobs to be done
- free features
- premium features
- upgrade triggers
- retention loops

4. Monetization Strategy
Recommend a monetization model that balances growth and revenue.
Include:
- what should remain free
- what should require account creation
- what should be premium subscription
- what should be sold per event to organizers
- how sponsor inventory should work
- how to avoid making the product feel greedy or cluttered
You must recommend practical, low-friction monetization patterns.
Avoid dark patterns and avoid overwhelming the user with popups or hard paywalls.

5. Feature Set Proposal
Define the recommended feature set for each tier:

A. Public Spectator Web
Examples to evaluate:
- race landing page
- countdown
- public course map
- public leaderboard
- athlete search by name/bib
- athlete detail page
- latest checkpoint
- split timeline
- limited timing alerts
- shareable tracking links
- sponsor placements
- race updates/news

B. Registered Free Account
Examples to evaluate:
- saved races
- follow favorite athletes
- limited alert subscriptions
- personalized race watchlist
- post-race recap
- email notifications

C. Premium Athlete/Fan Features
Examples to evaluate:
- unlimited athlete follows
- predictive finish estimates
- route replay
- advanced split analytics
- push alerts
- proximity / “near me” alerts
- live safety/status features
- richer history and stats
- family/friend sharing controls
- exclusive race-day tools that justify subscription

D. Organizer / Timer / Event Director Features
Examples to evaluate:
- branded public race tracker
- sponsor slots
- event command center
- announcer mode
- heatmaps
- timing incident management
- volunteer operations
- checkpoint health monitoring
- manual override and audit logs
- RFID reader health / ingest monitor
- data export / APIs
- white-label capabilities

6. RFID / Bib Tracking Integration Architecture
You must explicitly define how StrideX should support real bib timing integration, not just “RFID-ready” language.

Design the environment for:
- bib/tag assignment
- RFID tag identity mapping
- reader/station registration
- checkpoint-to-device mapping
- raw timing ingest endpoint(s)
- device authentication
- duplicate read handling
- out-of-order event handling
- buffering and retry
- offline ingestion and later sync
- event normalization into checkpoint records
- hybrid GPS + RFID + QR + manual event model
- auditability and correction workflows
- real-time broadcasts to web/mobile
- simulator mode for development
- environment variables and local/dev setup
- docs updates in README and implementation guides

7. UX Direction
Recommend the UX model for:
- public spectator web
- athlete app
- organizer console
Call out what should feel simple, premium, operational, or high-trust.
Avoid generic UI advice. Be specific about information hierarchy and race-day behavior.

8. Competitive Recommendations
Answer:
- What are we missing compared to similar apps?
- What can we do better than them?
- What can become our unique differentiator?
- What should we not copy because it adds complexity without enough value?

9. Phased Roadmap
Create a phased implementation roadmap:
- Phase 1: highest-value public spectator + monetization foundation
- Phase 2: premium app differentiation
- Phase 3: organizer/timer and RFID integration maturity
- Phase 4: competitive polish / advanced analytics / growth loops

For each phase include:
- product goal
- files/systems likely affected
- backend changes
- web changes
- mobile changes
- data model changes
- integration changes
- metrics of success
- rollout risks

10. Deliverables Format
Return your final answer in this order:
1. Executive summary
2. Current-state audit
3. Competitive benchmark
4. Product and monetization strategy
5. Recommended feature tiers
6. RFID/bib integration architecture
7. Phased roadmap
8. Concrete next 10 implementation tasks
9. Documentation updates required
10. Open risks / assumptions

Important constraints:
- Do not redesign unrelated parts of the app
- Ground recommendations in the actual repo structure
- Preserve useful existing architecture where possible
- Make the public experience strong but intentionally limited
- Make premium value real and easy to understand
- Keep monetization subtle, credible, and business-oriented
- Treat RFID/bib tracking as a first-class integration concern
- Explicitly mention required README, guide, and prompt updates
- If a recommendation depends on assumptions, label it clearly
- Use exact file paths when referencing current code
- Cite the external official sources you used
```

## Benchmark Sources

- [RaceJoy Organizer](https://racejoy.com/organizer.html)
- [RunSignup RaceJoy](https://info.runsignup.com/products/raceday/racejoy/)
- [RTRT.me](https://rtrt.me/)
- [Strava Subscription Features](https://support.strava.com/hc/en-us/articles/216917657-Strava-Subscription-Features)
- [Garmin LiveTrack](https://support.garmin.com/en-US/?faq=oPPijumqU23KHBCZk2wlc9)
