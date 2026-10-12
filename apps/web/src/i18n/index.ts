import i18n from 'i18next'
import { initReactI18next } from 'react-i18next'
import LanguageDetector from 'i18next-browser-languagedetector'
import {
  FALLBACK_LOCALE,
  SHARED_NAMESPACES,
  SUPPORTED_LOCALES,
  sharedResources,
} from '@race-tracker/i18n'
import { appNamespaceNames, appNamespaces } from './resources'

export const i18nNamespaces = [...SHARED_NAMESPACES, ...appNamespaceNames]

void i18n
  .use(LanguageDetector)
  .use(initReactI18next)
  .init({
    resources: {
      en: { ...sharedResources.en, ...appNamespaces },
    },
    supportedLngs: SUPPORTED_LOCALES,
    fallbackLng: FALLBACK_LOCALE,
    defaultNS: 'common',
    fallbackNS: 'common',
    ns: i18nNamespaces,
    interpolation: { escapeValue: false },
    initImmediate: false,
    detection: {
      order: ['localStorage', 'navigator'],
      lookupLocalStorage: 'race_tracker_language',
      caches: ['localStorage'],
    },
  })

i18n.on('languageChanged', (lng) => {
  if (typeof document !== 'undefined') {
    document.documentElement.lang = lng
  }
})

if (typeof document !== 'undefined') {
  document.documentElement.lang = i18n.resolvedLanguage ?? i18n.language ?? 'en'
}

export default i18n
