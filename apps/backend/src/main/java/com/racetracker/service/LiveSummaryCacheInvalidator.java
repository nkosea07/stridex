package com.racetracker.service;

import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;

@Component
public class LiveSummaryCacheInvalidator {

    private static final Logger log = LoggerFactory.getLogger(LiveSummaryCacheInvalidator.class);
    private final CacheManager cacheManager;

    public LiveSummaryCacheInvalidator(CacheManager cacheManager) {
        this.cacheManager = cacheManager;
    }

    public void evictLastCheckpointAfterCommit(UUID participantId, UUID raceId) {
        String key = participantId + "-" + raceId;
        Runnable eviction = () -> safelyInvalidate(cache -> cache.evict(key));

        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    eviction.run();
                }
            });
        } else {
            eviction.run();
        }
    }

    public void evictAllLastCheckpointsAfterCommit() {
        Runnable eviction = () -> safelyInvalidate(Cache::clear);

        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    eviction.run();
                }
            });
        } else {
            eviction.run();
        }
    }

    private void safelyInvalidate(java.util.function.Consumer<Cache> operation) {
        try {
            Cache cache = cacheManager.getCache("lastCheckpoint");
            if (cache != null) operation.accept(cache);
        } catch (RuntimeException exception) {
            log.warn("Live checkpoint cache invalidation failed; short cache TTL will bound staleness: {}",
                exception.getClass().getSimpleName());
        }
    }
}
