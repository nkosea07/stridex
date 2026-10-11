import test from 'node:test';
import assert from 'node:assert/strict';

import { loadConfig } from '../src/config.mjs';
import { GatewayService } from '../src/gateway-service.mjs';

test('loadConfig exposes hardware profile metadata for edge deployments', () => {
  process.env.RFID_BACKEND_BASE_URL = 'https://backend.example.com';
  process.env.RFID_BACKEND_READER_KEY = 'reader-key';
  process.env.RFID_GATEWAY_MODE = 'hardware';
  process.env.RFID_GATEWAY_READER_SERIAL = 'R700-001';
  process.env.RFID_GATEWAY_HARDWARE_MODEL = 'Impinj R700';
  process.env.RFID_GATEWAY_HARDWARE_PROFILE = 'DEMO_COMPACT_UHF';
  process.env.RFID_GATEWAY_READER_CLASS = 'FIXED_COMPACT_2_PORT';

  const config = loadConfig();

  assert.equal(config.hardwareProfile, 'DEMO_COMPACT_UHF');
  assert.equal(config.readerClass, 'FIXED_COMPACT_2_PORT');
  assert.equal(config.eventOrigin, 'HARDWARE');
  assert.equal(config.readerSerial, 'R700-001');
});

test('heartbeat forwards hardware profile metadata to the backend', async () => {
  const backendClient = {
    payload: null,
    async sendHeartbeat(payload) {
      this.payload = payload;
      return { ok: true, status: 200, body: {} };
    }
  };

  const store = {
    async countPendingReads() {
      return 3;
    },
    async countPendingFiles() {
      return 1;
    }
  };

  const gatewayService = new GatewayService({
    config: {
      gatewayName: 'edge-demo-1',
      backendBaseUrl: 'https://backend.example.com',
      firmwareVersion: 'demo-fw',
      hardwareModel: 'ThingMagic M7E',
      hardwareProfile: 'DEMO_COMPACT_UHF',
      readerClass: 'FIXED_COMPACT_2_PORT',
      readerSerial: 'DEMO-001',
      mode: 'simulation',
      eventOrigin: 'SIMULATED',
      retryInitialDelayMs: 1000,
      retryMaxDelayMs: 2000
    },
    store,
    backendClient
  });

  await gatewayService.heartbeat();

  assert.equal(backendClient.payload.hardwareProfile, 'DEMO_COMPACT_UHF');
  assert.equal(backendClient.payload.readerClass, 'FIXED_COMPACT_2_PORT');
  assert.equal(backendClient.payload.gatewayBacklog, 3);
  assert.equal(backendClient.payload.mode, 'simulation');
  assert.equal(backendClient.payload.readerSerial, 'DEMO-001');
});

test('ingest stamps every queued read with the configured event origin', async () => {
  const backendClient = {
    async sendHeartbeat() { return { ok: true, status: 200, body: {} }; },
  };
  const queued = [];
  const store = {
    async enqueue(batch) { queued.push(batch); },
    async countPendingFiles() { return queued.length; },
  };
  const gatewayService = new GatewayService({
    config: { batchSize: 10, eventOrigin: 'SIMULATED' },
    store,
    backendClient,
  });

  await gatewayService.ingest({ reads: [{ rfidTag: 'TAG-1', readAt: '2026-06-02T06:00:00Z' }] });

  assert.equal(queued[0].reads[0].eventOrigin, 'SIMULATED');
});

test('flush backs off after a backend failure and exposes retry state', async () => {
  const backendClient = {
    async submitReads() {
      return { ok: false, status: 502, body: {} };
    }
  };

  const store = {
    async enqueue() {},
    async countPendingFiles() {
      return 1;
    },
    async countPendingReads() {
      return 1;
    },
    async leaseNext() {
      return { filename: 'batch.json', payload: { reads: [{ rfidTag: 'TAG-1', readAt: '2026-06-02T06:00:00Z' }] } };
    },
    async requeue() {},
    async ack() {}
  };

  const gatewayService = new GatewayService({
    config: {
      gatewayName: 'edge-prod-1',
      backendBaseUrl: 'https://backend.example.com',
      firmwareVersion: 'prod-fw',
      hardwareModel: 'Impinj R700',
      hardwareProfile: 'IMPINJ_EVENT_GRADE',
      readerClass: 'FIXED_4_PORT',
      retryInitialDelayMs: 1000,
      retryMaxDelayMs: 2000
    },
    store,
    backendClient
  });

  const first = await gatewayService.flushOnce();
  const second = await gatewayService.flushOnce();
  const health = await gatewayService.health();

  assert.equal(first.failed, true);
  assert.equal(second.skipped, true);
  assert.equal(health.flushFailureCount, 1);
  assert.equal(typeof health.nextFlushRetryAt, 'string');
});
