package com.racetracker.controller;

import com.racetracker.dto.media.MediaDto;
import com.racetracker.security.RaceAccessGuard;
import com.racetracker.service.AuditEventService;
import com.racetracker.service.MediaService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
public class MediaController {

    private final MediaService mediaService;
    private final AuditEventService auditEventService;
    private final RaceAccessGuard raceAccessGuard;

    public MediaController(MediaService mediaService, AuditEventService auditEventService, RaceAccessGuard raceAccessGuard) {
        this.mediaService = mediaService;
        this.auditEventService = auditEventService;
        this.raceAccessGuard = raceAccessGuard;
    }

    @PostMapping(value = "/v${racetracker.api.version}/races/{raceId}/media", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'RACE_ADMIN', 'VOLUNTEER', 'OFFICIAL')")
    public ResponseEntity<MediaDto> upload(
        @PathVariable UUID raceId,
        @RequestPart("file") MultipartFile file,
        @RequestParam(required = false) UUID participantId,
        @RequestParam(required = false) String caption,
        Authentication authentication
    ) {
        raceAccessGuard.assertCanManage(raceId);
        MediaDto media = mediaService.upload(raceId, file, participantId, caption, authentication.getName());
        auditEventService.recordForActor("MEDIA_UPLOADED", authentication.getName(), "RACE_MEDIA", media.getId().toString(),
            "SUCCESS", Map.of("raceId", raceId.toString()));
        return ResponseEntity.ok(media);
    }

    @GetMapping("/v${racetracker.api.version}/races/{raceId}/media")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'RACE_ADMIN', 'VOLUNTEER', 'OFFICIAL')")
    public ResponseEntity<List<MediaDto>> list(@PathVariable UUID raceId) {
        raceAccessGuard.assertCanReadStaff(raceId);
        return ResponseEntity.ok(mediaService.listForRace(raceId));
    }

    @PostMapping("/v${racetracker.api.version}/races/{raceId}/media/{mediaId}/approve")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'RACE_ADMIN')")
    public ResponseEntity<MediaDto> approve(@PathVariable UUID raceId, @PathVariable UUID mediaId, Authentication authentication) {
        raceAccessGuard.assertCanManage(raceId);
        MediaDto media = mediaService.moderate(raceId, mediaId, true, authentication.getName());
        auditEventService.recordForActor("MEDIA_APPROVED", authentication.getName(), "RACE_MEDIA", mediaId.toString(),
            "SUCCESS", Map.of());
        return ResponseEntity.ok(media);
    }

    @PostMapping("/v${racetracker.api.version}/races/{raceId}/media/{mediaId}/reject")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'RACE_ADMIN')")
    public ResponseEntity<MediaDto> reject(@PathVariable UUID raceId, @PathVariable UUID mediaId, Authentication authentication) {
        raceAccessGuard.assertCanManage(raceId);
        MediaDto media = mediaService.moderate(raceId, mediaId, false, authentication.getName());
        auditEventService.recordForActor("MEDIA_REJECTED", authentication.getName(), "RACE_MEDIA", mediaId.toString(),
            "SUCCESS", Map.of());
        return ResponseEntity.ok(media);
    }

    @DeleteMapping("/v${racetracker.api.version}/races/{raceId}/media/{mediaId}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'RACE_ADMIN')")
    public ResponseEntity<Void> delete(@PathVariable UUID raceId, @PathVariable UUID mediaId, Authentication authentication) {
        raceAccessGuard.assertCanManage(raceId);
        mediaService.delete(raceId, mediaId);
        auditEventService.recordForActor("MEDIA_DELETED", authentication.getName(), "RACE_MEDIA", mediaId.toString(),
            "SUCCESS", Map.of());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/v${racetracker.api.version}/races/{raceId}/media/{mediaId}/content")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'RACE_ADMIN', 'VOLUNTEER', 'OFFICIAL')")
    public ResponseEntity<byte[]> staffContent(@PathVariable UUID raceId, @PathVariable UUID mediaId) {
        raceAccessGuard.assertCanReadStaff(raceId);
        return contentResponse(mediaService.content(raceId, mediaId, false));
    }

    @GetMapping("/v${racetracker.api.version}/races/{raceId}/media/{mediaId}/thumbnail")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'RACE_ADMIN', 'VOLUNTEER', 'OFFICIAL')")
    public ResponseEntity<byte[]> staffThumbnail(@PathVariable UUID raceId, @PathVariable UUID mediaId) {
        raceAccessGuard.assertCanReadStaff(raceId);
        return contentResponse(mediaService.thumbnail(raceId, mediaId, false));
    }

    @GetMapping("/v${racetracker.api.version}/public/races/{raceId}/media")
    public ResponseEntity<List<MediaDto>> publicList(@PathVariable UUID raceId) {
        return ResponseEntity.ok(mediaService.listApproved(raceId));
    }

    @GetMapping("/v${racetracker.api.version}/public/races/{raceId}/media/{mediaId}/content")
    public ResponseEntity<byte[]> publicContent(@PathVariable UUID raceId, @PathVariable UUID mediaId) {
        return contentResponse(mediaService.content(raceId, mediaId, true));
    }

    @GetMapping("/v${racetracker.api.version}/public/races/{raceId}/media/{mediaId}/thumbnail")
    public ResponseEntity<byte[]> publicThumbnail(@PathVariable UUID raceId, @PathVariable UUID mediaId) {
        return contentResponse(mediaService.thumbnail(raceId, mediaId, true));
    }

    private ResponseEntity<byte[]> contentResponse(MediaService.MediaContent content) {
        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType(content.contentType()))
            .body(content.bytes());
    }
}
