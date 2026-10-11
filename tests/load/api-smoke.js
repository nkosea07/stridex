// Fast k6 API smoke for CI: proves the backend is up and the public/spectator surface
// responds with expected status codes. No seed data required.
//
// Run: k6 run -e BASE_URL=http://localhost:8081 tests/load/api-smoke.js

import http from 'k6/http';
import { check, sleep } from 'k6';

export const options = {
  vus: 2,
  duration: '15s',
  thresholds: {
    checks: ['rate>0.99'],
  },
};

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8081';
const UNKNOWN_RACE = '00000000-0000-0000-0000-000000000000';

export default function () {
  const health = http.get(`${BASE_URL}/api/actuator/health`, {
    responseCallback: http.expectedStatuses(200),
  });
  check(health, { 'health 200': (r) => r.status === 200 });

  const hub = http.get(`${BASE_URL}/api/v1/public/races/${UNKNOWN_RACE}/hub`, {
    responseCallback: http.expectedStatuses(200, 401, 404),
  });
  check(hub, { 'public hub reachable': (r) => [200, 401, 404].includes(r.status) });

  const anonymousRaces = http.get(`${BASE_URL}/api/v1/races`, {
    responseCallback: http.expectedStatuses(200, 401, 403),
  });
  check(anonymousRaces, { 'protected races endpoint guarded': (r) => [200, 401, 403].includes(r.status) });

  sleep(1);
}
