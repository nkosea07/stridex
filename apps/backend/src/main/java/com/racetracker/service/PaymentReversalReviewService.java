package com.racetracker.service;

import com.racetracker.dto.participant.ReversalCaseDto;

import java.util.List;

public interface PaymentReversalReviewService {
    List<ReversalCaseDto> listOpenCases(int limit);
}
