export const PASSWORD_MIN_LENGTH = 8;
export const PASSWORD_MAX_LENGTH = 128;

export type NewPasswordProblem = 'tooShort' | 'tooLong' | 'mismatch';

export function validateNewPassword(password: string, confirmation: string): NewPasswordProblem | null {
  if (password.length < PASSWORD_MIN_LENGTH) return 'tooShort';
  if (password.length > PASSWORD_MAX_LENGTH) return 'tooLong';
  if (password !== confirmation) return 'mismatch';
  return null;
}

export function readLinkToken(value: string | string[] | undefined): string | null {
  const raw = Array.isArray(value) ? value[0] : value;
  const token = raw?.trim();
  return token ? token : null;
}
