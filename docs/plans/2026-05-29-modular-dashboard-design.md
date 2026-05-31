# Design Doc: Modular Hybrid Dashboard

**Date:** 2026-05-29
**Status:** Approved
**Topic:** Modernizing Race Tracking UI/UX (Web & Mobile)

## 1. Objective
To create a high-performance, professional, and visually striking "Mission Control" interface for the AMS platform. The dashboard must serve a hybrid audience of **Race Organizers** (safety/utility) and **Spectators** (engagement/visuals).

## 2. Aesthetic Direction
- **Style:** "Vibrant & Block-based" / "Brutalist Precision."
- **Color Palette:**
    - Primary: `#2563EB` (Trust Blue)
    - CTA: `#F97316` (High-visibility Orange)
    - Background: `#EFF6FF` (Light) / OLED Black (Map focus).
- **Typography:**
    - **Fira Code:** Data-centric text, bib numbers, timers, distances.
    - **Fira Sans:** General UI, labels, navigation.

## 3. Spatial Architecture (Bento-Grid)

### Web Dashboard
- **Central Hero:** Immersive Map with neon-colored runner paths.
- **Left Sidebar (Data Strip):** High-density participant list/alerts.
- **Right Sidebar (Analytics Tray):** Collapsible widgets for race progress and weather.
- **Top Command Bar:** Floating glassmorphism bar for global filters and mode toggling.

### Mobile App
- **Primary View:** Full-screen interactive map.
- **Interaction:** Bottom-sheet "Drawer" for data lists and participant details.
- **HUD:** Minimalist translucent floating actions for quick-access tools (QR Scan, My Location).

## 4. Key Components

### 4.1. Aero-Panel (Participant Drill-down)
- Translucent card with grain overlay.
- Large bib number in Fira Code.
- Live pace/gap/battery metrics.
- Contextual actions based on "Hybrid Mode" (Follow vs. Message/Flag).

### 4.2. Live Runner Cards
- Status indicators (Green/Yellow/Red) based on movement speed.
- Embedded 2-minute pace sparklines.
- Geometric block layout.

### 4.3. Race Progress Widget
- Horizontal flow chart representing checkpoints.
- Density heatmap overlay on the progress line.

## 5. Interaction & Experience
- **Weighted Fly-To:** Map transitions are smooth and non-jarring.
- **Data Freshness HUD:** Pulsing blue dot for live STOMP sync; static grey for offline/cached.
- **Staggered Load:** Widgets reveal with incremental animation delays on entry.

## 6. Technical Integrity (UX)
- **Contrast:** Strictly follow 4.5:1 WCAG guidelines.
- **Touch Targets:** Minimum 44x44px for all mobile interactive elements.
- **Performance:** Reserved space for async content (Map/Widgets) to prevent layout shifts.

## 7. Success Criteria
- Unified design language across Web and Mobile.
- Seamless transition between Spectator and Organizer modes.
- Visual clarity of critical data (alerts/emergency) even in high-density views.
