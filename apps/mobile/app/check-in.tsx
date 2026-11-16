import { useCallback, useEffect, useRef, useState } from 'react';
import { ActivityIndicator, Alert, FlatList, StyleSheet, Text, TextInput, TouchableOpacity, View } from 'react-native';
import { useFocusEffect } from 'expo-router';
import { Ionicons } from '@expo/vector-icons';
import { CameraView, useCameraPermissions } from 'expo-camera';
import { useTranslation } from 'react-i18next';
import { api } from '@/lib/api';
import { useAuthStore } from '@/store/auth';
import { isOperationsRole } from '@/lib/capabilities';
import { assignedOptions, staffErrorKey, validSelection } from '@/lib/operations';
import type { OperationOption } from '@/lib/operations';
import { statusTranslationKey } from '@/lib/format';
import type { StaffParticipant } from '@/types';

const SEARCH_DEBOUNCE_MS = 400;

function displayName(participant: StaffParticipant) {
  return [participant.firstName, participant.lastName].filter(Boolean).join(' ');
}

export default function CheckInScreen() {
  const { t } = useTranslation();
  const { user } = useAuthStore();
  const canOperate = isOperationsRole(user?.role);
  const [races, setRaces] = useState<OperationOption[]>([]);
  const [raceId, setRaceId] = useState('');
  const [loadingRaces, setLoadingRaces] = useState(false);
  const [query, setQuery] = useState('');
  const [results, setResults] = useState<StaffParticipant[]>([]);
  const [searching, setSearching] = useState(false);
  const [searchError, setSearchError] = useState<string | null>(null);
  const [busyId, setBusyId] = useState<string | null>(null);
  const [scanning, setScanning] = useState(false);
  const [scanLocked, setScanLocked] = useState(false);
  const [permission, requestPermission] = useCameraPermissions();
  const searchSequence = useRef(0);

  useFocusEffect(useCallback(() => {
    if (!canOperate) return;
    let active = true;
    setLoadingRaces(true);
    const load = async () => {
      try {
        const options = user?.role === 'VOLUNTEER'
          ? assignedOptions(await api.getAssignedCheckpoints()).races
          : (await api.getRaces()).map((race) => ({ id: race.id, name: race.name }));
        if (!active) return;
        setRaces(options);
        setRaceId((current) => validSelection(current, options));
      } catch (error) {
        if (active) Alert.alert(t('mobile:checkIn.title'), t(staffErrorKey(error)));
      } finally {
        if (active) setLoadingRaces(false);
      }
    };
    void load();
    return () => { active = false; };
  }, [canOperate, user?.role, t]));

  useEffect(() => {
    const term = query.trim();
    const sequence = ++searchSequence.current;
    setSearchError(null);
    if (!raceId || !term) {
      setResults([]);
      setSearching(false);
      return;
    }
    setSearching(true);
    const timer = setTimeout(() => {
      api.searchParticipants(raceId, term)
        .then((rows) => { if (sequence === searchSequence.current) setResults(rows); })
        .catch((error) => { if (sequence === searchSequence.current) setSearchError(t(staffErrorKey(error))); })
        .finally(() => { if (sequence === searchSequence.current) setSearching(false); });
    }, SEARCH_DEBOUNCE_MS);
    return () => clearTimeout(timer);
  }, [query, raceId, t]);

  const confirmCheckIn = (participant: StaffParticipant) => {
    Alert.alert(
      t('mobile:checkIn.confirmTitle'),
      t('mobile:checkIn.confirmBody', { name: displayName(participant), bib: participant.bibNumber ?? '-' }),
      [
        { text: t('mobile:checkIn.cancel'), style: 'cancel' },
        { text: t('mobile:checkIn.checkIn'), onPress: () => void checkIn(participant) },
      ],
    );
  };

  const checkIn = async (participant: StaffParticipant) => {
    if (!raceId) return;
    setBusyId(participant.id);
    try {
      const updated = await api.checkInParticipant(raceId, participant.id);
      setResults((current) => current.map((row) => row.id === participant.id
        ? { ...row, status: updated.status ?? 'CHECKED_IN', checkedInAt: updated.checkedInAt ?? new Date().toISOString() }
        : row));
      Alert.alert(t('mobile:checkIn.doneTitle'), t('mobile:checkIn.doneBody', { name: displayName(participant), bib: participant.bibNumber ?? '-' }));
    } catch (error) {
      Alert.alert(t('mobile:checkIn.failedTitle'), t(staffErrorKey(error)));
    } finally {
      setBusyId(null);
    }
  };

  const onScanned = async ({ data }: { data: string }) => {
    if (scanLocked || !raceId) return;
    setScanLocked(true);
    try {
      const participant = await api.qrCheckIn(raceId, data);
      Alert.alert(t('mobile:checkIn.doneTitle'),
        t('mobile:checkIn.doneBody', { name: displayName(participant), bib: participant.bibNumber ?? '-' }),
        [{ text: t('mobile:checkIn.next'), onPress: () => setScanLocked(false) }]);
    } catch (error) {
      Alert.alert(t('mobile:checkIn.failedTitle'), t(staffErrorKey(error)),
        [{ text: t('mobile:checkIn.next'), onPress: () => setScanLocked(false) }]);
    }
  };

  const openScanner = async () => {
    if (!permission?.granted) {
      const result = await requestPermission();
      if (!result.granted) {
        Alert.alert(t('mobile:checkIn.cameraTitle'), t('mobile:checkIn.cameraDenied'));
        return;
      }
    }
    setScanLocked(false);
    setScanning(true);
  };

  if (!canOperate) {
    return <View style={styles.center}><Text style={styles.muted}>{t('mobile:operations.unavailable')}</Text></View>;
  }

  return (
    <View style={styles.container}>
      <Text style={styles.title} accessibilityRole="header">{t('mobile:checkIn.title')}</Text>
      <Text style={styles.muted}>{t('mobile:checkIn.description')}</Text>
      {loadingRaces ? <ActivityIndicator color="#0F766E" /> : null}
      <View style={styles.row}>
        {races.map((race) => (
          <TouchableOpacity key={race.id} accessibilityRole="button" accessibilityState={{ selected: raceId === race.id }}
            onPress={() => { setRaceId(race.id); setResults([]); }}
            style={[styles.chip, raceId === race.id && styles.chipActive]}>
            <Text style={[styles.chipText, raceId === race.id && styles.chipTextActive]}>{race.name}</Text>
          </TouchableOpacity>
        ))}
      </View>
      {!loadingRaces && races.length === 0 ? <Text style={styles.muted}>{t('mobile:operations.empty')}</Text> : null}

      {scanning ? (
        <View style={styles.cameraBox}>
          <CameraView style={styles.camera} facing="back"
            onBarcodeScanned={scanLocked ? undefined : (result) => void onScanned(result)}
            barcodeScannerSettings={{ barcodeTypes: ['qr'] }} />
          <TouchableOpacity style={styles.secondaryButton} onPress={() => setScanning(false)} accessibilityRole="button">
            <Text style={styles.secondaryText}>{t('mobile:checkIn.stopScanning')}</Text>
          </TouchableOpacity>
        </View>
      ) : (
        <>
          <TouchableOpacity style={[styles.secondaryButton, !raceId && styles.disabled]} disabled={!raceId}
            onPress={() => void openScanner()} accessibilityRole="button">
            <Ionicons name="qr-code-outline" size={18} color="#0F766E" />
            <Text style={styles.secondaryText}>{t('mobile:checkIn.scan')}</Text>
          </TouchableOpacity>
          <TextInput
            style={styles.input}
            value={query}
            onChangeText={setQuery}
            editable={Boolean(raceId)}
            placeholder={t('mobile:checkIn.searchPlaceholder')}
            accessibilityLabel={t('mobile:checkIn.searchPlaceholder')}
            autoCorrect={false}
            maxLength={50}
          />
          {searching ? <ActivityIndicator color="#0F766E" /> : null}
          {searchError ? <Text style={styles.error} accessibilityRole="alert">{searchError}</Text> : null}
          <FlatList
            data={results}
            keyExtractor={(item) => item.id}
            keyboardShouldPersistTaps="handled"
            ListEmptyComponent={query.trim() && !searching && !searchError
              ? <Text style={styles.muted}>{t('mobile:checkIn.noMatches')}</Text>
              : null}
            renderItem={({ item }) => {
              const checkedIn = Boolean(item.checkedInAt) || item.status === 'CHECKED_IN';
              return (
                <View style={styles.resultRow}>
                  <View style={styles.resultInfo}>
                    <Text style={styles.resultName}>{displayName(item) || '-'}{item.bibNumber ? ` · #${item.bibNumber}` : ''}</Text>
                    <Text style={styles.muted}>
                      {[item.categoryName, item.waveName].filter(Boolean).join(' · ')}
                      {item.status ? `${item.categoryName || item.waveName ? ' · ' : ''}${t(statusTranslationKey(item.status), { defaultValue: item.status })}` : ''}
                    </Text>
                  </View>
                  {checkedIn ? (
                    <View style={styles.checkedBadge}>
                      <Ionicons name="checkmark-circle" size={16} color="#15803D" />
                      <Text style={styles.checkedText}>{t('mobile:checkIn.checkedIn')}</Text>
                    </View>
                  ) : (
                    <TouchableOpacity style={[styles.button, busyId === item.id && styles.disabled]} disabled={busyId === item.id}
                      onPress={() => confirmCheckIn(item)} accessibilityRole="button"
                      accessibilityLabel={t('mobile:checkIn.checkInName', { name: displayName(item) })}>
                      <Text style={styles.buttonText}>{busyId === item.id ? t('mobile:checkIn.checkingIn') : t('mobile:checkIn.checkIn')}</Text>
                    </TouchableOpacity>
                  )}
                </View>
              );
            }}
          />
        </>
      )}
    </View>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, padding: 20, gap: 12, backgroundColor: '#F8FAFC' },
  center: { flex: 1, alignItems: 'center', justifyContent: 'center', padding: 24 },
  title: { fontSize: 26, fontWeight: '800', color: '#0F172A' },
  muted: { color: '#64748B' },
  error: { color: '#B91C1C' },
  row: { flexDirection: 'row', flexWrap: 'wrap', gap: 8 },
  chip: { paddingHorizontal: 12, paddingVertical: 8, borderRadius: 20, backgroundColor: '#E2E8F0' },
  chipActive: { backgroundColor: '#0F766E' },
  chipText: { color: '#0F172A', fontSize: 12 },
  chipTextActive: { color: '#FFFFFF' },
  input: { borderWidth: 1, borderColor: '#CBD5E1', borderRadius: 12, padding: 14, backgroundColor: '#FFFFFF' },
  resultRow: { flexDirection: 'row', alignItems: 'center', gap: 12, backgroundColor: '#FFFFFF', borderRadius: 12, padding: 12, marginBottom: 8 },
  resultInfo: { flex: 1 },
  resultName: { fontWeight: '700', color: '#0F172A' },
  button: { backgroundColor: '#0F766E', borderRadius: 10, paddingVertical: 10, paddingHorizontal: 14 },
  buttonText: { color: '#FFFFFF', fontWeight: '700' },
  disabled: { opacity: 0.5 },
  secondaryButton: { flexDirection: 'row', gap: 8, alignItems: 'center', justifyContent: 'center', borderWidth: 1, borderColor: '#0F766E', borderRadius: 12, padding: 14 },
  secondaryText: { color: '#0F766E', fontWeight: '700' },
  checkedBadge: { flexDirection: 'row', alignItems: 'center', gap: 4 },
  checkedText: { color: '#15803D', fontWeight: '600' },
  cameraBox: { gap: 12 },
  camera: { height: 320, borderRadius: 16, overflow: 'hidden' },
});
