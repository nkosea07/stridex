import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');

const contractPath = path.join(root, 'packages/contracts/contract.json');
const backendConfigPath = path.join(root, 'apps/backend/src/main/resources/application.yml');
const backendSecurityPath = path.join(root, 'apps/backend/src/main/java/com/racetracker/config/SecurityConfig.java');
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

const contract = JSON.parse(read(contractPath));
const expectedWebBase = `${contract.apiPath}/v${contract.apiVersion}`;

assert(
  contract.web.apiBasePath === expectedWebBase,
  `contract.web.apiBasePath must be "${expectedWebBase}" but is "${contract.web.apiBasePath}".`,
);

const expectedWsPath = `${contract.apiPath}/ws-tracking`;
assert(
  contract.web.wsTrackingPath === expectedWsPath,
  `contract.web.wsTrackingPath must be "${expectedWsPath}" but is "${contract.web.wsTrackingPath}".`,
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

const endpointKeyMatches = contractTypes.matchAll(/^\s+\|\s+'([^']+)'/gm);
const typeEndpointKeys = new Set(Array.from(endpointKeyMatches, (match) => match[1]));

for (const endpointKey of requiredPublicEndpointKeys) {
  assert(
    typeEndpointKeys.has(endpointKey),
    `packages/contracts/index.d.ts is missing EndpointKey "${endpointKey}".`,
  );
}

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
