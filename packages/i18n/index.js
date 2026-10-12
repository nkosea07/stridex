import { API_CONTRACT } from '@race-tracker/contracts'
import commonEn from './locales/en/common.json'
import statusEn from './locales/en/status.json'

// Locale configuration is sourced from the contract (single source of truth).
export const SUPPORTED_LOCALES = API_CONTRACT.i18n.supportedLocales
export const DEFAULT_LOCALE = API_CONTRACT.i18n.defaultLocale
export const FALLBACK_LOCALE = API_CONTRACT.i18n.fallbackLocale
export const RTL_LOCALES = API_CONTRACT.i18n.rtlLocales

// Namespaces owned by the shared package (domain terms used across web, mobile and backend).
export const SHARED_NAMESPACES = ['common', 'status']

export const LOCALE_LABELS = {
  en: 'English',
  sn: 'Shona',
  nd: 'Ndebele',
  af: 'Afrikaans',
  zu: 'isiZulu',
  fr: 'Français',
  pt: 'Português',
  sw: 'Kiswahili',
}

// Only `en` is authored here today; other locales fall back until translations are added.
export const sharedResources = {
  en: {
    common: commonEn,
    status: statusEn,
  },
}

export function isRtlLocale(locale) {
  return RTL_LOCALES.includes(locale)
}

export function localeLabel(locale) {
  return LOCALE_LABELS[locale] ?? locale
}
