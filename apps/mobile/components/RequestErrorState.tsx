import { Ionicons } from '@expo/vector-icons';
import { StyleSheet, Text, TouchableOpacity, View } from 'react-native';
import { useTranslation } from 'react-i18next';
import type { ApiErrorKind } from '@/lib/api';

type RequestErrorStateProps = {
  kind: ApiErrorKind;
  resource: 'race' | 'favorites' | 'results';
  onRetry: () => void;
  compact?: boolean;
};

export function RequestErrorState({ kind, resource, onRetry, compact = false }: RequestErrorStateProps) {
  const { t } = useTranslation();
  const content = {
    race: {
      network: { title: t('mobile:errors.raceNetworkTitle'), description: t('mobile:errors.raceNetworkDescription') },
      not_found: { title: t('mobile:errors.raceNotFoundTitle'), description: t('mobile:errors.raceNotFoundDescription') },
      unknown: { title: t('mobile:errors.raceGenericTitle'), description: t('mobile:errors.raceGenericDescription') },
    },
    favorites: {
      network: { title: t('mobile:errors.favoritesNetworkTitle'), description: t('mobile:errors.favoritesNetworkDescription') },
      not_found: { title: t('mobile:errors.favoritesNotFoundTitle'), description: t('mobile:errors.favoritesNotFoundDescription') },
      unknown: { title: t('mobile:errors.favoritesGenericTitle'), description: t('mobile:errors.favoritesGenericDescription') },
    },
    results: {
      network: { title: t('mobile:errors.resultsNetworkTitle'), description: t('mobile:errors.resultsNetworkDescription') },
      not_found: { title: t('mobile:errors.resultsNotFoundTitle'), description: t('mobile:errors.resultsNotFoundDescription') },
      unknown: { title: t('mobile:errors.resultsGenericTitle'), description: t('mobile:errors.resultsGenericDescription') },
    },
  }[resource][kind];
  const icon = kind === 'not_found' ? 'search-outline' : kind === 'network' ? 'cloud-offline-outline' : 'alert-circle-outline';

  return (
    <View style={[styles.container, compact && styles.compactContainer]}>
      <Ionicons name={icon} size={compact ? 28 : 44} color="#B45309" />
      <Text style={[styles.title, compact && styles.compactTitle]}>{content.title}</Text>
      <Text style={styles.description}>{content.description}</Text>
      <TouchableOpacity
        style={styles.retryButton}
        onPress={onRetry}
        accessibilityRole="button"
        accessibilityLabel={t('mobile:errors.retry')}
        accessibilityState={{ disabled: false }}
      >
        <Text style={styles.retryButtonText}>{t('mobile:errors.retry')}</Text>
      </TouchableOpacity>
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    alignItems: 'center',
    backgroundColor: '#FFFBEB',
    borderColor: '#FDE68A',
    borderRadius: 16,
    borderWidth: 1,
    justifyContent: 'center',
    padding: 24,
  },
  compactContainer: {
    padding: 16,
  },
  title: {
    color: '#92400E',
    fontSize: 19,
    fontWeight: '700',
    marginTop: 10,
    textAlign: 'center',
  },
  compactTitle: {
    fontSize: 16,
  },
  description: {
    color: '#92400E',
    fontSize: 14,
    lineHeight: 20,
    marginTop: 6,
    textAlign: 'center',
  },
  retryButton: {
    backgroundColor: '#92400E',
    borderRadius: 10,
    marginTop: 16,
    paddingHorizontal: 18,
    paddingVertical: 11,
  },
  retryButtonText: {
    color: '#FFFFFF',
    fontSize: 14,
    fontWeight: '700',
  },
});
