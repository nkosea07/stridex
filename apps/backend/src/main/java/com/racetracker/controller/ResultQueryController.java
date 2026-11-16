package com.racetracker.controller;

import com.racetracker.dto.result.ResultQueryDto;
import com.racetracker.dto.result.ResultQueryRequest;
import com.racetracker.dto.result.ResultQueryResolution;
import com.racetracker.service.impl.ResultQueryService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
public class ResultQueryController {

    private final ResultQueryService resultQueryService;

    public ResultQueryController(ResultQueryService resultQueryService) {
        this.resultQueryService = resultQueryService;
    }

    @PostMapping("/v${racetracker.api.version}/results/my/{raceId}/queries")
    @PreAuthorize("hasAuthority('CAP_ATHLETE')")
    public ResponseEntity<ResultQueryDto> submit(@PathVariable UUID raceId, @Valid @RequestBody ResultQueryRequest request) {
        return ResponseEntity.ok(resultQueryService.submit(raceId, request));
    }

    @GetMapping("/v${racetracker.api.version}/results/my/queries")
    @PreAuthorize("hasAuthority('CAP_ATHLETE')")
    public ResponseEntity<List<ResultQueryDto>> mine() {
        return ResponseEntity.ok(resultQueryService.mine());
    }

    @GetMapping("/v${racetracker.api.version}/races/{raceId}/result-queries")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'RACE_ADMIN', 'OFFICIAL')")
    public ResponseEntity<List<ResultQueryDto>> open(@PathVariable UUID raceId) {
        return ResponseEntity.ok(resultQueryService.open(raceId));
    }

    @PostMapping("/v${racetracker.api.version}/races/{raceId}/result-queries/{queryId}/resolution")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'RACE_ADMIN', 'OFFICIAL')")
    public ResponseEntity<ResultQueryDto> resolve(@PathVariable UUID raceId, @PathVariable UUID queryId,
                                                  @Valid @RequestBody ResultQueryResolution resolution) {
        return ResponseEntity.ok(resultQueryService.resolve(raceId, queryId, resolution));
    }
}
