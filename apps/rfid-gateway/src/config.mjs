import path from 'node:path';

const MAX_BACKEND_BATCH_SIZE = 200;

function required(name) {
  const value = process.env[name]?.trim();
  if (!value) {
    throw new Error(`${name} is required`);
  }
  return value;
}

function optionalInt(name, fallback) {
  const raw = process.env[name]?.trim();
  if (!raw) {
    return fallback;
  }
  const parsed = Number.parseInt(raw, 10);
  if (!Number.isFinite(parsed) || parsed < 0) {
    throw new Error(`${name} must be a non-negative integer`);
  }
  return parsed;
}

function optionalBoundedInt(name, fallback, min, max) {
  const value = optionalInt(name, fallback);
  if (value < min || value > max) {
    throw new Error(`${name} must be between ${min} and ${max}`);
  }
  return value;
}

function optionalString(name, fallback = '') {
  return process.env[name]?.trim() || fallback;
}

export function loadConfig() {
  const port = optionalInt('RFID_GATEWAY_PORT', 8090);
  const backendBaseUrl = required('RFID_BACKEND_BASE_URL').replace(/\/+$/, '');
  const backendReaderKey = required('RFID_BACKEND_READER_KEY');
  const spoolDir = path.resolve(optionalString('RFID_GATEWAY_SPOOL_DIR', './data/rfid-gateway'));
  const mode = optionalString('RFID_GATEWAY_MODE', 'simulation').toLowerCase();
  if (!['simulation', 'hardware'].includes(mode)) {
    throw new Error('RFID_GATEWAY_MODE must be simulation or hardware');
  }

  const hardwareProfile = optionalString('RFID_GATEWAY_HARDWARE_PROFILE', 'UNSPECIFIED');
  const hardwareModel = optionalString('RFID_GATEWAY_HARDWARE_MODEL', 'generic-edge');
  const readerClass = optionalString('RFID_GATEWAY_READER_CLASS', 'UNKNOWN');
  const readerSerial = optionalString('RFID_GATEWAY_READER_SERIAL');
  if (mode === 'hardware' && (!readerSerial || hardwareProfile === 'UNSPECIFIED' || hardwareModel === 'generic-edge' || readerClass === 'UNKNOWN')) {
    throw new Error('Hardware RFID mode requires reader serial, hardware profile, hardware model, and reader class');
  }

  return {
    mode,
    eventOrigin: mode === 'simulation' ? 'SIMULATED' : 'HARDWARE',
    port,
    backendBaseUrl,
    backendReaderKey,
    spoolDir,
    gatewayName: optionalString('RFID_GATEWAY_NAME', 'edge-gateway'),
    firmwareVersion: optionalString('RFID_GATEWAY_FIRMWARE_VERSION', 'dev'),
    hardwareModel,
    hardwareProfile,
    readerClass,
    readerSerial,
    batchSize: optionalBoundedInt('RFID_GATEWAY_BATCH_SIZE', MAX_BACKEND_BATCH_SIZE, 1, MAX_BACKEND_BATCH_SIZE),
    heartbeatIntervalMs: optionalBoundedInt('RFID_GATEWAY_HEARTBEAT_INTERVAL_MS', 15000, 1000, 3600000),
    flushIntervalMs: optionalBoundedInt('RFID_GATEWAY_FLUSH_INTERVAL_MS', 1000, 100, 3600000),
    requestTimeoutMs: optionalBoundedInt('RFID_GATEWAY_REQUEST_TIMEOUT_MS', 10000, 1000, 600000),
    retryInitialDelayMs: optionalBoundedInt('RFID_GATEWAY_RETRY_INITIAL_DELAY_MS', 1000, 100, 3600000),
    retryMaxDelayMs: optionalBoundedInt('RFID_GATEWAY_RETRY_MAX_DELAY_MS', 10000, 100, 3600000)
  };
}
