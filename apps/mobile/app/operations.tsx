import { useCallback, useEffect, useRef, useState } from 'react';
import { ActivityIndicator, Alert, Modal, ScrollView, StyleSheet, Text, TextInput, TouchableOpacity, View } from 'react-native';
import { useFocusEffect } from 'expo-router';
import { useTranslation } from 'react-i18next';
import { api } from '@/lib/api';
import { useAuthStore } from '@/store/auth';
import { isOperationsRole } from '@/lib/capabilities';
import type { Race } from '@/types';
import { assignedOptions, createSingleFlight, incidentMessage, isCurrentOperation, receiptCopyKey, validSelection } from '@/lib/operations';
import type { OperationOption } from '@/lib/operations';

export default function OperationsScreen() {
  const { t } = useTranslation();
  const { user } = useAuthStore();
  const canOperate = isOperationsRole(user?.role);
  const [races, setRaces] = useState<OperationOption[]>([]);
  const [raceId, setRaceId] = useState('');
  const [checkpoints, setCheckpoints] = useState<OperationOption[]>([]);
  const [checkpointId, setCheckpointId] = useState('');
  const [bib, setBib] = useState('');
  const [loading, setLoading] = useState(false);
  const [checkpointLoading, setCheckpointLoading] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState(false);
  const [incidentOpen, setIncidentOpen] = useState(false);
  const [draft, setDraft] = useState('');
  const [incidentBusy, setIncidentBusy] = useState(false);
  const incidentInFlight = useRef(false);
  const recordFlight = useRef(createSingleFlight());
  const loadSequence = useRef(0);
  const checkpointSequence = useRef(0);
  const focusEpoch = useRef(0);
  const focused = useRef(false);
  const [focusRevision, setFocusRevision] = useState(0);
  const volunteer = user?.role === 'VOLUNTEER';
  const [assignments, setAssignments] = useState<Record<string, OperationOption[]> | null>(null);

  useFocusEffect(useCallback(() => {
    if (!canOperate) return;
    focused.current = true;
    focusEpoch.current += 1;
    setFocusRevision((current) => current + 1);
    setIncidentBusy(incidentInFlight.current);
    const sequence = ++loadSequence.current;
    setLoading(true);
    setError(false);
    setRaces([]);
    setCheckpoints([]);
    setAssignments(null);
    const load = async () => {
      try {
        if (volunteer) {
          const options = assignedOptions(await api.getAssignedCheckpoints());
          if (sequence !== loadSequence.current) return;
          setAssignments(options.checkpointsByRace);
          setRaces(options.races);
          setRaceId((current) => validSelection(current, options.races));
          setCheckpointId('');
        } else {
          const rows: Race[] = await api.getRaces();
          if (sequence !== loadSequence.current) return;
          const options = rows.map((row) => ({ id: row.id, name: row.name }));
          setRaces(options);
          setRaceId((current) => validSelection(current, options));
        }
      } catch {
        if (sequence === loadSequence.current) {
          setError(true);
          setRaceId('');
          setCheckpointId('');
          setRaces([]);
          setCheckpoints([]);
        }
      } finally {
        if (sequence === loadSequence.current) setLoading(false);
      }
    };
    void load();
    return () => {
      focused.current = false;
      focusEpoch.current += 1;
      loadSequence.current += 1;
      checkpointSequence.current += 1;
    };
  }, [canOperate, volunteer]));

  useEffect(() => {
    const sequence = ++checkpointSequence.current;
    const epoch = focusEpoch.current;
    if (!raceId) {
      setCheckpoints([]);
      setCheckpointId('');
      setCheckpointLoading(false);
      return;
    }
    if (volunteer) {
      const rows = assignments?.[raceId] ?? [];
      setCheckpoints(rows);
      setCheckpointId((current) => validSelection(current, rows));
      setCheckpointLoading(false);
      return;
    }
    if (!focused.current) return;
    setCheckpoints([]);
    setCheckpointId('');
    setCheckpointLoading(true);
    api.getRaceCheckpoints(raceId).then((rows) => {
      const mapped = rows.map((row) => ({
        id: String(row.id), name: row.name,
      }));
      if (focused.current && epoch === focusEpoch.current && sequence === checkpointSequence.current) {
        setCheckpoints(mapped);
        setCheckpointId((current) => validSelection(current, mapped));
      }
    }).catch(() => {
      if (focused.current && epoch === focusEpoch.current && sequence === checkpointSequence.current) {
        setError(true); setCheckpoints([]); setCheckpointId('');
      }
    }).finally(() => {
      if (focused.current && epoch === focusEpoch.current && sequence === checkpointSequence.current) setCheckpointLoading(false);
    });
    return () => { checkpointSequence.current += 1; };
  }, [raceId, volunteer, assignments, focusRevision]);

  const record = async () => {
    if (!raceId || !checkpointId || !bib.trim() || loading || checkpointLoading || error) return;
    await recordFlight.current.run(async () => {
      setBusy(true);
      try {
        await api.recordCheckpointTime(raceId, checkpointId, bib.trim());
        Alert.alert(t('mobile:operations.recordedTitle'), t('mobile:operations.recordedBody', { bib }));
        setBib('');
      } catch (error) {
        Alert.alert(t('mobile:operations.failedTitle'), error instanceof Error ? error.message : t('mobile:operations.failedBody'));
      } finally {
        setBusy(false);
      }
    });
  };

  const reportIncident = async () => {
    const message = incidentMessage(draft);
    if (!raceId || !checkpointId || !message || incidentInFlight.current) return;
    const epoch = focusEpoch.current;
    const session = useAuthStore.getState();
    const current = () => isCurrentOperation(epoch, focusEpoch.current, focused.current, session, useAuthStore.getState());
    incidentInFlight.current = true;
    setIncidentBusy(true);
    try {
      const receipt = await api.reportCheckpointEmergency(raceId, checkpointId, message);
      if (!current()) return;
      setIncidentOpen(false);
      setDraft('');
      Alert.alert(t('mobile:operations.incidentSentTitle'),
        `${t(`mobile:operations.${receiptCopyKey(receipt)}`)}\n\n${t('mobile:operations.emergencyGuidance')}`);
    } catch {
      if (current()) Alert.alert(t('mobile:operations.failedTitle'), t('mobile:operations.failedBody'));
    } finally {
      incidentInFlight.current = false;
      if (focused.current) setIncidentBusy(false);
    }
  };

  if (!canOperate) {
    return <View style={styles.center}><Text style={styles.muted}>{t('mobile:operations.unavailable')}</Text></View>;
  }

  return (
    <ScrollView contentContainerStyle={styles.container}>
      <Text style={styles.title}>{t('mobile:operations.title')}</Text>
      <Text style={styles.muted}>{t('mobile:operations.description')}</Text>
      {loading ? <ActivityIndicator color="#0F766E" /> : null}
      {error ? <Text accessibilityRole="alert" style={styles.error}>{t('mobile:operations.loadError')}</Text> : null}
      {!loading && !error && races.length === 0 ? <Text style={styles.muted}>{t('mobile:operations.empty')}</Text> : null}
      <Text style={styles.label}>{t('mobile:operations.race')}</Text>
      <View style={styles.row}>
        {races.map((race) => (
          <TouchableOpacity key={race.id} accessibilityRole="button" accessibilityState={{ selected: raceId === race.id }} onPress={() => {
            setError(false);
            setRaceId(race.id);
            setCheckpointId('');
            setFocusRevision((current) => current + 1);
          }}
            style={[styles.chip, raceId === race.id && styles.chipActive]}>
            <Text style={styles.chipText}>{race.name}</Text>
          </TouchableOpacity>
        ))}
      </View>
      <Text style={styles.label}>{t('mobile:operations.checkpoint')}</Text>
      {checkpointLoading ? <ActivityIndicator color="#0F766E" /> : null}
      <View style={styles.row}>
        {checkpoints.map((checkpoint) => (
          <TouchableOpacity key={checkpoint.id} accessibilityRole="button" accessibilityState={{ selected: checkpointId === checkpoint.id }} onPress={() => setCheckpointId(checkpoint.id)}
            style={[styles.chip, checkpointId === checkpoint.id && styles.chipActive]}>
            <Text style={styles.chipText}>{checkpoint.name}</Text>
          </TouchableOpacity>
        ))}
      </View>
      {!loading && !checkpointLoading && !error && raceId && checkpoints.length === 0 ? <Text style={styles.muted}>{t('mobile:operations.noCheckpoints')}</Text> : null}
      <Text style={styles.label}>{t('mobile:operations.bib')}</Text>
      <TextInput style={styles.input} value={bib} onChangeText={setBib}
        keyboardType="default" placeholder={t('mobile:operations.bibPlaceholder')} autoCapitalize="characters" />
      <TouchableOpacity style={[styles.button, (!checkpointId || !bib.trim() || busy || loading || checkpointLoading || error) && styles.disabled]}
        disabled={!checkpointId || !bib.trim() || busy || loading || checkpointLoading || error} onPress={() => void record()}>
        <Text style={styles.buttonText}>{busy ? t('mobile:operations.recording') : t('mobile:operations.record')}</Text>
      </TouchableOpacity>
      <TouchableOpacity style={[styles.secondary, (!checkpointId || loading || checkpointLoading || error) && styles.disabled]}
        disabled={!checkpointId || loading || checkpointLoading || error} accessibilityRole="button" onPress={() => setIncidentOpen(true)}>
        <Text style={styles.secondaryText}>{t('mobile:operations.reportIncident')}</Text>
      </TouchableOpacity>
      <Modal visible={incidentOpen} transparent animationType="fade" onRequestClose={() => { if (!incidentBusy) setIncidentOpen(false); }}>
        <View style={styles.modalBackdrop}>
          <View style={styles.modalContent} accessibilityViewIsModal>
            <Text style={styles.title}>{t('mobile:operations.incidentTitle')}</Text>
            <Text style={styles.muted}>{t('mobile:operations.incidentPrompt')}</Text>
            <Text style={styles.label}>{t('mobile:operations.incidentLabel')}</Text>
            <TextInput style={[styles.input, styles.multiline]} multiline maxLength={2000}
              accessibilityLabel={t('mobile:operations.incidentLabel')} value={draft} onChangeText={setDraft}
              placeholder={t('mobile:operations.incidentPlaceholder')} textAlignVertical="top" />
            <View style={styles.row}>
              <TouchableOpacity accessibilityRole="button" disabled={incidentBusy} onPress={() => setIncidentOpen(false)}>
                <Text style={styles.secondaryText}>{t('mobile:operations.cancel')}</Text>
              </TouchableOpacity>
              <TouchableOpacity accessibilityRole="button" disabled={!incidentMessage(draft) || incidentBusy || !checkpointId}
                style={[styles.button, (!incidentMessage(draft) || incidentBusy || !checkpointId) && styles.disabled]}
                onPress={() => void reportIncident()}>
                <Text style={styles.buttonText}>{incidentBusy ? t('mobile:operations.submitting') : t('mobile:operations.submit')}</Text>
              </TouchableOpacity>
            </View>
          </View>
        </View>
      </Modal>
    </ScrollView>
  );
}

