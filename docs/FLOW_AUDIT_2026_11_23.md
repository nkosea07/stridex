# Registration-to-results flow audit (2026-11-23)

Status: all 21 findings are fixed.
- **High findings, FL-01 to FL-06:** merged in PR #66.
- **Medium findings, FL-07 to FL-12, and FL-13:** merged in PR #67.
- **Low and Info findings, FL-14 to FL-21:** on branch `fix/registration-results-low`.

Scope: the athlete journey end to end. This covers race setup, public registration (web and mobile), payment, organizer review, bib allocation, check-in, timing, race completion, result calculation, publishing and certification, and athlete-facing results. Each stage was traced through the backend services, the shared contract and both clients. Every finding below was confirmed by reading the code at the referenced line; none has been reproduced against a running stack yet.

Code paths are relative to the repository root:
- `backend/` means `apps/backend/src/main/java/com/racetracker/`
- `web/` means `apps/web/src/`
- `mobile/` means `apps/mobile/`

## Summary

| ID | Stage | Finding | Severity | Status |
| --- | --- | --- | --- | --- |
| FL-01 | Results | Published results list every registration by name, including rejected, waitlisted, pending-review and cancelled ones | High | Fixed |
| FL-02 | Results | Positions follow finish clock time, ignoring wave and chip starts, so they contradict the displayed chip times | High | Fixed |
| FL-03 | Registration | Athlete cancellation can rewrite an official result (DQ or DNF to DNS), even after certification, without audit | High | Fixed |
| FL-04 | Payment | Fee rules disagree: category, team and early-bird fees are skipped or block approval | High | Fixed |
| FL-05 | Registration | Races in `REGISTRATION_CLOSED` or `DRAFT` still accept registrations | High | Fixed |
| FL-06 | Completion | Entrants who never started stay `REGISTERED`, `CONFIRMED` or `CHECKED_IN` after completion and publish as "in progress" | High | Fixed |
| FL-07 | Check-in / timing | Unpaid entries can check in, be timed and receive official results and certificates | Medium | Fixed |
| FL-08 | Registration | Category age, gender and capacity limits are stored but never enforced | Medium | Fixed |
| FL-09 | Registration / results | Self-registration never captures gender or date of birth, so gender and age-group results don't work for app and web sign-ups | Medium | Fixed |
| FL-10 | Athlete results | "My results" is always recalculated live and can differ from the published and certified results | Medium | Fixed |
| FL-11 | Payment | A 100% promo code is a dead end: checkout refuses, and nothing marks the entry paid or waived | Medium | Fixed |
| FL-12 | Registration | The cancellation model is incomplete: no UI, a status that means "did not start", no waitlist promotion, no refund path | Medium | Fixed |
| FL-13 | Athlete UI | A disqualified athlete is told their registration was cancelled | Low | Fixed |
| FL-14 | Notifications | "Results published" notifications go to rejected, waitlisted and cancelled registrations | Low | Fixed |
| FL-15 | Bibs | Bibs are allocated at registration for waitlisted, pending and unpaid entries and never released | Low | Fixed |
| FL-16 | Web results | The Publish button shows for staff who can't publish and isn't tied to race status; failures show a generic error | Low | Fixed |
| FL-17 | Results | Tied finish times get different positions | Low | Fixed |
| FL-18 | Payment | Failed or cancelled checkouts leave the entry `PENDING` | Low | Fixed |
| FL-19 | Registration | Registration free-text fields have no size or content validation | Low | Fixed |
| FL-20 | Waitlist | No automatic waitlist promotion or notice when a place frees | Low | Fixed |
| FL-21 | Check-in | The check-in QR exists only on printed bibs; athletes can't show it from the app | Info | Fixed |

## High

### FL-01: Published results list every registration, including non-entrants

**Fixed:** the result rows and the result count now leave out `PENDING_REVIEW`, `WAITLISTED`, `REJECTED` and the new `WITHDRAWN` status. Results published before this fix keep their old snapshot until they are published again.


