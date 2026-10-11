import { useEffect } from 'react';
import { Stack } from 'expo-router';
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
import { initializeAuth } from '@/store/auth';
import { useTrackingStore } from '@/store/tracking';
import { GestureHandlerRootView } from 'react-native-gesture-handler';
import { StyleSheet } from 'react-native';
import { ThemeProvider, useTheme } from '@/contexts/theme';

// Keep splash screen visible while loading resources
SplashScreen.preventAutoHideAsync();

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
    <ThemeProvider>
      <RootNavigator />
    </ThemeProvider>
  );
}

function RootNavigator() {
  const { colors, resolvedTheme } = useTheme();

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
            title: 'Race Details',
            presentation: 'card',
          }}
        />
        <Stack.Screen
          name="tracking"
          options={{
            title: 'Live Tracking',
            presentation: 'fullScreenModal',
            gestureEnabled: false,
          }}
        />
        <Stack.Screen
          name="scanner"
          options={{
            title: 'Scan Checkpoint',
            presentation: 'modal',
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
