import { API_CONTRACT } from '@race-tracker/contracts';
import type { User } from '@/types';

export type LegalDocumentKind = 'privacy' | 'terms';

export interface LegalSection {
  id: string;
  title: string;
  paragraphs?: string[];
  items?: string[];
  closing?: string;
}

export const LEGAL_VERSIONS: Record<LegalDocumentKind, string> = {
  privacy: API_CONTRACT.legal.privacyPolicyVersion,
  terms: API_CONTRACT.legal.termsVersion,
};

export const LEGAL_ROUTES = new Set(['privacy', 'terms', 'legal-acceptance']);

export function legalInterpolation() {
  return {
    operatorName: API_CONTRACT.legal.operatorName,
    operatorAddress: API_CONTRACT.legal.operatorAddress,
    contactEmail: API_CONTRACT.legal.contactEmail,
    graceDays: API_CONTRACT.legal.accountDeletionGraceDays,
    gpsRetentionDays: API_CONTRACT.legal.gpsRetentionDays,
  };
}

export const LEGAL_VERSION_MISMATCH_CODES = new Set(['PRIVACY_POLICY_VERSION_MISMATCH', 'TERMS_VERSION_MISMATCH']);

export function asList<T>(value: unknown): T[] {
  return Array.isArray(value) ? (value as T[]) : [];
}

export function needsLegalAcceptance(
  user: Pick<User, 'privacyPolicyVersion' | 'termsVersion' | 'legalAcceptanceRequired'> | null | undefined,
) {
  if (!user) return false;
  if (typeof user.legalAcceptanceRequired === 'boolean') return user.legalAcceptanceRequired;
  return user.privacyPolicyVersion !== LEGAL_VERSIONS.privacy || user.termsVersion !== LEGAL_VERSIONS.terms;
}
