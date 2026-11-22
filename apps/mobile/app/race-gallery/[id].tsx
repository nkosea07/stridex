import { useCallback, useEffect, useState } from 'react';
import { ActivityIndicator, FlatList, RefreshControl, StyleSheet, Text, TouchableOpacity, View } from 'react-native';
import { useLocalSearchParams } from 'expo-router';
import { Ionicons } from '@expo/vector-icons';
import { useTranslation } from 'react-i18next';
import { useTheme } from '@/contexts/theme';
import { api, getApiErrorKind, getNonEmptyRouteId, type ApiErrorKind } from '@/lib/api';
import { RequestErrorState } from '@/components/RequestErrorState';
import { MediaTile } from '@/components/MediaTile';
import { MediaViewer, openRaceVideo } from '@/components/MediaViewer';
import type { PublicRaceMedia } from '@/types';

const COLUMNS = 3;

export default function RaceGalleryScreen() {
  const { t } = useTranslation();
  const { colors } = useTheme();
  const { id } = useLocalSearchParams<{ id?: string }>();
  const raceId = getNonEmptyRouteId(id);
  const [items, setItems] = useState<PublicRaceMedia[]>([]);
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);
  const [errorKind, setErrorKind] = useState<ApiErrorKind | null>(null);
  const [viewerIndex, setViewerIndex] = useState<number | null>(null);

  const load = useCallback(async () => {
    if (!raceId) return;
    try {
      setItems(await api.getPublicRaceMedia(raceId));
      setErrorKind(null);
    } catch (error) {
      setErrorKind(getApiErrorKind(error));
    }
  }, [raceId]);

  useEffect(() => {
    setLoading(true);
    void load().finally(() => setLoading(false));
  }, [load]);

  const refresh = useCallback(async () => {
    setRefreshing(true);
    await load();
    setRefreshing(false);
  }, [load]);

  if (!raceId || errorKind === 'not_found') {
    return <RequestErrorState resource="race" kind="not_found" onRetry={() => void load()} />;
  }

  if (loading) {
    return (
      <View style={[styles.center, { backgroundColor: colors.background }]} accessibilityLiveRegion="polite">
        <ActivityIndicator size="large" color={colors.primary} />
        <Text style={[styles.muted, { color: colors.subText }]}>{t('mobile:gallery.loading')}</Text>
      </View>
    );
  }

  if (errorKind && items.length === 0) {
    return (
      <View style={[styles.center, { backgroundColor: colors.background }]} accessibilityLiveRegion="polite">
        <Ionicons name={errorKind === 'network' ? 'cloud-offline-outline' : 'alert-circle-outline'} size={44} color="#B45309" />
        <Text style={[styles.errorTitle, { color: colors.text }]}>{t('mobile:gallery.loadFailed')}</Text>
        <Text style={[styles.muted, { color: colors.subText }]}>{t('mobile:errors.networkDescription')}</Text>
        <TouchableOpacity
          style={[styles.retry, { backgroundColor: colors.primary }]}
          onPress={() => {
            setLoading(true);
            void load().finally(() => setLoading(false));
          }}
          accessibilityRole="button"
        >
          <Text style={styles.retryText}>{t('mobile:errors.retry')}</Text>
        </TouchableOpacity>
      </View>
    );
  }

  const open = (position: number) => {
    const item = items[position];
    if (item.mediaType === 'VIDEO') {
      void openRaceVideo(raceId, item.id);
    } else {
      setViewerIndex(position);
    }
  };

  return (
    <View style={[styles.container, { backgroundColor: colors.background }]}>
      <FlatList
        data={items}
        keyExtractor={(item) => item.id}
        numColumns={COLUMNS}
        contentContainerStyle={styles.list}
        refreshControl={<RefreshControl refreshing={refreshing} onRefresh={() => void refresh()} tintColor={colors.primary} />}
        ListHeaderComponent={
          <Text style={[styles.note, { color: colors.subText }]}>{t('mobile:gallery.consentNote')}</Text>
        }
        ListEmptyComponent={
          <View style={styles.empty}>
            <Ionicons name="images-outline" size={44} color={colors.subText} />
            <Text style={[styles.muted, { color: colors.subText }]}>{t('mobile:gallery.empty')}</Text>
          </View>
        }
        renderItem={({ item, index }) => (
          <View style={styles.cell}>
            <MediaTile raceId={raceId} item={item} position={index} total={items.length} onPress={() => open(index)} />
          </View>
        )}
      />
      <MediaViewer raceId={raceId} items={items} initialIndex={viewerIndex} onClose={() => setViewerIndex(null)} />
    </View>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1 },
  center: { flex: 1, alignItems: 'center', justifyContent: 'center', gap: 12, padding: 24 },
  list: { padding: 12 },
  cell: { width: `${100 / COLUMNS}%`, padding: 4 },
  note: { fontSize: 13, lineHeight: 18, paddingHorizontal: 4, paddingBottom: 12 },
  empty: { alignItems: 'center', gap: 12, paddingVertical: 48 },
  muted: { fontSize: 15, textAlign: 'center' },
  errorTitle: { fontSize: 18, fontWeight: '600', textAlign: 'center' },
  retry: { borderRadius: 12, paddingHorizontal: 20, paddingVertical: 12, marginTop: 4 },
  retryText: { color: '#FFFFFF', fontSize: 15, fontWeight: '600' },
});
