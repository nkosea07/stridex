import http from 'node:http';
import crypto from 'node:crypto';
import { GatewayValidationError } from './payload.mjs';

const MAX_BODY_BYTES = 5 * 1024 * 1024;

function sendJson(response, statusCode, body) {
  response.writeHead(statusCode, { 'Content-Type': 'application/json' });
  response.end(JSON.stringify(body));
}

async function readJson(request) {
  const chunks = [];
  let size = 0;
  for await (const chunk of request) {
    size += chunk.length;
    if (size > MAX_BODY_BYTES) {
      throw new GatewayValidationError('Request body is too large');
    }
    chunks.push(chunk);
  }
  const raw = Buffer.concat(chunks).toString('utf8');
  try {
    return raw ? JSON.parse(raw) : {};
  } catch {
    throw new GatewayValidationError('Request body must be valid JSON');
  }
}

function tokenMatches(expected, provided) {
  if (typeof provided !== 'string') {
    return false;
  }
  const left = Buffer.from(expected, 'utf8');
  const right = Buffer.from(provided, 'utf8');
  return left.length === right.length && crypto.timingSafeEqual(left, right);
}

export function createServer({ gatewayService, localToken = null, logger = console }) {
  return http.createServer(async (request, response) => {
    try {
      if (request.method === 'GET' && request.url === '/health') {
        sendJson(response, 200, await gatewayService.health());
        return;
      }

      if (localToken && !tokenMatches(localToken, request.headers['x-gateway-token'])) {
        sendJson(response, 401, { message: 'Gateway token required' });
        return;
      }

      if (request.method === 'POST' && request.url === '/reads') {
        const body = await readJson(request);
        const result = await gatewayService.ingest(body);
        sendJson(response, 202, result);
        return;
      }

      if (request.method === 'POST' && request.url === '/flush') {
        sendJson(response, 200, await gatewayService.flushOnce());
        return;
      }

      if (request.method === 'POST' && request.url === '/failed/requeue') {
        sendJson(response, 200, await gatewayService.requeueRejected());
        return;
      }

      if (request.method === 'POST' && request.url === '/heartbeat') {
        const result = await gatewayService.heartbeat();
        sendJson(response, result.ok ? 200 : 502, result);
        return;
      }

      sendJson(response, 404, { message: 'Not found' });
    } catch (error) {
      logger.error('Gateway request failed', error);
      if (error instanceof GatewayValidationError) {
        sendJson(response, 400, { message: error.message });
        return;
      }
      sendJson(response, 500, { message: 'Gateway request failed' });
    }
  });
}
