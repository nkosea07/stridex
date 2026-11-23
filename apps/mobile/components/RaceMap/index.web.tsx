import { StyleSheet, Text, View } from 'react-native';
import { useTranslation } from 'react-i18next';
import type { RaceMapProps } from '@/lib/map/types';

export type { MapCircle, MapLine, MapPin, MapTheme, RaceMapProps } from '@/lib/map/types';
export { useMapTheme } from './useMapTheme';

export function RaceMap({ accessibilityLabel, height, fill, theme }: RaceMapProps) {
  const { t } = useTranslation();
  return (
    <View style={[styles.container, { backgroundColor: theme.surface }, fill ? styles.fill : { height: height ?? 320 }]} accessibilityLabel={accessibilityLabel}>
      <Text style={[styles.title, { color: theme.text }]}>{t('mobile:mapWeb.title')}</Text>
      <Text style={[styles.subtitle, { color: theme.text }]}>{t('mobile:mapWeb.subtitle')}</Text>
      <Text style={[styles.subtitle, { color: theme.text }]}>{t('mobile:mapWeb.info')}</Text>
    </View>
  );
}

const styles = StyleSheet.create({
  container: { alignItems: 'center', justifyContent: 'center', padding: 24, gap: 8 },
  fill: { flex: 1 },
  title: { fontSize: 16, fontWeight: '700', textAlign: 'center' },
  subtitle: { fontSize: 14, textAlign: 'center' },
});
