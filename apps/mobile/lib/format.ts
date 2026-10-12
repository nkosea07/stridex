import i18n from './i18n'

function activeLocale(): string {
  return i18n.resolvedLanguage ?? i18n.language ?? 'en'
}

type DateInput = Date | string | number

function toDate(value: DateInput): Date {
  return value instanceof Date ? value : new Date(value)
}

/** Locale-aware date. Prefer this over `toLocaleDateString`. */
export function formatDate(value: DateInput, options?: Intl.DateTimeFormatOptions): string {
  return new Intl.DateTimeFormat(activeLocale(), options ?? { year: 'numeric', month: 'short', day: 'numeric' }).format(toDate(value))
}

/** Locale-aware time. Prefer this over `toLocaleTimeString`. */
export function formatTime(value: DateInput, options?: Intl.DateTimeFormatOptions): string {
  return new Intl.DateTimeFormat(activeLocale(), options ?? { hour: '2-digit', minute: '2-digit' }).format(toDate(value))
}

/** Locale-aware date + time. Prefer this over `toLocaleString`. */
export function formatDateTime(value: DateInput, options?: Intl.DateTimeFormatOptions): string {
  return new Intl.DateTimeFormat(activeLocale(), options ?? { dateStyle: 'medium', timeStyle: 'short' }).format(toDate(value))
}

export function formatNumber(value: number, options?: Intl.NumberFormatOptions): string {
  return new Intl.NumberFormat(activeLocale(), options).format(value)
}

export function formatCurrency(value: number, currency = 'USD'): string {
  return new Intl.NumberFormat(activeLocale(), { style: 'currency', currency }).format(value)
}

/** Locale-neutral stopwatch format (HH:MM:SS) used for elapsed times. */
export function formatDuration(totalSeconds: number): string {
  const safe = Math.max(0, Math.floor(totalSeconds))
  const hours = Math.floor(safe / 3600)
  const minutes = Math.floor((safe % 3600) / 60)
  const seconds = safe % 60
  return `${String(hours).padStart(2, '0')}:${String(minutes).padStart(2, '0')}:${String(seconds).padStart(2, '0')}`
}

/** Pace per kilometre as M:SS (not locale-specific). */
export function formatPace(secondsPerKm: number): string {
  if (!Number.isFinite(secondsPerKm) || secondsPerKm <= 0) return '--'
  const minutes = Math.floor(secondsPerKm / 60)
  const seconds = Math.round(secondsPerKm % 60)
  return `${minutes}:${String(seconds).padStart(2, '0')}`
}
