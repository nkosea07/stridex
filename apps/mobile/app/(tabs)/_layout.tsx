import { Tabs } from 'expo-router';
import { Ionicons } from '@expo/vector-icons';
import { useTheme } from '@/contexts/theme';
import { useTranslation } from 'react-i18next';
import { getPreferredCapability, getUserCapabilities, hasCapability, isOperationsRole } from '@/lib/capabilities';
import { useAuthStore } from '@/store/auth';

export default function TabLayout() {
  const { colors } = useTheme();
  const { t } = useTranslation();
  const { user } = useAuthStore();
  const isOperationsUser = isOperationsRole(user?.role);
  const capabilities = getUserCapabilities(user);
  const activeCapability = getPreferredCapability(user);
  const hasPersonalCapability = !isOperationsUser && capabilities.length > 0;
  const athleteModeAvailable = !isOperationsUser && hasCapability(user, 'ATHLETE');
  const spectatorModeAvailable = !isOperationsUser && hasCapability(user, 'SPECTATOR');

  return (
    <Tabs
      screenOptions={{
        tabBarActiveTintColor: colors.primary,
        tabBarInactiveTintColor: colors.subText,
        tabBarStyle: {
          backgroundColor: colors.cardBackground,
          borderTopWidth: 1,
          borderTopColor: colors.border,
          paddingBottom: 8,
          paddingTop: 8,
          height: 60,
        },
        tabBarLabelStyle: {
          fontSize: 12,
          fontWeight: '500',
        },
        headerStyle: {
          backgroundColor: colors.primary,
        },
        headerTintColor: colors.cardBackground,
        headerTitleStyle: {
          fontWeight: 'bold',
        },
      }}
    >
      <Tabs.Screen
        name="index"
        options={{
          title: 'Home',
          tabBarIcon: ({ color, size }) => (
            <Ionicons name="home" size={size} color={color} />
          ),
        }}
      />
      <Tabs.Screen
        name="races"
        options={{
          title: activeCapability === 'SPECTATOR' ? t('mobile:register.followRaces') : t('mobile:register.joinRace'),
          href: hasPersonalCapability ? undefined : null,
          tabBarIcon: ({ color, size }) => (
            <Ionicons name="flag" size={size} color={color} />
          ),
        }}
      />
      <Tabs.Screen
        name="my-races"
        options={{
          title: 'My Races',
          href: athleteModeAvailable && activeCapability === 'ATHLETE' ? undefined : null,
          tabBarIcon: ({ color, size }) => (
            <Ionicons name="calendar" size={size} color={color} />
          ),
        }}
      />
      <Tabs.Screen
        name="results"
        options={{
          title: 'Results',
          href: athleteModeAvailable && activeCapability === 'ATHLETE' ? undefined : null,
          tabBarIcon: ({ color, size }) => (
            <Ionicons name="trophy" size={size} color={color} />
          ),
        }}
      />
      <Tabs.Screen
        name="followed"
        options={{
          title: t('mobile:following.title'),
          href: spectatorModeAvailable && activeCapability === 'SPECTATOR' ? undefined : null,
          tabBarIcon: ({ color, size }) => (
            <Ionicons name="heart" size={size} color={color} />
          ),
        }}
      />
      <Tabs.Screen
        name="profile"
        options={{
          title: 'Profile',
          tabBarIcon: ({ color, size }) => (
            <Ionicons name="person" size={size} color={color} />
          ),
        }}
      />
    </Tabs>
  );
}
