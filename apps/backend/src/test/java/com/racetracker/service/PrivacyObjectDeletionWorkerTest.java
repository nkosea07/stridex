package com.racetracker.service;

import com.racetracker.exception.ResourceNotFoundException;
import com.racetracker.service.impl.AccountPrivacyServiceImpl;
import com.racetracker.service.impl.MediaStorageScope;
import com.racetracker.service.impl.PrivacyObjectDeletionWorker;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PrivacyObjectDeletionWorkerTest {

    @Mock AccountPrivacyServiceImpl privacyService;
    @Mock MediaStorage mediaStorage;
    @Mock AuthEmailService authEmailService;
    @Mock MediaStorageScope mediaStorageScope;

    @Test
    void deletesObjectsBeforeFinalizingAndNotifying() {
        UUID id = UUID.randomUUID();
        UUID token = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        var job = new AccountPrivacyServiceImpl.ClaimedJob(id, "race/original.jpg", "scope-a", token);
        var notification = new AccountPrivacyServiceImpl.NotificationClaim(userId, "runner@example.com", "Ada", token);
        when(privacyService.claimJobs(50)).thenReturn(List.of(job));
        when(privacyService.finalizeReady(50)).thenReturn(1);
        when(privacyService.claimNotifications(50)).thenReturn(List.of(notification));
        when(mediaStorageScope.id()).thenReturn("scope-a");

        new PrivacyObjectDeletionWorker(privacyService, mediaStorage, authEmailService, mediaStorageScope).processBatch();

        InOrder order = inOrder(mediaStorage, privacyService, authEmailService);
        order.verify(mediaStorage).delete("race/original.jpg");
        order.verify(privacyService).completeJob(id, token);
        order.verify(privacyService).finalizeReady(50);
        order.verify(authEmailService).sendAccountDeletionCompletedConfirmed("runner@example.com", "Ada");
        order.verify(privacyService).completeNotification(userId, token);
    }

    @Test
    void storageFailureSchedulesRetryAndDoesNotCompleteJob() {
        UUID id = UUID.randomUUID();
        UUID token = UUID.randomUUID();
        when(privacyService.claimJobs(50)).thenReturn(List.of(
            new AccountPrivacyServiceImpl.ClaimedJob(id, "race/original.jpg", "scope-a", token)));
        when(mediaStorageScope.id()).thenReturn("scope-a");
        doThrow(new IllegalStateException("storage unavailable")).when(mediaStorage).delete("race/original.jpg");

        new PrivacyObjectDeletionWorker(privacyService, mediaStorage, authEmailService, mediaStorageScope).processBatch();

        verify(privacyService).retryJob(id, token);
        verify(privacyService, never()).completeJob(id, token);
        verify(authEmailService, never()).sendAccountDeletionCompletedConfirmed(org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any());
    }

    @Test
    void missingObjectCountsAsAlreadyDeleted() {
        UUID id = UUID.randomUUID();
        UUID token = UUID.randomUUID();
        when(privacyService.claimJobs(50)).thenReturn(List.of(
            new AccountPrivacyServiceImpl.ClaimedJob(id, "race/missing.jpg", "scope-a", token)));
        when(mediaStorageScope.id()).thenReturn("scope-a");
        doThrow(new ResourceNotFoundException("Media file not found"))
            .when(mediaStorage).delete("race/missing.jpg");

        new PrivacyObjectDeletionWorker(privacyService, mediaStorage, authEmailService, mediaStorageScope).processBatch();

        verify(privacyService).completeJob(id, token);
    }

    @Test
    void changedStorageLocationCannotCompleteDeletion() {
        UUID id = UUID.randomUUID();
        UUID token = UUID.randomUUID();
        when(privacyService.claimJobs(50)).thenReturn(List.of(
            new AccountPrivacyServiceImpl.ClaimedJob(id, "race/original.jpg", "old-bucket", token)));
        when(mediaStorageScope.id()).thenReturn("new-bucket");

        new PrivacyObjectDeletionWorker(privacyService, mediaStorage, authEmailService, mediaStorageScope).processBatch();

        verify(privacyService).retryJob(id, token);
        verify(privacyService, never()).completeJob(id, token);
        org.mockito.Mockito.verifyNoInteractions(mediaStorage);
    }

    @Test
    void failedNoticeIsRetriedWithoutClearingRecipient() {
        UUID userId = UUID.randomUUID();
        UUID token = UUID.randomUUID();
        when(privacyService.claimNotifications(50)).thenReturn(List.of(
            new AccountPrivacyServiceImpl.NotificationClaim(userId, "runner@example.com", "Ada", token)));
        doThrow(new IllegalStateException("mail disabled")).when(authEmailService)
            .sendAccountDeletionCompletedConfirmed("runner@example.com", "Ada");

        new PrivacyObjectDeletionWorker(privacyService, mediaStorage, authEmailService, mediaStorageScope).processBatch();

        verify(privacyService).retryNotification(userId, token);
        verify(privacyService, never()).completeNotification(userId, token);
    }
}
