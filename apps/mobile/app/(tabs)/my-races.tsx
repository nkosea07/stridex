import { View, Text, FlatList, StyleSheet, TouchableOpacity, RefreshControl } from 'react-native'
import { Link, router } from 'expo-router'
import { Ionicons } from '@expo/vector-icons'
import { useCallback, useEffect, useMemo, useState } from 'react'
import { api } from '@/lib/api'
import { CapabilityGate } from '@/components/CapabilityGate'
import { formatDate, formatDistanceKm } from '@/lib/format'

type MyRace = {
  id: string
  raceId: string
  name: string
  date: string
  location: string
  distance: number
  status: string
  bibNumber?: string
  category: string
  position?: number
  time?: string
}

const tabs = ['Upcoming', 'Completed', 'All']

export default function MyRacesScreen() {
  return (
    <CapabilityGate capability="ATHLETE">
      <MyRacesScreenContent />
    </CapabilityGate>
  )
}

function MyRacesScreenContent() {
  const [activeTab, setActiveTab] = useState('Upcoming')
  const [refreshing, setRefreshing] = useState(false)
  const [races, setRaces] = useState<MyRace[]>([])

  const load = async () => {
    const [registrations, allRaces] = await Promise.all([api.getMyRaces(), api.getRaces()])
    const raceMap = new Map((allRaces as any[]).map((race) => [String(race.id), race]))

    const mapped: MyRace[] = (registrations as any[]).map((reg) => {
      const race = raceMap.get(String(reg.raceId))
      return {
        id: String(reg.id),
        raceId: String(reg.raceId),
        name: race?.name || 'Race',
        date: race?.raceDate && !Number.isNaN(new Date(race.raceDate).getTime()) ? formatDate(race.raceDate) : '-',
        location: [race?.city, race?.country].filter(Boolean).join(', '),
        distance: Number(race?.distanceKm || 0),
        status: String(reg.status || 'pending').toLowerCase(),
        bibNumber: reg.bibNumber,
        category: race?.raceType || 'Category',
      }
    })

    setRaces(mapped)
  }

  useEffect(() => {
    void load()
  }, [])

  const onRefresh = useCallback(() => {
    setRefreshing(true)
    load().finally(() => setRefreshing(false))
  }, [])

  const filteredRaces = useMemo(
    () =>
      races.filter((race) => {
        if (activeTab === 'All') return true
        if (activeTab === 'Upcoming') return race.status === 'pending' || race.status === 'confirmed'
        if (activeTab === 'Completed') return race.status === 'finished'
        return true
      }),
    [activeTab, races],
  )

  const renderRaceCard = ({ item }: { item: MyRace }) => (
    <Link href={`/race/${item.raceId}`} asChild>
      <TouchableOpacity style={styles.raceCard}>
        <View style={styles.raceHeader}>
          <View style={[styles.statusIndicator, { backgroundColor: item.status === 'finished' ? '#6B7280' : '#22C55E' }]} />
          <Text style={styles.raceName}>{item.name}</Text>
        </View>

        <View style={styles.raceInfo}>
          <View style={styles.infoRow}><Ionicons name="calendar-outline" size={16} color="#6B7280" /><Text style={styles.infoText}>{item.date}</Text></View>
          <View style={styles.infoRow}><Ionicons name="location-outline" size={16} color="#6B7280" /><Text style={styles.infoText}>{item.location}</Text></View>
          <View style={styles.infoRow}><Ionicons name="walk-outline" size={16} color="#6B7280" /><Text style={styles.infoText}>{formatDistanceKm(item.distance)} - {item.category}</Text></View>
        </View>

        <View style={styles.bibSection}>
          <View><Text style={styles.bibLabel}>Bib Number</Text><Text style={styles.bibNumber}>{item.bibNumber || '-'}</Text></View>
          {item.status === 'finished' && item.position && (
            <View style={styles.resultInfo}><Text style={styles.resultLabel}>Result</Text><Text style={styles.resultText}>#{item.position} - {item.time}</Text></View>
          )}
        </View>

        {(item.status === 'pending' || item.status === 'confirmed') && (
          <TouchableOpacity
            style={styles.trackButton}
            onPress={(e) => {
              e.preventDefault();
              router.push({ pathname: '/tracking', params: { raceId: item.raceId } });
            }}
          >
            <Ionicons name="navigate" size={18} color="#FFFFFF" />
            <Text style={styles.trackButtonText}>Start Tracking</Text>
          </TouchableOpacity>
        )}
      </TouchableOpacity>
    </Link>
  )

  return (
    <View style={styles.container}>
      <View style={styles.tabsContainer}>
        {tabs.map((tab) => (
          <TouchableOpacity key={tab} style={[styles.tab, activeTab === tab && styles.tabActive]} onPress={() => setActiveTab(tab)}>
            <Text style={[styles.tabText, activeTab === tab && styles.tabTextActive]}>{tab}</Text>
          </TouchableOpacity>
        ))}
      </View>

      <FlatList
        data={filteredRaces}
        keyExtractor={(item) => item.id}
        renderItem={renderRaceCard}
        contentContainerStyle={styles.racesList}
        showsVerticalScrollIndicator={false}
        refreshControl={<RefreshControl refreshing={refreshing} onRefresh={onRefresh} />}
        ListEmptyComponent={<View style={styles.emptyState}><Text style={styles.emptyText}>No {activeTab.toLowerCase()} races</Text></View>}
      />
    </View>
  )
}

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: '#F3F4F6' },
  tabsContainer: { flexDirection: 'row', backgroundColor: '#FFFFFF', padding: 8, gap: 8, borderBottomWidth: 1, borderBottomColor: '#E5E7EB' },
  tab: { flex: 1, paddingVertical: 10, alignItems: 'center', borderRadius: 8 },
  tabActive: { backgroundColor: '#0F766E' },
  tabText: { fontSize: 14, fontWeight: '500', color: '#6B7280' },
  tabTextActive: { color: '#FFFFFF' },
  racesList: { padding: 16 },
  raceCard: { backgroundColor: '#FFFFFF', borderRadius: 16, padding: 16, marginBottom: 16, shadowColor: '#000', shadowOffset: { width: 0, height: 2 }, shadowOpacity: 0.1, shadowRadius: 8, elevation: 3 },
  raceHeader: { flexDirection: 'row', alignItems: 'center', gap: 10, marginBottom: 12 },
  statusIndicator: { width: 10, height: 10, borderRadius: 5 },
  raceName: { fontSize: 18, fontWeight: 'bold', color: '#1F2937', flex: 1 },
  raceInfo: { gap: 8, marginBottom: 16 },
  infoRow: { flexDirection: 'row', alignItems: 'center', gap: 8 },
  infoText: { fontSize: 14, color: '#6B7280' },
  bibSection: { flexDirection: 'row', justifyContent: 'space-between', backgroundColor: '#F0FDF4', padding: 12, borderRadius: 12, marginBottom: 12 },
  bibLabel: { fontSize: 12, color: '#6B7280', marginBottom: 2 },
  bibNumber: { fontSize: 16, fontWeight: 'bold', color: '#0F766E' },
  resultInfo: { alignItems: 'flex-end' },
  resultLabel: { fontSize: 12, color: '#6B7280', marginBottom: 2 },
  resultText: { fontSize: 16, fontWeight: 'bold', color: '#0F766E' },
  trackButton: { flexDirection: 'row', alignItems: 'center', justifyContent: 'center', gap: 8, backgroundColor: '#0F766E', paddingVertical: 12, borderRadius: 10 },
  trackButtonText: { color: '#FFFFFF', fontSize: 14, fontWeight: '600' },
  emptyState: { padding: 32, alignItems: 'center' },
  emptyText: { fontSize: 16, color: '#6B7280' },
})
