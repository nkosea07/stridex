import { useMemo } from 'react';
import { useTheme } from '@/contexts/theme';
import type { MapTheme } from '@/lib/map/types';

export const USER_LOCATION_COLOR = '#2563EB';

export function useMapTheme(): MapTheme {
  const { colors } = useTheme();
  return useMemo(() => ({
    primary: colors.primary,
    surface: colors.cardBackground,
    text: colors.subText,
    userLocation: USER_LOCATION_COLOR,
  }), [colors.primary, colors.cardBackground, colors.subText]);
}
