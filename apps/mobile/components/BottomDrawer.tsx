import React from 'react';
import { View, Text, StyleSheet, Switch } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import Animated, {
  useAnimatedStyle,
  useSharedValue,
  withSpring,
  interpolate,
  Extrapolate,
} from 'react-native-reanimated';
import { useTheme } from '@/contexts/theme';

const DRAWER_MIN_HEIGHT = 120;
const DRAWER_MAX_HEIGHT = 350;

interface BottomDrawerProps {
  elapsedTime: string;
  distance: string;
  pace: string;
  progress: number; // 0 to 1
  isTracking: boolean;
  batterySaver: boolean;
  onBatterySaverChange: (enabled: boolean) => void;
  onStartTracking?: () => void;
  raceName?: string;
}

export const BottomDrawer: React.FC<BottomDrawerProps> = ({
  elapsedTime,
  distance,
  pace,
  progress,
  isTracking,
  batterySaver,
  onBatterySaverChange,
  onStartTracking,
  raceName,
}) => {
  const { colors } = useTheme();
  const translateY = useSharedValue(0);
  const gestureStartY = useSharedValue(0);

  const panGesture = Gesture.Pan()
    .onBegin(() => {
      gestureStartY.value = translateY.value;
    })
    .onUpdate((event) => {
      translateY.value = Math.max(
        -(DRAWER_MAX_HEIGHT - DRAWER_MIN_HEIGHT),
        Math.min(0, gestureStartY.value + event.translationY)
      );
    })
    .onEnd((event) => {
      if (event.velocityY < -500 || translateY.value < -(DRAWER_MAX_HEIGHT - DRAWER_MIN_HEIGHT) / 2) {
        translateY.value = withSpring(-(DRAWER_MAX_HEIGHT - DRAWER_MIN_HEIGHT));
      } else {
        translateY.value = withSpring(0);
      }
    });

  const animatedStyle = useAnimatedStyle(() => {
    return {
      transform: [{ translateY: translateY.value }],
    };
  });

  const contentOpacity = useAnimatedStyle(() => {
    return {
      opacity: interpolate(
        translateY.value,
        [-(DRAWER_MAX_HEIGHT - DRAWER_MIN_HEIGHT), 0],
        [1, 0.5],
        Extrapolate.CLAMP
      ),
    };
  });

  return (
    <GestureDetector gesture={panGesture}>
      <Animated.View style={[styles.container, { backgroundColor: colors.cardBackground, height: DRAWER_MAX_HEIGHT }, animatedStyle]}>
        {/* Handle */}
        <View style={styles.handleContainer}>
          <View style={[styles.handle, { backgroundColor: colors.border }]} />
        </View>

        <View style={styles.header}>
          <Text style={[styles.raceName, { color: colors.text }]}>{raceName || 'Race Tracking'}</Text>
          <Text style={[styles.timer, { color: colors.primary }]}>{elapsedTime}</Text>
        </View>

        <Animated.View style={[styles.content, contentOpacity]}>
          <View style={styles.statsRow}>
            <View style={styles.statItem}>
              <Text style={[styles.statLabel, { color: colors.subText }]}>DISTANCE</Text>
              <Text style={[styles.statValue, { color: colors.text }]}>{distance} km</Text>
            </View>
            <View style={styles.statItem}>
              <Text style={[styles.statLabel, { color: colors.subText }]}>PACE</Text>
              <Text style={[styles.statValue, { color: colors.text }]}>{pace} /km</Text>
            </View>
          </View>

          {/* Race Progress Chart */}
          <View style={styles.progressContainer}>
            <View style={styles.progressHeader}>
              <Text style={[styles.statLabel, { color: colors.subText }]}>RACE PROGRESS</Text>
              <Text style={[styles.progressText, { color: colors.text }]}>{Math.round(progress * 100)}%</Text>
            </View>
            <View style={[styles.progressBarBase, { backgroundColor: colors.border }]}>
              <View 
                style={[
                  styles.progressBarFill, 
                  { 
                    backgroundColor: colors.primary, 
                    width: `${Math.min(100, progress * 100)}%` 
                  }
                ]} 
              />
            </View>
          </View>

          {/* Settings Row */}
          <View style={styles.settingsRow}>
             <View style={styles.settingsLabelContainer}>
                <Text style={[styles.statLabel, { color: colors.subText, marginBottom: 0 }]}>BATTERY SAVER</Text>
                <Text style={[styles.settingsSubtext, { color: colors.subText }]}>Reduces GPS frequency</Text>
             </View>
             <Switch
                value={batterySaver}
                onValueChange={onBatterySaverChange}
                trackColor={{ false: colors.border, true: colors.primary + '80' }}
                thumbColor={batterySaver ? colors.primary : '#f4f3f4'}
             />
          </View>
        </Animated.View>
      </Animated.View>
    </GestureDetector>
  );
};

const styles = StyleSheet.create({
  container: {
    position: 'absolute',
    bottom: -(DRAWER_MAX_HEIGHT - DRAWER_MIN_HEIGHT),
    left: 0,
    right: 0,
    borderTopLeftRadius: 24,
    borderTopRightRadius: 24,
    shadowColor: '#000',
    shadowOffset: { width: 0, height: -4 },
    shadowOpacity: 0.1,
    shadowRadius: 10,
    elevation: 10,
    paddingHorizontal: 20,
  },
  handleContainer: {
    width: '100%',
    height: 30,
    justifyContent: 'center',
    alignItems: 'center',
  },
  handle: {
    width: 40,
    height: 5,
    borderRadius: 2.5,
  },
  header: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    marginBottom: 20,
  },
  raceName: {
    fontFamily: 'FiraSans_600SemiBold',
    fontSize: 18,
  },
  timer: {
    fontFamily: 'FiraCode_700Bold',
    fontSize: 24,
  },
  content: {
    flex: 1,
  },
  statsRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    marginBottom: 24,
  },
  statItem: {
    flex: 1,
  },
  statLabel: {
    fontFamily: 'FiraSans_500Medium',
    fontSize: 12,
    letterSpacing: 1,
    marginBottom: 4,
  },
  statValue: {
    fontFamily: 'FiraCode_600SemiBold',
    fontSize: 20,
  },
  settingsRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    marginTop: 24,
    paddingTop: 16,
    borderTopWidth: 1,
    borderTopColor: 'rgba(0,0,0,0.05)',
  },
  settingsLabelContainer: {
    flex: 1,
  },
  settingsSubtext: {
    fontSize: 10,
    fontFamily: 'FiraSans_400Regular',
    marginTop: 2,
  },
  progressContainer: {
    marginTop: 8,
  },
  progressHeader: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'flex-end',
    marginBottom: 8,
  },
  progressText: {
    fontFamily: 'FiraCode_500Medium',
    fontSize: 14,
  },
  progressBarBase: {
    height: 8,
    borderRadius: 4,
    overflow: 'hidden',
  },
  progressBarFill: {
    height: '100%',
    borderRadius: 4,
  },
});
