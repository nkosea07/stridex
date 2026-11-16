# Race Tracker Mobile App

A cross-platform React Native mobile application built with Expo for athletes to track their race participation and progress.

## Features

### For Athletes
- **Account**: Sign up, sign in, reset a forgotten password, resend the verification email, cancel a pending account deletion
- **Race Discovery**: Browse and search available races
- **Race Registration**: Register with category, emergency contact, and custom fields; see your registration status and bib
- **Entry fee**: Pay the entry fee (optional promo code) through the provider's hosted checkout in an in-app browser; the status updates when the provider confirms
- **Live GPS Tracking**: Real-time location tracking during races, with an offline queue
- **QR Code Scanning**: Scan checkpoint self-scan codes
- **Results**: Personal results (provisional or official), finisher certificate for official results, and result queries to the officials with their answers
- **Stats**: Races, distance, medals, and personal bests from your server race history; achievements
- **Notifications and privacy**: Choose push, email, and WhatsApp channels; manage marketing consent; share a copy of your data; delete your account. Tapping a notification opens the related race, result, or followed athlete

### For Race Staff (volunteers and officials)
- **Checkpoint timing**: record bibs (works offline), see the last 20 entries, and undo your own entry within 5 minutes
- **Athlete check-in**: search by bib or name, or scan the athlete's QR code
- **Wave starts** (officials): start an unstarted wave at the current time
- **Incidents**: report an emergency from a checkpoint

### For Spectators
- **Following**: Follow public athletes and see their status, last checkpoint, and distance remaining
- **Live athlete view**: Map with the course route, the athlete's track and latest position, checkpoint pins, splits, and "last updated" time. Refreshes every 30 seconds while the screen is open and the app is in the foreground, fetching only new track points (`?since=`)

### Technical Features
- **Expo Router**: File-based navigation
- **Zustand**: State management
- **React Native Maps**: Interactive map display
- **Expo Location**: GPS tracking
- **Expo Camera**: QR code scanning
- **Expo SQLite**: Local GPS queue and active tracking session recovery
- **Expo BackgroundTask**: Opportunistic catch-up sync for queued GPS points
- **Expo Updates**: OTA update readiness with app-version runtime policy

## Project Structure

```
apps/mobile/
├── app/                    # Expo Router pages
│   ├── (auth)/            # Sign in, sign up, forgot password
│   ├── (tabs)/            # Home, races, my races, results, following, profile
│   ├── race/[id].tsx      # Race detail, registration, entry fee payment
│   ├── athlete/[raceId]/[participantId].tsx  # Spectator live view of an athlete
│   ├── tracking.tsx       # Athlete live GPS tracking
│   ├── scanner.tsx        # Athlete checkpoint self-scan
│   ├── operations.tsx     # Staff checkpoint timing, recent entries, wave starts
│   ├── check-in.tsx       # Staff athlete check-in (search or QR)
│   ├── notifications.tsx  # Notification list
│   ├── settings.tsx       # Notification channels, consent, data export
│   ├── account-deletion.tsx, legal-acceptance.tsx, privacy.tsx, terms.tsx
│   └── _layout.tsx        # Root layout, legal and app-version gates, notification routing
├── components/            # Reusable components (maps, gates, update screen)
├── lib/                   # API client, tracking service, offline queues, i18n, helpers
├── store/                 # auth, tracking, appUpdate (zustand)
├── tests/                 # Vitest suites (run through the web workspace)
├── types/
│   └── index.ts          # TypeScript types
```

## Getting Started

### Prerequisites
- Node.js 20.19+
- pnpm
- iOS Simulator (Mac) or Android Emulator
- Development build for native-module, background-location, notification, and OTA-update validation

### Installation

```bash
# Install dependencies from the monorepo root
pnpm install

# Start the development server
pnpm --filter race-tracker-mobile start
```

### Running on Device

1. **iOS Simulator**: Press `i` in the terminal
2. **Android Emulator**: Press `a` in the terminal
3. **Physical Device**: use a development build for background tracking, native module, notification, and update testing

Expo Go is useful for quick UI checks, but it is not the validation target for race-day tracking. Android remote push notifications require a development build from Expo SDK 53 onward, and native background behavior should be verified in a development build.

## Configuration

### Environment Variables

Create a `.env` file:

```env
EXPO_PUBLIC_API_URL=http://your-backend-url/api
EXPO_PUBLIC_MAPBOX_TOKEN=your-mapbox-token
```

