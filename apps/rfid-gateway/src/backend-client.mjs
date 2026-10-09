export class BackendClient {
  constructor(config) {
    this.config = config;
  }

  async submitReads(batch) {
    return this.postJson('/api/v1/rfid/read-events', batch);
  }

  async sendHeartbeat(payload) {
    return this.postJson('/api/v1/rfid/readers/heartbeat', payload);
  }

  async postJson(route, payload) {
    const controller = new AbortController();
    const timeout = setTimeout(() => controller.abort(), this.config.requestTimeoutMs);

    try {
      const response = await fetch(`${this.config.backendBaseUrl}${route}`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          'X-RFID-Reader-Key': this.config.backendReaderKey
        },
        body: JSON.stringify(payload),
        signal: controller.signal
      });

      const text = await response.text();
      let json = null;
      if (text) {
        try {
          json = JSON.parse(text);
        } catch {
          json = { raw: text };
        }
      }

      return { ok: response.ok, status: response.status, body: json };
    } finally {
      clearTimeout(timeout);
    }
  }
}
