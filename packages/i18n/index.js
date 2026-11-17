import { API_CONTRACT } from '@race-tracker/contracts'
import commonEn from './locales/en/common.json'
import statusEn from './locales/en/status.json'
import legalEn from './locales/en/legal.json'
import accountEn from './locales/en/account.json'
import commonSn from './locales/sn/common.json'
import legalSn from './locales/sn/legal.json'
import accountSn from './locales/sn/account.json'
import commonNd from './locales/nd/common.json'
import legalNd from './locales/nd/legal.json'
import accountNd from './locales/nd/account.json'
import commonAf from './locales/af/common.json'
import legalAf from './locales/af/legal.json'
import accountAf from './locales/af/account.json'
import commonZu from './locales/zu/common.json'
import legalZu from './locales/zu/legal.json'
import accountZu from './locales/zu/account.json'
import commonFr from './locales/fr/common.json'
import legalFr from './locales/fr/legal.json'
import accountFr from './locales/fr/account.json'
import commonPt from './locales/pt/common.json'
import legalPt from './locales/pt/legal.json'
import accountPt from './locales/pt/account.json'
import commonSw from './locales/sw/common.json'
import legalSw from './locales/sw/legal.json'
import accountSw from './locales/sw/account.json'

// Locale configuration is sourced from the contract (single source of truth).
export const SUPPORTED_LOCALES = API_CONTRACT.i18n.supportedLocales
export const DEFAULT_LOCALE = API_CONTRACT.i18n.defaultLocale
export const FALLBACK_LOCALE = API_CONTRACT.i18n.fallbackLocale
export const RTL_LOCALES = API_CONTRACT.i18n.rtlLocales

// Namespaces owned by the shared package (domain terms used across web, mobile and backend).
export const SHARED_NAMESPACES = ['common', 'status', 'legal', 'account']

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

export const sharedResources = {
  en: {
    common: commonEn,
    status: statusEn,
    legal: legalEn,
    account: accountEn,
  },
  sn: { common: commonSn, legal: legalSn, account: accountSn },
  nd: { common: commonNd, legal: legalNd, account: accountNd },
  af: { common: commonAf, legal: legalAf, account: accountAf },
  zu: { common: commonZu, legal: legalZu, account: accountZu },
  fr: { common: commonFr, legal: legalFr, account: accountFr },
  pt: { common: commonPt, legal: legalPt, account: accountPt },
  sw: { common: commonSw, legal: legalSw, account: accountSw },
}

export function isRtlLocale(locale) {
  return RTL_LOCALES.includes(locale)
}

export function localeLabel(locale) {
  return LOCALE_LABELS[locale] ?? locale
}
