import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const DAY_MS = 24 * 60 * 60 * 1000;
const ENTRY = /^(\S+)(?:\s+exp:(\d{4}-\d{2}-\d{2}))?\s*$/;

export function parseIgnoreFile(text) {
  return text
    .split('\n')
    .map((line) => line.trim())
    .filter((line) => line && !line.startsWith('#'))
    .map((line) => {
      const match = ENTRY.exec(line);
      if (!match) return { id: line, expires: null, invalid: true };
      return { id: match[1], expires: match[2] ?? null, invalid: false };
    });
}

export function classify(entries, today, warnDays) {
  const start = Date.parse(`${today}T00:00:00Z`);
  return entries.map((entry) => {
    if (entry.invalid || !entry.expires) {
      return { ...entry, daysLeft: null, state: 'missing-expiry' };
    }
    const daysLeft = Math.round((Date.parse(`${entry.expires}T00:00:00Z`) - start) / DAY_MS);
    const state = daysLeft < 0 ? 'expired' : daysLeft <= warnDays ? 'due-soon' : 'ok';
    return { ...entry, daysLeft, state };
  });
}

export function needsAttention(results) {
  return results.some((result) => result.state !== 'ok');
}

export function renderReport(results, { today, warnDays }) {
  const label = { ok: 'OK', 'due-soon': 'Expires soon', expired: 'EXPIRED', 'missing-expiry': 'No expiry date' };
  const rows = results
    .slice()
    .sort((a, b) => (a.daysLeft ?? -Infinity) - (b.daysLeft ?? -Infinity))
    .map((r) => `| ${r.id} | ${r.expires ?? '—'} | ${r.daysLeft ?? '—'} | ${label[r.state]} |`);
  return [
    `Security exception review (${today}, alert window ${warnDays} days).`,
    '',
    'These advisories are accepted in `.trivyignore`. When an entry expires, Trivy reports it again',
    'and the `dependency-security` / `containers` CI jobs fail.',
    '',
    '| Advisory | Expires | Days left | State |',
    '| --- | --- | --- | --- |',
    ...rows,
    '',
    'What to do:',
    '- Spring MVC CVE-2026-47884 / CVE-2026-47890: complete the Spring Boot 4 migration in',
    '  `docs/plans/2026-11-17-spring-boot-4-migration.md`, then delete both lines from `.trivyignore`.',
    '- nginx CVE-2026-113425 / -118899 / -121810 / -123007 / -123633 / -123634: rebuild the web image once',
    '  `nginxinc/nginx-unprivileged` ships nginx 1.30.7, then delete those lines from `.trivyignore`.',
    '- JavaScript advisories (image-size, decode-uri-component): upgrade Expo / React Navigation if a',
    '  compatible fix exists; otherwise re-review and renew the date.',
    '- Rationale for every exception: `docs/RELEASE_READINESS.md` → *Dependency audit exceptions*.',
    '- Renewing is allowed only after a fresh review; record it in `docs/RELEASE_READINESS.md`.',
  ].join('\n');
}

function argument(name, fallback) {
  const index = process.argv.indexOf(`--${name}`);
  return index >= 0 && process.argv[index + 1] ? process.argv[index + 1] : fallback;
}

function main() {
  const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
  const file = argument('file', path.join(root, '.trivyignore'));
  const today = argument('today', new Date().toISOString().slice(0, 10));
  const warnDays = Number(argument('warn-days', '45'));
  const results = classify(parseIgnoreFile(fs.readFileSync(file, 'utf8')), today, warnDays);
  const due = needsAttention(results);
  process.stdout.write(`${renderReport(results, { today, warnDays })}\n`);
  const githubOutput = argument('github-output', null);
  if (githubOutput) fs.appendFileSync(githubOutput, `due=${due}\n`);
  if (process.argv.includes('--strict') && results.some((r) => r.state === 'missing-expiry')) {
    process.exitCode = 1;
  }
}

if (process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  main();
}
