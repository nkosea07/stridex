package com.racetracker.service;

import com.racetracker.dto.route.RoutePointDto;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

public interface RaceRouteService {

    List<RoutePointDto> getRoute(UUID raceId);

    List<RoutePointDto> importGpx(UUID raceId, MultipartFile file);
}
