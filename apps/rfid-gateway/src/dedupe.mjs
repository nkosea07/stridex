const MAX_TRACKED_TAGS = 100000;

// UHF readers report a tag dozens of times while it is in the antenna field. Timing needs the
// first read of each pass, so repeated reads of the same tag closer together than the window are
// suppressed (sliding: a runner standing in the chute stays one pass). Earlier, out-of-order reads
// are always forwarded because the backend keeps the earliest read.
export class ReadDeduplicator {
  constructor(windowMs) {
    this.windowMs = windowMs;
    this.lastReadMsByTag = new Map();
    this.suppressedCount = 0;
  }

  filter(reads) {
    if (!this.windowMs) {
      return reads;
    }
    const ordered = [...reads].sort((left, right) => Date.parse(left.readAt) - Date.parse(right.readAt));
    const accepted = [];
    for (const read of ordered) {
      const at = Date.parse(read.readAt);
      const last = this.lastReadMsByTag.get(read.rfidTag);
      if (last !== undefined && at >= last && at - last < this.windowMs) {
        this.suppressedCount += 1;
        this.lastReadMsByTag.set(read.rfidTag, at);
        continue;
      }
      accepted.push(read);
      this.lastReadMsByTag.set(read.rfidTag, last === undefined ? at : Math.max(last, at));
    }
    this.prune();
    return accepted;
  }

  prune() {
    if (this.lastReadMsByTag.size <= MAX_TRACKED_TAGS) {
      return;
    }
    let newest = 0;
    for (const value of this.lastReadMsByTag.values()) {
      newest = Math.max(newest, value);
    }
    for (const [tag, value] of this.lastReadMsByTag) {
      if (newest - value >= this.windowMs) {
        this.lastReadMsByTag.delete(tag);
      }
    }
  }
}
