import { Ionicons } from '@expo/vector-icons';
import { Linking, Platform, StyleSheet, Text, TouchableOpacity, View } from 'react-native';
import { useTranslation } from 'react-i18next';
import { useTheme } from '@/contexts/theme';
import { storeUrlFor } from '@/lib/appVersion';
import { useAppUpdateStore } from '@/store/appUpdate';

export function UpdateRequiredScreen() {
  const { t } = useTranslation();
  const { colors } = useTheme();
  const config = useAppUpdateStore((state) => state.config);
  const storeUrl = storeUrlFor(config, Platform.OS);

  return (
    <View style={[styles.container, { backgroundColor: colors.background }]} accessibilityLiveRegion="polite">
      <Ionicons name="cloud-download-outline" size={56} color={colors.primary} />
      <Text style={[styles.title, { color: colors.text }]} accessibilityRole="header">
        {t('mobile:appUpdate.title')}
      </Text>
      <Text style={[styles.body, { color: colors.subText }]}>{t('mobile:appUpdate.body')}</Text>
      {storeUrl ? (
        <TouchableOpacity
          style={[styles.button, { backgroundColor: colors.primary }]}
          onPress={() => void Linking.openURL(storeUrl)}
          accessibilityRole="button"
          accessibilityLabel={t('mobile:appUpdate.action')}
        >
          <Text style={styles.buttonText}>{t('mobile:appUpdate.action')}</Text>
        </TouchableOpacity>
      ) : (
        <Text style={[styles.body, { color: colors.subText }]}>{t('mobile:appUpdate.storeFallback')}</Text>
      )}
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    alignItems: 'center',
    flex: 1,
    gap: 16,
    justifyContent: 'center',
    padding: 32,
  },
  title: {
    fontSize: 22,
    fontWeight: '700',
    textAlign: 'center',
  },
  body: {
    fontSize: 15,
    lineHeight: 22,
    textAlign: 'center',
  },
  button: {
    borderRadius: 12,
    minHeight: 48,
    justifyContent: 'center',
    paddingHorizontal: 28,
  },
  buttonText: {
    color: '#FFFFFF',
    fontSize: 16,
    fontWeight: '600',
  },
});
