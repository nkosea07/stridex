import AsyncStorage from '@react-native-async-storage/async-storage'
import { getLocales } from 'expo-localization'
import { createInstance, type i18n as I18nInstance } from 'i18next'
import { initReactI18next } from 'react-i18next'
import {
  FALLBACK_LOCALE,
  SHARED_NAMESPACES,
  SUPPORTED_LOCALES,
  sharedResources,
} from '@race-tracker/i18n'

const LANGUAGE_STORAGE_KEY = 'race_tracker_language'

const i18n: I18nInstance = createInstance()

function resolveInitialLocale(stored: string | null): string {
  if (stored && SUPPORTED_LOCALES.includes(stored)) {
    return stored
  }
  const device = getLocales()[0]?.languageCode ?? FALLBACK_LOCALE
  return SUPPORTED_LOCALES.includes(device) ? device : FALLBACK_LOCALE
}

export async function initI18n(): Promise<I18nInstance> {
  const stored = await AsyncStorage.getItem(LANGUAGE_STORAGE_KEY).catch(() => null)
  const initial = resolveInitialLocale(stored)

  if (!i18n.isInitialized) {
    await i18n.use(initReactI18next).init({
      resources: { en: { ...sharedResources.en } },
      lng: initial,
      supportedLngs: SUPPORTED_LOCALES,
      fallbackLng: FALLBACK_LOCALE,
      defaultNS: 'common',
      fallbackNS: 'common',
      ns: [...SHARED_NAMESPACES],
      interpolation: { escapeValue: false },
    })
  } else {
    await i18n.changeLanguage(initial)
  }
  return i18n
}

export async function setAppLanguage(locale: string): Promise<void> {
  await i18n.changeLanguage(locale)
  await AsyncStorage.setItem(LANGUAGE_STORAGE_KEY, locale).catch(() => undefined)
}

export default i18n
