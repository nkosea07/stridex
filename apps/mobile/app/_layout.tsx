import { useEffect, useRef } from 'react';
import { Stack, router, useSegments } from 'expo-router';
import { StatusBar } from 'expo-status-bar';
import * as SplashScreen from 'expo-splash-screen';
import { useFonts } from 'expo-font';
import { 
  FiraSans_300Light,
  FiraSans_400Regular, 
  FiraSans_500Medium, 
  FiraSans_600SemiBold, 
  FiraSans_700Bold 
} from '@expo-google-fonts/fira-sans';
import { 
  FiraCode_400Regular, 
  FiraCode_500Medium, 
  FiraCode_600SemiBold, 
  FiraCode_700Bold 
} from '@expo-google-fonts/fira-code';
import { initializeAuth, useAuthStore } from '@/store/auth';
import { LEGAL_ROUTES, needsLegalAcceptance } from '@/lib/legal';
import { useTrackingStore } from '@/store/tracking';
import { useAppUpdateStore } from '@/store/appUpdate';
import { setAppIdentity, setUpdateRequiredHandler } from '@/lib/api';
import { UpdateRequiredScreen } from '@/components/UpdateRequiredScreen';
import Constants from 'expo-constants';
import { addNotificationResponseListener, takeLaunchNotificationData } from '@/lib/notifications';
import { routeForNotification } from '@/lib/notificationRoutes';
import { I18nextProvider, useTranslation } from 'react-i18next';
import i18n, { initI18n } from '@/lib/i18n';
import { GestureHandlerRootView } from 'react-native-gesture-handler';
import { Alert, Platform, StyleSheet } from 'react-native';
import { ThemeProvider, useTheme } from '@/contexts/theme';

// Keep splash screen visible while loading resources
SplashScreen.preventAutoHideAsync();

const APP_VERSION = Constants.expoConfig?.version ?? null;
setAppIdentity({ version: APP_VERSION, platform: Platform.OS });
setUpdateRequiredHandler(() => useAppUpdateStore.getState().markUpdateRequired());

export default function RootLayout() {
  const [fontsLoaded] = useFonts({
    FiraSans_300Light,
    FiraSans_400Regular,
    FiraSans_500Medium,
    FiraSans_600SemiBold,
    FiraSans_700Bold,
    FiraCode_400Regular,
    FiraCode_500Medium,
    FiraCode_600SemiBold,
    FiraCode_700Bold,
  });

  useEffect(() => {
    if (!fontsLoaded) return;

    let isMounted = true;

    const init = async () => {
      try {
        await initI18n();
        await useAppUpdateStore.getState().check(APP_VERSION);
        await initializeAuth();
        await useTrackingStore.getState().recoverActiveTrackingSession();
      } catch (error) {
        console.error('Failed to initialize app state:', error);
      } finally {
        if (isMounted) {
          await SplashScreen.hideAsync();
        }
      }
    };

    init();

    return () => {
      isMounted = false;
    };
  }, [fontsLoaded]);

  if (!fontsLoaded) {
    return null;
  }

  return (
    <I18nextProvider i18n={i18n}>
      <ThemeProvider>
        <RootNavigator />
      </ThemeProvider>
    </I18nextProvider>
  );
}

