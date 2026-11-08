import { chunkReads, validateBatchPayload } from './payload.mjs';

const RETRYABLE_CLIENT_STATUSES = new Set([401, 403, 408, 409, 425, 429]);

export function isPermanentRejection(status) {
  return status >= 400 && status < 500 && !RETRYABLE_CLIENT_STATUSES.has(status);
}

function describeBackendError(response) {
  const message = typeof response.body?.message === 'string' ? response.body.message : null;
  return message ? `Backend returned ${response.status}: ${message}` : `Backend returned ${response.status}`;
}

export class GatewayService {
  constructor({ config, store, backendClient, logger = console }) {
    this.config = config;
    this.store = store;
    this.backendClient = backendClient;
    this.logger = logger;
    this.flushError = null;
    this.heartbeatError = null;
    this.flushInProgress = false;
    this.lastFlushAttemptAt = null;
    this.lastFlushSuccessAt = null;
    this.lastHeartbeatAttemptAt = null;
    this.lastHeartbeatSuccessAt = null;
    this.nextFlushRetryAt = null;
    this.nextHeartbeatRetryAt = null;
    this.flushFailureCount = 0;
    this.heartbeatFailureCount = 0;
    this.rejectedBatchCount = 0;
    this.currentFlushRetryDelayMs = config.retryInitialDelayMs;
    this.currentHeartbeatRetryDelayMs = config.retryInitialDelayMs;
  }

  get lastError() {
    return this.flushError ?? this.heartbeatError;
  }

