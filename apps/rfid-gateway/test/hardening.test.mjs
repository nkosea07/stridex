import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs/promises';
import os from 'node:os';
import path from 'node:path';

import { FileBatchStore } from '../src/store.mjs';
import { GatewayService } from '../src/gateway-service.mjs';
import { loadConfig } from '../src/config.mjs';
import { createServer } from '../src/server.mjs';
import { canonicalTag } from '../src/tag.mjs';

function memoryStore() {
  const queued = [];
  const raw = [];
  return {
    queued,
    raw,
    async enqueue(batch) { queued.push(batch); },
    async appendRawLog(reads) { raw.push(...reads); },
    async countPendingFiles() { return queued.length; },
    async countPendingReads() { return queued.reduce((total, batch) => total + batch.reads.length, 0); },
    async countFailedFiles() { return 0; },
  };
}

function service(store, overrides = {}) {
  return new GatewayService({
    config: { batchSize: 200, eventOrigin: 'SIMULATED', gatewayName: 'finish-1', readerSerial: 'R700-9',
      dedupeWindowMs: 3000, retryInitialDelayMs: 1000, retryMaxDelayMs: 2000, ...overrides },
    store,
    backendClient: {},
    logger: { error() {} },
  });
}

test('callers cannot choose the event origin; the gateway mode decides it', async () => {
  const store = memoryStore();
  await service(store).ingest({ reads: [{ rfidTag: 'E200AA', readAt: '2026-11-15T06:10:00Z', eventOrigin: 'HARDWARE' }] });

  assert.equal(store.queued[0].reads[0].eventOrigin, 'SIMULATED');
});

test('reads without a source event id get a stable one so retries are recognised', async () => {
  const first = memoryStore();
  const second = memoryStore();
  const read = { rfidTag: 'E200AA', readAt: '2026-11-15T06:10:00.123Z', antenna: 'A2' };
  await service(first).ingest({ reads: [read] });
  await service(second).ingest({ reads: [read] });

  const id = first.queued[0].reads[0].sourceEventId;
  assert.match(id, /^gw-[0-9a-f]{40}$/);
  assert.equal(second.queued[0].reads[0].sourceEventId, id);
});

test('repeated reads of one pass collapse to the first read, while later passes and earlier reads still flow', async () => {
  const store = memoryStore();
  const gateway = service(store);
  const result = await gateway.ingest({ reads: [
    { rfidTag: 'E200AA', readAt: '2026-11-15T06:10:00.000Z' },
    { rfidTag: 'E200AA', readAt: '2026-11-15T06:10:00.400Z' },
    { rfidTag: 'E200AA', readAt: '2026-11-15T06:10:02.900Z' },
    { rfidTag: 'E200BB', readAt: '2026-11-15T06:10:01.000Z' },
  ] });
  assert.equal(result.queuedReads, 2);
  assert.equal(result.suppressedReads, 2);

  await gateway.ingest({ reads: [{ rfidTag: 'E200AA', readAt: '2026-11-15T06:20:00Z' }] });
  await gateway.ingest({ reads: [{ rfidTag: 'E200AA', readAt: '2026-11-15T06:09:59Z' }] });
  assert.equal(store.queued.length, 3);
  assert.equal(store.raw.length, 6);
  assert.equal((await gateway.health()).suppressedDuplicateReads, 2);
});

test('tag values are canonical before they leave the edge', () => {
  assert.equal(canonicalTag(' e200 3411-b802 '), 'E2003411B802');
  assert.equal(canonicalTag('TAG-001'), 'TAG-001');
});

test('a truncated spool file is quarantined instead of blocking every later batch', async () => {
  const root = await fs.mkdtemp(path.join(os.tmpdir(), 'rfid-gateway-corrupt-'));
  const store = new FileBatchStore(root);
  await store.init();
  await fs.writeFile(path.join(root, 'pending', '0000-corrupt.json'), '{"reads":[{"rfidTag":"E2', 'utf8');
  await store.enqueue({ reads: [{ rfidTag: 'E200AA', readAt: '2026-11-15T06:10:00Z' }] });

  assert.equal(await store.countPendingReads(), 1);
  const leased = await store.leaseNext();

  assert.equal(leased.payload.reads[0].rfidTag, 'E200AA');
  assert.equal(await store.countCorruptFiles(), 1);
  assert.deepEqual((await fs.readdir(path.join(root, 'pending'))).filter((name) => name.endsWith('.tmp')), []);
});

test('raw reads are appended to a local log on the edge device', async () => {
  const root = await fs.mkdtemp(path.join(os.tmpdir(), 'rfid-gateway-raw-'));
  const store = new FileBatchStore(root);
  await store.init();
  await store.appendRawLog([{ rfidTag: 'E200AA', readAt: '2026-11-15T06:10:00Z' }]);
  await store.appendRawLog([{ rfidTag: 'E200BB', readAt: '2026-11-15T06:10:01Z' }]);

  const [file] = await fs.readdir(path.join(root, 'raw'));
  const lines = (await fs.readFile(path.join(root, 'raw', file), 'utf8')).trim().split('\n');
  assert.equal(lines.length, 2);
  assert.equal(JSON.parse(lines[1]).rfidTag, 'E200BB');
});

test('listening beyond loopback requires a local token', () => {
  process.env.RFID_BACKEND_BASE_URL = 'http://backend:8081';
  process.env.RFID_BACKEND_READER_KEY = 'reader-key';
  process.env.RFID_GATEWAY_MODE = 'simulation';
  process.env.RFID_GATEWAY_HOST = '0.0.0.0';
  delete process.env.RFID_GATEWAY_LOCAL_TOKEN;
  assert.throws(() => loadConfig(), /RFID_GATEWAY_LOCAL_TOKEN/);

  process.env.RFID_GATEWAY_LOCAL_TOKEN = 'a-long-random-local-token-value';
  assert.equal(loadConfig().localToken, 'a-long-random-local-token-value');
  delete process.env.RFID_GATEWAY_HOST;
  delete process.env.RFID_GATEWAY_LOCAL_TOKEN;
  assert.equal(loadConfig().host, '127.0.0.1');
});

test('the local API rejects reads without the token but health stays readable', async () => {
  const gatewayService = {
    async health() { return { ok: true }; },
    async ingest() { return { queuedReads: 1 }; },
  };
  const server = createServer({ gatewayService, localToken: 'a-long-random-local-token-value', logger: { error() {} } });
  await new Promise((resolve) => server.listen(0, '127.0.0.1', resolve));
  const base = `http://127.0.0.1:${server.address().port}`;
  try {
    const body = JSON.stringify({ reads: [{ rfidTag: 'E200AA', readAt: '2026-11-15T06:10:00Z' }] });
    const denied = await fetch(`${base}/reads`, { method: 'POST', body });
    const allowed = await fetch(`${base}/reads`, { method: 'POST', body, headers: { 'X-Gateway-Token': 'a-long-random-local-token-value' } });
    const health = await fetch(`${base}/health`);
    assert.equal(denied.status, 401);
    assert.equal(allowed.status, 202);
    assert.equal(health.status, 200);
  } finally {
    await new Promise((resolve) => server.close(resolve));
  }
});