### App version gate

The app sends `X-App-Version` (from `app.json` `expo.version`) and `X-App-Platform` with every API call and checks `GET /api/v1/public/app-config` at launch. When the backend's `MOBILE_MINIMUM_VERSION` is higher, or any call returns 426 `APP_UPDATE_REQUIRED`, the app shows a full-screen update prompt linking to the configured store. Bump `expo.version` for every store release.

### API Configuration

Update `lib/api.ts` with your backend URL:

```typescript
const API_ROOT = process.env.EXPO_PUBLIC_API_URL || 'http://localhost:8081/api';
```

## Features Deep Dive

### Live Tracking

The app tracks GPS location during races:
- Background location tracking
- Real-time pace calculation
- Distance covered tracking
- Checkpoint detection
- Data sync to backend
- Active tracking session recovery after app restart
- SQLite-backed GPS queue for offline or failed sync scenarios

### Offline Tracking Model

GPS points are written to a local SQLite queue. Each point has idempotency metadata, retry counts, last error details, and sync status. Failed network delivery does not delete or mark unsent points as synced; points remain queued for the next foreground sync interval, live background-location callback, or opportunistic background sync task.

Upload failures are classified. Network errors, timeouts, rate limits (429), and server errors (5xx) keep the points queued for retry; an expired access token is refreshed once by the API client. A 400/413/422 means the server will never accept that batch, so its points are marked rejected (`synced = 2`, error kept) and newer points continue to upload. A 403 or 404 (no athlete access, or no longer registered for the race) also stops tracking, records the reason on the session row, and shows the athlete a one-time explanation, including when the stop happened in a background task while the app was closed. Only one upload per race runs at a time. Uploaded and rejected points older than seven days are pruned from the device when no active session uses them; points still awaiting upload are never pruned.

The active race session is also stored locally. When the app starts, it attempts to recover the active race, battery-saver mode, start time, and local path before the splash screen is hidden. If recovery fails, queued GPS points are kept for later retry.

`expo-background-task` is only a catch-up worker. iOS and Android decide when it runs, so it must not be treated as immediate race timing infrastructure. Live tracking still depends on Expo Location background updates and foreground sync while the app is open.

### QR Code Scanning

- Athletes scan a checkpoint's self-scan code; the server checks the code and the athlete's location and records a verified time or a provisional claim for officials to review. Self-scans need a connection.
- Staff scan an athlete's registration QR code on the check-in screen.

### Maps Integration

Uses `react-native-maps` for:
- The uploaded course route (falls back to straight lines between checkpoints when no route is uploaded)
- Checkpoint markers
- The athlete's own position and path while tracking
- A followed athlete's track and latest position in the spectator live view

## Building for Production

This app is configured for EAS build channels in `eas.json`:

- `development`: internal development client, channel `development`
- `preview`: internal preview build, channel `preview`
- `production`: store-ready build, channel `production`

OTA update readiness is configured with `runtimeVersion.policy = appVersion`. Do not publish OTA updates that require native changes without shipping a new binary with a new app version.

### iOS (App Store)

```bash
# Build for iOS
eas build --platform ios

# Submit to App Store
eas submit --platform ios
```

### Android (Play Store)

```bash
# Build for Android
eas build --platform android

# Submit to Play Store
eas submit --platform android
```

## Connecting to Backend

Every endpoint path comes from `@race-tracker/contracts` (`packages/contracts/contract.json`); never hardcode paths in the app. `pnpm contracts:validate` checks that the app only uses endpoints the contract defines. See the backend README for the API documentation.

## Permissions Required

- **Location**: For GPS tracking during races
- **Background Location**: For continuing race tracking while the app is backgrounded
- **Camera**: For QR code scanning at checkpoints
- **Notifications**: For race updates (optional)

## Validation

Run these from the monorepo root:

```bash
pnpm --filter race-tracker-mobile exec tsc --noEmit
pnpm --filter race-tracker-mobile lint
pnpm --dir apps/mobile test
pnpm --filter race-tracker-mobile exec expo-doctor
```

## Troubleshooting

### Location not updating
- Check location permissions in device settings
- Ensure GPS is enabled
- Test in an open area for better signal

### QR Scanner not working
- Check camera permissions
- Ensure adequate lighting
- Clean camera lens

### Map not loading
- Check Mapbox token configuration
- Verify internet connection
