import { View, Text, FlatList, StyleSheet, TouchableOpacity, TextInput, ActivityIndicator, RefreshControl } from 'react-native'
import { Link } from 'expo-router'
import { Ionicons } from '@expo/vector-icons'
import { useCallback, useEffect, useMemo, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { api, getApiErrorKind, type ApiErrorKind } from '@/lib/api'
import { CapabilityGate } from '@/components/CapabilityGate'
import { RequestErrorState } from '@/components/RequestErrorState'
import { formatDate, formatDistanceKm, formatNumber } from '@/lib/format'

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
  registration_open: 'mobile:status.registrationOpen',
  upcoming: 'mobile:status.upcoming',
  registration_closed: 'mobile:status.registrationClosed',
  in_progress: 'mobile:status.inProgress',
  completed: 'mobile:status.completed',
  cancelled: 'mobile:status.cancelled',
}

function normalizeStatus(raw: string) {
  const value = raw?.toLowerCase()
  if (value === 'draft') return 'upcoming'
  return value
}

export default function RacesScreen() {
  return (
    <CapabilityGate anyOf={['ATHLETE', 'SPECTATOR']}>
      <RacesScreenContent />
    </CapabilityGate>
  )
}

function RacesScreenContent() {
  const { t } = useTranslation()
  const [searchQuery, setSearchQuery] = useState('')
  const [races, setRaces] = useState<RaceCard[]>([])
  const [isLoading, setIsLoading] = useState(true)
  const [refreshing, setRefreshing] = useState(false)
  const [errorKind, setErrorKind] = useState<ApiErrorKind | null>(null)
  const [failedPage, setFailedPage] = useState<number | null>(null)
  const [currentPage, setCurrentPage] = useState(0)
  const [hasMore, setHasMore] = useState(false)
  const [isLoadingMore, setIsLoadingMore] = useState(false)

  const loadPage = useCallback(async (page: number, append: boolean) => {
    setErrorKind(null)
    if (append) setIsLoadingMore(true)
    else setIsLoading(true)
    try {
      const response = await api.getRacesPage(page, 50)
      const mapped = response.content.map((r) => {
        const raceDate = r.raceDate ? new Date(r.raceDate) : null
        return {
          id: String(r.id),
          name: r.name,
          date: raceDate && !Number.isNaN(raceDate.getTime()) ? formatDate(raceDate) : '-',
          location: [r.city, r.country].filter(Boolean).join(', '),
          distance: Number(r.distanceKm || 0),
          status: normalizeStatus(r.status || 'upcoming'),
          participants: Number(r.participantCount || 0),
        }
      })
      setRaces((existing) => append ? [...existing, ...mapped] : mapped)
      setCurrentPage(response.number)
      setHasMore(response.number + 1 < response.totalPages)
      setFailedPage(null)
    } catch (error) {
      setFailedPage(page)
      setErrorKind(getApiErrorKind(error))
    } finally {
      if (append) setIsLoadingMore(false)
      else setIsLoading(false)
    }
  }, [])

  const load = useCallback(() => loadPage(0, false), [loadPage])

  const loadMore = useCallback(() => {
    if (!hasMore || isLoadingMore) return
    void loadPage(failedPage ?? currentPage + 1, true)
  }, [currentPage, failedPage, hasMore, isLoadingMore, loadPage])

  useEffect(() => {
    void load()
  }, [load])

  const onRefresh = useCallback(() => {
    setRefreshing(true)
    load().finally(() => setRefreshing(false))
  }, [load])

  const filteredRaces = useMemo(
    () => races.filter((race) => race.name.toLowerCase().includes(searchQuery.toLowerCase()) || race.location.toLowerCase().includes(searchQuery.toLowerCase())),
    [races, searchQuery],
  )

  const renderRaceCard = ({ item }: { item: RaceCard }) => {
    const statusStyle = statusColors[item.status] || statusColors.upcoming
    const statusLabel = t(statusLabels[item.status] || 'mobile:status.unknown')
    return (
      <Link href={`/race/${item.id}`} asChild>
        <TouchableOpacity
          style={styles.raceCard}
          accessibilityRole="button"
          accessibilityLabel={`${item.name}, ${statusLabel}, ${formatDistanceKm(item.distance)}`}
          accessibilityHint={t('mobile:races.openHint')}
          accessibilityState={{ disabled: false }}
        >
          <View style={styles.raceContent}>
            <View style={[styles.statusBadge, { backgroundColor: statusStyle.bg }]}> 
              <Text style={[styles.statusText, { color: statusStyle.text }]}>{statusLabel}</Text>
            </View>
            <Text style={styles.raceName}>{item.name}</Text>
            <View style={styles.raceMetadata}>
              <View style={styles.metaItem}><Ionicons name="calendar-outline" size={14} color="#6B7280" /><Text style={styles.metaText}>{item.date}</Text></View>
              <View style={styles.metaItem}><Ionicons name="location-outline" size={14} color="#6B7280" /><Text style={styles.metaText}>{item.location}</Text></View>
            </View>
            <View style={styles.raceStats}>
              <View style={styles.statChip}><Ionicons name="walk-outline" size={14} color="#0F766E" /><Text style={styles.statText}>{formatDistanceKm(item.distance)}</Text></View>
              <View style={styles.statChip}><Ionicons name="people-outline" size={14} color="#0F766E" /><Text style={styles.statText}>{formatNumber(item.participants)}</Text></View>
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
            placeholder={t('mobile:races.searchPlaceholder')}
            value={searchQuery}
            onChangeText={setSearchQuery}
            placeholderTextColor="#9CA3AF"
            accessibilityRole="search"
            accessibilityLabel={t('mobile:races.searchLabel')}
            accessibilityState={{ disabled: false }}
          />
        </View>
      </View>

      {isLoading && races.length === 0 ? (
        <View style={styles.center}><ActivityIndicator size="large" color="#0F766E" /></View>
      ) : (
        <>
          {errorKind && (
            <RequestErrorState
              resource="race"
              kind={errorKind}
              onRetry={() => void loadPage(failedPage && failedPage > 0 ? failedPage : 0, Boolean(failedPage && failedPage > 0))}
              compact
            />
          )}
          <FlatList
            data={filteredRaces}
            keyExtractor={(item) => item.id}
            renderItem={renderRaceCard}
            contentContainerStyle={styles.racesList}
            showsVerticalScrollIndicator={false}
            refreshControl={<RefreshControl refreshing={refreshing} onRefresh={onRefresh} />}
            ListEmptyComponent={!errorKind ? <View style={styles.emptyState}><Text style={styles.emptyText}>{t('mobile:races.empty')}</Text></View> : null}
            ListFooterComponent={hasMore ? (
              <TouchableOpacity
                style={styles.loadMoreButton}
                onPress={loadMore}
                disabled={isLoadingMore}
                accessibilityRole="button"
                accessibilityLabel={t('mobile:races.loadMore')}
                accessibilityState={{ busy: isLoadingMore, disabled: isLoadingMore }}
              >
                {isLoadingMore ? <ActivityIndicator color="#0F766E" /> : <Text style={styles.loadMoreText}>{t('mobile:races.loadMore')}</Text>}
              </TouchableOpacity>
            ) : null}
          />
        </>
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
  loadMoreButton: { alignItems: 'center', borderColor: '#0F766E', borderRadius: 10, borderWidth: 1, marginBottom: 24, paddingHorizontal: 18, paddingVertical: 12 },
  loadMoreText: { color: '#0F766E', fontSize: 14, fontWeight: '700' },
})
