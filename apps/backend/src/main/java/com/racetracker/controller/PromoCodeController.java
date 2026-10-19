package com.racetracker.controller;

import com.racetracker.dto.participant.PromoCodeDto;
import com.racetracker.dto.participant.PromoCodeRequest;
import com.racetracker.security.RaceAccessGuard;
import com.racetracker.service.PromoCodeService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/v${racetracker.api.version}/races/{raceId}/promo-codes")
public class PromoCodeController {

    private final PromoCodeService promoCodeService;
    private final RaceAccessGuard raceAccessGuard;

    public PromoCodeController(PromoCodeService promoCodeService, RaceAccessGuard raceAccessGuard) {
        this.promoCodeService = promoCodeService;
        this.raceAccessGuard = raceAccessGuard;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'RACE_ADMIN', 'VOLUNTEER', 'OFFICIAL')")
    public ResponseEntity<List<PromoCodeDto>> list(@PathVariable UUID raceId) {
        raceAccessGuard.assertCanReadStaff(raceId);
        return ResponseEntity.ok(promoCodeService.list(raceId));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'RACE_ADMIN')")
    public ResponseEntity<PromoCodeDto> create(@PathVariable UUID raceId, @Valid @RequestBody PromoCodeRequest request) {
        raceAccessGuard.assertCanManage(raceId);
        return ResponseEntity.ok(promoCodeService.create(raceId, request));
    }

    @PatchMapping("/{promoId}/active")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'RACE_ADMIN')")
    public ResponseEntity<PromoCodeDto> setActive(@PathVariable UUID raceId, @PathVariable UUID promoId,
                                                  @RequestBody Map<String, Boolean> body) {
        raceAccessGuard.assertCanManage(raceId);
        return ResponseEntity.ok(promoCodeService.setActive(raceId, promoId, Boolean.TRUE.equals(body.get("active"))));
    }

    @DeleteMapping("/{promoId}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'RACE_ADMIN')")
    public ResponseEntity<Void> delete(@PathVariable UUID raceId, @PathVariable UUID promoId) {
        raceAccessGuard.assertCanManage(raceId);
        promoCodeService.delete(raceId, promoId);
        return ResponseEntity.noContent().build();
    }
}
