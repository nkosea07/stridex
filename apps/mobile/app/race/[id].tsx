import { View, Text, ScrollView, StyleSheet, TouchableOpacity, Image, Alert, ActivityIndicator } from 'react-native';
import { useLocalSearchParams } from 'expo-router';
import { Ionicons } from '@expo/vector-icons';
import { useCallback, useEffect, useState } from 'react';
import { api } from '@/lib/api';
import { useAuthStore } from '@/store/auth';

const DEFAULT_BANNER = 'https://images.unsplash.com/photo-1513593771513-7b58b6c4af38?w=800';

function formatDate(value?: string) {
  if (!value) return '-';
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return '-';
  return date.toLocaleDateString();
}

function formatTime(value?: string) {
  if (!value) return '-';
  return String(value).slice(0, 5);
}

function statusLabel(status?: string) {
  const normalized = String(status || '').toLowerCase();
  if (normalized === 'registration_open') return 'Registration Open';
  if (normalized === 'registration_closed') return 'Registration Closed';
  if (normalized === 'in_progress') return 'In Progress';
  if (normalized === 'completed') return 'Completed';
  return 'Upcoming';
}

function statusColors(status?: string) {
  const normalized = String(status || '').toLowerCase();
  if (normalized === 'registration_open') return { bg: '#DCFCE7', text: '#16A34A' };
  if (normalized === 'registration_closed') return { bg: '#FEE2E2', text: '#DC2626' };
  if (normalized === 'in_progress') return { bg: '#DBEAFE', text: '#2563EB' };
  if (normalized === 'completed') return { bg: '#E5E7EB', text: '#4B5563' };
  return { bg: '#FEF3C7', text: '#D97706' };
}

