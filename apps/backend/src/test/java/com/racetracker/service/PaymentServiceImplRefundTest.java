package com.racetracker.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.racetracker.config.RaceTrackerProperties;
import com.racetracker.dto.participant.PaymentDto;
import com.racetracker.exception.ConflictException;
import com.racetracker.exception.ServiceUnavailableException;
import com.racetracker.model.Participant;
import com.racetracker.model.Payment;
import com.racetracker.model.PaymentRefund;
import com.racetracker.model.Race;
import com.racetracker.model.User;
import com.racetracker.repository.ParticipantRepository;
import com.racetracker.repository.PaymentRefundRepository;
import com.racetracker.repository.PaymentRepository;
import com.racetracker.repository.UserRepository;
import com.racetracker.service.impl.PaymentServiceImpl;
import com.racetracker.service.impl.PesepayClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentServiceImplRefundTest {

    @Mock PaymentRepository paymentRepository;
    @Mock ParticipantRepository participantRepository;
    @Mock PesepayClient pesepayClient;
    @Mock PromoCodeService promoCodeService;
    @Mock NotificationService notificationService;
    @Mock PaymentRefundRepository paymentRefundRepository;
    @Mock UserRepository userRepository;
    @Mock PlatformTransactionManager transactionManager;

    private PaymentServiceImpl service;
    private RaceTrackerProperties properties;
    private final UUID raceId = UUID.randomUUID();
    private final UUID participantId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        properties = new RaceTrackerProperties();
        org.mockito.Mockito.lenient().when(transactionManager.getTransaction(any()))
            .thenAnswer(inv -> new SimpleTransactionStatus());
        service = new PaymentServiceImpl(paymentRepository, participantRepository, pesepayClient,
            properties, new ObjectMapper(), promoCodeService, notificationService,
            paymentRefundRepository, userRepository, transactionManager);
    }

    private Participant participant() {
        Race race = new Race();
        race.setId(raceId);
        race.setName("Demo Marathon");
        Participant participant = new Participant();
        participant.setId(participantId);
        participant.setRace(race);
        participant.setPaymentStatus(Participant.PaymentStatus.PAID);
        return participant;
    }

    private Payment payment(Payment.PaymentStatus status) {
        Payment payment = new Payment();
        payment.setId(UUID.randomUUID());
        payment.setParticipant(participant());
        payment.setProvider("PESEPAY");
        payment.setProviderRef("PROV-1");
        payment.setAmount(BigDecimal.valueOf(100));
        payment.setDiscountAmount(BigDecimal.ZERO);
        payment.setCurrency("USD");
        payment.setStatus(status);
        return payment;
    }

    @Test
    void automatedRefundIsDisabledWithoutCallingProvider() {
        assertThatThrownBy(() -> service.refund(raceId, participantId, null, "reason", "admin@example.com"))
            .isInstanceOf(ServiceUnavailableException.class)
            .extracting("code").isEqualTo("REFUND_AUTOMATION_UNAVAILABLE");
        verifyNoInteractions(pesepayClient);
    }

    @Test
    void stageRefundUsesFullServerCalculatedPayableAmount() {
        Participant participant = participant();
        Payment payment = payment(Payment.PaymentStatus.COMPLETED);
        payment.setDiscountAmount(BigDecimal.valueOf(20));
        when(participantRepository.findByRaceIdAndId(raceId, participantId)).thenReturn(Optional.of(participant));
        when(paymentRepository.findFirstByParticipantIdOrderByCreatedAtDesc(participantId)).thenReturn(Optional.of(payment));
        when(paymentRepository.findByIdForUpdate(payment.getId())).thenReturn(Optional.of(payment));

        PaymentDto dto = service.stageExternalRefund(raceId, participantId, "Duplicate entry", "admin@example.com");

        assertThat(dto.getStatus()).isEqualTo("COMPLETED");

        ArgumentCaptor<PaymentRefund> captor = ArgumentCaptor.forClass(PaymentRefund.class);
        verify(paymentRefundRepository).save(captor.capture());
        PaymentRefund savedRefund = captor.getValue();
        assertThat(savedRefund.getAmount()).isEqualByComparingTo(BigDecimal.valueOf(80));
        assertThat(savedRefund.getStatus()).isEqualTo(PaymentRefund.RefundStatus.PENDING);
        verifyNoInteractions(pesepayClient);
    }

    @Test
    void repeatedStageRejectsInsteadOfSuggestingAnotherProviderRefund() {
        Payment payment = payment(Payment.PaymentStatus.COMPLETED);
        when(participantRepository.findByRaceIdAndId(raceId, participantId)).thenReturn(Optional.of(participant()));
        when(paymentRepository.findFirstByParticipantIdOrderByCreatedAtDesc(participantId)).thenReturn(Optional.of(payment));
        when(paymentRepository.findByIdForUpdate(payment.getId())).thenReturn(Optional.of(payment));
        when(paymentRefundRepository.findFirstByPaymentIdAndStatus(payment.getId(), PaymentRefund.RefundStatus.REFUNDED))
            .thenReturn(Optional.empty());
        when(paymentRefundRepository.findFirstByPaymentIdAndStatus(payment.getId(), PaymentRefund.RefundStatus.PENDING))
            .thenReturn(Optional.of(PaymentRefund.builder().payment(payment).build()));

        assertThatThrownBy(() -> service.stageExternalRefund(raceId, participantId, "retry", "admin@example.com"))
            .isInstanceOf(ConflictException.class)
            .extracting("code").isEqualTo("REFUND_ATTEMPT_ALREADY_PENDING");
        verify(paymentRefundRepository, never()).save(any());
    }

    @Test
    void reconciliationMarksPaymentRefundedAndRepeatIsIdempotent() {
        Participant participant = participant();
        Payment payment = payment(Payment.PaymentStatus.COMPLETED);
        PaymentRefund attempt = PaymentRefund.builder().payment(payment).amount(BigDecimal.valueOf(100))
            .status(PaymentRefund.RefundStatus.PENDING).build();
        when(participantRepository.findByRaceIdAndId(raceId, participantId)).thenReturn(Optional.of(participant));
        when(paymentRepository.findFirstByParticipantIdOrderByCreatedAtDesc(participantId)).thenReturn(Optional.of(payment));
        when(paymentRepository.findByIdForUpdate(payment.getId())).thenReturn(Optional.of(payment));
        when(paymentRefundRepository.findFirstByPaymentIdAndStatus(payment.getId(), PaymentRefund.RefundStatus.PENDING))
            .thenAnswer(inv -> attempt.getStatus() == PaymentRefund.RefundStatus.PENDING
                ? Optional.of(attempt) : Optional.empty());
        when(paymentRefundRepository.findByProviderRef("REF-1")).thenAnswer(inv ->
            attempt.getProviderRef() == null ? Optional.empty() : Optional.of(attempt));

        PaymentDto result = service.reconcileExternalRefund(raceId, participantId, "REF-1", "Verified externally",
            true, "admin@example.com");
        PaymentDto repeated = service.reconcileExternalRefund(raceId, participantId, "REF-1", "Verified externally",
            true, "admin@example.com");

        assertThat(result.getStatus()).isEqualTo("REFUNDED");
        assertThat(repeated.getStatus()).isEqualTo("REFUNDED");
        assertThat(participant.getPaymentStatus()).isEqualTo(Participant.PaymentStatus.REFUNDED);
        assertThat(attempt.getStatus()).isEqualTo(PaymentRefund.RefundStatus.REFUNDED);
        verify(paymentRefundRepository).saveAndFlush(attempt);
    }

    @Test
    void reconciliationRejectsReferenceReuseAcrossPayments() {
        Payment payment = payment(Payment.PaymentStatus.COMPLETED);
        Payment other = payment(Payment.PaymentStatus.REFUNDED);
        PaymentRefund previous = PaymentRefund.builder().payment(other).providerRef("REF-1")
            .status(PaymentRefund.RefundStatus.REFUNDED).build();
        when(participantRepository.findByRaceIdAndId(raceId, participantId)).thenReturn(Optional.of(participant()));
        when(paymentRepository.findFirstByParticipantIdOrderByCreatedAtDesc(participantId)).thenReturn(Optional.of(payment));
        when(paymentRepository.findByIdForUpdate(payment.getId())).thenReturn(Optional.of(payment));
        when(paymentRefundRepository.findByProviderRef("REF-1")).thenReturn(Optional.of(previous));

        assertThatThrownBy(() -> service.reconcileExternalRefund(raceId, participantId, "REF-1", "case", true,
            "admin@example.com")).isInstanceOf(ConflictException.class);
    }

    @Test
    void verifiedNotRefundedOutcomeLeavesPaymentCompletedAndAllowsNewAttempt() {
        Participant participant = participant();
        Payment payment = payment(Payment.PaymentStatus.COMPLETED);
        PaymentRefund attempt = PaymentRefund.builder().payment(payment).amount(BigDecimal.valueOf(100))
            .status(PaymentRefund.RefundStatus.PENDING).build();
        when(participantRepository.findByRaceIdAndId(raceId, participantId)).thenReturn(Optional.of(participant));
        when(paymentRepository.findFirstByParticipantIdOrderByCreatedAtDesc(participantId)).thenReturn(Optional.of(payment));
        when(paymentRepository.findByIdForUpdate(payment.getId())).thenReturn(Optional.of(payment));
        when(paymentRefundRepository.findFirstByPaymentIdAndStatus(payment.getId(), PaymentRefund.RefundStatus.PENDING))
            .thenAnswer(inv -> attempt.getStatus() == PaymentRefund.RefundStatus.PENDING
                ? Optional.of(attempt) : Optional.empty());

        service.reconcileExternalRefund(raceId, participantId, "CASE-1", "Provider confirmed no refund",
            false, "admin@example.com");

        assertThat(attempt.getStatus()).isEqualTo(PaymentRefund.RefundStatus.NOT_REFUNDED);
        assertThat(payment.getStatus()).isEqualTo(Payment.PaymentStatus.COMPLETED);
        assertThat(participant.getPaymentStatus()).isEqualTo(Participant.PaymentStatus.PAID);
        service.stageExternalRefund(raceId, participantId, "Retry with provider", "admin@example.com");
        verify(paymentRefundRepository).saveAndFlush(attempt);
        verify(paymentRefundRepository).save(any(PaymentRefund.class));
    }

    @Test
    void conflictingRepeatedOutcomeIsRejected() {
        Payment payment = payment(Payment.PaymentStatus.REFUNDED);
        PaymentRefund prior = PaymentRefund.builder().payment(payment).providerRef("REF-1")
            .reason("case").status(PaymentRefund.RefundStatus.REFUNDED).build();
        when(participantRepository.findByRaceIdAndId(raceId, participantId)).thenReturn(Optional.of(participant()));
        when(paymentRepository.findFirstByParticipantIdOrderByCreatedAtDesc(participantId)).thenReturn(Optional.of(payment));
        when(paymentRepository.findByIdForUpdate(payment.getId())).thenReturn(Optional.of(payment));
        when(paymentRefundRepository.findByProviderRef("REF-1")).thenReturn(Optional.of(prior));

        assertThatThrownBy(() -> service.reconcileExternalRefund(raceId, participantId, "REF-1", "case", false,
            "admin@example.com")).isInstanceOf(ConflictException.class);
    }

    @Test
    void oldReferenceCannotAcknowledgeANewerPendingAttempt() {
        Payment payment = payment(Payment.PaymentStatus.COMPLETED);
        PaymentRefund old = PaymentRefund.builder().payment(payment).providerRef("CASE-1")
            .reason("No refund").status(PaymentRefund.RefundStatus.NOT_REFUNDED).build();
        PaymentRefund pending = PaymentRefund.builder().payment(payment)
            .status(PaymentRefund.RefundStatus.PENDING).build();
        when(participantRepository.findByRaceIdAndId(raceId, participantId)).thenReturn(Optional.of(participant()));
        when(paymentRepository.findFirstByParticipantIdOrderByCreatedAtDesc(participantId)).thenReturn(Optional.of(payment));
        when(paymentRepository.findByIdForUpdate(payment.getId())).thenReturn(Optional.of(payment));
        when(paymentRefundRepository.findFirstByPaymentIdAndStatus(payment.getId(), PaymentRefund.RefundStatus.PENDING))
            .thenReturn(Optional.of(pending));
        when(paymentRefundRepository.findByProviderRef("CASE-1")).thenReturn(Optional.of(old));

        assertThatThrownBy(() -> service.reconcileExternalRefund(raceId, participantId, "CASE-1", "No refund",
            false, "admin@example.com")).isInstanceOf(ConflictException.class)
            .extracting("code").isEqualTo("REFUND_REFERENCE_CONFLICT");
    }

    @Test
    void callbackCannotRestoreRefundedPaymentToPaid() {
        Payment payment = payment(Payment.PaymentStatus.REFUNDED);
        properties.getPayments().setIntegrationKey("secret");
        when(paymentRepository.findByProviderAndProviderRef("PESEPAY", "PROV-1"))
            .thenReturn(Optional.of(payment));

        service.handlePesepayCallback("secret", "{\"referenceNumber\":\"PROV-1\"}");

        assertThat(payment.getStatus()).isEqualTo(Payment.PaymentStatus.REFUNDED);
        verifyNoInteractions(pesepayClient);
    }

    @Test
    void reversedCallbackKeepsParticipantAndPaymentConsistent() {
        Payment payment = payment(Payment.PaymentStatus.COMPLETED);
        properties.getPayments().setIntegrationKey("secret");
        when(paymentRepository.findByProviderAndProviderRef("PESEPAY", "PROV-1"))
            .thenReturn(Optional.of(payment));
        when(paymentRepository.findByIdForUpdate(payment.getId())).thenReturn(Optional.of(payment));
        when(pesepayClient.checkStatus("PROV-1")).thenReturn("REVERSED");

        service.handlePesepayCallback("secret", "{\"referenceNumber\":\"PROV-1\"}");

        assertThat(payment.getStatus()).isEqualTo(Payment.PaymentStatus.REFUNDED);
        assertThat(payment.getParticipant().getPaymentStatus()).isEqualTo(Participant.PaymentStatus.REFUNDED);
        org.mockito.InOrder order = org.mockito.Mockito.inOrder(pesepayClient, paymentRepository);
        order.verify(pesepayClient).checkStatus("PROV-1");
        order.verify(paymentRepository).findByIdForUpdate(payment.getId());
    }

    @Test
    void lateFailedCallbackCannotDowngradeCompletedPayment() {
        Payment payment = payment(Payment.PaymentStatus.COMPLETED);
        properties.getPayments().setIntegrationKey("secret");
        when(paymentRepository.findByProviderAndProviderRef("PESEPAY", "PROV-1"))
            .thenReturn(Optional.of(payment));
        when(paymentRepository.findByIdForUpdate(payment.getId())).thenReturn(Optional.of(payment));
        when(pesepayClient.checkStatus("PROV-1")).thenReturn("FAILED");

        service.handlePesepayCallback("secret", "{\"referenceNumber\":\"PROV-1\"}");

        assertThat(payment.getStatus()).isEqualTo(Payment.PaymentStatus.COMPLETED);
        assertThat(payment.getParticipant().getPaymentStatus()).isEqualTo(Participant.PaymentStatus.PAID);
    }

    @Test
    void refundCompletedDuringProviderCheckWinsOverLateCallback() {
        Payment observed = payment(Payment.PaymentStatus.COMPLETED);
        Payment locked = payment(Payment.PaymentStatus.REFUNDED);
        locked.setId(observed.getId());
        properties.getPayments().setIntegrationKey("secret");
        when(paymentRepository.findByProviderAndProviderRef("PESEPAY", "PROV-1"))
            .thenReturn(Optional.of(observed));
        when(pesepayClient.checkStatus("PROV-1")).thenReturn("SUCCESS");
        when(paymentRepository.findByIdForUpdate(observed.getId())).thenReturn(Optional.of(locked));

        service.handlePesepayCallback("secret", "{\"referenceNumber\":\"PROV-1\"}");

        assertThat(locked.getStatus()).isEqualTo(Payment.PaymentStatus.REFUNDED);
        verify(paymentRepository, never()).save(any(Payment.class));
    }

    @Test
    void reversedCallbackDoesNotBlockPendingRefundConfirmation() {
        Payment payment = payment(Payment.PaymentStatus.REFUNDED);
        PaymentRefund pending = PaymentRefund.builder().payment(payment).amount(BigDecimal.valueOf(100))
            .status(PaymentRefund.RefundStatus.PENDING).build();
        when(participantRepository.findByRaceIdAndId(raceId, participantId))
            .thenReturn(Optional.of(payment.getParticipant()));
        when(paymentRepository.findFirstByParticipantIdOrderByCreatedAtDesc(participantId))
            .thenReturn(Optional.of(payment));
        when(paymentRepository.findByIdForUpdate(payment.getId())).thenReturn(Optional.of(payment));
        when(paymentRefundRepository.findFirstByPaymentIdAndStatus(payment.getId(), PaymentRefund.RefundStatus.PENDING))
            .thenReturn(Optional.of(pending));

        PaymentDto result = service.reconcileExternalRefund(raceId, participantId, "REV-1", "Provider reversal",
            true, "admin@example.com");

        assertThat(result.getStatus()).isEqualTo("REFUNDED");
        assertThat(pending.getStatus()).isEqualTo(PaymentRefund.RefundStatus.REFUNDED);
    }

    @Test
    void stageRejectsWhenNoCompletedPayment() {
        Payment payment = payment(Payment.PaymentStatus.PENDING);
        when(participantRepository.findByRaceIdAndId(raceId, participantId)).thenReturn(Optional.of(participant()));
        when(paymentRepository.findFirstByParticipantIdOrderByCreatedAtDesc(participantId)).thenReturn(Optional.of(payment));
        when(paymentRepository.findByIdForUpdate(payment.getId())).thenReturn(Optional.of(payment));

        assertThatThrownBy(() -> service.stageExternalRefund(raceId, participantId, "case", "admin@example.com"))
            .isInstanceOf(ConflictException.class);
    }

    @Test
    void athleteCanStartCheckoutOnlyForOwnParticipant() {
        Participant participant = participant();
        User athlete = new User();
        athlete.setEmail("athlete@example.com");
        participant.setUser(athlete);
        participant.getRace().setRegistrationFee(BigDecimal.valueOf(100));
        participant.getRace().setCurrency("USD");

        when(participantRepository.findByRaceIdAndId(raceId, participantId)).thenReturn(Optional.of(participant));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(pesepayClient.initiate(any(Double.class), eq("USD"), any(), any(), any(), any()))
            .thenReturn(new PesepayClient.InitiateResult("PROV-1", "https://pay.example/checkout", null));
        RaceTrackerProperties.Payments payments = new RaceTrackerProperties.Payments();
        payments.setResultUrl("https://app.example/payment/result");
        payments.setReturnUrl("https://app.example/payment/return");
        service = new PaymentServiceImpl(paymentRepository, participantRepository, pesepayClient,
            propertiesWithPayments(payments), new ObjectMapper(), promoCodeService, notificationService,
            paymentRefundRepository, userRepository, transactionManager);

        PaymentDto result = service.startCheckout(raceId, participantId, null, "athlete@example.com", true);

        assertThat(result.getCheckoutUrl()).isEqualTo("https://pay.example/checkout");
        verify(pesepayClient).initiate(any(Double.class), eq("USD"), any(), any(), any(), any());
    }

    @Test
    void athleteCannotStartCheckoutForAnotherUsersParticipant() {
        Participant participant = participant();
        User owner = new User();
        owner.setEmail("owner@example.com");
        participant.setUser(owner);
        when(participantRepository.findByRaceIdAndId(raceId, participantId)).thenReturn(Optional.of(participant));

        assertThatThrownBy(() -> service.startCheckout(raceId, participantId, null, "other@example.com", true))
            .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);

        verify(paymentRepository, never()).save(any(Payment.class));
        verifyNoInteractions(pesepayClient);
    }

    private RaceTrackerProperties propertiesWithPayments(RaceTrackerProperties.Payments payments) {
        RaceTrackerProperties configured = new RaceTrackerProperties();
        configured.setPayments(payments);
        return configured;
    }
}
