package com.racetracker.controller;

import com.racetracker.dto.checkpoint.OperationCheckpointDto;
import com.racetracker.exception.ApiException;
import com.racetracker.service.OperationsCheckpointService;
import org.springframework.data.domain.Page;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v${racetracker.api.version}/operations")
public class OperationsCheckpointController {
    private final OperationsCheckpointService service;

    public OperationsCheckpointController(OperationsCheckpointService service) {
        this.service = service;
    }

    @GetMapping("/checkpoints")
    @PreAuthorize("hasRole('VOLUNTEER')")
    public Page<OperationCheckpointDto> list(@RequestParam(defaultValue = "0") int page,
                                              @RequestParam(defaultValue = "50") int size,
                                              Authentication authentication) {
        if (page < 0 || size < 1 || size > 100) {
            throw new ApiException("Invalid page or size", "INVALID_PAGINATION");
        }
        return service.list(authentication.getName(), page, size);
    }
}
