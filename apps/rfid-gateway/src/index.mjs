import { loadConfig } from './config.mjs';
import { FileBatchStore } from './store.mjs';
import { BackendClient } from './backend-client.mjs';
import { GatewayService } from './gateway-service.mjs';
import { createServer } from './server.mjs';

const config = loadConfig();
const store = new FileBatchStore(config.spoolDir);
await store.init();

const backendClient = new BackendClient(config);
const gatewayService = new GatewayService({ config, store, backendClient });
const server = createServer({ gatewayService });

server.listen(config.port, () => {
  console.log(
    `RFID gateway listening on ${config.port} for ${config.gatewayName} ` +
    `(${config.hardwareProfile}/${config.readerClass}) -> ${config.backendBaseUrl}`
  );
});

const flushTimer = setInterval(() => {
  gatewayService.flushOnce().catch((error) => {
    console.error('Scheduled flush failed', error);
  });
}, config.flushIntervalMs);

const heartbeatTimer = setInterval(() => {
  gatewayService.heartbeat().catch((error) => {
    console.error('Scheduled heartbeat failed', error);
  });
}, config.heartbeatIntervalMs);

async function shutdown(signal) {
  clearInterval(flushTimer);
  clearInterval(heartbeatTimer);
  console.log(`Received ${signal}, shutting down RFID gateway`);
  server.close(() => process.exit(0));
}

process.on('SIGINT', () => shutdown('SIGINT'));
process.on('SIGTERM', () => shutdown('SIGTERM'));
