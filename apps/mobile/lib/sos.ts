import type { GpsLocation } from '../types'

export const SOS_FIX_MAX_AGE_MS = 2 * 60 * 1000
const SOS_LOCATION_PRECISION = 5

export function freshSosFix(locations: GpsLocation[], now = Date.now()) {
  if (locations.length === 0) return null
  const latest = locations[locations.length - 1]
  if (!Number.isFinite(latest.latitude) || !Number.isFinite(latest.longitude)) return null
  if (latest.latitude < -90 || latest.latitude > 90 || latest.longitude < -180 || latest.longitude > 180) return null
  const capturedAt = Date.parse(latest.timestamp)
  if (!Number.isFinite(capturedAt) || now - capturedAt > SOS_FIX_MAX_AGE_MS) return null
  return {
    latitude: Number(latest.latitude.toFixed(SOS_LOCATION_PRECISION)),
    longitude: Number(latest.longitude.toFixed(SOS_LOCATION_PRECISION)),
  }
}
