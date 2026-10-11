# Race Tracker Mobile App

A cross-platform React Native mobile application built with Expo for athletes to track their race participation and progress.

## Features

### For Athletes
- **Race Discovery**: Browse and search available races
- **Race Registration**: Register for races with category selection
- **Live GPS Tracking**: Real-time location tracking during races
- **QR Code Scanning**: Scan checkpoints with QR codes
- **Results & Stats**: View personal race results and statistics
- **Achievements**: Earn badges for milestones

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
│   ├── (auth)/            # Authentication screens
│   │   ├── login.tsx
│   │   └── register.tsx
│   ├── (tabs)/            # Main tab navigation
│   │   ├── index.tsx      # Home
│   │   ├── races.tsx      # Browse races
│   │   ├── my-races.tsx   # User's registered races
│   │   ├── results.tsx    # Results & stats
│   │   └── profile.tsx    # User profile
│   ├── race/
│   │   └── [id].tsx       # Race detail page
│   ├── tracking.tsx       # Live tracking screen
│   ├── scanner.tsx        # QR code scanner
│   └── _layout.tsx        # Root layout
├── components/            # Reusable components
├── lib/
│   └── api.ts            # API client
├── store/
│   ├── auth.ts           # Auth state management
│   └── tracking.ts       # Tracking state management
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

The active race session is also stored locally. When the app starts, it attempts to recover the active race, battery-saver mode, start time, and local path before the splash screen is hidden. If recovery fails, queued GPS points are kept for later retry.

`expo-background-task` is only a catch-up worker. iOS and Android decide when it runs, so it must not be treated as immediate race timing infrastructure. Live tracking still depends on Expo Location background updates and foreground sync while the app is open.

### QR Code Scanning

Athletes can scan QR codes at checkpoints:
- Camera-based scanning
- Instant checkpoint recording
- Offline support with sync

### Maps Integration

Uses `react-native-maps` for:
- Race route visualization
- Checkpoint markers
- Current location display
- Tracked path overlay

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

The app expects these API endpoints from your Spring Boot backend:

- `POST /api/v1/auth/login` - User authentication
- `POST /api/v1/auth/register` - User registration
- `GET /api/v1/races` - List races
- `GET /api/v1/races/:id` - Race details
- `POST /api/v1/races/:id/participants/register` - Register for race
- `POST /api/v1/tracking/:raceId/location` - Send GPS location
- `POST /api/v1/tracking/:raceId/checkpoint` - Record checkpoint
- `GET /api/v1/results/my` - User's results

See the backend README for full API documentation.

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
