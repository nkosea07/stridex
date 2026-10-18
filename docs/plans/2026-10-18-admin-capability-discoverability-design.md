# Admin capability discoverability design

**Date:** 2026-10-18
**Status:** Approved for implementation in the current task; uncommitted

## Problem

The admin shell currently renders race-specific navigation only after a real race id is
present in the URL. The local runtime has no races, so an authorized user sees an empty
race list and no explanation of where the race operations are. The race list and dashboard
also render blank/partial content while requests are in flight and only report failures
through transient toasts or a single page-level error.

The fix must not make privileged actions visible or callable by every role. The existing
role boundary is intentional: administrators manage races, officials and volunteers run
assigned race operations, and athletes/spectators do not enter the admin shell.

## Design

1. Keep the existing server-side authorization and role guards as the source of truth.
2. Extract the race capability catalog used by the sidebar so the empty-state preview and
   the live race navigation cannot drift apart.
3. Add a role-aware capability preview to the race-management page when no race matches
   the current filters. It will show the operations available to the signed-in role and
   explain the next action:
   - administrators can create the first race;
   - operations staff must open or be assigned a race;
   - filtering/archiving remains visible and actionable.
4. Add stable loading skeletons, inline retryable error states, and explicit empty states
   to the race list and dashboard. Request failures must not look like a race list that
   appeared and disappeared.
5. Keep notifications and settings reachable from the existing authenticated user menu;
   expose account settings in both the operations and personal shells without weakening
   route guards in this increment.
6. Keep backend-only capabilities such as audit events, notification-template CRUD, and
   emergency alert management as separately tracked product work until their UI contracts
   and role matrix are confirmed. This task does not invent a user-management workflow or
   expose an untested privileged surface.
7. Apply the same role matrix to direct race URLs. Sidebar filtering is a discoverability
   aid; route boundaries remain an independent authorization layer.

## Role matrix

| User state | Shell | Primary capabilities |
| --- | --- | --- |
| `SUPER_ADMIN` | Operations | Platform overview, race management, organizer approvals, and every race operation |
| `RACE_ADMIN` | Operations | Assigned race management and race configuration/operations permitted by the backend |
| `OFFICIAL` | Operations | Assigned race execution, participants, checkpoints, tracking, results, media, analytics, and RFID/insights where permitted |
| `VOLUNTEER` | Operations | Assigned race execution capabilities exposed by the shared race catalog |
| `ATHLETE` | Personal | Registrations, official personal results, performance, notifications, and account settings |
| `SPECTATOR` | Personal | Followed athletes, public tracking, notifications, and account settings |
| Anonymous | Public race pages | PII-safe race hub, public results, public media, and public athlete tracking |

The frontend matrix is not a replacement for backend authorization or race assignment
checks. It prevents misleading options and direct-route confusion while the backend remains
the final enforcement boundary.

## Non-goals

- Granting all roles access to all operations.
- Creating seed races or mutating local runtime data.
- Rewriting the existing API contract or backend authorization model.
- Introducing a second navigation configuration for mobile.

## Verification

- Add browser/component assertions for administrator, official, and volunteer capability
  previews and for the no-race state.
- Verify failed race loading leaves an inline retry action.
- Run the focused web router tests, web typecheck, lint, build, and contract validation.
- Confirm the running local platform still loads `/admin/races` without a transient blank
  state for the seeded empty database.
