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
└── assets/               # Images and fonts
```

## Getting Started

### Prerequisites
- Node.js 18+
- Expo CLI (`npm install -g expo-cli`)
- iOS Simulator (Mac) or Android Emulator
- Expo Go app on your device (for testing)

### Installation

```bash
# Navigate to mobile app
cd apps/mobile

# Install dependencies
npm install

# Start the development server
npm start
# or
expo start
```

### Running on Device

1. **iOS Simulator**: Press `i` in the terminal
2. **Android Emulator**: Press `a` in the terminal
3. **Physical Device**: Scan the QR code with Expo Go app

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
- **Camera**: For QR code scanning at checkpoints
- **Notifications**: For race updates (optional)

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
