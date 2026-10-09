package com.racetracker.controller;

import com.racetracker.dto.auth.ApiMessageResponse;
import com.racetracker.dto.result.RaceResultDto;
import com.racetracker.dto.result.RaceResultPageDto;
import com.racetracker.dto.result.PublicRaceResultDto;
import com.racetracker.dto.result.PublicRaceResultPageDto;
import com.racetracker.service.ResultService;
import com.racetracker.service.AuditEventService;
import com.racetracker.security.ClientAddressResolver;
import com.racetracker.security.RequestRateLimiter;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
public class ResultController {

    private final ResultService resultService;
    private final AuditEventService auditEventService;
    private final RequestRateLimiter requestRateLimiter;
    private final ClientAddressResolver clientAddressResolver;

    public ResultController(
        ResultService resultService,
        AuditEventService auditEventService,
        RequestRateLimiter requestRateLimiter,
        ClientAddressResolver clientAddressResolver
    ) {
        this.resultService = resultService;
        this.auditEventService = auditEventService;
        this.requestRateLimiter = requestRateLimiter;
        this.clientAddressResolver = clientAddressResolver;
    }

    @GetMapping("/v${racetracker.api.version}/races/{raceId}/results")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'RACE_ADMIN', 'VOLUNTEER', 'OFFICIAL')")
    public ResponseEntity<List<RaceResultDto>> listByRace(@PathVariable UUID raceId) {
        return ResponseEntity.ok(resultService.listByRace(raceId));
    }

    @GetMapping("/v${racetracker.api.version}/races/{raceId}/results/page")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'RACE_ADMIN', 'VOLUNTEER', 'OFFICIAL')")
    public ResponseEntity<RaceResultPageDto> listByRacePage(
        @PathVariable UUID raceId,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "50") int size,
        @RequestParam(required = false) Long revision
    ) {
        return ResponseEntity.ok(resultService.listByRacePage(raceId, page, size, revision));
    }

    @GetMapping("/v${racetracker.api.version}/public/races/{raceId}/results")
    public ResponseEntity<PublicRaceResultPageDto> listPublishedPublicResults(
        @PathVariable UUID raceId,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "50") int size,
        @RequestParam(required = false) Long revision,
        HttpServletRequest request
    ) {
        requestRateLimiter.checkPublicTracking(raceId.toString(), clientAddressResolver.resolve(request));
        RaceResultPageDto results = resultService.listPublishedByRacePage(raceId, page, size, revision);
        List<PublicRaceResultDto> publicItems = results.items().stream().map(this::toPublicResult).toList();
        return ResponseEntity.ok(new PublicRaceResultPageDto(publicItems, results.page(), results.size(),
            results.totalElements(), results.totalPages(), results.revision()));
    }

    @GetMapping("/v${racetracker.api.version}/races/{raceId}/results/{participantId}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'RACE_ADMIN', 'VOLUNTEER', 'OFFICIAL')")
    public ResponseEntity<RaceResultDto> getByParticipant(
        @PathVariable UUID raceId,
        @PathVariable UUID participantId
    ) {
        return ResponseEntity.ok(resultService.getByRaceAndParticipant(raceId, participantId));
    }

    @GetMapping("/v${racetracker.api.version}/results/my")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<RaceResultDto>> myResults() {
        return ResponseEntity.ok(resultService.getMyResults());
    }

    @GetMapping("/v${racetracker.api.version}/results/my/page")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<RaceResultPageDto> myResultsPage(
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "50") int size
    ) {
        return ResponseEntity.ok(resultService.getMyResultsPage(page, size));
    }

    @PostMapping("/v${racetracker.api.version}/races/{raceId}/results/publish")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'RACE_ADMIN', 'VOLUNTEER', 'OFFICIAL')")
    public ResponseEntity<ApiMessageResponse> publish(@PathVariable UUID raceId, Authentication authentication) {
        resultService.publish(raceId);
        auditEventService.recordForActor("RESULTS_PUBLISHED", authentication.getName(), "RACE", raceId.toString(),
            "SUCCESS", java.util.Map.of());
        return ResponseEntity.ok(new ApiMessageResponse("Results published"));
    }

    private PublicRaceResultDto toPublicResult(RaceResultDto result) {
        String participantName = result.getParticipant() == null ? null
            : (String.valueOf(result.getParticipant().getFirstName()) + " " + String.valueOf(result.getParticipant().getLastName())).trim();
        return new PublicRaceResultDto(result.getParticipantId(), participantName, result.getBibNumber(), result.getStatus(),
            result.getTotalTime(), result.getFinishTime(), result.getCategory(), result.getOverallPosition(),
            result.getGenderPosition(), result.getCategoryPosition());
    }
}
