package com.racetracker.service.impl;

import com.racetracker.exception.ResourceNotFoundException;
import com.racetracker.service.AuthEmailService;
import com.racetracker.service.MediaStorage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class PrivacyObjectDeletionWorker {

    private final AccountPrivacyServiceImpl privacyService;
    private final MediaStorage mediaStorage;
    private final AuthEmailService authEmailService;
    private final MediaStorageScope mediaStorageScope;

    public PrivacyObjectDeletionWorker(AccountPrivacyServiceImpl privacyService,
                                       MediaStorage mediaStorage,
                                       AuthEmailService authEmailService,
                                       MediaStorageScope mediaStorageScope) {
        this.privacyService = privacyService;
        this.mediaStorage = mediaStorage;
        this.authEmailService = authEmailService;
        this.mediaStorageScope = mediaStorageScope;
    }

    @Scheduled(
        fixedDelayString = "${racetracker.privacy.object-cleanup-interval-millis:60000}",
        initialDelayString = "${racetracker.privacy.object-cleanup-initial-delay-millis:60000}"
    )
    public void scheduledCleanup() {
        try {
            processBatch();
        } catch (RuntimeException ex) {
            log.error("Privacy object cleanup worker failed", ex);
        }
    }

    public void processBatch() {
        for (AccountPrivacyServiceImpl.ClaimedJob job : privacyService.claimJobs(50)) {
            if (!mediaStorageScope.id().equals(job.storageScope())) {
                privacyService.retryJob(job.id(), job.leaseToken());
                log.error("Privacy object deletion blocked by storage-scope mismatch; jobId={}", job.id());
                continue;
            }
            try {
                mediaStorage.delete(job.storageKey());
            } catch (ResourceNotFoundException ignored) {
            } catch (RuntimeException ex) {
                privacyService.retryJob(job.id(), job.leaseToken());
                log.warn("Privacy object deletion will retry; jobId={}", job.id());
                continue;
            }
            privacyService.completeJob(job.id(), job.leaseToken());
        }

        int finalized = privacyService.finalizeReady(50);
        privacyService.repairLegacyAccounts(50);
        if (finalized > 0) {
            log.info("Privacy erasure finalized {} account(s)", finalized);
        }
        for (AccountPrivacyServiceImpl.NotificationClaim notification : privacyService.claimNotifications(50)) {
            try {
                authEmailService.sendAccountDeletionCompletedConfirmed(notification.email(), notification.firstName());
                privacyService.completeNotification(notification.userId(), notification.leaseToken());
            } catch (RuntimeException ex) {
                privacyService.retryNotification(notification.userId(), notification.leaseToken());
                log.warn("Account deletion completion notification will retry; userId={}", notification.userId());
            }
        }
    }
}
