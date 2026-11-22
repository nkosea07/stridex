import { useCallback, useEffect, useState } from 'react';
import {
  ActivityIndicator,
  FlatList,
  Image,
  Modal,
  StyleSheet,
  Text,
  TouchableOpacity,
  View,
  useWindowDimensions,
  type NativeScrollEvent,
  type NativeSyntheticEvent,
} from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { Ionicons } from '@expo/vector-icons';
import * as WebBrowser from 'expo-web-browser';
import { useTranslation } from 'react-i18next';
import { publicRaceMediaUrl } from '@/lib/api';
import { formatNumber } from '@/lib/format';
import type { PublicRaceMedia } from '@/types';

type MediaViewerProps = {
  raceId: string;
  items: PublicRaceMedia[];
  initialIndex: number | null;
  onClose: () => void;
};

export function openRaceVideo(raceId: string, mediaId: string) {
  return WebBrowser.openBrowserAsync(publicRaceMediaUrl(raceId, mediaId, 'content'));
}

function ViewerPage({ raceId, item, width, label }: { raceId: string; item: PublicRaceMedia; width: number; label: string }) {
  const { t } = useTranslation();
  const [loading, setLoading] = useState(true);
  const [failed, setFailed] = useState(false);

  if (item.mediaType === 'VIDEO') {
    return (
      <View style={[styles.page, { width }]}>
        <TouchableOpacity
          style={styles.playButton}
          onPress={() => void openRaceVideo(raceId, item.id)}
          accessibilityRole="button"
          accessibilityLabel={label}
          accessibilityHint={t('mobile:gallery.playVideoHint')}
        >
          <Ionicons name="play-circle" size={72} color="#FFFFFF" />
          <Text style={styles.playText}>{t('mobile:gallery.playVideo')}</Text>
        </TouchableOpacity>
      </View>
    );
  }

  return (
    <View style={[styles.page, { width }]}>
      {failed ? (
        <View style={styles.failed} accessibilityLiveRegion="polite">
          <Ionicons name="image-outline" size={40} color="#9CA3AF" />
          <Text style={styles.failedText}>{t('mobile:gallery.imageFailed')}</Text>
        </View>
      ) : (
        <Image
          source={{ uri: publicRaceMediaUrl(raceId, item.id, 'content') }}
          style={styles.image}
          resizeMode="contain"
          accessible
          accessibilityRole="image"
          accessibilityLabel={label}
          onLoadEnd={() => setLoading(false)}
          onError={() => {
            setLoading(false);
            setFailed(true);
          }}
        />
      )}
      {loading && !failed && <ActivityIndicator style={StyleSheet.absoluteFill} size="large" color="#FFFFFF" />}
    </View>
  );
}

export function MediaViewer({ raceId, items, initialIndex, onClose }: MediaViewerProps) {
  const { t } = useTranslation();
  const { width } = useWindowDimensions();
  const [index, setIndex] = useState(initialIndex ?? 0);
  const visible = initialIndex !== null && items.length > 0;

  useEffect(() => {
    if (initialIndex !== null) setIndex(Math.min(initialIndex, Math.max(items.length - 1, 0)));
  }, [initialIndex, items.length]);

  const onScrollEnd = useCallback((event: NativeSyntheticEvent<NativeScrollEvent>) => {
    const next = Math.round(event.nativeEvent.contentOffset.x / width);
    setIndex(Math.min(Math.max(next, 0), items.length - 1));
  }, [items.length, width]);

  const labelFor = useCallback((item: PublicRaceMedia, position: number) => {
    const values = { index: formatNumber(position + 1), total: formatNumber(items.length) };
    const label = item.mediaType === 'VIDEO'
      ? t('mobile:gallery.videoLabel', values)
      : t('mobile:gallery.photoLabel', values);
    return item.caption ? `${item.caption}. ${label}` : label;
  }, [items.length, t]);

  const current = items[index];

  return (
    <Modal visible={visible} animationType="fade" onRequestClose={onClose} supportedOrientations={['portrait', 'landscape']}>
      <SafeAreaView style={styles.container} edges={['top', 'bottom']}>
        <View style={styles.header}>
          <Text style={styles.counter} accessibilityLiveRegion="polite">
            {t('mobile:gallery.counter', { index: formatNumber(index + 1), total: formatNumber(items.length) })}
          </Text>
          <TouchableOpacity
            onPress={onClose}
            style={styles.closeButton}
            accessibilityRole="button"
            accessibilityLabel={t('mobile:gallery.close')}
            hitSlop={12}
          >
            <Ionicons name="close" size={28} color="#FFFFFF" />
          </TouchableOpacity>
        </View>
        {visible && (
          <FlatList
            key={width}
            data={items}
            keyExtractor={(item) => item.id}
            horizontal
            pagingEnabled
            showsHorizontalScrollIndicator={false}
            initialScrollIndex={Math.min(initialIndex ?? 0, items.length - 1)}
            getItemLayout={(_, position) => ({ length: width, offset: width * position, index: position })}
            onMomentumScrollEnd={onScrollEnd}
            windowSize={3}
            initialNumToRender={1}
            maxToRenderPerBatch={2}
            renderItem={({ item, index: position }) => (
              <ViewerPage raceId={raceId} item={item} width={width} label={labelFor(item, position)} />
            )}
          />
        )}
        {current?.caption ? <Text style={styles.caption}>{current.caption}</Text> : null}
      </SafeAreaView>
    </Modal>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: '#000000' },
  header: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    paddingHorizontal: 16,
    paddingVertical: 8,
  },
  counter: { color: '#E5E7EB', fontSize: 15, fontWeight: '600' },
  closeButton: { padding: 4 },
  page: { flex: 1, alignItems: 'center', justifyContent: 'center' },
  image: { width: '100%', height: '100%' },
  playButton: { alignItems: 'center', gap: 8, padding: 24 },
  playText: { color: '#FFFFFF', fontSize: 16, fontWeight: '600' },
  failed: { alignItems: 'center', gap: 8, padding: 24 },
  failedText: { color: '#D1D5DB', fontSize: 15, textAlign: 'center' },
  caption: { color: '#F3F4F6', fontSize: 15, lineHeight: 21, paddingHorizontal: 20, paddingVertical: 12 },
});
