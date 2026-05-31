import { View, Text, FlatList, StyleSheet, TouchableOpacity, TextInput, ActivityIndicator } from 'react-native'
import { Link } from 'expo-router'
import { Ionicons } from '@expo/vector-icons'
import { useEffect, useMemo, useState } from 'react'
import { api } from '@/lib/api'

type RaceCard = {
  id: string
  name: string
  date: string
  location: string
  distance: number
  status: string
  participants: number
}

const statusColors: Record<string, { bg: string; text: string }> = {
  registration_open: { bg: '#DCFCE7', text: '#16A34A' },
  upcoming: { bg: '#FEF3C7', text: '#D97706' },
  registration_closed: { bg: '#FEE2E2', text: '#DC2626' },
  in_progress: { bg: '#DBEAFE', text: '#2563EB' },
  completed: { bg: '#E5E7EB', text: '#4B5563' },
}

const statusLabels: Record<string, string> = {
  registration_open: 'Registration Open',
  upcoming: 'Coming Soon',
  registration_closed: 'Registration Closed',
  in_progress: 'In Progress',
  completed: 'Completed',
}

function normalizeStatus(raw: string) {
  const value = raw?.toLowerCase()
  if (value === 'draft') return 'upcoming'
  return value
}

export default function RacesScreen() {
  const [searchQuery, setSearchQuery] = useState('')
  const [races, setRaces] = useState<RaceCard[]>([])
  const [isLoading, setIsLoading] = useState(true)

  useEffect(() => {
    let mounted = true
    const load = async () => {
      try {
        const response = await api.getRaces()
        if (!mounted) return
        const mapped = (response as unknown[]).map((r: any) => ({
          id: String(r.id),
          name: r.name,
          date: r.raceDate ? new Date(r.raceDate).toLocaleDateString() : '-',
          location: [r.city, r.country].filter(Boolean).join(', '),
          distance: Number(r.distanceKm || 0),
          status: normalizeStatus(r.status || 'upcoming'),
          participants: Number(r.participantCount || 0),
        }))
        setRaces(mapped)
      } finally {
        if (mounted) setIsLoading(false)
      }
    }
    load()
    return () => {
      mounted = false
    }
  }, [])

  const filteredRaces = useMemo(
    () => races.filter((race) => race.name.toLowerCase().includes(searchQuery.toLowerCase()) || race.location.toLowerCase().includes(searchQuery.toLowerCase())),
    [races, searchQuery],
  )

  const renderRaceCard = ({ item }: { item: RaceCard }) => {
    const statusStyle = statusColors[item.status] || statusColors.upcoming
    return (
      <Link href={`/race/${item.id}`} asChild>
        <TouchableOpacity style={styles.raceCard}>
          <View style={styles.raceContent}>
            <View style={[styles.statusBadge, { backgroundColor: statusStyle.bg }]}> 
              <Text style={[styles.statusText, { color: statusStyle.text }]}>{statusLabels[item.status] || item.status}</Text>
            </View>
            <Text style={styles.raceName}>{item.name}</Text>
            <View style={styles.raceMetadata}>
              <View style={styles.metaItem}><Ionicons name="calendar-outline" size={14} color="#6B7280" /><Text style={styles.metaText}>{item.date}</Text></View>
              <View style={styles.metaItem}><Ionicons name="location-outline" size={14} color="#6B7280" /><Text style={styles.metaText}>{item.location}</Text></View>
            </View>
            <View style={styles.raceStats}>
              <View style={styles.statChip}><Ionicons name="walk-outline" size={14} color="#0F766E" /><Text style={styles.statText}>{item.distance}km</Text></View>
              <View style={styles.statChip}><Ionicons name="people-outline" size={14} color="#0F766E" /><Text style={styles.statText}>{item.participants.toLocaleString()}</Text></View>
            </View>
          </View>
        </TouchableOpacity>
      </Link>
    )
  }

  return (
    <View style={styles.container}>
      <View style={styles.searchContainer}>
        <View style={styles.searchBar}>
          <Ionicons name="search" size={20} color="#6B7280" />
          <TextInput
            style={styles.searchInput}
            placeholder="Search races..."
            value={searchQuery}
            onChangeText={setSearchQuery}
            placeholderTextColor="#9CA3AF"
          />
        </View>
      </View>

      {isLoading ? (
        <View style={styles.center}><ActivityIndicator size="large" color="#0F766E" /></View>
      ) : (
        <FlatList
          data={filteredRaces}
          keyExtractor={(item) => item.id}
          renderItem={renderRaceCard}
          contentContainerStyle={styles.racesList}
          showsVerticalScrollIndicator={false}
          ListEmptyComponent={<View style={styles.emptyState}><Text style={styles.emptyText}>No races found</Text></View>}
        />
      )}
    </View>
  )
}

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: '#F3F4F6' },
  center: { flex: 1, justifyContent: 'center', alignItems: 'center' },
  searchContainer: { backgroundColor: '#FFFFFF', padding: 16, paddingTop: 8, borderBottomWidth: 1, borderBottomColor: '#E5E7EB' },
  searchBar: { flexDirection: 'row', alignItems: 'center', backgroundColor: '#F3F4F6', borderRadius: 12, paddingHorizontal: 12, paddingVertical: 10, gap: 8 },
  searchInput: { flex: 1, fontSize: 16, color: '#1F2937' },
  racesList: { padding: 16, gap: 16 },
  raceCard: { backgroundColor: '#FFFFFF', borderRadius: 16, overflow: 'hidden', shadowColor: '#000', shadowOffset: { width: 0, height: 2 }, shadowOpacity: 0.1, shadowRadius: 8, elevation: 3, marginBottom: 16 },
  raceContent: { padding: 16 },
  statusBadge: { alignSelf: 'flex-start', paddingHorizontal: 10, paddingVertical: 4, borderRadius: 12, marginBottom: 8 },
  statusText: { fontSize: 12, fontWeight: '600' },
  raceName: { fontSize: 18, fontWeight: 'bold', color: '#1F2937', marginBottom: 8 },
  raceMetadata: { flexDirection: 'row', gap: 16, marginBottom: 12 },
  metaItem: { flexDirection: 'row', alignItems: 'center', gap: 4 },
  metaText: { fontSize: 13, color: '#6B7280' },
  raceStats: { flexDirection: 'row', gap: 8 },
  statChip: { flexDirection: 'row', alignItems: 'center', gap: 4, backgroundColor: '#ECFDF5', paddingHorizontal: 8, paddingVertical: 4, borderRadius: 8 },
  statText: { fontSize: 12, color: '#0F766E', fontWeight: '500' },
  emptyState: { padding: 24, alignItems: 'center' },
  emptyText: { color: '#6B7280', fontSize: 16 },
})
