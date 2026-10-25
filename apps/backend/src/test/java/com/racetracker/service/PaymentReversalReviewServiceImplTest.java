package com.racetracker.service;

import com.racetracker.dto.participant.ReversalCaseDto;
import com.racetracker.model.Participant;
import com.racetracker.model.Payment;
import com.racetracker.model.PaymentRefund;
import com.racetracker.model.PaymentReversalCase;
import com.racetracker.model.Race;
import com.racetracker.repository.PaymentRefundRepository;
import com.racetracker.repository.PaymentReversalCaseRepository;
import com.racetracker.service.impl.PaymentReversalReviewServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentReversalReviewServiceImplTest {

    @Mock PaymentReversalCaseRepository reversalCaseRepository;
    @Mock PaymentRefundRepository refundRepository;

    private PaymentReversalReviewServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new PaymentReversalReviewServiceImpl(reversalCaseRepository, refundRepository);
    }

    @Test
    void returnsOpenCasesWithPayableAmountAndPendingAttempt() {
        Race race = new Race();
        race.setId(UUID.randomUUID());
        race.setName("City Marathon");
        Participant participant = new Participant();
        participant.setId(UUID.randomUUID());
        participant.setFirstName("Ada");
        participant.setLastName("Runner");
        participant.setRace(race);
        Payment payment = new Payment();
        payment.setId(UUID.randomUUID());
        payment.setProvider("PESEPAY");
        payment.setProviderRef("PROV-1");
        payment.setAmount(BigDecimal.valueOf(100));
        payment.setDiscountAmount(BigDecimal.valueOf(20));
        payment.setCurrency("USD");
        payment.setStatus(Payment.PaymentStatus.COMPLETED);
        payment.setParticipant(participant);
        payment.setRace(race);
        PaymentReversalCase reversal = new PaymentReversalCase();
        reversal.setPaymentId(payment.getId());
        reversal.setPayment(payment);
        reversal.setProviderReference("PROV-1");
        reversal.setObservedAt(OffsetDateTime.now().minusHours(1));
        PaymentRefund attempt = PaymentRefund.builder().id(UUID.randomUUID()).payment(payment)
            .status(PaymentRefund.RefundStatus.PENDING).build();

        when(reversalCaseRepository.findByResolvedAtIsNullOrderByObservedAtAsc(PageRequest.of(0, 100)))
            .thenReturn(List.of(reversal));
        when(refundRepository.findFirstByPaymentIdAndStatus(payment.getId(), PaymentRefund.RefundStatus.PENDING))
            .thenReturn(Optional.of(attempt));

        List<ReversalCaseDto> result = service.listOpenCases(100);

        assertThat(result).singleElement().satisfies(dto -> {
            assertThat(dto.paymentId()).isEqualTo(payment.getId());
            assertThat(dto.raceName()).isEqualTo("City Marathon");
            assertThat(dto.participantName()).isEqualTo("Ada Runner");
            assertThat(dto.amount()).isEqualByComparingTo(BigDecimal.valueOf(80));
            assertThat(dto.pendingRefundAttemptId()).isEqualTo(attempt.getId());
        });
    }
}
