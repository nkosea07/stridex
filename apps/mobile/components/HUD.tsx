import React from 'react';
import { View, StyleSheet, TouchableOpacity, Platform } from 'react-native';
import { Ionicons } from '@expo/vector-icons';
import { useTheme } from '@/contexts/theme';

interface HUDProps {
  onClose: () => void;
  onMyLocation: () => void;
  onScan: () => void;
  onStop: () => void;
  isTracking: boolean;
}

export const HUD: React.FC<HUDProps> = ({
  onClose,
  onMyLocation,
  onScan,
  onStop,
  isTracking,
}) => {
  const { colors, resolvedTheme } = useTheme();

  const hudBackground = resolvedTheme === 'dark' 
    ? 'rgba(0, 0, 0, 0.6)' 
    : 'rgba(255, 255, 255, 0.8)';

  const iconColor = colors.text;

  return (
    <View style={styles.container} pointerEvents="box-none">
      {/* Top Right Group */}
      <View style={styles.topRight} pointerEvents="box-none">
        <TouchableOpacity
          style={[styles.button, { backgroundColor: hudBackground }]}
          onPress={onClose}
          activeOpacity={0.7}
        >
          <Ionicons name="close" size={24} color={iconColor} />
        </TouchableOpacity>
      </View>

      {/* Bottom Right Group */}
      <View style={styles.bottomRight} pointerEvents="box-none">
        {isTracking && (
          <>
            <TouchableOpacity
              style={[styles.button, styles.marginBottom, { backgroundColor: hudBackground }]}
              onPress={onStop}
              activeOpacity={0.7}
            >
              <Ionicons name="stop" size={24} color={colors.danger} />
            </TouchableOpacity>
            
            <TouchableOpacity
              style={[styles.button, styles.marginBottom, { backgroundColor: hudBackground }]}
              onPress={onScan}
              activeOpacity={0.7}
            >
              <Ionicons name="qr-code" size={24} color={iconColor} />
            </TouchableOpacity>
          </>
        )}

        <TouchableOpacity
          style={[styles.button, { backgroundColor: hudBackground }]}
          onPress={onMyLocation}
          activeOpacity={0.7}
        >
          <Ionicons name="locate" size={24} color={iconColor} />
        </TouchableOpacity>
      </View>
    </View>
  );
};

const styles = StyleSheet.create({
  container: {
    ...StyleSheet.absoluteFillObject,
    padding: 16,
    paddingTop: Platform.OS === 'ios' ? 60 : 40,
  },
  topRight: {
    position: 'absolute',
    top: Platform.OS === 'ios' ? 60 : 40,
    right: 16,
    alignItems: 'flex-end',
  },
  bottomRight: {
    position: 'absolute',
    bottom: 120, // Leave space for BottomDrawer
    right: 16,
    alignItems: 'flex-end',
  },
  button: {
    width: 48,
    height: 48,
    borderRadius: 24,
    justifyContent: 'center',
    alignItems: 'center',
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 2 },
    shadowOpacity: 0.2,
    shadowRadius: 4,
    elevation: 4,
  },
  marginBottom: {
    marginBottom: 12,
  },
});
