export function validateBatchPayload(payload) {
  if (!payload || typeof payload !== 'object' || !Array.isArray(payload.reads) || payload.reads.length === 0) {
    throw new Error('payload.reads must be a non-empty array');
  }

  const reads = payload.reads.map((read, index) => {
    if (!read || typeof read !== 'object') {
      throw new Error(`reads[${index}] must be an object`);
    }
    const rfidTag = typeof read.rfidTag === 'string' ? read.rfidTag.trim() : '';
    const readAt = typeof read.readAt === 'string' ? read.readAt.trim() : '';

    if (!rfidTag) {
      throw new Error(`reads[${index}].rfidTag is required`);
    }
    if (!readAt) {
      throw new Error(`reads[${index}].readAt is required`);
    }

    return {
      sourceEventId: typeof read.sourceEventId === 'string' && read.sourceEventId.trim() ? read.sourceEventId.trim() : undefined,
      rfidTag,
      readAt,
      antenna: typeof read.antenna === 'string' && read.antenna.trim() ? read.antenna.trim() : undefined,
      rssi: read.rssi ?? undefined,
      rawPayload: typeof read.rawPayload === 'string' && read.rawPayload.trim() ? read.rawPayload : undefined,
      eventOrigin: typeof read.eventOrigin === 'string' && read.eventOrigin.trim() ? read.eventOrigin.trim().toUpperCase() : undefined
    };
  });

  return { reads };
}

export function chunkReads(reads, batchSize) {
  const chunks = [];
  for (let index = 0; index < reads.length; index += batchSize) {
    chunks.push(reads.slice(index, index + batchSize));
  }
  return chunks;
}
