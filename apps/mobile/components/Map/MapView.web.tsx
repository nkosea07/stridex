import React from 'react';
import { View, Text, StyleSheet } from 'react-native';

export const PROVIDER_DEFAULT = 'default';

export const Marker: React.FC<any> = ({ children }) => <>{children}</>;
export const Polyline: React.FC<any> = () => null;

const MapView: React.FC<any> = ({ children, style }) => {
  return (
    <View style={[styles.container, style]}>
      <View style={styles.content}>
        <Text style={styles.title}>Map Preview Not Available on Web</Text>
        <Text style={styles.subtitle}>Full tracking maps are optimized for native iOS and Android devices.</Text>
        <View style={styles.infoBox}>
          <Text style={styles.infoText}>Use the official Web Dashboard for high-density monitoring.</Text>
        </View>
      </View>
      {children}
    </View>
  );
};

const styles = StyleSheet.create({
  container: {
    backgroundColor: '#F1F5F9',
    alignItems: 'center',
    justifyContent: 'center',
  },
  content: {
    padding: 20,
    alignItems: 'center',
    maxWidth: 300,
  },
  title: {
    fontSize: 18,
    fontWeight: '800',
    color: '#0F172A',
    textAlign: 'center',
    marginBottom: 8,
  },
  subtitle: {
    fontSize: 14,
    color: '#64748B',
    textAlign: 'center',
    marginBottom: 20,
    lineHeight: 20,
  },
  infoBox: {
    backgroundColor: '#2563EB10',
    padding: 12,
    borderRadius: 8,
    borderWidth: 1,
    borderColor: '#2563EB20',
  },
  infoText: {
    fontSize: 12,
    color: '#2563EB',
    fontWeight: '600',
    textAlign: 'center',
  },
});

export default MapView;