  async ingest(payload) {
    const normalized = validateBatchPayload(payload);
    const reads = normalized.reads.map((read) => ({
      ...read,
      eventOrigin: read.eventOrigin ?? this.config.eventOrigin,
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
    let leased = null;
    try {
      this.lastFlushAttemptAt = new Date().toISOString();
      leased = await this.store.leaseNext();
      if (!leased) {
        this.flushError = null;
        return { skipped: false, delivered: 0 };
      }

      const response = await this.backendClient.submitReads(leased.payload);
      if (!response.ok) {
        const message = describeBackendError(response);
        if (isPermanentRejection(response.status)) {
          await this.store.reject(leased.filename);
          leased = null;
          this.rejectedBatchCount += 1;
          this.flushError = `Batch rejected and moved to failed spool. ${message}`;
          this.logger.error(this.flushError);
          return { skipped: false, delivered: 0, failed: true, rejected: true, status: response.status };
        }
        await this.store.requeue(leased.filename);
        leased = null;
        this.recordFlushFailure(message);
        return { skipped: false, delivered: 0, failed: true, status: response.status };
      }

      if ((response.body?.invalidReader ?? 0) > 0) {
        await this.store.requeue(leased.filename);
        leased = null;
        this.recordFlushFailure('Backend reports this reader key is revoked; reads are retained until a new key is configured');
        return { skipped: false, delivered: 0, failed: true, status: response.status };
      }

      const delivered = Array.isArray(leased.payload?.reads) ? leased.payload.reads.length : 0;
      await this.store.ack(leased.filename);
      leased = null;
      this.flushError = null;
      this.lastFlushSuccessAt = new Date().toISOString();
      this.flushFailureCount = 0;
      this.nextFlushRetryAt = null;
      this.currentFlushRetryDelayMs = this.config.retryInitialDelayMs;
      return {
        skipped: false,
        delivered,
        response: response.body
      };
    } catch (error) {
      if (leased) {
        try {
          await this.store.requeue(leased.filename);
        } catch (requeueError) {
          this.logger.error('Gateway failed to requeue leased batch', requeueError);
        }
      }
      this.recordFlushFailure(error instanceof Error ? error.message : String(error));
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

    let response;
    try {
      const backlog = await this.store.countPendingReads();
      const rejectedFiles = await this.countFailedFiles();
      const degraded = Boolean(this.flushError || this.heartbeatError) || rejectedFiles > 0;
      const payload = {
        gatewayName: this.config.gatewayName,
        gatewayStatus: degraded ? 'DEGRADED' : 'ONLINE',
        gatewayBacklog: backlog,
        firmwareVersion: this.config.firmwareVersion,
        hardwareModel: this.config.hardwareModel,
        hardwareProfile: this.config.hardwareProfile,
        readerClass: this.config.readerClass,
        readerSerial: this.config.readerSerial,
        mode: this.config.mode,
        lastError: this.lastError ?? (rejectedFiles > 0 ? `${rejectedFiles} rejected batch file(s) need operator review` : null),
        observedAt: new Date().toISOString()
      };
      response = await this.backendClient.sendHeartbeat(payload);
    } catch (error) {
      this.recordHeartbeatFailure(error instanceof Error ? error.message : String(error));
      this.logger.error('Gateway heartbeat failed', error);
      return { ok: false, error: this.heartbeatError };
    }

    if (!response.ok) {
      this.recordHeartbeatFailure(`Heartbeat failed with ${response.status}`);
      return { ok: false, status: response.status, body: response.body };
    }

    this.heartbeatError = null;
    this.lastHeartbeatSuccessAt = new Date().toISOString();
    this.heartbeatFailureCount = 0;
    this.nextHeartbeatRetryAt = null;
    this.currentHeartbeatRetryDelayMs = this.config.retryInitialDelayMs;
    return { ok: true, status: response.status, body: response.body };
  }

  async requeueRejected() {
    const requeued = await this.store.requeueFailed();
    if (requeued > 0) {
      this.nextFlushRetryAt = null;
    }
    return { requeuedFiles: requeued };
  }

  async health() {
    return {
      gatewayName: this.config.gatewayName,
      backendBaseUrl: this.config.backendBaseUrl,
      hardwareProfile: this.config.hardwareProfile,
      readerClass: this.config.readerClass,
      pendingFiles: await this.store.countPendingFiles(),
      pendingReads: await this.store.countPendingReads(),
      failedFiles: await this.countFailedFiles(),
      lastError: this.lastError,
      lastFlushError: this.flushError,
      lastHeartbeatError: this.heartbeatError,
      lastFlushAttemptAt: this.lastFlushAttemptAt,
      lastFlushSuccessAt: this.lastFlushSuccessAt,
      lastHeartbeatAttemptAt: this.lastHeartbeatAttemptAt,
      lastHeartbeatSuccessAt: this.lastHeartbeatSuccessAt,
      flushFailureCount: this.flushFailureCount,
      heartbeatFailureCount: this.heartbeatFailureCount,
      rejectedBatchCount: this.rejectedBatchCount,
      nextFlushRetryAt: this.nextFlushRetryAt ? new Date(this.nextFlushRetryAt).toISOString() : null,
      nextHeartbeatRetryAt: this.nextHeartbeatRetryAt ? new Date(this.nextHeartbeatRetryAt).toISOString() : null
    };
  }

  async countFailedFiles() {
    return typeof this.store.countFailedFiles === 'function' ? this.store.countFailedFiles() : 0;
  }

  recordFlushFailure(message) {
    this.flushError = message;
    this.flushFailureCount += 1;
    this.nextFlushRetryAt = Date.now() + this.currentFlushRetryDelayMs;
    this.currentFlushRetryDelayMs = Math.min(this.currentFlushRetryDelayMs * 2, this.config.retryMaxDelayMs);
  }

  recordHeartbeatFailure(message) {
    this.heartbeatError = message;
    this.heartbeatFailureCount += 1;
    this.nextHeartbeatRetryAt = Date.now() + this.currentHeartbeatRetryDelayMs;
    this.currentHeartbeatRetryDelayMs = Math.min(this.currentHeartbeatRetryDelayMs * 2, this.config.retryMaxDelayMs);
  }
}
