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
- **Live race**: race-wide leaderboard of athletes on course (ranked by distance covered, refreshed every 30 seconds) and search for any athlete by name or bib, with follow from either list
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
EXPO_PUBLIC_WEB_URL=https://race.example.com
```

### Maps

All maps (live tracking, followed athletes and the race map) use one component, `components/RaceMap`, with a provider chosen at build time:

| `EXPO_PUBLIC_MAP_PROVIDER` | iOS | Android | Keys |
| --- | --- | --- | --- |
| `leaflet` (default) | Leaflet in a WebView | Leaflet in a WebView | none |
| `native` | Apple Maps | Google Maps | `GOOGLE_MAPS_ANDROID_API_KEY` |
| `google` | Google Maps | Google Maps | `GOOGLE_MAPS_ANDROID_API_KEY`, `GOOGLE_MAPS_IOS_API_KEY` |

Leaflet 1.9.4 is bundled in the app (`lib/map/leafletAssets.generated.ts`, written by `pnpm --dir apps/mobile generate:leaflet` from the pinned `leaflet` package, which refuses files that differ from the published integrity hashes), so maps never depend on a CDN; only the map tiles need a connection. It draws raster tiles from `EXPO_PUBLIC_MAP_TILE_URL`, any `https` template with `{z}`, `{x}` and `{y}` (and optionally `{s}`), which must be paired with that provider's `EXPO_PUBLIC_MAP_TILE_ATTRIBUTION`; `EXPO_PUBLIC_MAP_MAX_ZOOM` (1–22) defaults to 19. The default is OpenStreetMap's public tiles, which suit development and a pilot but are not for heavy production traffic under the [OSM tile usage policy](https://operations.osmfoundation.org/policies/tiles/); switch to a dedicated free or paid tile provider (for example CARTO, MapTiler or a self-hosted server) by changing the URL and attribution.

`app.config.js` validates these settings, fails the build on a malformed value, and passes the result to the app as `extra.map`. The Google keys are read only by `app.config.js`: store them as EAS secrets. A missing key fails builds on EAS (`EAS_BUILD=true`) and only warns when the config is evaluated locally. Changing `EXPO_PUBLIC_MAP_PROVIDER` or the Google keys needs a new native build, not an over-the-air update.

### Email links in the app

`app.config.js` reads `EXPO_PUBLIC_WEB_URL` at build time. When it is an HTTPS URL, the build declares an iOS associated domain and Android verified app links for `/verify-email` and `/reset-password`, so links in verification and password-reset emails open the in-app screens (`app/verify-email.tsx`, `app/reset-password.tsx`). The web server must publish the matching `/.well-known` files (see `APP_LINKS_*` in `.env.pilot.example`); otherwise the links keep opening the web pages. The `racetracker://verify-email?token=…` scheme also works for testing.

### Public request identity

The app sends a random install id (`X-Race-Tracker-Client`, from `API_CONTRACT.headers.publicClientId`) with every request. It is not tied to the account and is used only to rate-limit public spectator endpoints per install instead of per carrier IP address.

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
- Tracking with while-in-use location only: Android keeps it running with a location foreground service, and iOS with the `location` background mode and the blue status-bar indicator, so the screen can be locked without needing "Always" or Android background-location permission
- An in-app disclosure, shown before the first location prompt, explaining what is collected and who sees it
- Athletes can hide their live position from spectators per race entry (organizers still see it)
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

`components/RaceMap` (Leaflet or `react-native-maps`, see [Maps](#maps)) draws:
- The uploaded course route (falls back to straight lines between checkpoints when no route is uploaded)
- Checkpoint markers
- The athlete's own position and path while tracking, with a re-centre button
- A followed athlete's track and latest position in the spectator live view
- The race map's recent-density layer and followed athletes

## Building for Production

This app is configured for EAS build channels in `eas.json`:

- `development`: internal development client, channel `development`
- `preview`: internal preview build, channel `preview`
- `production`: store-ready build, channel `production`

OTA update readiness is configured with `runtimeVersion.policy = appVersion`. Do not publish OTA updates that require native changes without shipping a new binary with a new app version.

`production` builds on EAS fail unless `EXPO_PUBLIC_API_URL` and `EXPO_PUBLIC_WEB_URL` are https URLs, so a store binary cannot ship pointing at `localhost`. `production` and `preview` builds use the production APNs environment.

### Store review settings

These live in `app.json` and are checked by `tests/store-readiness.test.mjs`:

- iOS: `usesNonExemptEncryption: false`, HTTPS-only App Transport Security, an app privacy manifest (required-reason APIs, no tracking, collected data types), iPhone only (`supportsTablet: false`).
- Permission prompts are translated per language in `locales/native/<lang>.json` (no double quotes or backslashes: Expo writes them into `InfoPlist.strings` unescaped).
- Android: `RECORD_AUDIO`, external storage, `SYSTEM_ALERT_WINDOW` and `ACCESS_BACKGROUND_LOCATION` are blocked; `FOREGROUND_SERVICE_LOCATION` is declared for tracking.
- The public account deletion page for Google Play is `<EXPO_PUBLIC_WEB_URL>/account-deletion`.

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

- **Location (while using the app)**: For GPS tracking during races; tracking continues with the screen locked through the location foreground service (Android) and background mode (iOS)
- **Camera**: For QR code scanning at checkpoints (no microphone)
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
