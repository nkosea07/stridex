import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs/promises';
import os from 'node:os';
import path from 'node:path';

import { FileBatchStore } from '../src/store.mjs';

test('store recovers processing batches back to pending on startup', async () => {
  const root = await fs.mkdtemp(path.join(os.tmpdir(), 'rfid-gateway-store-'));
  const processingDir = path.join(root, 'processing');
  await fs.mkdir(processingDir, { recursive: true });
  await fs.writeFile(path.join(processingDir, 'batch.json'), JSON.stringify({ reads: [{ rfidTag: 'TAG-1', readAt: '2026-06-02T06:00:00Z' }] }));

  const store = new FileBatchStore(root);
  await store.init();

  const pending = await store.listPending();
  assert.deepEqual(pending, ['batch.json']);
});
