import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');

const contractPath = path.join(root, 'packages/contracts/contract.json');
const backendConfigPath = path.join(root, 'apps/backend/src/main/resources/application.yml');
const backendSecurityPath = path.join(root, 'apps/backend/src/main/java/com/racetracker/config/SecurityConfig.java');
const backendAuthServicePath = path.join(root, 'apps/backend/src/main/java/com/racetracker/service/impl/AuthServiceImpl.java');
const backendControllerDirectory = path.join(root, 'apps/backend/src/main/java/com/racetracker/controller');
const webApiPath = path.join(root, 'apps/web/src/lib/api.ts');
const mobileApiPath = path.join(root, 'apps/mobile/lib/api.ts');
const contractPackagePath = path.join(root, 'packages/contracts/index.js');
const contractTypesPath = path.join(root, 'packages/contracts/index.d.ts');

const requiredPublicEndpointKeys = [
  'publicRaceHub',
  'publicRaceLive',
  'publicRaceLiveFeed',
  'publicRaceResults',
  'publicRaceSearch',
  'publicAthleteTracking',
  'publicAthleteTrack',
  'publicRaceHeatmap',
  'publicRaceRegistrationFields',
];

const errors = [];

function fail(message) {
  errors.push(message);
}

function read(filePath) {
  return fs.readFileSync(filePath, 'utf8');
}

function assert(condition, message) {
  if (!condition) {
    fail(message);
  }
}

function isRoutePlaceholderSegment(segment) {
  return /^:[A-Za-z][A-Za-z0-9_]*$/.test(segment)
    || /^\{[A-Za-z][A-Za-z0-9_]*(?::[^{}]+)?\}$/.test(segment);
}

function validateRouteTemplate(route, label, { allowBracePlaceholders = false } = {}) {
  const withoutQuery = route.split('?')[0];
  assert(withoutQuery.startsWith('/'), `${label} must begin with "/".`);

  for (const segment of withoutQuery.split('/').filter(Boolean)) {
    if (segment.startsWith(':')) {
      assert(
        /^:[A-Za-z][A-Za-z0-9_]*$/.test(segment),
        `${label} contains an invalid colon placeholder segment "${segment}".`,
      );
    } else if (segment.startsWith('{')) {
      assert(
        allowBracePlaceholders && /^\{[A-Za-z][A-Za-z0-9_]*(?::[^{}]+)?\}$/.test(segment),
        `${label} uses a brace placeholder where the shared client contract requires a colon placeholder: "${segment}".`,
      );
    } else {
      assert(
        !/[{}:]/.test(segment),
        `${label} contains an embedded placeholder marker in static segment "${segment}".`,
      );
    }
  }
}

// Route comparison intentionally ignores placeholder names while preserving their
// segment positions and count. Pairing a contract key to a specific Java method
// would require Java AST/Spring mapping metadata, so the validator compares the
// normalized shape sets in both directions instead.
function canonicalRoute(route) {
  const withoutQuery = route.split('?')[0].replace(/\/+$/, '') || '/';
  return withoutQuery
    .split('/')
    .map((segment) => (isRoutePlaceholderSegment(segment) ? '{}' : segment))
    .join('/') || '/';
}

function replaceTemplateExpressions(value, resolveExpression = () => undefined) {
  let output = '';

  for (let index = 0; index < value.length; index += 1) {
    if (!value.startsWith('${', index)) {
      output += value[index];
      continue;
    }

    let depth = 1;
    let quote = null;
    let end = index + 2;

    for (; end < value.length && depth > 0; end += 1) {
      const character = value[end];

      if (quote) {
        if (character === '\\') {
          end += 1;
        } else if (character === quote) {
          quote = null;
        }
        continue;
      }

      if (character === "'" || character === '"' || character === '`') {
        quote = character;
      } else if (character === '{') {
        depth += 1;
      } else if (character === '}') {
        depth -= 1;
      }
    }

    const expression = value.slice(index + 2, end - 1).trim();
    output += resolveExpression(expression) ?? (expression.includes('?') ? '' : ':param');
    index = end - 1;
  }

  return output;
}

