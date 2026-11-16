import fs from 'node:fs/promises';
import path from 'node:path';
import crypto from 'node:crypto';

function batchFileName() {
  return `${Date.now()}-${crypto.randomUUID()}.json`;
}

async function syncDirectory(dir) {
  let handle;
  try {
    handle = await fs.open(dir, 'r');
    await handle.sync();
  } catch {
    // Directory fsync is not supported on every platform; the file itself is already synced.
  } finally {
    await handle?.close();
  }
}

function isBatch(payload) {
  return payload && typeof payload === 'object' && Array.isArray(payload.reads);
}

export class FileBatchStore {
  constructor(rootDir) {
    this.rootDir = rootDir;
    this.pendingDir = path.join(rootDir, 'pending');
    this.processingDir = path.join(rootDir, 'processing');
    this.failedDir = path.join(rootDir, 'failed');
    this.corruptDir = path.join(rootDir, 'corrupt');
    this.rawDir = path.join(rootDir, 'raw');
  }

  async init() {
    for (const dir of [this.pendingDir, this.processingDir, this.failedDir, this.corruptDir, this.rawDir]) {
      await fs.mkdir(dir, { recursive: true });
    }
    await this.removeTemporaryFiles();
    await this.recoverProcessing();
  }

  // Write to a temporary file, fsync it, then rename: a power cut leaves either the complete
  // batch or nothing, never a truncated file that blocks the queue.
  async enqueue(batch) {
    const filename = batchFileName();
    const temporary = path.join(this.pendingDir, `.${filename}.tmp`);
    const handle = await fs.open(temporary, 'w');
    try {
      await handle.writeFile(JSON.stringify(batch), 'utf8');
      await handle.sync();
    } finally {
      await handle.close();
    }
    await fs.rename(temporary, path.join(this.pendingDir, filename));
    await syncDirectory(this.pendingDir);
    return filename;
  }

  // Append-only record of every read the gateway accepted, kept on the edge device for timing
  // disputes and recovery. It is never pruned by the gateway.
  async appendRawLog(reads) {
    if (!reads.length) {
      return;
    }
    const day = new Date().toISOString().slice(0, 10);
    const handle = await fs.open(path.join(this.rawDir, `reads-${day}.ndjson`), 'a');
    try {
      const receivedAt = new Date().toISOString();
      await handle.writeFile(reads.map((read) => JSON.stringify({ ...read, receivedAt })).join('\n') + '\n', 'utf8');
      await handle.sync();
    } finally {
      await handle.close();
    }
  }

  async listPending() {
    return this.listJson(this.pendingDir);
  }

  async listJson(dir) {
    const names = await fs.readdir(dir);
    return names.filter((name) => name.endsWith('.json') && !name.startsWith('.')).sort();
  }

  async countPendingFiles() {
    return (await this.listPending()).length;
  }

  async countPendingReads() {
    const names = await this.listPending();
    let total = 0;
    for (const name of names) {
      const payload = await this.tryRead(this.pendingDir, name);
      total += isBatch(payload) ? payload.reads.length : 0;
    }
    return total;
  }

  async leaseNext() {
    for (const filename of await this.listPending()) {
      const from = path.join(this.pendingDir, filename);
      const to = path.join(this.processingDir, filename);
      try {
        await fs.rename(from, to);
      } catch (error) {
        if (error?.code === 'ENOENT') {
          continue;
        }
        throw error;
      }
      const payload = await this.tryRead(this.processingDir, filename);
      if (!isBatch(payload)) {
        await fs.rename(to, path.join(this.corruptDir, filename));
        continue;
      }
      return { filename, payload };
    }
    return null;
  }

  async ack(filename) {
    await fs.rm(path.join(this.processingDir, filename), { force: true });
  }

  async requeue(filename) {
    await fs.rename(path.join(this.processingDir, filename), path.join(this.pendingDir, filename));
  }

  async reject(filename) {
    await fs.rename(path.join(this.processingDir, filename), path.join(this.failedDir, filename));
  }

  async listFailed() {
    return this.listJson(this.failedDir);
  }

  async countFailedFiles() {
    return (await this.listFailed()).length;
  }

  async countCorruptFiles() {
    return (await this.listJson(this.corruptDir)).length;
  }

  async requeueFailed() {
    const names = await this.listFailed();
    for (const name of names) {
      await fs.rename(path.join(this.failedDir, name), path.join(this.pendingDir, name));
    }
    return names.length;
  }

  async readFromDir(dir, filename) {
    const raw = await fs.readFile(path.join(dir, filename), 'utf8');
    return JSON.parse(raw);
  }

  async tryRead(dir, filename) {
    try {
      return await this.readFromDir(dir, filename);
    } catch (error) {
      if (error instanceof SyntaxError) {
        return null;
      }
      if (error?.code === 'ENOENT') {
        return null;
      }
      throw error;
    }
  }

  async removeTemporaryFiles() {
    for (const name of await fs.readdir(this.pendingDir)) {
      if (name.startsWith('.') && name.endsWith('.tmp')) {
        await fs.rm(path.join(this.pendingDir, name), { force: true });
      }
    }
  }

  async recoverProcessing() {
    const names = await this.listJson(this.processingDir);
    await Promise.all(
      names.map((name) =>
        fs.rename(path.join(this.processingDir, name), path.join(this.pendingDir, name))
      )
    );
  }
}