const styles = StyleSheet.create({
  container: { padding: 20, gap: 12, backgroundColor: '#F8FAFC' },
  center: { flex: 1, alignItems: 'center', justifyContent: 'center', padding: 24 },
  title: { fontSize: 26, fontWeight: '800', color: '#0F172A' },
  muted: { color: '#64748B' },
  label: { fontWeight: '700', color: '#0F172A', marginTop: 8 },
  row: { flexDirection: 'row', flexWrap: 'wrap', gap: 8 },
  chip: { paddingHorizontal: 12, paddingVertical: 8, borderRadius: 20, backgroundColor: '#E2E8F0' },
  chipActive: { backgroundColor: '#0F766E' },
  chipText: { color: '#0F172A', fontSize: 12 },
  input: { borderWidth: 1, borderColor: '#CBD5E1', borderRadius: 12, padding: 14, backgroundColor: '#FFFFFF' },
  button: { backgroundColor: '#0F766E', borderRadius: 12, padding: 16, alignItems: 'center', marginTop: 8 },
  disabled: { opacity: 0.5 },
  buttonText: { color: '#FFFFFF', fontWeight: '800' },
  secondary: { borderWidth: 1, borderColor: '#DC2626', borderRadius: 12, padding: 16, alignItems: 'center' },
  secondaryText: { color: '#DC2626', fontWeight: '700' },
  error: { color: '#B91C1C' },
  modalBackdrop: { flex: 1, backgroundColor: '#0008', justifyContent: 'center', padding: 20 },
  modalContent: { backgroundColor: '#FFFFFF', borderRadius: 16, padding: 20, gap: 12 },
  multiline: { minHeight: 120 },
});
