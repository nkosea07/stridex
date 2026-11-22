import { StyleSheet } from 'react-native';

export const authLinkStyles = StyleSheet.create({
  container: { flex: 1, backgroundColor: '#FFFFFF' },
  content: { flexGrow: 1, padding: 24, justifyContent: 'center', gap: 12 },
  title: { fontSize: 26, fontWeight: '700', color: '#111827' },
  body: { fontSize: 15, lineHeight: 22, color: '#4B5563' },
  label: { fontSize: 14, fontWeight: '600', color: '#374151', marginTop: 4 },
  input: {
    borderWidth: 1,
    borderColor: '#D1D5DB',
    borderRadius: 12,
    paddingHorizontal: 16,
    paddingVertical: 14,
    fontSize: 16,
    color: '#111827',
  },
  error: { color: '#B91C1C', fontSize: 14 },
  status: { flexDirection: 'row', alignItems: 'center', gap: 10 },
  button: {
    backgroundColor: '#0F766E',
    borderRadius: 12,
    minHeight: 52,
    alignItems: 'center',
    justifyContent: 'center',
    marginTop: 8,
  },
  buttonDisabled: { opacity: 0.6 },
  buttonText: { color: '#FFFFFF', fontSize: 16, fontWeight: '600' },
  secondaryButton: { minHeight: 48, alignItems: 'center', justifyContent: 'center' },
  secondaryButtonText: { color: '#0F766E', fontSize: 15, fontWeight: '600' },
});
