package com.racetracker.service;

import com.racetracker.dto.checkpoint.TimingImportResultDto;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

public interface TimingImportService {
    TimingImportResultDto importCsv(UUID raceId, MultipartFile file);
}
