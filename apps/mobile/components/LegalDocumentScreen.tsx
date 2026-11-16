import { ScrollView, StyleSheet, Text, View } from 'react-native';
import { router } from 'expo-router';
import { useTranslation } from 'react-i18next';
import { useTheme } from '@/contexts/theme';
import { LEGAL_VERSIONS, asList, legalInterpolation, type LegalDocumentKind, type LegalSection } from '@/lib/legal';

export function LegalDocumentScreen({ kind }: { kind: LegalDocumentKind }) {
  const { colors } = useTheme();
  const { t } = useTranslation();
  const values = legalInterpolation();
  const sections = asList<LegalSection>(t(`legal:${kind}.sections`, { returnObjects: true, ...values }));
  const other: LegalDocumentKind = kind === 'privacy' ? 'terms' : 'privacy';

  return (
    <ScrollView style={[styles.container, { backgroundColor: colors.background }]} contentContainerStyle={styles.content}>
      <Text accessibilityRole="header" style={[styles.title, { color: colors.text }]}>{t(`legal:${kind}.title`)}</Text>
      <Text style={[styles.version, { color: colors.subText }]}>
        {t('legal:common.version', { version: LEGAL_VERSIONS[kind] })}
      </Text>
      <Text style={[styles.summary, { color: colors.subText }]}>{t(`legal:${kind}.summary`, values)}</Text>

      {sections.map((section) => (
        <View key={section.id} style={styles.section}>
          <Text accessibilityRole="header" style={[styles.sectionTitle, { color: colors.text }]}>{section.title}</Text>
          {section.paragraphs?.map((paragraph) => (
            <Text key={paragraph} style={[styles.body, { color: colors.subText }]}>{paragraph}</Text>
          ))}
          {section.items?.map((item) => (
            <View key={item} style={styles.itemRow}>
              <Text style={[styles.bullet, { color: colors.subText }]}>{'•'}</Text>
              <Text style={[styles.body, styles.itemText, { color: colors.subText }]}>{item}</Text>
            </View>
          ))}
          {section.closing ? <Text style={[styles.body, { color: colors.subText }]}>{section.closing}</Text> : null}
        </View>
      ))}

      <View style={[styles.footer, { borderTopColor: colors.border }]}>
        <Text style={[styles.body, { color: colors.subText }]}>{t('legal:common.contactLine', values)}</Text>
        <Text
          accessibilityRole="link"
          style={[styles.link, { color: colors.primary }]}
          onPress={() => router.push(other === 'privacy' ? '/privacy' : '/terms')}
        >
          {t(other === 'privacy' ? 'legal:common.relatedPrivacy' : 'legal:common.relatedTerms')}
        </Text>
      </View>
    </ScrollView>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1 },
  content: { padding: 20, paddingBottom: 48 },
  title: { fontSize: 26, fontWeight: '800' },
  version: { fontSize: 14, marginTop: 4, marginBottom: 12 },
  summary: { fontSize: 14, lineHeight: 21, marginBottom: 20 },
  section: { marginBottom: 20 },
  sectionTitle: { fontSize: 16, fontWeight: '700', marginBottom: 6 },
  body: { fontSize: 14, lineHeight: 21, marginBottom: 6 },
  itemRow: { flexDirection: 'row', paddingRight: 8 },
  bullet: { width: 16, fontSize: 14, lineHeight: 21 },
  itemText: { flex: 1 },
  footer: { marginTop: 8, paddingTop: 16, borderTopWidth: 1 },
  link: { fontSize: 14, fontWeight: '600', marginTop: 4 },
});
