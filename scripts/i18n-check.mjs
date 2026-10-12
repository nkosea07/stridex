import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const reportMode = process.argv.includes('--report');

const contract = JSON.parse(fs.readFileSync(path.join(root, 'packages/contracts/contract.json'), 'utf8'));
const supported = new Set(contract.i18n?.supportedLocales ?? []);
const defaultLocale = contract.i18n?.defaultLocale ?? 'en';

const roots = [
  { name: 'shared', dir: path.join(root, 'packages/i18n/locales') },
  { name: 'web', dir: path.join(root, 'apps/web/src/i18n/locales') },
  { name: 'mobile', dir: path.join(root, 'apps/mobile/i18n/locales') },
];

const sourceRoots = [
  path.join(root, 'apps/web/src'),
  path.join(root, 'apps/web/app'),
  path.join(root, 'apps/mobile/app'),
  path.join(root, 'apps/mobile/lib'),
];

const errors = [];
const warnings = [];

function listDirs(dir) {
  if (!fs.existsSync(dir)) return [];
  return fs.readdirSync(dir, { withFileTypes: true }).filter((e) => e.isDirectory()).map((e) => e.name);
}

function listJsonFiles(dir) {
  if (!fs.existsSync(dir)) return [];
  return fs.readdirSync(dir).filter((f) => f.endsWith('.json'));
}

function flatten(obj, prefix = '') {
  const out = new Map();
  for (const [key, value] of Object.entries(obj)) {
    const next = prefix ? `${prefix}.${key}` : key;
    if (value && typeof value === 'object' && !Array.isArray(value)) {
      for (const [k, v] of flatten(value, next)) out.set(k, v);
    } else {
      out.set(next, value);
    }
  }
  return out;
}

function readCatalog(localeDir) {
  const keys = new Map();
  for (const file of listJsonFiles(localeDir)) {
    const namespace = path.basename(file, '.json');
    const content = JSON.parse(fs.readFileSync(path.join(localeDir, file), 'utf8'));
    for (const [k, v] of flatten(content)) keys.set(`${namespace}.${k}`, v);
  }
  return keys;
}

let totalKeys = 0;
let pendingTranslations = 0;

for (const rootEntry of roots) {
  if (!fs.existsSync(rootEntry.dir)) continue;
  const locales = listDirs(rootEntry.dir);

  for (const locale of locales) {
    if (!supported.has(locale)) {
      errors.push(`[${rootEntry.name}] locale "${locale}" is not in contract.i18n.supportedLocales.`);
    }
  }

  const enDir = path.join(rootEntry.dir, defaultLocale);
  if (!fs.existsSync(enDir)) {
    errors.push(`[${rootEntry.name}] missing default locale directory "${defaultLocale}".`);
    continue;
  }
  const enKeys = readCatalog(enDir);
  totalKeys += enKeys.size;

  for (const locale of locales) {
    if (locale === defaultLocale) continue;
    const localeKeys = readCatalog(path.join(rootEntry.dir, locale));
    for (const key of localeKeys.keys()) {
      if (!enKeys.has(key)) {
        errors.push(`[${rootEntry.name}/${locale}] key "${key}" does not exist in ${defaultLocale} (orphan/typo).`);
      }
    }
    const missing = [...enKeys.keys()].filter((key) => !localeKeys.has(key));
    if (missing.length > 0) {
      pendingTranslations += missing.length;
      warnings.push(`[${rootEntry.name}/${locale}] ${missing.length} untranslated key(s) falling back to ${defaultLocale}.`);
    }
  }
}

// Best-effort unused-key detection: a key is referenced if its full path or its leaf
// segment appears quoted in the client source.
const sourceText = sourceRoots
  .filter((dir) => fs.existsSync(dir))
  .flatMap((dir) => {
    const files = [];
    const walk = (current) => {
      for (const entry of fs.readdirSync(current, { withFileTypes: true })) {
        const full = path.join(current, entry.name);
        if (entry.isDirectory()) walk(full);
        else if (/\.(ts|tsx|js|jsx)$/.test(entry.name)) files.push(fs.readFileSync(full, 'utf8'));
      }
    };
    walk(dir);
    return files;
  })
  .join('\n');

let unusedCount = 0;
for (const rootEntry of roots) {
  const enDir = path.join(rootEntry.dir, defaultLocale);
  if (!fs.existsSync(enDir)) continue;
  for (const key of readCatalog(enDir).keys()) {
    const leaf = key.includes('.') ? key.slice(key.lastIndexOf('.') + 1) : key;
    if (!sourceText.includes(`'${key}'`) && !sourceText.includes(`"${key}"`) && !sourceText.includes(`'${leaf}'`) && !sourceText.includes(`"${leaf}"`) && !sourceText.includes(`.${leaf}`)) {
      unusedCount += 1;
      if (reportMode) warnings.push(`[${rootEntry.name}] key "${key}" appears unused in client source.`);
    }
  }
}

if (reportMode) {
  console.log('i18n report');
  console.log(`  default locale: ${defaultLocale}`);
  console.log(`  supported locales: ${[...supported].join(', ')}`);
  console.log(`  default-locale keys: ${totalKeys}`);
  console.log(`  untranslated keys: ${pendingTranslations}`);
  console.log(`  possibly-unused keys: ${unusedCount}`);
  for (const warning of warnings) console.log(`  - ${warning}`);
  process.exit(errors.length > 0 ? 1 : 0);
}

if (errors.length > 0) {
  console.error('i18n check failed:\n');
  for (const error of errors) console.error(`- ${error}`);
  process.exit(1);
}

if (pendingTranslations > 0) {
  console.log(`i18n check passed (${pendingTranslations} untranslated key(s) falling back to ${defaultLocale}).`);
} else {
  console.log('i18n check passed.');
}
