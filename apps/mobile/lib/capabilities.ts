import type { User, UserCapability, UserRole } from '../types';

export const PERSONAL_CAPABILITIES: UserCapability[] = ['ATHLETE', 'SPECTATOR'];

export const OPERATIONS_ROLES: UserRole[] = ['SUPER_ADMIN', 'RACE_ADMIN', 'OFFICIAL', 'VOLUNTEER'];

function isUserCapability(value: unknown): value is UserCapability {
  return value === 'ATHLETE' || value === 'SPECTATOR';
}

export function getUserCapabilities(user: Pick<User, 'role' | 'capabilities'> | null | undefined): UserCapability[] {
  const declared = Array.isArray(user?.capabilities)
    ? Array.from(new Set(user.capabilities.filter(isUserCapability)))
    : [];

  if (declared.length > 0) return declared;
  if (user?.role === 'ATHLETE' || user?.role === 'SPECTATOR') return [user.role];
  return [];
}

export function hasCapability(
  user: Pick<User, 'role' | 'capabilities'> | null | undefined,
  capability: UserCapability,
): boolean {
  return getUserCapabilities(user).includes(capability);
}

export function hasAnyCapability(
  user: Pick<User, 'role' | 'capabilities'> | null | undefined,
  capabilities: UserCapability[],
): boolean {
  const available = getUserCapabilities(user);
  return capabilities.some((capability) => available.includes(capability));
}

export function hasActiveCapability(
  user: Pick<User, 'role' | 'capabilities' | 'preferredCapability'> | null | undefined,
  capability: UserCapability,
): boolean {
  return getPreferredCapability(user) === capability;
}

export function getPreferredCapability(
  user: Pick<User, 'role' | 'capabilities' | 'preferredCapability'> | null | undefined,
): UserCapability | null {
  const capabilities = getUserCapabilities(user);
  if (user?.preferredCapability && capabilities.includes(user.preferredCapability)) {
    return user.preferredCapability;
  }
  return capabilities[0] ?? null;
}

export function isOperationsRole(role: UserRole | undefined): boolean {
  return role !== undefined && OPERATIONS_ROLES.includes(role);
}

export function normalizeUser(user: User): User {
  const candidate = user as User & { capabilities?: unknown; preferredCapability?: unknown };
  const declaredCapabilities = Array.isArray(candidate.capabilities)
    ? Array.from(new Set(candidate.capabilities.filter(isUserCapability)))
    : [];
  const legacyCapability = isUserCapability(user.role) ? user.role : undefined;
  const capabilities = declaredCapabilities.length > 0
    ? declaredCapabilities
    : legacyCapability
      ? [legacyCapability]
      : [];
  const preferredCapability = isUserCapability(candidate.preferredCapability)
    && capabilities.includes(candidate.preferredCapability)
    ? candidate.preferredCapability
    : capabilities[0] ?? null;

  return { ...user, capabilities, preferredCapability };
}