The result query has no status filter. `findResultRows` builds `base` from all `participants` of the race, [ParticipantRepository.java:188](../apps/backend/src/main/java/com/racetracker/repository/ParticipantRepository.java#L188). `publish` snapshots every row. The public endpoint maps every snapshot row to a name, bib and status, [ResultController.java:279](../apps/backend/src/main/java/com/racetracker/controller/ResultController.java#L279).

- **Impact:**
  - Rejected applicants, waitlisted and pending-review sign-ups, and athletes who cancelled appear by full name on the public results page. Rejected, waitlisted and pending entries show as `in_progress` (`mapStatus` default, [ResultServiceImpl.java:660](../apps/backend/src/main/java/com/racetracker/service/impl/ResultServiceImpl.java#L660)); cancelled ones show as `dns`.
  - This leaks who applied and was turned down, which is a privacy problem.
  - It also inflates the result count.
- **Fix:** restrict result rows to entrants (`CONFIRMED`, `CHECKED_IN`, `STARTED`, `FINISHED`, `DNF`, `DNS`, `DISQUALIFIED`, plus `REGISTERED` where the race doesn't require approval). Exclude `PENDING_REVIEW`, `WAITLISTED` and `REJECTED`, and athlete cancellations once FL-12 adds a real cancelled state. Apply the same filter in `countResultRows` and the snapshot.

### FL-02: Positions use finish clock time, ignoring each runner's start

**Fixed:** positions now follow elapsed time plus penalties, measured from the same start as the displayed time: chip start (including the first pass of a shared start/finish mat), then wave gun, then wave scheduled start, then race gun, then scheduled start. The finish is the final expected pass. Covered by `ResultRankingPostgresTest`, which fails against the old query. Already-published races will report "changed since publish" until they are republished.


Positions come from `row_number() OVER (... ORDER BY finish_at + penalty)`, [ParticipantRepository.java:194](../apps/backend/src/main/java/com/racetracker/repository/ParticipantRepository.java#L194). That is the wall-clock time of crossing the finish. The query computes a `start_time`, but only as `coalesce(START checkpoint, race scheduled start)`, and never uses it for ordering. It also ignores wave starts, the recorded race gun (`race.startedAt`) and shared start/finish mats.

The displayed time is calculated separately in Java, from the chip start, else the wave gun, else the race gun: `CheckpointCutoff.resolveStart`, used by `computeResult`. The web results table shows "Pos" next to "Chip time".

- **Impact:** whenever waves or start mats are used, positions disagree with the times shown.
  - **Example:** a wave-2 runner who starts 10 minutes later and runs 5 minutes faster is ranked behind slower wave-1 runners.
  - Gender, category and age-group positions, medals and qualification all inherit the wrong order.
- **Fix:** rank on elapsed time, `finish_at - start + penalty`, in SQL. Use the same start rule as `CheckpointCutoff.resolveStart`: chip start including the first pass of a shared start/finish mat, then wave gun, then race gun, then scheduled start. Choose gun or chip ranking explicitly per race. Also align the finish: SQL takes `max` of any `FINISH`-type pass, while Java requires the final expected pass (`CheckpointPasses.isFinish`).

### FL-03: Athlete cancellation can overwrite an official result

**Fixed:** athletes can withdraw only `REGISTERED`, `CONFIRMED`, `PENDING_REVIEW` or `WAITLISTED` entries, and only before the race starts. The entry becomes the new `WITHDRAWN` status (migration V61), with an audited change record. Another user's registration returns 404.


`cancelRegistration`, [ParticipantServiceImpl.java:562](../apps/backend/src/main/java/com/racetracker/service/impl/ParticipantServiceImpl.java#L562), blocks only `STARTED`, `FINISHED` and `CHECKED_IN`, then sets `DNS`. It doesn't use `ParticipantChangeControl` or `ResultLock`, and it writes no audit event.

- **Impact:** an athlete marked `DISQUALIFIED` or `DNF` can call `DELETE /registrations/{id}` after the race and become `DNS`.
  - This works even after the results are certified.
  - It leaves no audit trail and needs no reason.
  - The next publish, and the athlete's live results, show the rewritten status.
- **Fix:**
  - Allow cancellation only for pre-race states: `REGISTERED`, `CONFIRMED`, `PENDING_REVIEW` and `WAITLISTED`, while the race is not `IN_PROGRESS`, `COMPLETED` or `CANCELLED`.
  - Record an audit event.
  - Return 404 rather than 401 for someone else's registration (it currently throws `UnauthorizedException`).

### FL-04: Fee rules disagree between registration, approval and checkout

**Fixed:** the initial payment status and the approval check both use `RegistrationFees.current`, the same rule checkout uses.


Three different rules decide whether a registration must be paid for:
- **Initial payment status:** `defaultPaymentStatus` uses only `race.registrationFee`, [ParticipantServiceImpl.java:839](../apps/backend/src/main/java/com/racetracker/service/impl/ParticipantServiceImpl.java#L839).
- **Approval:** approval requires payment only when `race.registrationFee > 0`, [ParticipantServiceImpl.java:610](../apps/backend/src/main/java/com/racetracker/service/impl/ParticipantServiceImpl.java#L610).
- **Checkout:** checkout charges `RegistrationFees.current`, which covers category, team and early-bird fees.

- **Impact:**
  - **Fee only on a category or team:** with no race-level fee, the entry is created `WAIVED`. Checkout refuses with `PAYMENT_WAIVED`, so the fee is never collected and approval doesn't ask for payment.
  - **Race fee set, free category** (such as a kids' race): the entry is `UNPAID`, but checkout refuses with "does not require an online payment". Approval then demands payment, so the entry can't be approved without `force`.
- **Fix:** derive both the initial payment status and the approval check from `RegistrationFees.current(participant, race)`.

### FL-05: Closed and draft races still accept registrations

**Fixed:** registration now requires `REGISTRATION_OPEN` as well as the date window, and fails with `REGISTRATION_NOT_OPEN` otherwise.


`validateRegistrationOpen`, [ParticipantServiceImpl.java:795](../apps/backend/src/main/java/com/racetracker/service/impl/ParticipantServiceImpl.java#L795), rejects only `CANCELLED`, `COMPLETED` and `IN_PROGRESS`, then checks the open and close dates.

- **Impact:** a race the organizer moved to `REGISTRATION_CLOSED`, or one still in `DRAFT` with public registration enabled, keeps accepting entries through the API whenever the dates allow it. The apps hide the button, but the API doesn't enforce it.
- **Fix:** require `status == REGISTRATION_OPEN` in addition to the date window.

### FL-06: Completing a race leaves non-starters unfinished

**Fixed:** completion now sets `REGISTERED`, `CONFIRMED` and `CHECKED_IN` entrants to `DNS`, and `STARTED` entrants to `DNF`, each with a status reason. One audit event records the counts.


`completeRace` turns `STARTED` into `DNF`, [RaceCompletionServiceImpl.java:74](../apps/backend/src/main/java/com/racetracker/service/impl/RaceCompletionServiceImpl.java#L74). Entrants still `REGISTERED`, `CONFIRMED` or `CHECKED_IN` are left alone.

- **Impact:** after completion and publishing, those athletes appear as `in_progress` in published results indefinitely, and athletes never see a final status.
- **Fix:** on completion, set never-started entrants to `DNS` with an automatic reason, audited.

## Medium

### FL-07: Unpaid entries can race and receive official results

**Fixed:**
- **Check-in:** refuses entries that owe a fee that is neither paid nor waived (`CHECK_IN_PAYMENT_REQUIRED`). Staff resolve this by verifying or waiving the payment.
- **Publishing:** refused while a finisher still owes the fee (`RESULTS_UNPAID_FINISHERS`).
- **Imports:** imported entries now use the same fee rule.


Check-in accepts `REGISTERED` and `CONFIRMED` regardless of payment, [ParticipantServiceImpl.java:222](../apps/backend/src/main/java/com/racetracker/service/impl/ParticipantServiceImpl.java#L222). Timing treats `REGISTERED` as timeable, [CheckpointProgression.java:21](../apps/backend/src/main/java/com/racetracker/service/CheckpointProgression.java#L21). Payment is enforced only by the approval step, which most races skip.

- **Impact:** in a paid race without approval, an athlete who never paid can check in, run, finish, be ranked, and download a certificate.
- **Fix:** make check-in, or at least the official result, require `PAID` or `WAIVED` when `RegistrationFees.current > 0`. Allow a staff override with a reason. Show unpaid entries clearly at check-in.

### FL-08: Category age, gender and capacity limits aren't enforced

**Fixed:**
- **Self-registration:** enforces category gender (`CATEGORY_GENDER_INELIGIBLE`), age on race day (`CATEGORY_AGE_INELIGIBLE`) and capacity. A full category waitlists the athlete, or refuses with `CATEGORY_FULL` when the race has no waitlist.
- **Approval:** respects category capacity unless forced.


`RaceCategory` stores `minAge`, `maxAge`, `gender` and `maxParticipants`. `resolveCategory`, [ParticipantServiceImpl.java:1282](../apps/backend/src/main/java/com/racetracker/service/impl/ParticipantServiceImpl.java#L1282), checks only that the category belongs to the race, and nothing else reads those fields.

- **Impact:** a category can exceed its capacity, and athletes can enter categories they aren't eligible for.
- **Fix:** enforce category capacity, counting with the same occupying statuses as race capacity (and waitlisting when full). Enforce age and gender once FL-09 captures the data.

### FL-09: Self-registration never captures gender or date of birth

**Fixed:** registration requires gender and date of birth (web and mobile forms, with mobile validating YYYY-MM-DD) and stores them on the entry.


`User` has no gender or date-of-birth fields. `RaceRegistrationRequest` has neither, and `registerForRace` copies only name, email and phone. Only organizer-created or imported participants have them.

- **Impact:**
  - Every app or web sign-up falls into the `UNKNOWN` age group, with a null gender.
  - The gender position ranks all unknown-gender finishers together.
  - The web Men and Women tabs, [ResultsPage.tsx:103](../apps/web/src/pages/ResultsPage.tsx#L103), leave these athletes out entirely.
  - Category eligibility (FL-08) can't be checked.
- **Fix:** add gender and date of birth to the athlete profile or the registration form (contract and both clients), and store them on the participant. Treat them as personal data in the privacy notice and data export.

### FL-10: "My results" ignores the published results

**Fixed:** "My results" (list and page) now reads only published result snapshots, the same source as History and the public results.


`getMyResultsPage`, [ResultServiceImpl.java:186](../apps/backend/src/main/java/com/racetracker/service/impl/ResultServiceImpl.java#L186), always recalculates live: `resultPage(null, userId, publishedOnly=false)` never uses the snapshot. `getMyHistory`, however, reads the published rows.

- **Impact:**
  - Before publishing, athletes see provisional positions.
  - After publishing, a timing correction or penalty that hasn't been republished shows in "My results" but not in the official results or the History tab.
  - Mobile "My races" shows the live position.
- **Fix:** for races with published results, serve the athlete's persisted row. Show unpublished results only with a clear "provisional" label, or hide them.

### FL-11: A 100% promo code leaves the entry unpaid

**Fixed:** when a promo code covers the whole fee, the entry is marked `WAIVED` with reference `PROMO:<code>`, the promo use is recorded, and checkout returns status `WAIVED` without calling the payment provider. Both apps show a confirmation.


When a promo code covers the whole fee, checkout throws "no online payment is required", [PaymentServiceImpl.java:146](../apps/backend/src/main/java/com/racetracker/service/impl/PaymentServiceImpl.java#L146). Nothing then marks the entry `WAIVED` or records the promo use.

- **Impact:** the athlete stays `UNPAID`. Approval, if the race requires it, stays blocked until an organizer verifies the payment by hand.
- **Fix:** when the payable amount is 0, mark the entry `WAIVED` with the promo code as the reference, record the promo use, and return success.

### FL-12: The cancellation model is incomplete

**Fixed:**
- **Withdraw action:** athletes can withdraw from the mobile race page and from the web "Your race entries" card, after confirming. The server decides who can withdraw (`canWithdraw`).
- **Waitlist:** withdrawing an entry that held a place offers it to the next waitlisted athlete whose category has room.
- **Refunds:** withdrawing a paid entry notifies the race organizers to review a refund.
- **Registering again:** a withdrawn athlete can register again and reuses the same entry.


The API client defines `cancelRegistration`, but neither app calls it. The backend endpoint:
- sets `DNS` ("did not start"), not a cancelled or withdrawn state;
- leaves cancelled entries counted as `DNS`, which also feeds FL-01;
- never offers the freed place to the waitlist;
- starts no refund for paid entries;
- doesn't stop a cancelled entry from starting a new checkout (`DNS` isn't in the blocked list).

- **Partly done with FL-03:** the `WITHDRAWN` status exists. It is excluded from capacity and results, cannot be timed or tracked, and blocks checkout.
- **Still open:**
  - a refund review for paid entries;
  - notifying the next waitlisted athlete (FL-20);
  - letting a withdrawn athlete register again (one entry per athlete per race is still enforced);
  - a withdraw action in both apps.

## Low

### FL-13: Disqualification shown to the athlete as "cancelled"

**Fixed:** `DISQUALIFIED` now maps to `disqualified`, with labels in both apps.


`mapRegistrationStatus` maps `DISQUALIFIED` to `cancelled`, [ParticipantServiceImpl.java:1184](../apps/backend/src/main/java/com/racetracker/service/impl/ParticipantServiceImpl.java#L1184). Mobile then says "Your registration was cancelled". `REGISTERED` maps to `pending`, so a paid entry in a race without approval shows "Pending" in My races until check-in.

**Fix:** map `DISQUALIFIED` to `disqualified`, and add the label to both apps.

### FL-14: Results notifications go to non-entrants

**Fixed:** results notifications skip pending-review, waitlisted, rejected and withdrawn registrations.


`ResultNotifier.resultsPublished`, [ResultNotifier.java:40](../apps/backend/src/main/java/com/racetracker/service/ResultNotifier.java#L40), notifies every participant who has an account. That includes rejected, waitlisted and cancelled registrations.

**Fix:** notify entrants only, using the FL-01 status set.

### FL-15: Bibs allocated before a place is confirmed

**Fixed:**
- **When bibs are allocated:** only to entries that hold a place, including on approval and waitlist promotion.
- **When they are released:** on rejection, waitlisting and withdrawal.
- **Category prefixes:** a category prefix that differs from the race prefix has its own sequence, continuing from the highest bib already issued with that prefix.


`ParticipantIdentityService.assign`, [ParticipantIdentityService.java:22](../apps/backend/src/main/java/com/racetracker/service/impl/ParticipantIdentityService.java#L22), allocates a bib at registration for every status. Rejected and cancelled entries keep theirs. Category prefixes share one race-wide sequence.

**Fix:** allocate bibs on confirmation (or at check-in for races without approval), and release them on rejection or cancellation.

### FL-16: The Publish button ignores permissions and race state

**Fixed:** the Publish button shows only to race managers once the race is `COMPLETED`, and a failure caused by unpaid finishers shows its own message.


The web Results page shows Publish to everyone who can open it, [ResultsPage.tsx:228](../apps/web/src/pages/ResultsPage.tsx#L228). That includes volunteers and officials, who get a 403. The button also shows before the race is `COMPLETED`. Every failure shows a generic "failed to publish" message, hiding the backend reason, for example "Race must be completed before results can be published".

**Fix:** show the button only for managers on completed races, and display the API's error.

### FL-17: Tied times get different positions

**Fixed:** positions use standard competition ranking (`rank()`) over elapsed time truncated to whole seconds, so equal times share a position and the next runner skips a place. Finishers with no known start rank after the timed finishers, ordered by finish time. Covered by `ResultRankingPostgresTest`.


The ranking uses `row_number()`, so finishers with identical times get different positions, ordered by participant ID.

**Fix:** use `rank()`, or a documented tie-break such as chip time and then gun time.

### FL-18: A failed checkout leaves the entry `PENDING`

**Fixed:** a failed or cancelled checkout returns the entry to `UNPAID`, unless another checkout is still open.


Checkout sets the participant to `PENDING`, [PaymentServiceImpl.java:177](../apps/backend/src/main/java/com/racetracker/service/impl/PaymentServiceImpl.java#L177). `FAILED` and `CANCELLED` callbacks update only the payment record, so organizers see "pending" for entries whose payment failed. The athlete can still retry.

**Fix:** go back to `UNPAID` when the only open payment fails or is cancelled.

### FL-19: Registration fields aren't validated

**Fixed:** the emergency contact's name and phone are required, and every free-text field has a size limit matching its database column, so overlong values return a validation error instead of a server error.


`RaceRegistrationRequest` requires `emergencyContact`, but not its name or phone. Medical, club, team and shirt fields have no `@Size` limits.

**Fix:** add Bean Validation limits, and require at least the emergency contact's name and phone.

### FL-20: No waitlist promotion

**Fixed:** a shared `RegistrationCapacity` component offers every freed place to the waitlist, in waitlist order and skipping athletes whose category is still full. It runs after a withdrawal, after a rejection or waitlisting of an entry that held a place, and when an organizer raises the race or category limit. It stops once the race has started. Promoted athletes are notified and get a bib when they hold a place.

When a place frees, through rejection, cancellation or a capacity increase, nothing offers it to the waitlist or tells the next athlete. Organizers have to approve waitlisted entries by hand.

**Fix:** promote, or at least notify, the lowest waitlist position. Pair this with FL-12.

## Info

### FL-21: The check-in QR exists only on printed bibs

**Fixed:**
- **New endpoint:** `GET /participants/my-registrations/:participantId/check-in-qr` (owner only) returns the code for an entry that can be checked in.
- **In the apps:** both show it, on the mobile race page and in the web "Your race entries" card.
- **In email:** the registration-received and confirmed emails embed it.


The `AMS-PARTICIPANT-<id>` QR that QR check-in expects is printed only on bib PDFs (`BibServiceImpl`). Athletes can't show it in the app, and bib pickup usually happens at check-in. Staff can still search by name or bib.

**Fix:** show the check-in QR on the athlete's registration screen in the app, and in the confirmation email.

## Verified as consistent

- **Voided timing reads:** excluded everywhere. The entity has `@SQLRestriction("voided_at is null")`, and the native queries filter `voided_at`.
- **Timing guards:** every timing path (RFID, staff, import, self-scan) refuses non-entrants and refuses changes after certification (`ResultLock`).
- **Organizer status changes:** audited, require a reason for official decisions, and are locked after certification (`ParticipantChangeControl`).
- **Publishing and certification:**
  - Publishing requires `COMPLETED`.
  - Certifying requires a matching snapshot, no timing changes since publish, and no open result queries.
  - Reopening is audited with a reason.
- **Public results access:** gated on publication, public visibility and the per-client rate limit. The PDF certificate needs a signed token.
- **Payment callbacks:** idempotent, ignore success after a refund, and record promo use only on success.
- **Endpoints:** all endpoints used in this flow come from `@race-tracker/contracts` in both apps.

## Suggested fix order

1. **FL-01, FL-03 and FL-05:** small backend changes that close a privacy leak and two integrity holes.
2. **FL-04, FL-11 and FL-07:** one fee rule everywhere, so entries are charged, waived and approved consistently.
3. **FL-02 and FL-17:** rank on elapsed time with a defined tie rule. This needs a migration-free query change and regression tests with waves and start mats.
4. **FL-06 and FL-10:** final statuses at completion, and athlete results that match the published version.
5. **FL-09 and FL-08:** collect gender and date of birth (a contract change and both apps), then enforce category eligibility.
6. **The rest:** FL-12 with FL-20 (cancellation model and waitlist), then the Low items.
