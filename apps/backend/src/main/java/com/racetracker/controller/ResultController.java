package com.racetracker.controller;

import com.racetracker.dto.auth.ApiMessageResponse;
import com.racetracker.dto.result.RaceResultDto;
import com.racetracker.dto.result.RaceResultPageDto;
import com.racetracker.dto.result.PublicRaceResultDto;
import com.racetracker.dto.result.PublicRaceResultPageDto;
import com.racetracker.dto.result.RankingEntryDto;
import com.racetracker.security.CertificateTokenService;
import com.racetracker.service.CertificateService;
import com.racetracker.security.RaceAccessGuard;
import com.racetracker.service.RankingService;
import com.racetracker.service.ExportedFile;
import com.racetracker.service.ResultExportService;
import com.racetracker.service.ResultService;
import com.racetracker.service.PublicRaceVisibility;
import com.racetracker.service.AuditEventService;
import com.racetracker.security.PublicRequestLimiter;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
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
    private final PublicRequestLimiter publicRequestLimiter;
    private final ResultExportService resultExportService;
    private final CertificateService certificateService;
    private final CertificateTokenService certificateTokenService;
    private final RankingService rankingService;
    private final RaceAccessGuard raceAccessGuard;
    private final PublicRaceVisibility publicRaceVisibility;

    private final com.racetracker.service.impl.TimingAnomalyService timingAnomalyService;

    private final com.racetracker.service.ResultNotifier resultNotifier;

    public ResultController(
        ResultService resultService,
        AuditEventService auditEventService,
        PublicRequestLimiter publicRequestLimiter,
        ResultExportService resultExportService,
        CertificateService certificateService,
        CertificateTokenService certificateTokenService,
        RankingService rankingService,
        RaceAccessGuard raceAccessGuard,
        PublicRaceVisibility publicRaceVisibility,
        com.racetracker.service.impl.TimingAnomalyService timingAnomalyService,
        com.racetracker.service.ResultNotifier resultNotifier
    ) {
        this.resultService = resultService;
        this.timingAnomalyService = timingAnomalyService;
        this.resultNotifier = resultNotifier;
        this.auditEventService = auditEventService;
        this.publicRequestLimiter = publicRequestLimiter;
        this.resultExportService = resultExportService;
        this.certificateService = certificateService;
        this.certificateTokenService = certificateTokenService;
        this.rankingService = rankingService;
        this.raceAccessGuard = raceAccessGuard;
        this.publicRaceVisibility = publicRaceVisibility;
    }

    @GetMapping("/v${racetracker.api.version}/races/{raceId}/results")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'RACE_ADMIN', 'VOLUNTEER', 'OFFICIAL')")
    public ResponseEntity<List<RaceResultDto>> listByRace(@PathVariable UUID raceId) {
        raceAccessGuard.assertCanReadStaff(raceId);
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
        raceAccessGuard.assertCanReadStaff(raceId);
        return ResponseEntity.ok(resultService.listByRacePage(raceId, page, size, revision));
    }

    @GetMapping("/v${racetracker.api.version}/races/{raceId}/results/export")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'RACE_ADMIN', 'VOLUNTEER', 'OFFICIAL')")
    public ResponseEntity<byte[]> exportResults(
        @PathVariable UUID raceId,
        @RequestParam(defaultValue = "csv") String format
    ) {
        raceAccessGuard.assertCanReadStaff(raceId);
        ExportedFile file = resultExportService.export(raceId, format);
        return fileResponse(file);    }

    @GetMapping("/v${racetracker.api.version}/races/{raceId}/results/{participantId}/certificate")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'RACE_ADMIN', 'VOLUNTEER', 'OFFICIAL')")
    public ResponseEntity<byte[]> participantCertificate(
        @PathVariable UUID raceId,
        @PathVariable UUID participantId
    ) {
        raceAccessGuard.assertCanReadStaff(raceId);
        return pdfResponse(certificateService.generate(raceId, participantId), "certificate.pdf");
    }

    @GetMapping("/v${racetracker.api.version}/races/{raceId}/rankings/teams")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'RACE_ADMIN', 'VOLUNTEER', 'OFFICIAL')")
    public ResponseEntity<List<RankingEntryDto>> teamRankings(@PathVariable UUID raceId) {
        raceAccessGuard.assertCanReadStaff(raceId);
        return ResponseEntity.ok(rankingService.teamRankings(raceId));
    }

    @GetMapping("/v${racetracker.api.version}/races/{raceId}/rankings/countries")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'RACE_ADMIN', 'VOLUNTEER', 'OFFICIAL')")
    public ResponseEntity<List<RankingEntryDto>> countryRankings(@PathVariable UUID raceId) {
        raceAccessGuard.assertCanReadStaff(raceId);
        return ResponseEntity.ok(rankingService.countryRankings(raceId));
    }

    @GetMapping("/v${racetracker.api.version}/public/races/{raceId}/results")
    public ResponseEntity<PublicRaceResultPageDto> listPublishedPublicResults(
        @PathVariable UUID raceId,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "50") int size,
        @RequestParam(required = false) Long revision,
        HttpServletRequest request
    ) {
        publicRequestLimiter.check(raceId, request);
        publicRaceVisibility.requireVisible(raceId);
        RaceResultPageDto results = resultService.listPublishedByRacePage(raceId, page, size, revision);
        List<PublicRaceResultDto> publicItems = results.items().stream().map(this::toPublicResult).toList();
        return ResponseEntity.ok(new PublicRaceResultPageDto(publicItems, results.page(), results.size(),
            results.totalElements(), results.totalPages(), results.revision()));
    }

    @GetMapping("/v${racetracker.api.version}/public/races/{raceId}/results/{participantId}/certificate")
    public ResponseEntity<byte[]> publicCertificate(
        @PathVariable UUID raceId,
        @PathVariable UUID participantId,
        @RequestParam(required = false) String token,
        HttpServletRequest request
    ) {
        publicRequestLimiter.check(raceId, request);
        var claims = certificateTokenService.verify(token);
        boolean ownerToken = claims.isPresent()
            && claims.get().raceId().equals(raceId)
            && claims.get().participantId().equals(participantId);
        if (!ownerToken) {
            publicRaceVisibility.requireVisible(raceId);
            return ResponseEntity.status(403).build();
        }
        return pdfResponse(certificateService.generate(raceId, participantId), "certificate.pdf");
    }

    @GetMapping("/v${racetracker.api.version}/races/{raceId}/results/{participantId}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'RACE_ADMIN', 'VOLUNTEER', 'OFFICIAL')")
    public ResponseEntity<RaceResultDto> getByParticipant(
        @PathVariable UUID raceId,
        @PathVariable UUID participantId
    ) {
        raceAccessGuard.assertCanReadStaff(raceId);
        return ResponseEntity.ok(resultService.getByRaceAndParticipant(raceId, participantId));
    }

    @GetMapping("/v${racetracker.api.version}/results/my")
    @PreAuthorize("hasAuthority('CAP_ATHLETE')")
    public ResponseEntity<List<RaceResultDto>> myResults() {
        return ResponseEntity.ok(withCertificateTokens(resultService.getMyResults()));
    }

    @GetMapping("/v${racetracker.api.version}/results/my/history")
    @PreAuthorize("hasAuthority('CAP_ATHLETE')")
    public ResponseEntity<List<com.racetracker.dto.result.AthleteHistoryEntryDto>> myHistory() {
        return ResponseEntity.ok(resultService.getMyHistory());
    }

    @GetMapping("/v${racetracker.api.version}/results/my/personal-bests")
    @PreAuthorize("hasAuthority('CAP_ATHLETE')")
    public ResponseEntity<List<com.racetracker.dto.result.PersonalBestDto>> myPersonalBests() {
        return ResponseEntity.ok(resultService.getMyPersonalBests());
    }

    @GetMapping("/v${racetracker.api.version}/results/my/page")
    @PreAuthorize("hasAuthority('CAP_ATHLETE')")
    public ResponseEntity<RaceResultPageDto> myResultsPage(
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "50") int size
    ) {
        RaceResultPageDto results = resultService.getMyResultsPage(page, size);
        withCertificateTokens(results.items());
        return ResponseEntity.ok(results);
    }

    private List<RaceResultDto> withCertificateTokens(List<RaceResultDto> ownResults) {
        for (RaceResultDto result : ownResults) {
            if (result.isCertificateAvailable() && result.getRaceId() != null && result.getParticipantId() != null) {
                result.setCertificateToken(certificateTokenService.issue(result.getRaceId(), result.getParticipantId()));
            }
        }
        return ownResults;
    }

    @PostMapping("/v${racetracker.api.version}/races/{raceId}/results/publish")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'RACE_ADMIN')")
    public ResponseEntity<ApiMessageResponse> publish(@PathVariable UUID raceId, Authentication authentication) {
        raceAccessGuard.assertCanManage(raceId);
        int version = resultService.publish(raceId);
        auditEventService.recordForActor("RESULTS_PUBLISHED", authentication.getName(), "RACE", raceId.toString(),
            "SUCCESS", java.util.Map.of("version", String.valueOf(version)));
        resultNotifier.resultsPublished(raceId, version, false);
        return ResponseEntity.ok(new ApiMessageResponse("Results published"));
    }

    @PostMapping("/v${racetracker.api.version}/races/{raceId}/results/certify")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'RACE_ADMIN')")
    public ResponseEntity<com.racetracker.dto.result.ResultVersionDto> certify(@PathVariable UUID raceId,
                                                                               Authentication authentication) {
        raceAccessGuard.assertCanManage(raceId);
        com.racetracker.dto.result.ResultVersionDto certified = resultService.certify(raceId);
        auditEventService.recordForActor("RESULTS_CERTIFIED", authentication.getName(), "RACE", raceId.toString(),
            "SUCCESS", java.util.Map.of("version", String.valueOf(certified.version()),
                "contentSha256", certified.contentSha256()));
        resultNotifier.resultsPublished(raceId, certified.version(), true);
        return ResponseEntity.ok(certified);
    }

    @PostMapping("/v${racetracker.api.version}/races/{raceId}/results/reopen")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'RACE_ADMIN')")
    public ResponseEntity<ApiMessageResponse> reopen(@PathVariable UUID raceId,
                                                     @jakarta.validation.Valid @RequestBody com.racetracker.dto.result.ResultReopenRequest request,
                                                     Authentication authentication) {
        raceAccessGuard.assertCanManage(raceId);
        resultService.reopen(raceId, request.getReason().trim());
        auditEventService.recordForActor("RESULTS_REOPENED", authentication.getName(), "RACE", raceId.toString(),
            "SUCCESS", java.util.Map.of("reason", request.getReason().trim()));
        return ResponseEntity.ok(new ApiMessageResponse("Results reopened"));
    }

    @GetMapping("/v${racetracker.api.version}/races/{raceId}/results/integrity")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'RACE_ADMIN', 'OFFICIAL')")
    public ResponseEntity<com.racetracker.dto.result.ResultIntegrityDto> integrity(@PathVariable UUID raceId) {
        raceAccessGuard.assertCanReadStaff(raceId);
        return ResponseEntity.ok(resultService.integrity(raceId));
    }

    @GetMapping("/v${racetracker.api.version}/races/{raceId}/results/anomalies")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'RACE_ADMIN', 'OFFICIAL')")
    public ResponseEntity<java.util.List<com.racetracker.dto.result.TimingAnomalyDto>> anomalies(@PathVariable UUID raceId) {
        return ResponseEntity.ok(timingAnomalyService.anomalies(raceId));
    }

    @GetMapping("/v${racetracker.api.version}/races/{raceId}/results/versions")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'RACE_ADMIN', 'OFFICIAL')")
    public ResponseEntity<java.util.List<com.racetracker.dto.result.ResultVersionDto>> versions(@PathVariable UUID raceId) {
        raceAccessGuard.assertCanReadStaff(raceId);
        return ResponseEntity.ok(resultService.versions(raceId));
    }

    private ResponseEntity<byte[]> fileResponse(ExportedFile file) {
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + file.filename() + "\"")
            .contentType(MediaType.parseMediaType(file.contentType()))
            .body(file.content());
    }

    private ResponseEntity<byte[]> pdfResponse(byte[] content, String filename) {
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + filename + "\"")
            .contentType(MediaType.APPLICATION_PDF)
            .body(content);
    }

    private PublicRaceResultDto toPublicResult(RaceResultDto result) {
        String participantName = result.getParticipant() == null ? null
            : java.util.stream.Stream.of(result.getParticipant().getFirstName(), result.getParticipant().getLastName())
                .filter(part -> part != null && !part.isBlank())
                .collect(java.util.stream.Collectors.joining(" "));
        if (participantName != null && participantName.isBlank()) {
            participantName = null;
        }
        String certificateToken = result.isCertificateAvailable()
            ? certificateTokenService.issue(result.getRaceId(), result.getParticipantId())
            : null;
        return new PublicRaceResultDto(result.getParticipantId(), participantName, result.getBibNumber(), result.getStatus(),
            result.getTotalTime(), result.getFinishTime(), result.getCategory(), result.getOverallPosition(),
            result.getGenderPosition(), result.getCategoryPosition(), certificateToken);
    }
}
