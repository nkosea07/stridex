import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs/promises';
import os from 'node:os';
import path from 'node:path';

import { FileBatchStore } from '../src/store.mjs';
import { GatewayService, isPermanentRejection } from '../src/gateway-service.mjs';
import { loadConfig } from '../src/config.mjs';
import { validateBatchPayload } from '../src/payload.mjs';

const silentLogger = { error() {} };
const read = { rfidTag: 'TAG-1', readAt: '2026-06-02T06:00:00Z' };

function config(overrides = {}) {
  return { batchSize: 200, retryInitialDelayMs: 1000, retryMaxDelayMs: 2000, eventOrigin: 'HARDWARE', ...overrides };
}

async function storeWithBatches(...batches) {
  const root = await fs.mkdtemp(path.join(os.tmpdir(), 'rfid-gateway-flush-'));
  const store = new FileBatchStore(root);
  await store.init();
  for (const batch of batches) {
    await store.enqueue(batch);
    await new Promise((resolve) => setTimeout(resolve, 2));
  }
  return store;
}

test('a network error requeues the leased batch instead of stranding it in processing', async () => {
  const store = await storeWithBatches({ reads: [read] });
  const backendClient = { async submitReads() { throw new Error('connect ECONNREFUSED'); } };
  const service = new GatewayService({ config: config(), store, backendClient, logger: silentLogger });

  const result = await service.flushOnce();

  assert.equal(result.failed, true);
  assert.equal(await store.countPendingFiles(), 1);
  assert.deepEqual(await fs.readdir(store.processingDir), []);
  assert.equal(service.flushFailureCount, 1);
});

test('a permanently rejected batch moves to the failed spool so later batches still deliver', async () => {
  const store = await storeWithBatches({ reads: [{ ...read, rfidTag: 'BAD' }] }, { reads: [read] });
  const submitted = [];
  const backendClient = {
    async submitReads(payload) {
      submitted.push(payload.reads[0].rfidTag);
      return payload.reads[0].rfidTag === 'BAD'
        ? { ok: false, status: 400, body: { message: 'Simulated RFID events are disabled' } }
        : { ok: true, status: 200, body: { invalidReader: 0 } };
    }
  };
  const service = new GatewayService({ config: config(), store, backendClient, logger: silentLogger });

  const first = await service.flushOnce();
  const second = await service.flushOnce();
  const health = await service.health();

  assert.equal(first.rejected, true);
  assert.equal(second.delivered, 1);
  assert.deepEqual(submitted, ['BAD', 'TAG-1']);
  assert.equal(health.failedFiles, 1);
  assert.equal(health.pendingFiles, 0);

  assert.deepEqual(await service.requeueRejected(), { requeuedFiles: 1 });
  assert.equal(await store.countPendingFiles(), 1);
});

test('retryable statuses keep the batch pending with backoff', async () => {
  assert.equal(isPermanentRejection(401), false);
  assert.equal(isPermanentRejection(429), false);
  assert.equal(isPermanentRejection(503), false);
  assert.equal(isPermanentRejection(400), true);
  assert.equal(isPermanentRejection(413), true);
});

test('heartbeat network failures are counted, backed off, and cleared on recovery', async () => {
  let fail = true;
  const backendClient = {
    async sendHeartbeat() {
      if (fail) throw new Error('timeout');
      return { ok: true, status: 200, body: {} };
    }
  };
  const store = { async countPendingReads() { return 0; }, async countPendingFiles() { return 0; } };
  const service = new GatewayService({ config: config({ retryInitialDelayMs: 0 }), store, backendClient, logger: silentLogger });

  const failed = await service.heartbeat();
  assert.equal(failed.ok, false);
  assert.equal(service.heartbeatFailureCount, 1);
  assert.equal(service.lastError, 'timeout');

  fail = false;
  service.nextHeartbeatRetryAt = null;
  const recovered = await service.heartbeat();
  assert.equal(recovered.ok, true);
  assert.equal(service.lastError, null);
});

test('loadConfig rejects batch sizes the backend cannot accept', () => {
  const previous = { ...process.env };
  try {
    process.env.RFID_BACKEND_BASE_URL = 'http://backend:8081';
    process.env.RFID_BACKEND_READER_KEY = 'key';
    process.env.RFID_GATEWAY_BATCH_SIZE = '0';
    assert.throws(() => loadConfig(), /RFID_GATEWAY_BATCH_SIZE/);
    process.env.RFID_GATEWAY_BATCH_SIZE = '500';
    assert.throws(() => loadConfig(), /RFID_GATEWAY_BATCH_SIZE/);
  } finally {
    process.env = previous;
  }
});

test('payload validation rejects timestamps without an offset and non-numeric rssi', () => {
  assert.throws(() => validateBatchPayload({ reads: [{ rfidTag: 'T', readAt: '2026-06-02T06:00:00' }] }), /UTC offset/);
  assert.throws(() => validateBatchPayload({ reads: [{ ...read, rssi: '-40' }] }), /rssi/);
  assert.equal(validateBatchPayload({ reads: [{ rfidTag: 'T', readAt: '2026-06-02T06:00Z', rssi: -40.5 }] }).reads.length, 1);
});