function RootNavigator() {
  const { colors, resolvedTheme } = useTheme();
  const { t } = useTranslation();
  const trackingStopNotice = useTrackingStore((state) => state.trackingStopNotice);
  const segments = useSegments();
  const user = useAuthStore((state) => state.user);
  const isAuthenticated = useAuthStore((state) => state.isAuthenticated);
  const isAuthLoading = useAuthStore((state) => state.isLoading);
  const currentSegment = segments[0] as string | undefined;
  const legalAcceptanceRequired = !isAuthLoading && isAuthenticated && needsLegalAcceptance(user);
  const updateRequired = useAppUpdateStore((state) => state.updateRequired);
  const { i18n: i18nInstance } = useTranslation();
  const language = i18nInstance.resolvedLanguage ?? i18nInstance.language;
  const syncedLanguage = useRef<string | null>(null);

  useEffect(() => {
    if (!isAuthenticated || !user || !language || user.preferredLocale === language) return;
    const key = `${user.id}:${language}`;
    if (syncedLanguage.current === key) return;
    syncedLanguage.current = key;
    useAuthStore.getState().updateProfile({ preferredLocale: language }).catch(() => undefined);
  }, [isAuthenticated, user, language]);

  useEffect(() => {
    if (!legalAcceptanceRequired) return;
    if (currentSegment === '(auth)' || currentSegment === 'tracking') return;
    if (currentSegment && LEGAL_ROUTES.has(currentSegment)) return;
    router.replace('/legal-acceptance');
  }, [legalAcceptanceRequired, currentSegment]);

  useEffect(() => {
    if (!trackingStopNotice) return;
    const body = trackingStopNotice.reason === 'closed'
      ? t('mobile:trackingStop.closed')
      : trackingStopNotice.reason === 'not_registered'
        ? t('mobile:trackingStop.notRegistered')
        : t('mobile:trackingStop.notPermitted');
    Alert.alert(t('mobile:trackingStop.title'), body, [{
      text: t('mobile:trackingStop.ok'),
      onPress: () => void useTrackingStore.getState().acknowledgeTrackingStop(),
    }], { cancelable: false });
  }, [trackingStopNotice, t]);

  useEffect(() => {
    const subscription = addNotificationResponseListener((response) => {
      const data = response.notification.request.content.data;
      router.push(routeForNotification(data && typeof data === 'object' ? data as Record<string, unknown> : null));
    });
    return () => subscription.remove();
  }, []);

  const launchRouteChecked = useRef(false);
  useEffect(() => {
    if (isAuthLoading || !isAuthenticated || legalAcceptanceRequired || updateRequired || launchRouteChecked.current) return;
    launchRouteChecked.current = true;
    void takeLaunchNotificationData().then((data) => {
      if (data) router.push(routeForNotification(data));
    });
  }, [isAuthLoading, isAuthenticated, legalAcceptanceRequired, updateRequired]);

  if (updateRequired) {
    return (
      <GestureHandlerRootView style={[styles.container, { backgroundColor: colors.background }]}>
        <StatusBar style={resolvedTheme === 'dark' ? 'light' : 'dark'} />
        <UpdateRequiredScreen />
      </GestureHandlerRootView>
    );
  }

  return (
    <GestureHandlerRootView style={[styles.container, { backgroundColor: colors.background }]}>
      <StatusBar style={resolvedTheme === 'dark' ? 'light' : 'dark'} />
      <Stack
        screenOptions={{
          headerStyle: {
            backgroundColor: colors.primary,
          },
          headerTintColor: colors.cardBackground,
          headerTitleStyle: {
            fontWeight: 'bold',
          },
          contentStyle: {
            backgroundColor: colors.background,
          },
        }}
      >
        <Stack.Screen name="(tabs)" options={{ headerShown: false }} />
        <Stack.Screen name="(auth)" options={{ headerShown: false }} />
        <Stack.Screen
          name="race/[id]"
          options={{
            title: t('mobile:screens.raceDetails'),
            presentation: 'card',
          }}
        />
        <Stack.Screen
          name="tracking"
          options={{
            title: t('mobile:screens.liveTracking'),
            presentation: 'fullScreenModal',
            gestureEnabled: false,
          }}
        />
        <Stack.Screen
          name="scanner"
          options={{
            title: t('mobile:scanner.title'),
            presentation: 'modal',
          }}
        />
        <Stack.Screen
          name="race-live/[id]"
          options={{
            title: t('mobile:raceLive.title'),
            presentation: 'card',
          }}
        />
        <Stack.Screen
          name="race-gallery/[id]"
          options={{
            title: t('mobile:screens.raceGallery'),
            presentation: 'card',
          }}
        />
        <Stack.Screen name="verify-email" options={{ headerShown: false }} />
        <Stack.Screen name="reset-password" options={{ headerShown: false }} />
        <Stack.Screen
          name="settings"
          options={{
            title: t('mobile:settings.title'),
            presentation: 'card',
          }}
        />
        <Stack.Screen
          name="check-in"
          options={{
            title: t('mobile:checkIn.title'),
            presentation: 'card',
          }}
        />
        <Stack.Screen
          name="athlete/[raceId]/[participantId]"
          options={{
            title: t('mobile:screens.athleteLive'),
            presentation: 'card',
          }}
        />
        <Stack.Screen
          name="notifications"
          options={{
            title: t('mobile:screens.notifications'),
            presentation: 'card',
          }}
        />
        <Stack.Screen
          name="privacy"
          options={{
            title: t('legal:common.privacyPolicy'),
            presentation: 'card',
          }}
        />
        <Stack.Screen
          name="terms"
          options={{
            title: t('legal:common.terms'),
            presentation: 'card',
          }}
        />
        <Stack.Screen
          name="legal-acceptance"
          options={{
            title: t('legal:acceptance.title'),
            headerShown: false,
            gestureEnabled: false,
          }}
        />
        <Stack.Screen
          name="account-deletion"
          options={{
            title: t('account:deletion.title'),
            presentation: 'card',
          }}
        />
      </Stack>
    </GestureHandlerRootView>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
  },
});
