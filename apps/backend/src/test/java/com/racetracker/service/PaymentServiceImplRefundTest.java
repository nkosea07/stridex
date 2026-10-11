package com.racetracker.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.racetracker.config.RaceTrackerProperties;
import com.racetracker.dto.participant.PaymentDto;
import com.racetracker.exception.ApiException;
import com.racetracker.model.Participant;
import com.racetracker.model.Payment;
import com.racetracker.model.PaymentRefund;
import com.racetracker.model.Race;
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

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
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
    @Mock PaymentProvider paymentProvider;

    private PaymentServiceImpl service;
    private final UUID raceId = UUID.randomUUID();
    private final UUID participantId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new PaymentServiceImpl(paymentRepository, participantRepository, pesepayClient,
            new RaceTrackerProperties(), new ObjectMapper(), promoCodeService, notificationService,
            List.of(paymentProvider), paymentRefundRepository, userRepository);
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
    void refundCallsProviderAndMarksPaymentRefunded() {
        Participant participant = participant();
        Payment payment = payment(Payment.PaymentStatus.COMPLETED);
        when(participantRepository.findByRaceIdAndId(raceId, participantId)).thenReturn(Optional.of(participant));
        when(paymentRepository.findFirstByParticipantIdOrderByCreatedAtDesc(participantId)).thenReturn(Optional.of(payment));
        when(paymentProvider.key()).thenReturn("PESEPAY");
        when(paymentProvider.refund(eq(payment), any(), any())).thenReturn("REF-1");

        PaymentDto dto = service.refund(raceId, participantId, null, "Duplicate entry", "admin@example.com");

        assertThat(dto.getStatus()).isEqualTo("REFUNDED");
        assertThat(payment.getStatus()).isEqualTo(Payment.PaymentStatus.REFUNDED);
        assertThat(participant.getPaymentStatus()).isEqualTo(Participant.PaymentStatus.REFUNDED);

        ArgumentCaptor<PaymentRefund> captor = ArgumentCaptor.forClass(PaymentRefund.class);
        verify(paymentRefundRepository, org.mockito.Mockito.times(2)).save(captor.capture());
        PaymentRefund savedRefund = captor.getValue();
        assertThat(savedRefund.getAmount()).isEqualByComparingTo(BigDecimal.valueOf(100));
        assertThat(savedRefund.getProviderRef()).isEqualTo("REF-1");
        assertThat(savedRefund.getStatus()).isEqualTo(PaymentRefund.RefundStatus.REFUNDED);
    }

    @Test
    void failedProviderRefundIsRecordedAndNotMarkedRefunded() {
        Participant participant = participant();
        Payment payment = payment(Payment.PaymentStatus.COMPLETED);
        when(participantRepository.findByRaceIdAndId(raceId, participantId)).thenReturn(Optional.of(participant));
        when(paymentRepository.findFirstByParticipantIdOrderByCreatedAtDesc(participantId)).thenReturn(Optional.of(payment));
        when(paymentProvider.key()).thenReturn("PESEPAY");
        when(paymentProvider.refund(eq(payment), any(), any())).thenThrow(new ApiException("provider down"));
        when(paymentRefundRepository.save(any(PaymentRefund.class))).thenAnswer(inv -> inv.getArgument(0));

        assertThatThrownBy(() -> service.refund(raceId, participantId, null, null, "admin@example.com"))
            .isInstanceOf(ApiException.class);

        assertThat(payment.getStatus()).isEqualTo(Payment.PaymentStatus.COMPLETED);
        assertThat(participant.getPaymentStatus()).isEqualTo(Participant.PaymentStatus.PAID);
        ArgumentCaptor<PaymentRefund> captor = ArgumentCaptor.forClass(PaymentRefund.class);
        verify(paymentRefundRepository, org.mockito.Mockito.times(2)).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(PaymentRefund.RefundStatus.FAILED);
        assertThat(captor.getValue().getErrorMessage()).contains("provider down");
    }

    @Test
    void refundIsIdempotentWhenAlreadyRefunded() {
        Payment payment = payment(Payment.PaymentStatus.REFUNDED);
        when(participantRepository.findByRaceIdAndId(raceId, participantId)).thenReturn(Optional.of(participant()));
        when(paymentRepository.findFirstByParticipantIdOrderByCreatedAtDesc(participantId)).thenReturn(Optional.of(payment));

        PaymentDto dto = service.refund(raceId, participantId, null, null, "admin@example.com");

        assertThat(dto.getStatus()).isEqualTo("REFUNDED");
        verify(paymentProvider, never()).refund(any(), any(), any());
        verify(paymentRefundRepository, never()).save(any());
    }

    @Test
    void refundRejectsWhenNoCompletedPayment() {
        Payment payment = payment(Payment.PaymentStatus.PENDING);
        when(participantRepository.findByRaceIdAndId(raceId, participantId)).thenReturn(Optional.of(participant()));
        when(paymentRepository.findFirstByParticipantIdOrderByCreatedAtDesc(participantId)).thenReturn(Optional.of(payment));

        assertThatThrownBy(() -> service.refund(raceId, participantId, null, null, "admin@example.com"))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("No completed payment");
    }
}
