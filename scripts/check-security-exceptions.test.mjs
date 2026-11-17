import { test } from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import { classify, needsAttention, parseIgnoreFile, renderReport } from './check-security-exceptions.mjs';

const sample = `
CVE-2026-47884 exp:2027-02-17
CVE-2026-118664 exp:2027-05-16
CVE-2020-0001
`;

test('parses ids and expiry dates and flags entries without one', () => {
  assert.deepEqual(parseIgnoreFile(sample), [
    { id: 'CVE-2026-47884', expires: '2027-02-17', invalid: false },
    { id: 'CVE-2026-118664', expires: '2027-05-16', invalid: false },
    { id: 'CVE-2020-0001', expires: null, invalid: false },
  ]);
});

test('alerts from 45 days before an expiry and after it passes', () => {
  const entries = parseIgnoreFile('CVE-2026-47884 exp:2027-02-17\n');
  assert.equal(classify(entries, '2027-01-02', 45)[0].state, 'ok');
  assert.equal(classify(entries, '2027-01-03', 45)[0].state, 'due-soon');
  assert.equal(classify(entries, '2027-02-17', 45)[0].state, 'due-soon');
  assert.equal(classify(entries, '2027-02-18', 45)[0].state, 'expired');
});

test('nothing needs attention while every entry is outside the window', () => {
  const results = classify(parseIgnoreFile(sample).slice(0, 2), '2026-11-17', 45);
  assert.equal(needsAttention(results), false);
});

test('an entry without an expiry always needs attention', () => {
  const results = classify(parseIgnoreFile(sample), '2026-11-17', 45);
  assert.equal(results[2].state, 'missing-expiry');
  assert.equal(needsAttention(results), true);
});

test('the report lists the soonest expiry first and links the migration plan', () => {
  const results = classify(parseIgnoreFile(sample).slice(0, 2), '2027-01-10', 45);
  const report = renderReport(results, { today: '2027-01-10', warnDays: 45 });
  const rows = report.split('\n').filter((line) => line.startsWith('| CVE'));
  assert.match(rows[0], /CVE-2026-47884 \| 2027-02-17 \| 38 \| Expires soon/);
  assert.match(report, /2026-11-17-spring-boot-4-migration\.md/);
});

test('every entry in the repository .trivyignore has an expiry date', () => {
  const entries = parseIgnoreFile(fs.readFileSync(new URL('../.trivyignore', import.meta.url), 'utf8'));
  assert.ok(entries.length > 0);
  for (const entry of entries) assert.ok(entry.expires, `${entry.id} has no exp: date`);
});
