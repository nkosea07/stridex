package com.racetracker.service.impl;

import com.racetracker.dto.participant.ReversalCaseDto;
import com.racetracker.model.Payment;
import com.racetracker.model.PaymentRefund;
import com.racetracker.model.PaymentReversalCase;
import com.racetracker.repository.PaymentRefundRepository;
import com.racetracker.repository.PaymentReversalCaseRepository;
import com.racetracker.service.PaymentReversalReviewService;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Service
public class PaymentReversalReviewServiceImpl implements PaymentReversalReviewService {

    private final PaymentReversalCaseRepository reversalCaseRepository;
    private final PaymentRefundRepository refundRepository;

    public PaymentReversalReviewServiceImpl(PaymentReversalCaseRepository reversalCaseRepository,
                                            PaymentRefundRepository refundRepository) {
        this.reversalCaseRepository = reversalCaseRepository;
        this.refundRepository = refundRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public List<ReversalCaseDto> listOpenCases(int limit) {
        int boundedLimit = Math.min(Math.max(limit, 1), 200);
        return reversalCaseRepository.findByResolvedAtIsNullOrderByObservedAtAsc(PageRequest.of(0, boundedLimit))
            .stream()
            .map(this::toDto)
            .toList();
    }

    private ReversalCaseDto toDto(PaymentReversalCase reversal) {
        Payment payment = reversal.getPayment();
        UUID pendingAttemptId = payment == null ? null : refundRepository
            .findFirstByPaymentIdAndStatus(payment.getId(), PaymentRefund.RefundStatus.PENDING)
            .map(PaymentRefund::getId)
            .orElse(null);
        BigDecimal payable = payment == null || payment.getAmount() == null ? null
            : payment.getAmount().subtract(payment.getDiscountAmount() == null ? BigDecimal.ZERO : payment.getDiscountAmount());
        return new ReversalCaseDto(
            payment == null ? reversal.getPaymentId() : payment.getId(),
            payment == null || payment.getRace() == null ? null : payment.getRace().getId(),
            payment == null || payment.getRace() == null ? null : payment.getRace().getName(),
            payment == null || payment.getParticipant() == null ? null : payment.getParticipant().getId(),
            payment == null || payment.getParticipant() == null ? null : payment.getParticipant().getFullName(),
            payment == null ? null : payment.getProvider(),
            reversal.getProviderReference(),
            payable,
            payment == null ? null : payment.getCurrency(),
            payment == null || payment.getStatus() == null ? null : payment.getStatus().name(),
            reversal.getObservedAt(),
            pendingAttemptId);
    }
}