function findMatchingDelimiter(source, openingIndex, opening = '(', closing = ')') {
  let depth = 0;
  let quote = null;

  for (let index = openingIndex; index < source.length; index += 1) {
    const character = source[index];

    if (quote) {
      if (character === '\\') {
        index += 1;
      } else if (character === quote) {
        quote = null;
      }
      continue;
    }

    if (character === "'" || character === '"' || character === '`') {
      quote = character;
    } else if (character === opening) {
      depth += 1;
    } else if (character === closing) {
      depth -= 1;
      if (depth === 0) return index;
    }
  }

  return -1;
}

function splitTopLevelArguments(value) {
  const argumentsList = [];
  let start = 0;
  let parentheses = 0;
  let braces = 0;
  let brackets = 0;
  let quote = null;

  for (let index = 0; index < value.length; index += 1) {
    const character = value[index];

    if (quote) {
      if (character === '\\') {
        index += 1;
      } else if (character === quote) {
        quote = null;
      }
      continue;
    }

    if (character === "'" || character === '"' || character === '`') {
      quote = character;
    } else if (character === '(') {
      parentheses += 1;
    } else if (character === ')') {
      parentheses -= 1;
    } else if (character === '{') {
      braces += 1;
    } else if (character === '}') {
      braces -= 1;
    } else if (character === '[') {
      brackets += 1;
    } else if (character === ']') {
      brackets -= 1;
    } else if (character === ',' && parentheses === 0 && braces === 0 && brackets === 0) {
      argumentsList.push(value.slice(start, index).trim());
      start = index + 1;
    }
  }

  argumentsList.push(value.slice(start).trim());
  return argumentsList;
}

