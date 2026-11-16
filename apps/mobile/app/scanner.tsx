import { View, Text, StyleSheet, TouchableOpacity, Alert, Dimensions } from 'react-native';
import { router } from 'expo-router';
import { Ionicons } from '@expo/vector-icons';
import { useState, useEffect } from 'react';
import { useTranslation } from 'react-i18next';
import { CameraView, useCameraPermissions } from 'expo-camera';
import * as Location from 'expo-location';
import { useTrackingStore } from '@/store/tracking';
import { CapabilityGate } from '@/components/CapabilityGate';
import { parseSelfScanCode, selfScanErrorKey } from '@/lib/selfScan';
import type { GpsLocation } from '@/types';

const FIX_TIMEOUT_MS = 6000;

async function currentFix(): Promise<GpsLocation | undefined> {
  const toGps = (position: Location.LocationObject): GpsLocation => ({
    latitude: position.coords.latitude,
    longitude: position.coords.longitude,
    altitude: position.coords.altitude ?? undefined,
    accuracy: position.coords.accuracy ?? undefined,
    speed: position.coords.speed ?? undefined,
    heading: position.coords.heading ?? undefined,
    timestamp: new Date(position.timestamp).toISOString(),
  });
  try {
    const fresh = await Promise.race([
      Location.getCurrentPositionAsync({ accuracy: Location.Accuracy.High }),
      new Promise<null>((resolve) => setTimeout(() => resolve(null), FIX_TIMEOUT_MS)),
    ]);
    if (fresh) return toGps(fresh);
    const lastKnown = await Location.getLastKnownPositionAsync({ maxAge: 60_000, requiredAccuracy: 100 });
    return lastKnown ? toGps(lastKnown) : undefined;
  } catch {
    return undefined;
  }
}

const { width } = Dimensions.get('window');
const SCANNER_SIZE = width * 0.7;

export default function ScannerScreen() {
  return (
    <CapabilityGate capability="ATHLETE">
      <ScannerScreenContent />
    </CapabilityGate>
  );
}

