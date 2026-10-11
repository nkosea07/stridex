// k6 load test for the spectator tracking hot paths.
//
// Run: k6 run -e BASE_URL=https://racetracker.example.com -e RACE_ID=<uuid> tests/load/tracking-load.js
// The race must be IN_PROGRESS with live athletes for meaningful results.

import http from 'k6/http';
import { check, sleep } from 'k6';

export const options = {
  scenarios: {
    public_spectators: {
      executor: 'ramping-vus',
      startVUs: 10,
      stages: [
        { duration: '1m', target: 200 },
        { duration: '5m', target: 200 },
        { duration: '1m', target: 0 },
      ],
    },
  },
  thresholds: {
    http_req_failed: ['rate<0.01'],
    http_req_duration: ['p(95)<500'],
  },
};

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8081';
const RACE_ID = __ENV.RACE_ID;
const API = `${BASE_URL}/api/v1`;

export default function () {
  if (!RACE_ID) {
    throw new Error('RACE_ID env var is required');
  }

  const hub = http.get(`${API}/public/races/${RACE_ID}/hub`, { tags: { endpoint: 'hub' } });
  check(hub, { 'hub 200': (r) => r.status === 200 });

  const live = http.get(`${API}/public/races/${RACE_ID}/tracking/live`, { tags: { endpoint: 'live' } });
  check(live, { 'live 200': (r) => r.status === 200 });

  const feed = http.get(`${API}/public/races/${RACE_ID}/tracking/live-feed?limit=50`, {
    tags: { endpoint: 'live-feed' },
  });
  check(feed, { 'feed 200': (r) => r.status === 200 });

  sleep(5 + Math.random() * 5);
}
