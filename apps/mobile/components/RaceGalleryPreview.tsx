import { useEffect, useState } from 'react';
import { StyleSheet, Text, TouchableOpacity, View } from 'react-native';
import { router } from 'expo-router';
import { Ionicons } from '@expo/vector-icons';
import { useTranslation } from 'react-i18next';
import { api } from '@/lib/api';
import { formatNumber } from '@/lib/format';
import { MediaTile } from '@/components/MediaTile';
import { MediaViewer, openRaceVideo } from '@/components/MediaViewer';
import type { PublicRaceMedia } from '@/types';

export const GALLERY_PREVIEW_SIZE = 6;

export function RaceGalleryPreview({ raceId }: { raceId: string }) {
  const { t } = useTranslation();
  const [items, setItems] = useState<PublicRaceMedia[]>([]);
  const [viewerIndex, setViewerIndex] = useState<number | null>(null);

  useEffect(() => {
    let active = true;
    api.getPublicRaceMedia(raceId)
      .then((media) => {
        if (active) setItems(media);
      })
      .catch(() => {
        if (active) setItems([]);
      });
    return () => { active = false; };
  }, [raceId]);

  if (items.length === 0) return null;

  const preview = items.slice(0, GALLERY_PREVIEW_SIZE);
  const open = (position: number) => {
    const item = items[position];
    if (item.mediaType === 'VIDEO') {
      void openRaceVideo(raceId, item.id);
    } else {
      setViewerIndex(position);
    }
  };

  return (
    <View style={styles.section}>
      <Text style={styles.sectionTitle} accessibilityRole="header">{t('mobile:gallery.title')}</Text>
      <View style={styles.grid}>
        {preview.map((item, position) => (
          <View key={item.id} style={styles.cell}>
            <MediaTile raceId={raceId} item={item} position={position} total={items.length} onPress={() => open(position)} />
          </View>
        ))}
      </View>
      {items.length > GALLERY_PREVIEW_SIZE && (
        <TouchableOpacity
          style={styles.seeAll}
          onPress={() => router.push({ pathname: '/race-gallery/[id]', params: { id: raceId } })}
          accessibilityRole="button"
        >
          <Text style={styles.seeAllText}>{t('mobile:gallery.seeAll', { total: formatNumber(items.length) })}</Text>
          <Ionicons name="chevron-forward" size={18} color="#0F766E" />
        </TouchableOpacity>
      )}
      <MediaViewer
        raceId={raceId}
        items={items}
        initialIndex={viewerIndex}
        onClose={() => setViewerIndex(null)}
      />
    </View>
  );
}

const styles = StyleSheet.create({
  section: { marginBottom: 24 },
  sectionTitle: { fontSize: 18, fontWeight: '600', color: '#1F2937', marginBottom: 12 },
  grid: { flexDirection: 'row', flexWrap: 'wrap', marginHorizontal: -4 },
  cell: { width: '33.333%', padding: 4 },
  seeAll: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    gap: 4,
    marginTop: 12,
    paddingVertical: 12,
    borderRadius: 12,
    backgroundColor: '#F0FDFA',
  },
  seeAllText: { color: '#0F766E', fontSize: 15, fontWeight: '600' },
});
