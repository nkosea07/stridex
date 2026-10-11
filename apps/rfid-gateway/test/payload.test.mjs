import test from 'node:test';
import assert from 'node:assert/strict';

import { chunkReads, validateBatchPayload } from '../src/payload.mjs';

test('validateBatchPayload normalizes a valid payload', () => {
  const payload = validateBatchPayload({
    reads: [
      {
        sourceEventId: ' event-1 ',
        rfidTag: ' TAG-1 ',
        readAt: '2026-06-02T06:00:00Z'
      }
    ]
  });

  assert.equal(payload.reads[0].sourceEventId, 'event-1');
  assert.equal(payload.reads[0].rfidTag, 'TAG-1');
});

test('chunkReads splits reads by the configured batch size', () => {
  const chunks = chunkReads([{ id: 1 }, { id: 2 }, { id: 3 }], 2);
  assert.equal(chunks.length, 2);
  assert.equal(chunks[0].length, 2);
  assert.equal(chunks[1].length, 1);
});
