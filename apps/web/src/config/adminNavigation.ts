import type { LucideIcon } from 'lucide-react'
import {
  BarChart3,
  ClipboardList,
  Image as ImageIcon,
  Layers,
  MapPin,
  Sparkles,
  Tag,
  Timer,
  Trophy,
  Users,
  UsersRound,
  Wifi,
} from 'lucide-react'
import type { UserRole } from '@/types'

export type RaceNavigationItem = {
  to: string
  icon: LucideIcon
  labelKey: string
  roles: readonly UserRole[]
}

export const RACE_OPERATION_ROLES = ['SUPER_ADMIN', 'RACE_ADMIN', 'OFFICIAL', 'VOLUNTEER'] as const satisfies readonly UserRole[]
export const RACE_ADMIN_ROLES = ['SUPER_ADMIN', 'RACE_ADMIN'] as const satisfies readonly UserRole[]
export const RFID_ROLES = ['SUPER_ADMIN', 'RACE_ADMIN', 'OFFICIAL'] as const satisfies readonly UserRole[]
export const INSIGHTS_ROLES = ['SUPER_ADMIN', 'RACE_ADMIN', 'OFFICIAL'] as const satisfies readonly UserRole[]

export const raceNavigationItems: readonly RaceNavigationItem[] = [
  { to: 'participants', icon: Users, labelKey: 'nav:participants', roles: RACE_OPERATION_ROLES },
  { to: 'registration', icon: ClipboardList, labelKey: 'nav:registration', roles: RACE_ADMIN_ROLES },
  { to: 'categories', icon: Layers, labelKey: 'nav:categories', roles: RACE_OPERATION_ROLES },
  { to: 'teams', icon: UsersRound, labelKey: 'nav:teams', roles: RACE_OPERATION_ROLES },
  { to: 'live', icon: MapPin, labelKey: 'nav:liveTracking', roles: RACE_OPERATION_ROLES },
  { to: 'checkpoints', icon: Timer, labelKey: 'nav:checkpoints', roles: RACE_OPERATION_ROLES },
  { to: 'rfid', icon: Wifi, labelKey: 'nav:rfid', roles: RFID_ROLES },
  { to: 'results', icon: Trophy, labelKey: 'nav:results', roles: RACE_OPERATION_ROLES },
  { to: 'analytics', icon: BarChart3, labelKey: 'nav:analytics', roles: RACE_OPERATION_ROLES },
  { to: 'insights', icon: Sparkles, labelKey: 'nav:insights', roles: INSIGHTS_ROLES },
  { to: 'media', icon: ImageIcon, labelKey: 'nav:media', roles: RACE_OPERATION_ROLES },
  { to: 'promos', icon: Tag, labelKey: 'nav:promoCodes', roles: RACE_OPERATION_ROLES },
]

export function getRaceNavigationForRole(role?: UserRole) {
  return raceNavigationItems.filter((item) => !!role && item.roles.includes(role))
}