function ScannerScreenContent() {
  const [permission, requestPermission] = useCameraPermissions();
  const [scanned, setScanned] = useState(false);
  const [flashOn, setFlashOn] = useState(false);
  const [locating, setLocating] = useState(false);
  const { recordCheckpoint, currentRace } = useTrackingStore();
  const { t } = useTranslation();

  useEffect(() => {
    if (!permission?.granted) {
      requestPermission();
    }
  }, [permission, requestPermission]);

  const handleBarCodeScanned = async ({ data }: { type: string; data: string }) => {
    if (scanned) return;
    setScanned(true);

    const parsed = parseSelfScanCode(data);
    if (parsed.kind !== 'ok') {
      Alert.alert(t('mobile:scanner.invalidTitle'),
        t(parsed.kind === 'outdated' ? 'mobile:scanner.outdatedCode' : 'mobile:scanner.unreadable'),
        [{ text: t('mobile:scanner.tryAgain'), onPress: () => setScanned(false) }]);
      return;
    }
    const { checkpointId, raceId, token } = parsed.code;
    if (!currentRace || currentRace.id !== raceId) {
      Alert.alert(t('mobile:scanner.invalidTitle'), t('mobile:scanner.wrongRace'),
        [{ text: t('mobile:scanner.tryAgain'), onPress: () => setScanned(false) }]);
      return;
    }

    setLocating(true);
    try {
      const location = await currentFix();
      const outcome = await recordCheckpoint({
        id: `${Date.now()}`,
        checkpointId,
        timestamp: new Date().toISOString(),
        method: 'qr_code',
      }, token, location);
      const verified = outcome.status === 'VERIFIED';
      Alert.alert(
        t(verified ? 'mobile:scanner.recordedTitle' : 'mobile:scanner.provisionalTitle'),
        t(verified ? 'mobile:scanner.recordedBody' : 'mobile:scanner.provisionalBody'),
        [{ text: t('mobile:scanner.ok'), onPress: () => router.back() }],
      );
    } catch (error) {
      Alert.alert(t('mobile:scanner.failedTitle'), t(selfScanErrorKey(error)),
        [{ text: t('mobile:scanner.tryAgain'), onPress: () => setScanned(false) }]);
    } finally {
      setLocating(false);
    }
  };

  if (!permission) {
    return (
      <View style={styles.container}>
        <Text style={styles.loadingText}>{t('mobile:scanner.requestingPermission')}</Text>
      </View>
    );
  }

  if (!permission.granted) {
    return (
      <View style={styles.container}>
        <View style={styles.permissionContainer}>
          <Ionicons name="camera-outline" size={64} color="#6B7280" />
          <Text style={styles.permissionTitle}>{t('mobile:scanner.permissionTitle')}</Text>
          <Text style={styles.permissionText}>{t('mobile:scanner.permissionBody')}</Text>
          <TouchableOpacity style={styles.permissionButton} onPress={requestPermission}>
            <Text style={styles.permissionButtonText}>{t('mobile:scanner.enableCamera')}</Text>
          </TouchableOpacity>
        </View>
      </View>
    );
  }

  return (
    <View style={styles.container}>
      <CameraView
        style={styles.camera}
        onBarcodeScanned={scanned ? undefined : handleBarCodeScanned}
        barcodeScannerSettings={{
          barcodeTypes: ['qr'],
        }}
        enableTorch={flashOn}
      >
        {/* Overlay */}
        <View style={styles.overlay}>
          {/* Header */}
          <View style={styles.header}>
            <TouchableOpacity style={styles.closeButton} onPress={() => router.back()}>
              <Ionicons name="close" size={28} color="#FFFFFF" />
            </TouchableOpacity>
            <Text style={styles.headerTitle}>{t('mobile:scanner.title')}</Text>
            <TouchableOpacity 
              style={styles.flashButton} 
              onPress={() => setFlashOn(!flashOn)}
            >
              <Ionicons 
                name={flashOn ? 'flash' : 'flash-off'} 
                size={24} 
                color="#FFFFFF" 
              />
            </TouchableOpacity>
          </View>

          {/* Scanner Frame */}
          <View style={styles.scannerContainer}>
            <View style={styles.scannerFrame}>
              <View style={[styles.corner, styles.topLeft]} />
              <View style={[styles.corner, styles.topRight]} />
              <View style={[styles.corner, styles.bottomLeft]} />
              <View style={[styles.corner, styles.bottomRight]} />
            </View>
          </View>

          {/* Instructions */}
          <View style={styles.instructionsContainer}>
            <Text style={styles.instructionsText} accessibilityLiveRegion="polite">
              {locating ? t('mobile:scanner.locating') : t('mobile:scanner.instructions')}
            </Text>
            {scanned && !locating && (
              <TouchableOpacity 
                style={styles.rescanButton}
                onPress={() => setScanned(false)}
              >
                <Text style={styles.rescanButtonText}>{t('mobile:scanner.scanAgain')}</Text>
              </TouchableOpacity>
            )}
          </View>
        </View>
      </CameraView>
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: '#000000',
  },
  loadingText: {
    color: '#FFFFFF',
    fontSize: 16,
    textAlign: 'center',
    marginTop: 100,
  },
  permissionContainer: {
    flex: 1,
    justifyContent: 'center',
    alignItems: 'center',
    padding: 32,
    backgroundColor: '#F3F4F6',
  },
  permissionTitle: {
    fontSize: 20,
    fontWeight: '600',
    color: '#1F2937',
    marginTop: 24,
    marginBottom: 8,
  },
  permissionText: {
    fontSize: 16,
    color: '#6B7280',
    textAlign: 'center',
    marginBottom: 24,
  },
  permissionButton: {
    backgroundColor: '#0F766E',
    paddingHorizontal: 32,
    paddingVertical: 14,
    borderRadius: 12,
  },
  permissionButtonText: {
    color: '#FFFFFF',
    fontSize: 16,
    fontWeight: '600',
  },
  camera: {
    flex: 1,
  },
  overlay: {
    flex: 1,
    backgroundColor: 'rgba(0,0,0,0.5)',
  },
  header: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    paddingTop: 60,
    paddingHorizontal: 16,
  },
  closeButton: {
    width: 44,
    height: 44,
    borderRadius: 22,
    backgroundColor: 'rgba(0,0,0,0.5)',
    justifyContent: 'center',
    alignItems: 'center',
  },
  headerTitle: {
    fontSize: 18,
    fontWeight: '600',
    color: '#FFFFFF',
  },
  flashButton: {
    width: 44,
    height: 44,
    borderRadius: 22,
    backgroundColor: 'rgba(0,0,0,0.5)',
    justifyContent: 'center',
    alignItems: 'center',
  },
  scannerContainer: {
    flex: 1,
    justifyContent: 'center',
    alignItems: 'center',
  },
  scannerFrame: {
    width: SCANNER_SIZE,
    height: SCANNER_SIZE,
    backgroundColor: 'transparent',
    position: 'relative',
  },
  corner: {
    position: 'absolute',
    width: 40,
    height: 40,
    borderColor: '#0F766E',
  },
  topLeft: {
    top: 0,
    left: 0,
    borderTopWidth: 4,
    borderLeftWidth: 4,
    borderTopLeftRadius: 8,
  },
  topRight: {
    top: 0,
    right: 0,
    borderTopWidth: 4,
    borderRightWidth: 4,
    borderTopRightRadius: 8,
  },
  bottomLeft: {
    bottom: 0,
    left: 0,
    borderBottomWidth: 4,
    borderLeftWidth: 4,
    borderBottomLeftRadius: 8,
  },
  bottomRight: {
    bottom: 0,
    right: 0,
    borderBottomWidth: 4,
    borderRightWidth: 4,
    borderBottomRightRadius: 8,
  },
  instructionsContainer: {
    paddingBottom: 80,
    alignItems: 'center',
  },
  instructionsText: {
    fontSize: 16,
    color: '#FFFFFF',
    textAlign: 'center',
    paddingHorizontal: 32,
  },
  rescanButton: {
    marginTop: 16,
    paddingHorizontal: 24,
    paddingVertical: 12,
    backgroundColor: '#0F766E',
    borderRadius: 8,
  },
  rescanButtonText: {
    color: '#FFFFFF',
    fontSize: 14,
    fontWeight: '600',
  },
});
