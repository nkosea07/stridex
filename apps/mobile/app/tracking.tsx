import { View, StyleSheet, Alert, Dimensions, ActivityIndicator, AppState } from 'react-native';
import { router, useLocalSearchParams } from 'expo-router';
import { useEffect, useMemo, useState } from 'react';
import { useTrackingStore } from '@/store/tracking';
import { api } from '@/lib/api';
import MapView, { Marker, Polyline, PROVIDER_DEFAULT } from '@/components/Map';
import type { Checkpoint, Race } from '@/types';
import { HUD } from '@/components/HUD';
import { BottomDrawer } from '@/components/BottomDrawer';
import { Ionicons } from '@expo/vector-icons';
import { useTheme } from '@/contexts/theme';

const { width } = Dimensions.get('window');

function normalizeRaceStatus(raw?: string): Race['status'] {
  const value = String(raw || '').toLowerCase();
  if (value === 'registration_open') return 'registration_open';
  if (value === 'registration_closed') return 'registration_closed';
  if (value === 'in_progress') return 'in_progress';
  if (value === 'completed') return 'completed';
  if (value === 'cancelled') return 'cancelled';
  return 'upcoming';
}

function mapCheckpointType(type?: string): Checkpoint['type'] {
  const value = String(type || '').toLowerCase();
  if (value === 'start') return 'start';
  if (value === 'finish') return 'finish';
  if (value === 'water_station') return 'water_station';
  if (value === 'medical') return 'medical';
  return 'checkpoint';
}

function formatTime(seconds: number) {
  const hrs = Math.floor(seconds / 3600);
  const mins = Math.floor((seconds % 3600) / 60);
  const secs = seconds % 60;
  return `${hrs.toString().padStart(2, '0')}:${mins.toString().padStart(2, '0')}:${secs.toString().padStart(2, '0')}`;
}