function extractClientOperations(source, endpointPaths, clientName) {
  const operations = [];
  const callPattern = /\b(fetchApi|fetchPublicApi|request)\s*(?:<[^>]*>)?\s*\(/g;

  for (const match of source.matchAll(callPattern)) {
    const openingIndex = match.index + match[0].lastIndexOf('(');
    const closingIndex = findMatchingDelimiter(source, openingIndex);
    if (closingIndex < 0) continue;

    const argumentsList = splitTopLevelArguments(source.slice(openingIndex + 1, closingIndex));
    const endpointArgument = argumentsList[0] ?? '';
    const endpointReference = endpointArgument.match(/API_CONTRACT\.endpoints\.([A-Za-z][A-Za-z0-9_]*)/);
    if (!endpointReference) continue;

    const endpointKey = endpointReference[1];
    if (!Object.hasOwn(endpointPaths, endpointKey)) {
      fail(`${clientName} client references unknown API_CONTRACT endpoint "${endpointKey}".`);
      continue;
    }

    const method = argumentsList[1]?.match(/\bmethod\s*:\s*['"]([A-Za-z]+)['"]/)?.[1]?.toUpperCase() ?? 'GET';
    operations.push({
      clientName,
      endpointKey,
      method,
      route: endpointPaths[endpointKey],
    });
  }

  return operations;
}

const ignoredNonHttpRoutePrefixes = ['/topic/', '/queue/', '/app/'];

function isIgnoredClientRoute(route) {
  return route === '/auth/' || ignoredNonHttpRoutePrefixes.some((prefix) => route.startsWith(prefix));
}

function extractClientRoutes(source, endpointPaths) {
  const routes = [];
  const literalPattern = /(['"`])((?:\\.|(?!\1)[\s\S])*)\1/g;
  const resolveExpression = (expression) => {
    if (expression === 'API_BASE' || expression === 'API_BASE_URL') return '';

    const endpointReference = expression.match(/^API_CONTRACT\.endpoints\.([A-Za-z][A-Za-z0-9_]*)$/);
    if (endpointReference) {
      const endpointPath = endpointPaths[endpointReference[1]];
      if (!endpointPath) {
        fail(`Client references unknown API_CONTRACT endpoint "${endpointReference[1]}".`);
        return '/__unknown_contract_endpoint__';
      }
      return endpointPath;
    }

    const builderEndpointReference = expression.match(/\bbuildContractPath\(\s*API_CONTRACT\.endpoints\.([A-Za-z][A-Za-z0-9_]*)/);
    if (builderEndpointReference) {
      const endpointPath = endpointPaths[builderEndpointReference[1]];
      if (!endpointPath) {
        fail(`Client references unknown API_CONTRACT endpoint "${builderEndpointReference[1]}".`);
        return '/__unknown_contract_endpoint__';
      }
      return endpointPath;
    }

    return undefined;
  };

  const addRoute = (route) => {
    if (!route || route.startsWith('${')) return;

    const normalized = replaceTemplateExpressions(route, resolveExpression).split('?')[0];
    if (!normalized.startsWith('/') || isIgnoredClientRoute(normalized)) return;
    routes.push(normalized);
  };

  for (const match of source.matchAll(literalPattern)) {
    const value = match[2];
    const apiBaseMatch = value.match(/^\$\{\s*API_BASE(?:_URL)?\s*\}/);
    const route = value.startsWith('/') || apiBaseMatch || value.startsWith('${API_CONTRACT.endpoints.')
      ? replaceTemplateExpressions(value, resolveExpression)
      : undefined;

    addRoute(route);

    if (apiBaseMatch) {
      const suffix = value.slice(apiBaseMatch[0].length);
      const staticPathSuffix = suffix
        .split(/\$\{[\s\S]*?\}/g)
        .some((part) => /\/[A-Za-z0-9._~-]+/.test(part));
      const resolvedSuffix = replaceTemplateExpressions(suffix, resolveExpression);

      if (staticPathSuffix && !resolvedSuffix.startsWith('/')) {
        fail('Client API_BASE composition contains an unresolved hardcoded HTTP path suffix; use API_CONTRACT.endpoints with buildContractPath.');
      }
    }
  }

  for (const match of source.matchAll(/\bbuildContractPath\s*\(\s*([^,)\n]+)/g)) {
    const firstArgument = match[1].trim();
    if (firstArgument === 'template: string') continue;

    assert(
      /^API_CONTRACT\.endpoints\.[A-Za-z][A-Za-z0-9_]*$/.test(firstArgument)
        || /^['"`]/.test(firstArgument),
      'Client buildContractPath calls must use API_CONTRACT.endpoints or a literal path as their first argument.',
    );
  }

  return routes;
}

function extractBackendRoutes() {
  const routes = new Set();
  const operations = new Map();

  for (const file of fs.readdirSync(backendControllerDirectory).filter((name) => name.endsWith('.java'))) {
    const source = read(path.join(backendControllerDirectory, file));
    const classMapping = source.match(/@RequestMapping\(\s*"([^"]+)"\s*\)/);
    const classPath = classMapping?.[1] ?? '';

    for (const match of source.matchAll(/@(Get|Post|Put|Patch|Delete)Mapping\s*(?:\(([\s\S]*?)\))?/g)) {
      const argumentsText = match[2] ?? '';
      const methodPath = argumentsText.match(/(?:value\s*=\s*)?"([^"]*)"/)?.[1] ?? '';
      const fullPath = methodPath.startsWith('/v${') ? methodPath : `${classPath}${methodPath}`;
      const versionlessPath = fullPath.replace(/^\/v\$\{racetracker\.api\.version\}/, '');
      validateRouteTemplate(versionlessPath, `${file} ${match[1]}Mapping`, { allowBracePlaceholders: true });
      const route = canonicalRoute(versionlessPath);
      routes.add(route);
      if (!operations.has(route)) operations.set(route, new Set());
      operations.get(route).add(match[1].toUpperCase());
    }
  }

  return { routes, operations };
}

const contract = JSON.parse(read(contractPath));
const expectedWebBase = `${contract.apiPath}/v${contract.apiVersion}`;
for (const [endpointKey, endpointPath] of Object.entries(contract.endpoints)) {
  if (typeof endpointPath === 'string') {
    validateRouteTemplate(endpointPath, `contract.endpoints.${endpointKey}`);
  }
}
const contractEndpointShapes = new Set(Object.values(contract.endpoints).map(canonicalRoute));

assert(
  Object.keys(contract.endpoints).length > 0,
  'contract.endpoints must contain at least one endpoint declaration.',
);
for (const [endpointKey, endpointPath] of Object.entries(contract.endpoints)) {
  assert(
    typeof endpointPath === 'string' && endpointPath.startsWith('/'),
    `contract.endpoints.${endpointKey} must be a versionless path beginning with "/".`,
  );
}

assert(
  contract.web.apiBasePath === expectedWebBase,
  `contract.web.apiBasePath must be "${expectedWebBase}" but is "${contract.web.apiBasePath}".`,
);

const expectedWsPath = `${contract.apiPath}/ws-tracking`;
assert(
  contract.web.wsTrackingPath === expectedWsPath,
  `contract.web.wsTrackingPath must be "${expectedWsPath}" but is "${contract.web.wsTrackingPath}".`,
);

assert(
  contract.legal && typeof contract.legal.privacyPolicyVersion === 'string' && contract.legal.privacyPolicyVersion.length > 0,
  'contract.legal.privacyPolicyVersion must be a non-empty string.',
);
assert(
  contract.legal && contract.legal.privacyPolicyPath === '/privacy',
  'contract.legal.privacyPolicyPath must be "/privacy".',
);
assert(
  read(backendAuthServicePath).includes(`CURRENT_PRIVACY_POLICY_VERSION = "${contract.legal.privacyPolicyVersion}"`),
  'Backend registration privacy policy version must match contract.legal.privacyPolicyVersion.',
);

assert(
  contract.i18n && typeof contract.i18n.defaultLocale === 'string' && contract.i18n.defaultLocale.length > 0,
  'contract.i18n.defaultLocale must be a non-empty string.',
);
const supportedLocales = Array.isArray(contract.i18n?.supportedLocales) ? contract.i18n.supportedLocales : [];
assert(
  supportedLocales.length > 0,
  'contract.i18n.supportedLocales must be a non-empty array.',
);
assert(
  supportedLocales.includes(contract.i18n?.defaultLocale),
  'contract.i18n.defaultLocale must be included in supportedLocales.',
);
assert(
  supportedLocales.includes(contract.i18n?.fallbackLocale),
  'contract.i18n.fallbackLocale must be included in supportedLocales.',
);
assert(
  Array.isArray(contract.i18n?.rtlLocales) && contract.i18n.rtlLocales.every((locale) => supportedLocales.includes(locale)),
  'contract.i18n.rtlLocales must be a subset of supportedLocales.',
);

const backendConfig = read(backendConfigPath);
const backendVersionMatch = backendConfig.match(/racetracker:\s*[\s\S]*?api:\s*[\s\S]*?version:\s*([0-9]+)/);
if (!backendVersionMatch) {
  fail('Could not find racetracker.api.version in backend application.yml.');
} else {
  const backendVersion = Number(backendVersionMatch[1]);
  assert(
    backendVersion === Number(contract.apiVersion),
    `Backend racetracker.api.version (${backendVersion}) must equal contract apiVersion (${contract.apiVersion}).`,
  );
}

const backendSecurity = read(backendSecurityPath);
assert(
  backendSecurity.includes('"/v" + raceTrackerProperties.getApi().getVersion()'),
  'SecurityConfig must build auth allowlist paths from racetracker.api.version (no hardcoded /v1).',
);
assert(
  !backendSecurity.includes('"/v1/auth/'),
  'SecurityConfig still contains hardcoded "/v1/auth/*" routes.',
);

const webApi = read(webApiPath);
assert(
  webApi.includes("from '@race-tracker/contracts'"),
  'Web API client must import API contract from @race-tracker/contracts.',
);
assert(
  webApi.includes('const API_BASE = API_CONTRACT.web.apiBasePath'),
  'Web API base path must come from API_CONTRACT.web.apiBasePath.',
);
assert(
  webApi.includes('API_CONTRACT.endpoints.publicRaceResults')
    && webApi.includes('API_CONTRACT.endpoints.raceResultsPage'),
  'Web results endpoints must be resolved from API_CONTRACT.',
);
assert(
  !/const API_BASE\s*=\s*['"]\/api\/v[0-9]+['"]/.test(webApi),
  'Web API client still hardcodes /api/vN.',
);
assert(
  webApi.includes('API_CONTRACT.web.wsTrackingPath'),
  'Web live-tracking client must resolve the WebSocket path from API_CONTRACT.web.wsTrackingPath.',
);

const backendWebSocketPath = path.join(root, 'apps/backend/src/main/java/com/racetracker/config/WebSocketConfig.java');
const backendWebSocket = read(backendWebSocketPath);
assert(
  backendWebSocket.includes('"/ws-tracking"'),
  'WebSocketConfig must register the "/ws-tracking" STOMP endpoint.',
);

const mobileApi = read(mobileApiPath);
assert(
  mobileApi.includes("from '@race-tracker/contracts'"),
  'Mobile API client must import API contract from @race-tracker/contracts.',
);
assert(
  mobileApi.includes('const API_BASE_URL = getVersionedApiBase(API_ROOT);'),
  'Mobile API base path must be derived from getVersionedApiBase(API_ROOT).',
);
assert(
  mobileApi.includes('API_CONTRACT.endpoints.myResultsPage'),
  'Mobile paginated results endpoint must be resolved from API_CONTRACT.',
);
assert(
  !/\/api\/v[0-9]+/.test(mobileApi),
  'Mobile API client still contains hardcoded /api/vN path.',
);

const { routes: backendEndpointShapes, operations: backendOperations } = extractBackendRoutes();
const backendOnlyShapes = [...backendEndpointShapes].filter((shape) => !contractEndpointShapes.has(shape)).sort();
const contractOnlyShapes = [...contractEndpointShapes].filter((shape) => !backendEndpointShapes.has(shape)).sort();

assert(
  backendOnlyShapes.length === 0,
  `Backend controller endpoint shapes missing from contract.endpoints: ${backendOnlyShapes.join(', ') || '(none)'}.`,
);
assert(
  contractOnlyShapes.length === 0,
  `Contract endpoint shapes missing from backend controllers: ${contractOnlyShapes.join(', ') || '(none)'}.`,
);

for (const [clientName, clientSource] of [['web', webApi], ['mobile', mobileApi]]) {
  for (const route of extractClientRoutes(clientSource, contract.endpoints)) {
    assert(
      contractEndpointShapes.has(canonicalRoute(route)),
      `${clientName} API client uses undeclared endpoint path "${route}".`,
    );
  }

  for (const operation of extractClientOperations(clientSource, contract.endpoints, clientName)) {
    const route = canonicalRoute(operation.route);
    const allowedMethods = backendOperations.get(route);
    assert(
      allowedMethods?.has(operation.method),
      `${clientName} client uses ${operation.method} for endpoints.${operation.endpointKey} (${operation.route}), but backend mappings allow ${[...(allowedMethods ?? [])].sort().join(', ') || '(none)'}.`,
    );
  }
}

const contractPackage = read(contractPackagePath);
const contractTypes = read(contractTypesPath);

for (const endpointKey of requiredPublicEndpointKeys) {
  assert(
    Object.hasOwn(contract.endpoints, endpointKey),
    `packages/contracts/contract.json is missing endpoints.${endpointKey}.`,
  );
}

for (const [endpointKey, endpointPath] of Object.entries(contract.endpoints)) {
  assert(
    contractPackage.includes(`${endpointKey}: '${endpointPath}'`),
    `packages/contracts/index.js endpoints.${endpointKey} is out of sync with contract.json.`,
  );
}

const packageEndpointBlock = contractPackage.match(/endpoints:\s*\{([\s\S]*?)\n\s*\},/)?.[1] ?? '';
const packageEndpointEntries = Object.fromEntries(
  [...packageEndpointBlock.matchAll(/^\s+([A-Za-z][A-Za-z0-9_]*): '([^']*)',?\s*$/gm)]
    .map((match) => [match[1], match[2]]),
);
assert(
  Object.keys(packageEndpointEntries).length === Object.keys(contract.endpoints).length
    && Object.entries(contract.endpoints).every(([key, value]) => packageEndpointEntries[key] === value),
  'packages/contracts/index.js endpoint keys or values are out of sync with contract.json.',
);

const endpointKeyMatches = contractTypes.matchAll(/^\s+\|\s+'([^']+)'/gm);
const typeEndpointKeys = new Set(Array.from(endpointKeyMatches, (match) => match[1]));

for (const endpointKey of requiredPublicEndpointKeys) {
  assert(
    typeEndpointKeys.has(endpointKey),
    `packages/contracts/index.d.ts is missing EndpointKey "${endpointKey}".`,
  );
}
assert(
  typeEndpointKeys.size === Object.keys(contract.endpoints).length
    && Object.keys(contract.endpoints).every((endpointKey) => typeEndpointKeys.has(endpointKey)),
  'packages/contracts/index.d.ts EndpointKey is out of sync with contract.json.',
);

assert(
  contractPackage.includes(`apiVersion: ${contract.apiVersion}`),
  'packages/contracts/index.js apiVersion is out of sync with contract.json.',
);
assert(
  contractPackage.includes(`apiBasePath: '${contract.web.apiBasePath}'`),
  'packages/contracts/index.js web.apiBasePath is out of sync with contract.json.',
);
assert(
  contractPackage.includes(`defaultApiRoot: '${contract.mobile.defaultApiRoot}'`),
  'packages/contracts/index.js mobile.defaultApiRoot is out of sync with contract.json.',
);
assert(
  contractPackage.includes(`wsTrackingPath: '${contract.web.wsTrackingPath}'`),
  'packages/contracts/index.js web.wsTrackingPath is out of sync with contract.json.',
);
assert(
  contractPackage.includes(`privacyPolicyVersion: '${contract.legal.privacyPolicyVersion}'`)
    && contractPackage.includes(`privacyPolicyPath: '${contract.legal.privacyPolicyPath}'`),
  'packages/contracts/index.js legal block is out of sync with contract.json.',
);
assert(
  contractPackage.includes(`defaultLocale: '${contract.i18n.defaultLocale}'`)
    && contractPackage.includes(`fallbackLocale: '${contract.i18n.fallbackLocale}'`)
    && supportedLocales.every((locale) => contractPackage.includes(`'${locale}'`)),
  'packages/contracts/index.js i18n block is out of sync with contract.json.',
);
assert(
  /requestMatchers\s*\(\s*HttpMethod\.GET\s*,\s*apiPrefix \+ "\/public\/races\/\*\*"\s*\)/.test(backendSecurity),
  'SecurityConfig must allow anonymous versioned public spectator routes with a GET-scoped apiPrefix matcher.',
);

if (errors.length > 0) {
  console.error('Contract validation failed:\n');
  for (const error of errors) {
    console.error(`- ${error}`);
  }
  process.exit(1);
}

console.log('Contract validation passed.');
