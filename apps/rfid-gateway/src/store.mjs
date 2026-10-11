import fs from 'node:fs/promises';
import path from 'node:path';
import crypto from 'node:crypto';

function batchFileName() {
  return `${Date.now()}-${crypto.randomUUID()}.json`;
}

export class FileBatchStore {
  constructor(rootDir) {
    this.rootDir = rootDir;
    this.pendingDir = path.join(rootDir, 'pending');
    this.processingDir = path.join(rootDir, 'processing');
  }

  async init() {
    await fs.mkdir(this.pendingDir, { recursive: true });
    await fs.mkdir(this.processingDir, { recursive: true });
    await this.recoverProcessing();
  }

  async enqueue(batch) {
    const filename = batchFileName();
    const fullPath = path.join(this.pendingDir, filename);
    await fs.writeFile(fullPath, JSON.stringify(batch), 'utf8');
    return filename;
  }

  async listPending() {
    const names = await fs.readdir(this.pendingDir);
    return names
      .filter((name) => name.endsWith('.json'))
      .sort();
  }

  async countPendingFiles() {
    return (await this.listPending()).length;
  }

  async countPendingReads() {
    const names = await this.listPending();
    let total = 0;
    for (const name of names) {
      const payload = await this.readFromDir(this.pendingDir, name);
      total += Array.isArray(payload.reads) ? payload.reads.length : 0;
    }
    return total;
  }

  async leaseNext() {
    const names = await this.listPending();
    if (names.length === 0) {
      return null;
    }

    const filename = names[0];
    const from = path.join(this.pendingDir, filename);
    const to = path.join(this.processingDir, filename);
    await fs.rename(from, to);
    const payload = await this.readFromDir(this.processingDir, filename);
    return { filename, payload };
  }

  async ack(filename) {
    await fs.rm(path.join(this.processingDir, filename), { force: true });
  }

  async requeue(filename) {
    const from = path.join(this.processingDir, filename);
    const to = path.join(this.pendingDir, filename);
    await fs.rename(from, to);
  }

  async readFromDir(dir, filename) {
    const raw = await fs.readFile(path.join(dir, filename), 'utf8');
    return JSON.parse(raw);
  }

  async recoverProcessing() {
    const names = await fs.readdir(this.processingDir);
    await Promise.all(
      names.map((name) =>
        fs.rename(path.join(this.processingDir, name), path.join(this.pendingDir, name))
      )
    );
  }
}
