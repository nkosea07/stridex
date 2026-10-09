package com.racetracker.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.UUID;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LiveSummaryCacheInvalidatorTest {

    private final CacheManager cacheManager = mock(CacheManager.class);
    private final Cache cache = mock(Cache.class);
    private final LiveSummaryCacheInvalidator invalidator = new LiveSummaryCacheInvalidator(cacheManager);

    @AfterEach
    void clearSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void evictsOnlyAfterTransactionCommit() {
        UUID participantId = UUID.randomUUID();
        UUID raceId = UUID.randomUUID();
        when(cacheManager.getCache("lastCheckpoint")).thenReturn(cache);
        TransactionSynchronizationManager.initSynchronization();

        invalidator.evictLastCheckpointAfterCommit(participantId, raceId);
        verify(cache, never()).evict(org.mockito.ArgumentMatchers.any());

        TransactionSynchronizationManager.getSynchronizations().forEach(sync -> sync.afterCommit());
        verify(cache).evict(participantId + "-" + raceId);
    }
}
