package com.racetracker.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.racetracker.dto.notification.NotificationDto;
import com.racetracker.model.EmergencyAlert;
import com.racetracker.model.EmergencyNotificationJob;
import com.racetracker.model.Notification;
import com.racetracker.model.NotificationDelivery;
import com.racetracker.model.Participant;
import com.racetracker.model.Race;
import com.racetracker.model.User;
import com.racetracker.notification.NotificationMessage;
import com.racetracker.repository.EmergencyAlertRepository;
import com.racetracker.repository.EmergencyNotificationJobRepository;
import com.racetracker.repository.NotificationRepository;
import com.racetracker.repository.UserRepository;
import com.racetracker.service.impl.EmergencyNotificationProcessor;
import com.racetracker.websocket.TrackingLivePublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EmergencyNotificationProcessorTest {

    @Mock EmergencyNotificationJobRepository jobRepository;
    @Mock EmergencyAlertRepository alertRepository;
    @Mock NotificationRepository notificationRepository;
    @Mock UserRepository userRepository;
    @Mock TrackingLivePublisher trackingLivePublisher;
    @Mock PlatformTransactionManager transactionManager;

    private TestChannel pushChannel;
    private EmergencyNotificationProcessor processor;
    private final UUID alertId = UUID.randomUUID();
    private final UUID raceId = UUID.randomUUID();
    private final UUID recipientId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        lenient().when(transactionManager.getTransaction(any())).thenAnswer(inv -> new SimpleTransactionStatus());
        pushChannel = new TestChannel();
        processor = new EmergencyNotificationProcessor(jobRepository, alertRepository, notificationRepository,
            userRepository, List.of(pushChannel), trackingLivePublisher, new ObjectMapper(), transactionManager);
    }

    @Test
    void remoteChannelSuccessMarksJobAndAlertDispatched() {
        EmergencyNotificationJob job = job(NotificationDelivery.Channel.PUSH, 0);
        when(jobRepository.findClaimableIds(any(), any(Integer.class))).thenReturn(List.of(job.getId()));
        when(jobRepository.findByIdForUpdate(job.getId())).thenReturn(Optional.of(job));
        when(jobRepository.countByAlertIdAndStatusIn(alertId, List.of(
            EmergencyNotificationJob.Status.PENDING, EmergencyNotificationJob.Status.PROCESSING))).thenReturn(0L);
        org.mockito.Mockito.doReturn(0L).when(jobRepository)
            .countByAlertIdAndStatus(alertId, EmergencyNotificationJob.Status.EXHAUSTED);
        when(jobRepository.countByAlertIdAndStatus(alertId, EmergencyNotificationJob.Status.DISPATCHED)).thenReturn(1L);
        when(alertRepository.findById(alertId)).thenReturn(Optional.of(job.getAlert()));

        processor.processBatch();

        assertThat(job.getStatus()).isEqualTo(EmergencyNotificationJob.Status.DISPATCHED);
        assertThat(job.getAttempts()).isEqualTo(1);
        assertThat(job.getLeaseToken()).isNull();
        assertThat(job.getAlert().getDeliveryStatus()).isEqualTo(EmergencyAlert.DeliveryStatus.DISPATCHED);
        assertThat(pushChannel.sent).isEqualTo(1);
    }

    @Test
    void failedChannelRetriesAndExhaustsWithEscalation() {
        EmergencyNotificationJob retry = job(NotificationDelivery.Channel.PUSH, 0);
        when(jobRepository.findClaimableIds(any(), any(Integer.class))).thenReturn(List.of(retry.getId()));
        when(jobRepository.findByIdForUpdate(retry.getId())).thenReturn(Optional.of(retry));
        when(jobRepository.countByAlertIdAndStatusIn(alertId, List.of(
            EmergencyNotificationJob.Status.PENDING, EmergencyNotificationJob.Status.PROCESSING))).thenReturn(1L);
        pushChannel.fail = true;

        processor.processBatch();

        assertThat(retry.getStatus()).isEqualTo(EmergencyNotificationJob.Status.PENDING);
        assertThat(retry.getAttempts()).isEqualTo(1);
        assertThat(retry.getNextAttemptAt()).isAfter(java.time.OffsetDateTime.now());

        retry.setAttempts(4);
        retry.setStatus(EmergencyNotificationJob.Status.PENDING);
        retry.setNextAttemptAt(java.time.OffsetDateTime.now().minusSeconds(1));
        when(jobRepository.findClaimableIds(any(), any(Integer.class))).thenReturn(List.of(retry.getId()));
        org.mockito.Mockito.doReturn(1L).when(jobRepository)
            .countByAlertIdAndStatus(alertId, EmergencyNotificationJob.Status.EXHAUSTED);
        when(jobRepository.countByAlertIdAndStatus(alertId, EmergencyNotificationJob.Status.DISPATCHED)).thenReturn(0L);
        when(alertRepository.findById(alertId)).thenReturn(Optional.of(retry.getAlert()));

        processor.processBatch();

        assertThat(retry.getStatus()).isEqualTo(EmergencyNotificationJob.Status.EXHAUSTED);
        assertThat(retry.getAlert().getDeliveryStatus())
            .isEqualTo(EmergencyAlert.DeliveryStatus.ESCALATION_REQUIRED);
    }

    @Test
    void exhaustedExternalDeliveryEscalatesEvenAfterInAppDispatch() {
        EmergencyNotificationJob job = job(NotificationDelivery.Channel.PUSH, 4);
        when(jobRepository.findClaimableIds(any(), any(Integer.class))).thenReturn(List.of(job.getId()));
        when(jobRepository.findByIdForUpdate(job.getId())).thenReturn(Optional.of(job));
        when(jobRepository.countByAlertIdAndStatus(alertId, EmergencyNotificationJob.Status.EXHAUSTED)).thenReturn(1L);
        when(jobRepository.countByAlertIdAndStatus(alertId, EmergencyNotificationJob.Status.DISPATCHED)).thenReturn(1L);
        when(alertRepository.findById(alertId)).thenReturn(Optional.of(job.getAlert()));
        pushChannel.fail = true;

        processor.processBatch();

        assertThat(job.getStatus()).isEqualTo(EmergencyNotificationJob.Status.EXHAUSTED);
        assertThat(job.getAlert().getDeliveryStatus())
            .isEqualTo(EmergencyAlert.DeliveryStatus.ESCALATION_REQUIRED);
    }

    @Test
    void exhaustedDeliveryEscalatesWhileOtherJobsArePending() {
        EmergencyNotificationJob job = job(NotificationDelivery.Channel.PUSH, 4);
        when(jobRepository.findClaimableIds(any(), any(Integer.class))).thenReturn(List.of(job.getId()));
        when(jobRepository.findByIdForUpdate(job.getId())).thenReturn(Optional.of(job));
        lenient().when(jobRepository.countByAlertIdAndStatusIn(alertId, List.of(
            EmergencyNotificationJob.Status.PENDING, EmergencyNotificationJob.Status.PROCESSING))).thenReturn(1L);
        when(jobRepository.countByAlertIdAndStatus(alertId, EmergencyNotificationJob.Status.EXHAUSTED))
            .thenReturn(1L);
        when(jobRepository.countByAlertIdAndStatus(alertId, EmergencyNotificationJob.Status.DISPATCHED))
            .thenReturn(1L);
        when(alertRepository.findById(alertId)).thenReturn(Optional.of(job.getAlert()));
        pushChannel.fail = true;

        processor.processBatch();

        assertThat(job.getAlert().getDeliveryStatus())
            .isEqualTo(EmergencyAlert.DeliveryStatus.ESCALATION_REQUIRED);
        org.mockito.Mockito.verify(jobRepository, org.mockito.Mockito.never())
            .countByAlertIdAndStatusIn(alertId, List.of(
                EmergencyNotificationJob.Status.PENDING, EmergencyNotificationJob.Status.PROCESSING));
    }

    @Test
    void inAppDeliveryPersistsNotificationAndPublishesReceipt() {
        EmergencyNotificationJob job = job(NotificationDelivery.Channel.IN_APP, 0);
        User recipient = job.getRecipient();
        when(jobRepository.findClaimableIds(any(), any(Integer.class))).thenReturn(List.of(job.getId()));
        when(jobRepository.findByIdForUpdate(job.getId())).thenReturn(Optional.of(job));
        when(userRepository.getReferenceById(recipientId)).thenReturn(recipient);
        when(notificationRepository.save(any(Notification.class))).thenAnswer(inv -> {
            Notification notification = inv.getArgument(0);
            notification.setId(UUID.randomUUID());
            return notification;
        });
        when(jobRepository.countByAlertIdAndStatusIn(alertId, List.of(
            EmergencyNotificationJob.Status.PENDING, EmergencyNotificationJob.Status.PROCESSING))).thenReturn(0L);
        org.mockito.Mockito.doReturn(0L).when(jobRepository)
            .countByAlertIdAndStatus(alertId, EmergencyNotificationJob.Status.EXHAUSTED);
        when(jobRepository.countByAlertIdAndStatus(alertId, EmergencyNotificationJob.Status.DISPATCHED)).thenReturn(1L);
        when(alertRepository.findById(alertId)).thenReturn(Optional.of(job.getAlert()));

        processor.processBatch();

        assertThat(job.getStatus()).isEqualTo(EmergencyNotificationJob.Status.DISPATCHED);
        verify(notificationRepository).save(any(Notification.class));
        verify(trackingLivePublisher).publishUserAlert(any(), any(NotificationDto.class));
        assertThat(pushChannel.sent).isZero();
    }

    private EmergencyNotificationJob job(NotificationDelivery.Channel channel, int attempts) {
        User recipient = User.builder().id(recipientId).email("staff@example.com").phone("0700").build();
        Race race = new Race();
        race.setId(raceId);
        Participant participant = Participant.builder().id(UUID.randomUUID()).race(race)
            .firstName("Ada").lastName("Runner").bibNumber("101").build();
        EmergencyAlert alert = EmergencyAlert.builder().id(alertId).race(race).participant(participant)
            .latitude(BigDecimal.valueOf(-33.9)).longitude(BigDecimal.valueOf(18.4))
            .message("Collapsed").status(EmergencyAlert.EmergencyStatus.ACTIVE)
            .deliveryStatus(EmergencyAlert.DeliveryStatus.QUEUED).build();
        EmergencyNotificationJob job = new EmergencyNotificationJob();
        job.setId(UUID.randomUUID());
        job.setAlert(alert);
        job.setRecipient(recipient);
        job.setChannel(channel);
        job.setStatus(EmergencyNotificationJob.Status.PENDING);
        job.setAttempts(attempts);
        job.setNextAttemptAt(java.time.OffsetDateTime.now().minusSeconds(1));
        job.setCreatedAt(java.time.OffsetDateTime.now().minusMinutes(5));
        return job;
    }

    private static class TestChannel implements NotificationChannel {
        private boolean fail;
        private int sent;

        @Override
        public NotificationDelivery.Channel channel() {
            return NotificationDelivery.Channel.PUSH;
        }

        @Override
        public boolean isEnabled() {
            return true;
        }

        @Override
        public void send(NotificationMessage message) {
            if (fail) {
                throw new IllegalStateException("provider down");
            }
            sent++;
        }
    }
}
