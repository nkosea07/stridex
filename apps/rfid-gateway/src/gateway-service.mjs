import { chunkReads, validateBatchPayload } from './payload.mjs';

export class GatewayService {
  constructor({ config, store, backendClient, logger = console }) {
    this.config = config;
    this.store = store;
    this.backendClient = backendClient;
    this.logger = logger;
    this.lastError = null;
    this.flushInProgress = false;
    this.lastFlushAttemptAt = null;
    this.lastFlushSuccessAt = null;
    this.lastHeartbeatAttemptAt = null;
    this.lastHeartbeatSuccessAt = null;
    this.nextFlushRetryAt = null;
    this.nextHeartbeatRetryAt = null;
    this.flushFailureCount = 0;
    this.heartbeatFailureCount = 0;
    this.currentFlushRetryDelayMs = config.retryInitialDelayMs;
    this.currentHeartbeatRetryDelayMs = config.retryInitialDelayMs;
  }

  async ingest(payload) {
    const normalized = validateBatchPayload(payload);
    const reads = normalized.reads.map((read) => ({
      ...read,
      eventOrigin: this.config.eventOrigin,
    }));
    const chunks = chunkReads(reads, this.config.batchSize);

    for (const reads of chunks) {
      await this.store.enqueue({ reads });
    }

    return {
      queuedBatches: chunks.length,
      queuedReads: normalized.reads.length,
      backlogFiles: await this.store.countPendingFiles()
    };
  }

  async flushOnce() {
    if (this.flushInProgress) {
      return { skipped: true };
    }
    if (this.nextFlushRetryAt && Date.now() < this.nextFlushRetryAt) {
      return { skipped: true, retryAt: new Date(this.nextFlushRetryAt).toISOString() };
    }

    this.flushInProgress = true;
    try {
      this.lastFlushAttemptAt = new Date().toISOString();
      const leased = await this.store.leaseNext();
      if (!leased) {
        return { skipped: false, delivered: 0 };
      }

      const response = await this.backendClient.submitReads(leased.payload);
      if (!response.ok) {
        this.lastError = `Backend returned ${response.status}`;
        await this.store.requeue(leased.filename);
        this.recordFlushFailure(this.lastError);
        return { skipped: false, delivered: 0, failed: true, status: response.status };
      }

      if ((response.body?.invalidReader ?? 0) > 0) {
        this.lastError = 'Backend rejected reader heartbeat key';
        await this.store.requeue(leased.filename);
        this.recordFlushFailure(this.lastError);
        return { skipped: false, delivered: 0, failed: true, status: response.status };
      }

      this.lastError = null;
      this.lastFlushSuccessAt = new Date().toISOString();
      this.flushFailureCount = 0;
      this.nextFlushRetryAt = null;
      this.currentFlushRetryDelayMs = this.config.retryInitialDelayMs;
      await this.store.ack(leased.filename);
      return {
        skipped: false,
        delivered: leased.payload.reads.length,
        response: response.body
      };
    } catch (error) {
      this.lastError = error instanceof Error ? error.message : String(error);
      this.recordFlushFailure(this.lastError);
      this.logger.error('Gateway flush failed', error);
      return { skipped: false, delivered: 0, failed: true };
    } finally {
      this.flushInProgress = false;
    }
  }

  async heartbeat() {
    if (this.nextHeartbeatRetryAt && Date.now() < this.nextHeartbeatRetryAt) {
      return { ok: false, skipped: true, retryAt: new Date(this.nextHeartbeatRetryAt).toISOString() };
    }
    this.lastHeartbeatAttemptAt = new Date().toISOString();
    const backlog = await this.store.countPendingReads();
    const payload = {
      gatewayName: this.config.gatewayName,
      gatewayStatus: this.lastError ? 'DEGRADED' : 'ONLINE',
      gatewayBacklog: backlog,
      firmwareVersion: this.config.firmwareVersion,
      hardwareModel: this.config.hardwareModel,
      hardwareProfile: this.config.hardwareProfile,
      readerClass: this.config.readerClass,
      readerSerial: this.config.readerSerial,
      mode: this.config.mode,
      lastError: this.lastError,
      observedAt: new Date().toISOString()
    };

    const response = await this.backendClient.sendHeartbeat(payload);
    if (!response.ok) {
      this.lastError = `Heartbeat failed with ${response.status}`;
      this.recordHeartbeatFailure(this.lastError);
      return { ok: false, status: response.status, body: response.body };
    }

    this.lastHeartbeatSuccessAt = new Date().toISOString();
    this.heartbeatFailureCount = 0;
    this.nextHeartbeatRetryAt = null;
    this.currentHeartbeatRetryDelayMs = this.config.retryInitialDelayMs;
    return { ok: true, status: response.status, body: response.body };
  }

  async health() {
    return {
      gatewayName: this.config.gatewayName,
      backendBaseUrl: this.config.backendBaseUrl,
      hardwareProfile: this.config.hardwareProfile,
      readerClass: this.config.readerClass,
      pendingFiles: await this.store.countPendingFiles(),
      pendingReads: await this.store.countPendingReads(),
      lastError: this.lastError,
      lastFlushAttemptAt: this.lastFlushAttemptAt,
      lastFlushSuccessAt: this.lastFlushSuccessAt,
      lastHeartbeatAttemptAt: this.lastHeartbeatAttemptAt,
      lastHeartbeatSuccessAt: this.lastHeartbeatSuccessAt,
      flushFailureCount: this.flushFailureCount,
      heartbeatFailureCount: this.heartbeatFailureCount,
      nextFlushRetryAt: this.nextFlushRetryAt ? new Date(this.nextFlushRetryAt).toISOString() : null,
      nextHeartbeatRetryAt: this.nextHeartbeatRetryAt ? new Date(this.nextHeartbeatRetryAt).toISOString() : null
    };
  }

  recordFlushFailure(message) {
    this.lastError = message;
    this.flushFailureCount += 1;
    this.nextFlushRetryAt = Date.now() + this.currentFlushRetryDelayMs;
    this.currentFlushRetryDelayMs = Math.min(this.currentFlushRetryDelayMs * 2, this.config.retryMaxDelayMs);
  }

  recordHeartbeatFailure(message) {
    this.lastError = message;
    this.heartbeatFailureCount += 1;
    this.nextHeartbeatRetryAt = Date.now() + this.currentHeartbeatRetryDelayMs;
    this.currentHeartbeatRetryDelayMs = Math.min(this.currentHeartbeatRetryDelayMs * 2, this.config.retryMaxDelayMs);
  }
}
