# Modular Hybrid Dashboard Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement a professional, hybrid "Mission Control" dashboard for race tracking on Web and Mobile, featuring a modular bento-grid layout, high-density data widgets, and a dedicated Spectator/Organizer toggle.

**Architecture:** A modular UI using React (Web) and React Native (Mobile) where the Map is the central hero, supported by contextual sidebars (Web) and drawers (Mobile). Uses STOMP for real-time updates and Fira Code for technical data visualization.

**Tech Stack:** React, TailwindCSS, Lucide Icons, Mapbox (Web), React Native, Expo, SQLite (Mobile).

---

### Task 1: Global Style & Typography Foundation

**Files:**
- Modify: `apps/web/src/index.css`
- Modify: `apps/mobile/app/_layout.tsx`
- Modify: `apps/web/tailwind.config.ts` (or `tailwind.config.js`)

- [x] **Step 1: Add Fira Sans and Fira Code fonts to Web**
    - Import from Google Fonts in `index.css`.
- [x] **Step 2: Add Fira Sans and Fira Code fonts to Mobile**
    - Load using `expo-font` in `_layout.tsx`.
- [x] **Step 3: Define "Vibrant & Block-based" color palette**
    - Update `index.css` with Primary (`#2563EB`), CTA (`#F97316`), Background (`#EFF6FF`).
- [x] **Step 4: Update Tailwind configuration**
    - Add colors and font families to `tailwind.config.js`.
- [x] **Step 5: Commit**
```bash
git add .
git commit -m "style: add Fira fonts and primary design system colors"
```

### Task 2: Web - Modular Layout Shell

**Files:**
- Create: `apps/web/src/components/dashboard/DashboardLayout.tsx`
- Create: `apps/web/src/components/dashboard/CommandBar.tsx`
- Modify: `apps/web/src/pages/LiveTrackingPage.tsx`

- [x] **Step 1: Implement the Bento-grid base layout with Grid/Flexbox**
- [x] **Step 2: Build the frosted-glass CommandBar with Mode Toggle state**
- [x] **Step 3: Integrate the new layout into LiveTrackingPage**
- [x] **Step 4: Commit**
```bash
git add .
git commit -m "feat(web): implement modular bento-grid layout shell"
```

### Task 3: Web - Data Strip & Analytics Widgets

**Files:**
- Create: `apps/web/src/components/dashboard/Sidebar.tsx`
- Create: `apps/web/src/components/dashboard/RunnerCard.tsx`
- Create: `apps/web/src/components/dashboard/AnalyticsTray.tsx`

- [x] **Step 1: Build the high-density RunnerCard with status bars and sparklines**
- [x] **Step 2: Implement the Sidebar with Spectator/Organizer conditional views**
- [x] **Step 3: Build the AnalyticsTray with the Race Progress Funnel**
- [x] **Step 4: Commit**
```bash
git add .
git commit -m "feat(web): add sidebar and analytics widgets"
```

### Task 4: Web - Map & Aero-Panel Integration

**Files:**
- Create: `apps/web/src/components/dashboard/AeroPanel.tsx`
- Modify: `apps/web/src/pages/LiveTrackingPage.tsx`

- [x] **Step 1: Refactor Map interaction to use "weighted fly-to" transitions**
- [x] **Step 2: Build the translucent Aero-Panel for participant drill-down**
- [x] **Step 3: Implement the "Data Freshness" pulsating sync dot**
- [x] **Step 4: Commit**
```bash
git add .
git commit -m "feat(web): integrate map interactions and aero-panel"
```

### Task 5: Mobile - Map HUD & Bottom Drawer

**Files:**
- Create: `apps/mobile/components/BottomDrawer.tsx`
- Create: `apps/mobile/components/HUD.tsx`
- Modify: `apps/mobile/app/tracking.tsx`

- [x] **Step 1: Build the floating HUD with translucent action buttons**
- [x] **Step 2: Implement the Bottom Drawer for participant lists**
- [x] **Step 3: Update tracking.tsx to use the new "Mission Control" aesthetic**
- [x] **Step 4: Commit**
```bash
git add .
git commit -m "feat(mobile): add map HUD and bottom drawer"
```
