package com.racetracker.controller;

import com.racetracker.dto.auth.ApiMessageResponse;
import com.racetracker.dto.result.RaceResultDto;
import com.racetracker.dto.result.RaceResultPageDto;
import com.racetracker.dto.result.PublicRaceResultDto;
import com.racetracker.dto.result.PublicRaceResultPageDto;
import com.racetracker.security.CertificateTokenService;
import com.racetracker.service.CertificateService;
import com.racetracker.service.ResultExportService;
import com.racetracker.service.ResultService;
import com.racetracker.service.AuditEventService;
import com.racetracker.security.ClientAddressResolver;
import com.racetracker.security.RequestRateLimiter;
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
    private final RequestRateLimiter requestRateLimiter;
    private final ClientAddressResolver clientAddressResolver;
    private final ResultExportService resultExportService;
    private final CertificateService certificateService;
    private final CertificateTokenService certificateTokenService;

    public ResultController(
        ResultService resultService,
        AuditEventService auditEventService,
        RequestRateLimiter requestRateLimiter,
        ClientAddressResolver clientAddressResolver,
        ResultExportService resultExportService,
        CertificateService certificateService,
        CertificateTokenService certificateTokenService
    ) {
        this.resultService = resultService;
        this.auditEventService = auditEventService;
        this.requestRateLimiter = requestRateLimiter;
        this.clientAddressResolver = clientAddressResolver;
        this.resultExportService = resultExportService;
        this.certificateService = certificateService;
        this.certificateTokenService = certificateTokenService;
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

    @GetMapping("/v${racetracker.api.version}/races/{raceId}/results/export")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'RACE_ADMIN', 'VOLUNTEER', 'OFFICIAL')")
    public ResponseEntity<byte[]> exportResults(
        @PathVariable UUID raceId,
        @RequestParam(defaultValue = "csv") String format
    ) {
        ResultExportService.ExportedFile file = resultExportService.export(raceId, format);
        return fileResponse(file);
    }

    @GetMapping("/v${racetracker.api.version}/races/{raceId}/results/{participantId}/certificate")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'RACE_ADMIN', 'VOLUNTEER', 'OFFICIAL')")
    public ResponseEntity<byte[]> participantCertificate(
        @PathVariable UUID raceId,
        @PathVariable UUID participantId
    ) {
        return pdfResponse(certificateService.generate(raceId, participantId), "certificate.pdf");
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

    @GetMapping("/v${racetracker.api.version}/public/races/{raceId}/results/{participantId}/certificate")
    public ResponseEntity<byte[]> publicCertificate(
        @PathVariable UUID raceId,
        @PathVariable UUID participantId,
        @RequestParam(required = false) String token,
        HttpServletRequest request
    ) {
        requestRateLimiter.checkPublicTracking(raceId.toString(), clientAddressResolver.resolve(request));
        var claims = certificateTokenService.verify(token);
        if (claims.isEmpty()
            || !claims.get().raceId().equals(raceId)
            || !claims.get().participantId().equals(participantId)) {
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

    private ResponseEntity<byte[]> fileResponse(ResultExportService.ExportedFile file) {
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
            : (String.valueOf(result.getParticipant().getFirstName()) + " " + String.valueOf(result.getParticipant().getLastName())).trim();
        String certificateToken = result.isCertificateAvailable()
            ? certificateTokenService.issue(result.getRaceId(), result.getParticipantId())
            : null;
        return new PublicRaceResultDto(result.getParticipantId(), participantName, result.getBibNumber(), result.getStatus(),
            result.getTotalTime(), result.getFinishTime(), result.getCategory(), result.getOverallPosition(),
            result.getGenderPosition(), result.getCategoryPosition(), certificateToken);
    }
}
