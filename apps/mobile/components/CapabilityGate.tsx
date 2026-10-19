import { ActivityIndicator, Alert, Linking, StyleSheet, Text, TouchableOpacity, View } from 'react-native';
import { router } from 'expo-router';
import { useTranslation } from 'react-i18next';
import { useAuthStore } from '@/store/auth';
import { getPreferredCapability, isOperationsRole } from '@/lib/capabilities';
import type { UserCapability } from '@/types';
import type { ReactNode } from 'react';

type CapabilityGateProps = {
  capability?: UserCapability;
  anyOf?: UserCapability[];
  children: ReactNode;
};

type AccessStateProps = {
  kind: 'signin' | 'capability' | 'operations';
};

export function AccessState({ kind }: AccessStateProps) {
  const { t } = useTranslation();

  const content = {
    signin: {
      title: t('mobile:access.signInTitle'),
      description: t('mobile:access.signInDescription'),
      action: t('mobile:access.signInAction'),
      onPress: () => router.push('/(auth)/login'),
    },
    capability: {
      title: t('mobile:access.capabilityTitle'),
      description: t('mobile:access.capabilityDescription'),
      action: t('mobile:access.switchModeAction'),
      onPress: () => router.push('/(tabs)/profile'),
    },
    operations: {
      title: t('mobile:access.operationsTitle'),
      description: t('mobile:access.operationsDescription'),
      action: t('mobile:access.webConsoleAction'),
      onPress: () => {
        const webUrl = process.env.EXPO_PUBLIC_WEB_URL;
        if (!webUrl) {
          Alert.alert(t('mobile:access.operationsTitle'), t('mobile:access.webConsoleUnavailable'));
          return;
        }
        void Linking.openURL(webUrl);
      },
    },
  }[kind];

  return (
    <View style={styles.container}>
      <View style={styles.card}>
        <Text style={styles.title}>{content.title}</Text>
        <Text style={styles.description}>{content.description}</Text>
        <TouchableOpacity style={styles.action} onPress={content.onPress} accessibilityRole="button">
          <Text style={styles.actionText}>{content.action}</Text>
        </TouchableOpacity>
      </View>
    </View>
  );
}

export function UnsupportedMobileState() {
  return <AccessState kind="operations" />;
}

type AuthenticatedGateProps = {
  children: ReactNode;
};

export function AuthenticatedGate({ children }: AuthenticatedGateProps) {
  const { isAuthenticated, isLoading } = useAuthStore();

  if (isLoading) {
    return (
      <View style={styles.container}>
        <ActivityIndicator size="large" color="#0F766E" />
      </View>
    );
  }

  if (!isAuthenticated) return <AccessState kind="signin" />;
  return <>{children}</>;
}

export function CapabilityGate({ capability, anyOf, children }: CapabilityGateProps) {
  const { user, isAuthenticated, isLoading } = useAuthStore();
  const requiredCapabilities = anyOf ?? (capability ? [capability] : []);

  if (isLoading) {
    return (
      <View style={styles.container}>
        <ActivityIndicator size="large" color="#0F766E" />
      </View>
    );
  }

  if (!isAuthenticated) return <AccessState kind="signin" />;
  if (isOperationsRole(user?.role)) return <AccessState kind="operations" />;
  const activeCapability = getPreferredCapability(user);
  if (!activeCapability || !requiredCapabilities.includes(activeCapability)) {
    return <AccessState kind="capability" />;
  }

  return <>{children}</>;
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    justifyContent: 'center',
    alignItems: 'center',
    padding: 24,
    backgroundColor: '#F3F4F6',
  },
  card: {
    width: '100%',
    maxWidth: 420,
    padding: 24,
    borderRadius: 16,
    backgroundColor: '#FFFFFF',
    alignItems: 'center',
    shadowColor: '#000000',
    shadowOffset: { width: 0, height: 2 },
    shadowOpacity: 0.08,
    shadowRadius: 8,
    elevation: 2,
  },
  title: {
    color: '#1F2937',
    fontSize: 20,
    fontWeight: '700',
    textAlign: 'center',
  },
  description: {
    marginTop: 10,
    color: '#6B7280',
    fontSize: 15,
    lineHeight: 22,
    textAlign: 'center',
  },
  action: {
    marginTop: 20,
    paddingHorizontal: 20,
    paddingVertical: 12,
    borderRadius: 10,
    backgroundColor: '#0F766E',
  },
  actionText: {
    color: '#FFFFFF',
    fontSize: 15,
    fontWeight: '700',
  },
});
