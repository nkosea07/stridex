import { useEffect, useRef } from 'react';
import { StyleSheet, Text, View } from 'react-native';
import MapView, { Circle, Marker, Polyline, PROVIDER_DEFAULT, PROVIDER_GOOGLE } from 'react-native-maps';
import { Ionicons } from '@expo/vector-icons';
import { mapConfig } from '@/lib/map/config';
import { NO_CIRCLES, NO_LINES, NO_PINS, type RaceMapProps } from '@/lib/map/types';
import type { Coordinate } from '@/lib/routeGeometry';

const RECENTER_DELTA = 0.01;

export function NativeRaceMap({
  initialRegion,
  lines = NO_LINES,
  pins = NO_PINS,
  circles = NO_CIRCLES,
  userLocation = null,
  showsUserLocation,
  followUser = false,
  recenterKey = 0,
  onPinPress,
  accessibilityLabel,
  theme,
  height,
  fill,
}: RaceMapProps) {
  const map = useRef<MapView>(null);
  const deviceLocation = useRef<Coordinate | null>(null);
  const followPaused = useRef(false);
  const lastRecenter = useRef(recenterKey);

  useEffect(() => {
    if (!followUser || !userLocation || followPaused.current) return;
    map.current?.animateCamera({ center: userLocation }, { duration: 400 });
  }, [followUser, userLocation]);

  useEffect(() => {
    if (recenterKey === lastRecenter.current) return;
    lastRecenter.current = recenterKey;
    followPaused.current = false;
    const target = userLocation ?? deviceLocation.current;
    if (target) {
      map.current?.animateToRegion({ ...target, latitudeDelta: RECENTER_DELTA, longitudeDelta: RECENTER_DELTA }, 400);
    }
  }, [recenterKey, userLocation]);

  return (
    <MapView
      ref={map}
      style={fill ? styles.fill : [styles.map, { height: height ?? 320 }]}
      provider={mapConfig.provider === 'google' ? PROVIDER_GOOGLE : PROVIDER_DEFAULT}
      initialRegion={initialRegion}
      showsUserLocation={showsUserLocation ?? Boolean(userLocation)}
      onUserLocationChange={(event) => {
        const coordinate = event.nativeEvent.coordinate;
        if (coordinate) deviceLocation.current = { latitude: coordinate.latitude, longitude: coordinate.longitude };
      }}
      onPanDrag={() => { followPaused.current = true; }}
      accessibilityLabel={accessibilityLabel}
    >
      {circles.map((circle) => (
        <Circle key={circle.id} center={circle.center} radius={circle.radius} fillColor={circle.fillColor}
          strokeColor="transparent" strokeWidth={0} />
      ))}
      {lines.map((line) => line.coordinates.length > 1 && (
        <Polyline key={line.id} coordinates={line.coordinates} strokeColor={line.color} strokeWidth={line.width} />
      ))}
      {pins.map((pin) => (
        <Marker key={pin.id} coordinate={pin.coordinate} title={pin.title} description={pin.description}
          onPress={() => { if (!pin.title && !pin.description) onPinPress?.(pin.id); }}
          onCalloutPress={() => onPinPress?.(pin.id)}>
          {pin.kind === 'athlete' ? (
            <View style={[styles.athletePin, { backgroundColor: theme.primary }]}>
              <Text style={styles.athletePinText}>{pin.label || '•'}</Text>
            </View>
          ) : (
            <View style={[styles.checkpointPin, { backgroundColor: theme.surface, borderColor: theme.primary }]}>
              <Ionicons name="flag" size={12} color={theme.primary} />
            </View>
          )}
        </Marker>
      ))}
    </MapView>
  );
}

const styles = StyleSheet.create({
  map: { width: '100%' },
  fill: { flex: 1 },
  checkpointPin: { width: 24, height: 24, borderRadius: 12, borderWidth: 2, alignItems: 'center', justifyContent: 'center' },
  athletePin: { minWidth: 34, height: 34, borderRadius: 17, paddingHorizontal: 6, alignItems: 'center', justifyContent: 'center', borderWidth: 2, borderColor: '#FFFFFF' },
  athletePinText: { color: '#FFFFFF', fontSize: 12, fontWeight: '700' },
});
