import { ScrollView, StyleSheet, Text, View } from 'react-native';
import { API_CONTRACT } from '@race-tracker/contracts';
import { useTheme } from '@/contexts/theme';

const sections: { title: string; body: string }[] = [
  {
    title: '1. Who we are',
    body: 'Race Tracker operates a race registration, timing, and live-tracking platform for organizers, athletes, and spectators. This policy explains what personal data we process, why, how long we keep it, and the choices available to you.',
  },
  {
    title: '2. Data we collect',
    body: 'Account data (name, email, phone, role), registration data (race entries, bib, emergency contact, any medical info you choose to share), race data (GPS location, checkpoint and timing records, results), technical data (device push tokens, session and audit records), and gallery media you or organizers upload.',
  },
  {
    title: '3. How we use it',
    body: 'To provide registration, timing, results, and live tracking; to send transactional notifications (confirmations, timing, and emergency alerts); and to keep the platform secure and meet legal obligations.',
  },
  {
    title: '4. Legal bases',
    body: 'We process data to perform our contract with you, to comply with legal obligations, and on the basis of legitimate interests for safety and platform integrity. Where required, we rely on your consent.',
  },
  {
    title: '5. Sharing',
    body: 'Race organizers receive the registration and timing data needed to run their events. Public spectator pages are designed to be PII-free and do not expose email, phone, emergency, medical, or audit data. We use hosting, email, push, and payment sub-processors under contract.',
  },
  {
    title: '6. Retention',
    body: 'We keep personal data only as long as needed. When you delete your account we deactivate it immediately and erase related personal data after a grace period, except where we must retain records for legal or financial obligations or to preserve append-only security and safety records.',
  },
  {
    title: '7. Your rights',
    body: 'You can access, correct, export, and request deletion of your personal data from your account settings.',
  },
  {
    title: '8. Security',
    body: 'We use access controls, hashed credentials, scoped sessions, and audit logging to protect your data. No method of transmission or storage is completely secure.',
  },
  {
    title: '9. Changes',
    body: 'We may update this policy. When we do, we publish a new version and update the version shown below.',
  },
];

export default function PrivacyScreen() {
  const { colors } = useTheme();
  const version = API_CONTRACT.legal?.privacyPolicyVersion ?? '1.0';

  return (
    <ScrollView style={[styles.container, { backgroundColor: colors.background }]} contentContainerStyle={styles.content}>
      <Text style={[styles.title, { color: colors.text }]}>Privacy Policy</Text>
      <Text style={[styles.version, { color: colors.subText }]}>Version {version}</Text>

      {sections.map((section) => (
        <View key={section.title} style={styles.section}>
          <Text style={[styles.sectionTitle, { color: colors.text }]}>{section.title}</Text>
          <Text style={[styles.sectionBody, { color: colors.subText }]}>{section.body}</Text>
        </View>
      ))}

      <Text style={[styles.footer, { color: colors.subText, borderTopColor: colors.border }]}>
        Manage data export and account deletion in your account settings. Contact the race organizer for
        event-specific questions.
      </Text>
    </ScrollView>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1 },
  content: { padding: 20, paddingBottom: 48 },
  title: { fontSize: 26, fontWeight: '800' },
  version: { fontSize: 14, marginTop: 4, marginBottom: 16 },
  section: { marginBottom: 18 },
  sectionTitle: { fontSize: 16, fontWeight: '700', marginBottom: 4 },
  sectionBody: { fontSize: 14, lineHeight: 21 },
  footer: { fontSize: 13, lineHeight: 19, marginTop: 8, paddingTop: 16, borderTopWidth: 1 },
});
