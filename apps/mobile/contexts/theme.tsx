import AsyncStorage from '@react-native-async-storage/async-storage';
import {
  createContext,
  useContext,
  useEffect,
  useMemo,
  useState,
  type ReactNode,
} from 'react';
import { Appearance, type ColorSchemeName } from 'react-native';
import { DarkColors, LightColors, type AppColors } from '@/constants/Colors';
import type { ThemePreference } from '@/types';

type ResolvedTheme = 'light' | 'dark';

type ThemeContextValue = {
  theme: ThemePreference;
  resolvedTheme: ResolvedTheme;
  colors: AppColors;
  setTheme: (value: ThemePreference) => Promise<void>;
};

const THEME_STORAGE_KEY = 'race_tracker_mobile_theme';
const ThemeContext = createContext<ThemeContextValue | undefined>(undefined);

function isThemePreference(value: string | null): value is ThemePreference {
  return value === 'light' || value === 'dark' || value === 'system';
}

function normalizeScheme(value: ColorSchemeName): ResolvedTheme {
  return value === 'dark' ? 'dark' : 'light';
}

export function ThemeProvider({ children }: { children: ReactNode }) {
  const [theme, setThemeState] = useState<ThemePreference>('system');
  const [systemScheme, setSystemScheme] = useState<ResolvedTheme>(normalizeScheme(Appearance.getColorScheme()));

  useEffect(() => {
    AsyncStorage.getItem(THEME_STORAGE_KEY)
      .then((stored) => {
        if (isThemePreference(stored)) {
          setThemeState(stored);
        }
      })
      .catch(() => undefined);

    const subscription = Appearance.addChangeListener(({ colorScheme }: { colorScheme: ColorSchemeName }) => {
      setSystemScheme(normalizeScheme(colorScheme));
    });
    return () => subscription.remove();
  }, []);

  const setTheme = async (value: ThemePreference) => {
    setThemeState(value);
    await AsyncStorage.setItem(THEME_STORAGE_KEY, value);
  };

  const resolvedTheme: ResolvedTheme = theme === 'system' ? systemScheme : theme;
  const colors = resolvedTheme === 'dark' ? DarkColors : LightColors;

  const value = useMemo(
    () => ({
      theme,
      resolvedTheme,
      colors,
      setTheme,
    }),
    [theme, resolvedTheme, colors],
  );

  return <ThemeContext.Provider value={value}>{children}</ThemeContext.Provider>;
}

export function useTheme() {
  const context = useContext(ThemeContext);
  if (!context) {
    throw new Error('useTheme must be used within ThemeProvider');
  }
  return context;
}