export default function RaceDetailScreen() {
  const { id } = useLocalSearchParams<{ id: string }>();
  const { user } = useAuthStore();
  const [race, setRace] = useState<any>(null);
  const [checkpoints, setCheckpoints] = useState<any[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [isRegistering, setIsRegistering] = useState(false);

  const load = useCallback(async () => {
    if (!id) return;
    const [raceResponse, checkpointsResponse] = await Promise.all([
      api.getRace(String(id)),
      api.getRaceCheckpoints(String(id)),
    ]);
    setRace(raceResponse);
    setCheckpoints((checkpointsResponse as any[]).sort((a, b) => Number(a.orderIndex || 0) - Number(b.orderIndex || 0)));
  }, [id]);

  useEffect(() => {
    load().finally(() => setIsLoading(false));
  }, [load]);

  const handleRegister = () => {
    if (!race) return;

    const firstCategory = (race.categories || [])[0];
    if (!firstCategory?.id) {
      Alert.alert('Registration unavailable', 'No race categories are available for registration yet.');
      return;
    }

    Alert.alert(
      'Register for race',
      `Register for ${race.name} in ${firstCategory.name}?`,
      [
        { text: 'Cancel', style: 'cancel' },
        {
          text: 'Continue',
          onPress: async () => {
            try {
              setIsRegistering(true);
              await api.registerForRace(String(id), {
                categoryId: String(firstCategory.id),
                emergencyContact: {
                  name: user ? `${user.firstName} ${user.lastName}`.trim() : 'Emergency Contact',
                  phone: user?.phone || '0000000000',
                  relationship: 'Self',
                },
              });
              Alert.alert('Success', 'Registration completed.');
            } catch (error: any) {
              Alert.alert('Registration failed', error?.message || 'Unable to register for this race.');
            } finally {
              setIsRegistering(false);
            }
          },
        },
      ],
    );
  };

  if (isLoading) {
    return (
      <View style={styles.center}>
        <ActivityIndicator size="large" color="#0F766E" />
      </View>
    );
  }

  if (!race) {
    return (
      <View style={styles.center}>
        <Text style={styles.emptyText}>Race not found</Text>
      </View>
    );
  }

  const statusStyle = statusColors(race.status);
  const filled = Number(race.participantCount || 0);
  const max = Number(race.maxParticipants || 0);
  const percent = max > 0 ? Math.min(100, (filled / max) * 100) : 0;

  return (
    <View style={styles.container}>
      <ScrollView showsVerticalScrollIndicator={false}>
        <Image source={{ uri: race.bannerImageUrl || DEFAULT_BANNER }} style={styles.bannerImage} />

        <View style={styles.content}>
          <View style={[styles.statusBadge, { backgroundColor: statusStyle.bg }]}>
            <Text style={[styles.statusText, { color: statusStyle.text }]}>{statusLabel(race.status)}</Text>
          </View>

          <Text style={styles.title}>{race.name}</Text>

          <View style={styles.infoGrid}>
            <View style={styles.infoItem}>
              <Ionicons name="calendar-outline" size={20} color="#0F766E" />
              <Text style={styles.infoText}>{formatDate(race.raceDate)}</Text>
            </View>
            <View style={styles.infoItem}>
              <Ionicons name="time-outline" size={20} color="#0F766E" />
              <Text style={styles.infoText}>{formatTime(race.startTime)}</Text>
            </View>
            <View style={styles.infoItem}>
              <Ionicons name="location-outline" size={20} color="#0F766E" />
              <Text style={styles.infoText}>{[race.city, race.country].filter(Boolean).join(', ') || '-'}</Text>
            </View>
            <View style={styles.infoItem}>
              <Ionicons name="walk-outline" size={20} color="#0F766E" />
              <Text style={styles.infoText}>{Number(race.distanceKm || 0)}km</Text>
            </View>
          </View>

          <View style={styles.section}>
            <Text style={styles.sectionTitle}>About</Text>
            <Text style={styles.description}>{race.description || 'No description available.'}</Text>
          </View>

          <View style={styles.section}>
            <Text style={styles.sectionTitle}>Categories</Text>
            {(race.categories || []).length ? (
              (race.categories || []).map((category: any) => (
                <View key={String(category.id)} style={styles.categoryCard}>
                  <View>
                    <Text style={styles.categoryName}>{category.name}</Text>
                    <Text style={styles.categoryDistance}>{Number(category.distanceKm || race.distanceKm || 0)}km</Text>
                  </View>
                  <Text style={styles.categoryFee}>
                    {category.registrationFee ? `${race.currency || ''} ${category.registrationFee}` : '-'}
                  </Text>
                </View>
              ))
            ) : (
              <Text style={styles.placeholderText}>No categories configured.</Text>
            )}
          </View>

          <View style={styles.section}>
            <Text style={styles.sectionTitle}>Checkpoints</Text>
            <View style={styles.checkpointsList}>
              {checkpoints.length ? (
                checkpoints.map((checkpoint, index) => (
                  <View key={String(checkpoint.id)} style={styles.checkpointItem}>
                    <View style={styles.checkpointMarker}>
                      <View
                        style={[
                          styles.checkpointDot,
                          index === 0 && styles.checkpointDotStart,
                          index === checkpoints.length - 1 && styles.checkpointDotFinish,
                        ]}
                      />
                      {index < checkpoints.length - 1 && <View style={styles.checkpointLine} />}
                    </View>
                    <View style={styles.checkpointInfo}>
                      <Text style={styles.checkpointName}>{checkpoint.name}</Text>
                      <Text style={styles.checkpointDetails}>
                        {Number(checkpoint.distanceFromStartKm || 0)}km
                        {checkpoint.cutoffTime ? ` • Cutoff: ${checkpoint.cutoffTime}` : ''}
                      </Text>
                    </View>
                  </View>
                ))
              ) : (
                <Text style={styles.placeholderText}>No checkpoints configured.</Text>
              )}
            </View>
          </View>

          <View style={styles.section}>
            <Text style={styles.sectionTitle}>Participants</Text>
            <View style={styles.participantsCard}>
              <Text style={styles.participantsCount}>
                {filled.toLocaleString()} {max > 0 ? `/ ${max.toLocaleString()}` : ''}
              </Text>
              <Text style={styles.participantsLabel}>registered</Text>
              <View style={styles.progressBar}>
                <View style={[styles.progressFill, { width: `${percent}%` }]} />
              </View>
            </View>
          </View>

          <View style={styles.bottomSpacing} />
        </View>
      </ScrollView>

      <View style={styles.registerContainer}>
        <TouchableOpacity style={styles.registerButton} onPress={handleRegister} disabled={isRegistering}>
          <Text style={styles.registerButtonText}>{isRegistering ? 'Registering...' : 'Register Now'}</Text>
        </TouchableOpacity>
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: '#FFFFFF',
  },
  center: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
    backgroundColor: '#FFFFFF',
  },
  emptyText: {
    color: '#6B7280',
    fontSize: 16,
  },
  bannerImage: {
    width: '100%',
    height: 250,
  },
  content: {
    padding: 20,
    marginTop: -24,
    backgroundColor: '#FFFFFF',
    borderTopLeftRadius: 24,
    borderTopRightRadius: 24,
  },
  statusBadge: {
    alignSelf: 'flex-start',
    paddingHorizontal: 12,
    paddingVertical: 6,
    borderRadius: 20,
    marginBottom: 12,
  },
  statusText: {
    fontSize: 12,
    fontWeight: '600',
  },
  title: {
    fontSize: 24,
    fontWeight: 'bold',
    color: '#1F2937',
    marginBottom: 16,
  },
  infoGrid: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    gap: 16,
    marginBottom: 24,
  },
  infoItem: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 8,
    width: '45%',
  },
  infoText: {
    fontSize: 14,
    color: '#6B7280',
  },
  section: {
    marginBottom: 24,
  },
  sectionTitle: {
    fontSize: 18,
    fontWeight: '600',
    color: '#1F2937',
    marginBottom: 12,
  },
  description: {
    fontSize: 15,
    color: '#4B5563',
    lineHeight: 24,
  },
  categoryCard: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    backgroundColor: '#F9FAFB',
    padding: 16,
    borderRadius: 12,
    marginBottom: 8,
  },
  categoryName: {
    fontSize: 16,
    fontWeight: '500',
    color: '#1F2937',
  },
  categoryDistance: {
    fontSize: 14,
    color: '#6B7280',
    marginTop: 2,
  },
  categoryFee: {
    fontSize: 18,
    fontWeight: '600',
    color: '#0F766E',
  },
  placeholderText: {
    color: '#6B7280',
    fontSize: 14,
  },
  checkpointsList: {},
  checkpointItem: {
    flexDirection: 'row',
    minHeight: 60,
  },
  checkpointMarker: {
    width: 24,
    alignItems: 'center',
  },
  checkpointDot: {
    width: 12,
    height: 12,
    borderRadius: 6,
    backgroundColor: '#D1D5DB',
    borderWidth: 2,
    borderColor: '#FFFFFF',
  },
  checkpointDotStart: {
    backgroundColor: '#22C55E',
  },
  checkpointDotFinish: {
    backgroundColor: '#0F766E',
  },
  checkpointLine: {
    flex: 1,
    width: 2,
    backgroundColor: '#E5E7EB',
  },
  checkpointInfo: {
    flex: 1,
    paddingLeft: 12,
    paddingBottom: 16,
  },
  checkpointName: {
    fontSize: 15,
    fontWeight: '500',
    color: '#1F2937',
  },
  checkpointDetails: {
    fontSize: 13,
    color: '#6B7280',
    marginTop: 2,
  },
  participantsCard: {
    backgroundColor: '#F9FAFB',
    borderRadius: 12,
    padding: 16,
  },
  participantsCount: {
    fontSize: 24,
    fontWeight: 'bold',
    color: '#1F2937',
  },
  participantsLabel: {
    fontSize: 14,
    color: '#6B7280',
    marginTop: 2,
  },
  progressBar: {
    height: 8,
    borderRadius: 999,
    backgroundColor: '#E5E7EB',
    marginTop: 12,
    overflow: 'hidden',
  },
  progressFill: {
    height: '100%',
    backgroundColor: '#0F766E',
  },
  registerContainer: {
    padding: 16,
    borderTopWidth: 1,
    borderTopColor: '#E5E7EB',
    backgroundColor: '#FFFFFF',
  },
  registerButton: {
    backgroundColor: '#0F766E',
    paddingVertical: 16,
    borderRadius: 12,
    alignItems: 'center',
  },
  registerButtonText: {
    color: '#FFFFFF',
    fontSize: 16,
    fontWeight: '600',
  },
  bottomSpacing: {
    height: 24,
  },
});
