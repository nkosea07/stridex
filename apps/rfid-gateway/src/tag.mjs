const SEPARATORS = /[\s:-]/g;
const HEX = /^[0-9A-Fa-f]+$/;

// Same canonical form as the backend (RfidTagValues): hex EPCs upper-case without separators,
// other labels only trimmed.
export function canonicalTag(rawValue) {
  if (typeof rawValue !== 'string') {
    return '';
  }
  const trimmed = rawValue.trim();
  const stripped = trimmed.replace(SEPARATORS, '');
  if (stripped && HEX.test(stripped)) {
    return stripped.toUpperCase();
  }
  return trimmed;
}
