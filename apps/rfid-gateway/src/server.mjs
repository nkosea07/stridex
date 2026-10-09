import http from 'node:http';

function sendJson(response, statusCode, body) {
  response.writeHead(statusCode, { 'Content-Type': 'application/json' });
  response.end(JSON.stringify(body));
}

async function readJson(request) {
  const chunks = [];
  for await (const chunk of request) {
    chunks.push(chunk);
  }
  const raw = Buffer.concat(chunks).toString('utf8');
  return raw ? JSON.parse(raw) : {};
}

export function createServer({ gatewayService, logger = console }) {
  return http.createServer(async (request, response) => {
    try {
      if (request.method === 'GET' && request.url === '/health') {
        sendJson(response, 200, await gatewayService.health());
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

      if (request.method === 'POST' && request.url === '/heartbeat') {
        const result = await gatewayService.heartbeat();
        sendJson(response, result.ok ? 200 : 502, result);
        return;
      }

      sendJson(response, 404, { message: 'Not found' });
    } catch (error) {
      logger.error('Gateway request failed', error);
      sendJson(response, 400, {
        message: error instanceof Error ? error.message : 'Request failed'
      });
    }
  });
}
