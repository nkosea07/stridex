import { useState } from 'react';
import { Image, StyleSheet, Text, TouchableOpacity, View, type StyleProp, type ViewStyle } from 'react-native';
import { Ionicons } from '@expo/vector-icons';
import { useTranslation } from 'react-i18next';
import { publicRaceMediaUrl } from '@/lib/api';
import { formatNumber } from '@/lib/format';
import type { PublicRaceMedia } from '@/types';

type MediaTileProps = {
  raceId: string;
  item: PublicRaceMedia;
  position: number;
  total: number;
  onPress: () => void;
  style?: StyleProp<ViewStyle>;
};

export function MediaTile({ raceId, item, position, total, onPress, style }: MediaTileProps) {
  const { t } = useTranslation();
  const [failed, setFailed] = useState(false);
  const isVideo = item.mediaType === 'VIDEO';
  const values = { index: formatNumber(position + 1), total: formatNumber(total) };
  const label = isVideo ? t('mobile:gallery.videoLabel', values) : t('mobile:gallery.photoLabel', values);
  const showImage = !isVideo && !failed;

  return (
    <TouchableOpacity
      style={[styles.tile, style]}
      onPress={onPress}
      accessibilityRole="button"
      accessibilityLabel={item.caption ? `${item.caption}. ${label}` : label}
      accessibilityHint={isVideo ? t('mobile:gallery.playVideoHint') : t('mobile:gallery.openPhotoHint')}
    >
      {showImage ? (
        <Image
          source={{ uri: publicRaceMediaUrl(raceId, item.id, item.hasThumbnail ? 'thumbnail' : 'content') }}
          style={styles.image}
          resizeMode="cover"
          onError={() => setFailed(true)}
        />
      ) : (
        <View style={styles.placeholder}>
          <Ionicons name={isVideo ? 'play-circle' : 'image-outline'} size={32} color="#FFFFFF" />
          {isVideo && <Text style={styles.videoText}>{t('mobile:gallery.video')}</Text>}
        </View>
      )}
    </TouchableOpacity>
  );
}

const styles = StyleSheet.create({
  tile: { aspectRatio: 1, borderRadius: 12, overflow: 'hidden', backgroundColor: '#1F2937' },
  image: { width: '100%', height: '100%' },
  placeholder: { flex: 1, alignItems: 'center', justifyContent: 'center', gap: 4, backgroundColor: '#374151' },
  videoText: { color: '#FFFFFF', fontSize: 12, fontWeight: '600' },
});