export default function TrackingScreen() {
  const { raceId } = useLocalSearchParams<{ raceId?: string }>();
  const { isTracking, liveStats, locations, startTracking, stopTracking, currentRace, batterySaver, setBatterySaver, rehydrateFromLocalDb } = useTrackingStore();
  const [elapsedTime, setElapsedTime] = useState(0);
  const [isLoading, setIsLoading] = useState(true);
  const [selectedRace, setSelectedRace] = useState<Race | null>(currentRace);
  const { colors } = useTheme();

  useEffect(() => {
    const subscription = AppState.addEventListener('change', (nextAppState) => {
      if (nextAppState === 'active' && isTracking && currentRace?.id) {
        rehydrateFromLocalDb(currentRace.id).catch(console.error);
      }
    });

    return () => {
      subscription.remove();
    };
  }, [isTracking, currentRace, rehydrateFromLocalDb]);

  useEffect(() => {
    let interval: ReturnType<typeof setInterval> | undefined;
    if (isTracking) {
      interval = setInterval(() => {
        setElapsedTime((prev) => prev + 1);
      }, 1000);
    }
    return () => {
      if (interval) {
        clearInterval(interval);
      }
    };
  }, [isTracking]);

  useEffect(() => {
    let mounted = true;

    const loadRace = async () => {
      const targetRaceId = raceId || currentRace?.id;
      if (!targetRaceId) {
        if (mounted) setIsLoading(false);
        return;
      }

      try {
        const [raceResponseRaw, checkpointsResponse] = await Promise.all([
          api.getRace(String(targetRaceId)),
          api.getRaceCheckpoints(String(targetRaceId)),
        ]);
        const raceResponse = raceResponseRaw as any;

        if (!mounted) return;

        const checkpointList = (checkpointsResponse as any[])
          .sort((a, b) => Number(a.orderIndex || 0) - Number(b.orderIndex || 0))
          .map((checkpoint) => ({
            id: String(checkpoint.id),
            name: checkpoint.name,
            distanceFromStart: Number(checkpoint.distanceFromStartKm || 0),
            location: {
              latitude: Number(checkpoint.latitude || raceResponse.startLocationLat || 0),
              longitude: Number(checkpoint.longitude || raceResponse.startLocationLng || 0),
            },
            cutoffTime: checkpoint.cutoffTime,
            type: mapCheckpointType(checkpoint.checkpointType),
          })) as Checkpoint[];

        const routeCoordinates = checkpointList
          .filter((checkpoint) => checkpoint.location.latitude !== 0 || checkpoint.location.longitude !== 0)
          .map((checkpoint) => checkpoint.location);

        const mappedRace: Race = {
          id: String(raceResponse.id),
          name: raceResponse.name,
          description: raceResponse.description,
          date: String(raceResponse.raceDate || ''),
          startTime: String(raceResponse.startTime || ''),
          location: [raceResponse.city, raceResponse.country].filter(Boolean).join(', '),
          distanceKm: Number(raceResponse.distanceKm || 0),
          distance: Number(raceResponse.distanceKm || 0),
          status: normalizeRaceStatus(raceResponse.status),
          categories: [],
          checkpoints: checkpointList,
          routeCoordinates,
          bannerImage: raceResponse.bannerImageUrl,
          organizer: '',
          registrationDeadline: raceResponse.registrationCloses,
          maxParticipants: raceResponse.maxParticipants,
          currentParticipants: raceResponse.participantCount,
        };

        setSelectedRace(mappedRace);
      } catch {
        if (mounted) {
          setSelectedRace(null);
        }
      } finally {
        if (mounted) setIsLoading(false);
      }
    };

    loadRace();

    return () => {
      mounted = false;
    };
  }, [raceId, currentRace]);

  const handleStartTracking = async () => {
    if (!selectedRace) {
      Alert.alert('No race selected', 'Choose a race from My Races before starting tracking.');
      return;
    }

    try {
      await startTracking(selectedRace);
    } catch {
      Alert.alert('Error', 'Failed to start tracking. Please enable location services.');
    }
  };

  const handleStopTracking = () => {
    Alert.alert(
      'Stop Tracking',
      'Are you sure you want to stop tracking? This will end your race session.',
      [
        { text: 'Cancel', style: 'cancel' },
        {
          text: 'Stop',
          style: 'destructive',
          onPress: () => {
            stopTracking();
            setElapsedTime(0);
            router.back();
          },
        },
      ],
    );
  };

  const routePoints = useMemo(() => selectedRace?.routeCoordinates || [], [selectedRace]);
  const checkpointList = useMemo(() => selectedRace?.checkpoints || [], [selectedRace]);

  const currentLocation = locations.length > 0
    ? locations[locations.length - 1]
    : routePoints[0] || { latitude: -34.0523, longitude: 18.4731 };

  if (isLoading) {
    return (
      <View style={[styles.center, { backgroundColor: colors.background }]}>
        <ActivityIndicator size="large" color={colors.primary} />
      </View>
    );
  }

  return (
    <View style={[styles.container, { backgroundColor: colors.background }]}>
      <MapView
        style={styles.map}
        provider={PROVIDER_DEFAULT}
        initialRegion={{
          latitude: currentLocation.latitude,
          longitude: currentLocation.longitude,
          latitudeDelta: 0.05,
          longitudeDelta: 0.05,
        }}
        showsUserLocation
        followsUserLocation={isTracking}
      >
        {routePoints.length > 1 && (
          <Polyline
            coordinates={routePoints}
            strokeColor={colors.primary}
            strokeWidth={4}
          />
        )}

        {checkpointList.map((checkpoint) => (
          <Marker
            key={checkpoint.id}
            coordinate={checkpoint.location}
            title={checkpoint.name}
            description={`${checkpoint.distanceFromStart}km`}
          >
            <View style={[styles.checkpointMarker, { backgroundColor: colors.primary }]}>
              <Ionicons name="flag" size={16} color="#FFFFFF" />
            </View>
          </Marker>
        ))}

        {locations.length > 1 && (
          <Polyline
            coordinates={locations.map((loc) => ({ latitude: loc.latitude, longitude: loc.longitude }))}
            strokeColor={colors.secondary}
            strokeWidth={3}
          />
        )}
      </MapView>

      <HUD
        onClose={() => router.back()}
        onMyLocation={() => {
          // Map centering logic would go here if we had mapRef
        }}
        onScan={() => router.push('/scanner')}
        onStop={handleStopTracking}
        isTracking={isTracking}
      />

      <BottomDrawer
        raceName={selectedRace?.name}
        elapsedTime={formatTime(liveStats?.elapsedTime || elapsedTime)}
        distance={(liveStats?.distanceCovered || 0).toFixed(2)}
        pace={(liveStats?.currentPace || 0).toFixed(2)}
        progress={(liveStats?.distanceCovered || 0) / (selectedRace?.distance || 1)}
        isTracking={isTracking}
        batterySaver={batterySaver}
        onBatterySaverChange={setBatterySaver}
        onStartTracking={handleStartTracking}
      />
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
  },
  center: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
  },
  map: {
    flex: 1,
  },
  checkpointMarker: {
    width: 32,
    height: 32,
    borderRadius: 16,
    justifyContent: 'center',
    alignItems: 'center',
    borderWidth: 3,
    borderColor: '#FFFFFF',
  },
});
