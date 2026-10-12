package com.racetracker.service.impl;

import com.racetracker.dto.route.RoutePointDto;
import com.racetracker.exception.ApiException;
import com.racetracker.exception.ResourceNotFoundException;
import com.racetracker.model.Race;
import com.racetracker.model.RaceRoutePoint;
import com.racetracker.repository.RaceRepository;
import com.racetracker.repository.RaceRoutePointRepository;
import com.racetracker.service.RaceRouteService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.InputStream;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class RaceRouteServiceImpl implements RaceRouteService {

    private static final int MAX_POINTS = 50_000;

    private final RaceRoutePointRepository routePointRepository;
    private final RaceRepository raceRepository;

    public RaceRouteServiceImpl(RaceRoutePointRepository routePointRepository, RaceRepository raceRepository) {
        this.routePointRepository = routePointRepository;
        this.raceRepository = raceRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public List<RoutePointDto> getRoute(UUID raceId) {
        requireRace(raceId);
        return routePointRepository.findByRaceIdOrderBySequenceAsc(raceId).stream()
            .map(point -> new RoutePointDto(point.getSequence(), point.getLatitude(), point.getLongitude(),
                point.getElevation()))
            .toList();
    }

    @Override
    @Transactional
    public List<RoutePointDto> importGpx(UUID raceId, MultipartFile file) {
        Race race = requireRace(raceId);
        if (file == null || file.isEmpty()) {
            throw new ApiException("A GPX file is required");
        }
        List<RoutePointDto> parsed = parseGpx(file);
        if (parsed.isEmpty()) {
            throw new ApiException("The GPX file did not contain any track points");
        }
        if (parsed.size() > MAX_POINTS) {
            throw new ApiException("The GPX file exceeds the maximum of " + MAX_POINTS + " track points");
        }

        routePointRepository.deleteByRaceId(raceId);
        List<RaceRoutePoint> points = new ArrayList<>(parsed.size());
        for (RoutePointDto dto : parsed) {
            points.add(RaceRoutePoint.builder()
                .race(race)
                .sequence(dto.sequence())
                .latitude(dto.latitude())
                .longitude(dto.longitude())
                .elevation(dto.elevation())
                .build());
        }
        routePointRepository.saveAll(points);
        return parsed;
    }

    @Override
    @Transactional(readOnly = true)
    public String exportGpx(UUID raceId) {
        requireRace(raceId);
        List<RaceRoutePoint> points = routePointRepository.findByRaceIdOrderBySequenceAsc(raceId);
        StringBuilder gpx = new StringBuilder();
        gpx.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        gpx.append("<gpx version=\"1.1\" creator=\"race-tracker\" xmlns=\"http://www.topografix.com/GPX/1/1\">\n");
        gpx.append("  <trk><trkseg>\n");
        for (RaceRoutePoint point : points) {
            gpx.append("    <trkpt lat=\"").append(point.getLatitude())
                .append("\" lon=\"").append(point.getLongitude()).append("\">");
            if (point.getElevation() != null) {
                gpx.append("<ele>").append(point.getElevation()).append("</ele>");
            }
            gpx.append("</trkpt>\n");
        }
        gpx.append("  </trkseg></trk>\n");
        gpx.append("</gpx>\n");
        return gpx.toString();
    }

    private List<RoutePointDto> parseGpx(MultipartFile file) {        try (InputStream input = file.getInputStream()) {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setExpandEntityReferences(false);
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document document = builder.parse(input);
            NodeList trackPoints = document.getElementsByTagName("trkpt");
            List<RoutePointDto> points = new ArrayList<>();
            int sequence = 0;
            for (int i = 0; i < trackPoints.getLength(); i++) {
                Element element = (Element) trackPoints.item(i);
                String lat = element.getAttribute("lat");
                String lon = element.getAttribute("lon");
                if (lat == null || lat.isBlank() || lon == null || lon.isBlank()) {
                    continue;
                }
                BigDecimal latitude;
                BigDecimal longitude;
                try {
                    latitude = new BigDecimal(lat.trim());
                    longitude = new BigDecimal(lon.trim());
                } catch (NumberFormatException ex) {
                    continue;
                }
                BigDecimal elevation = null;
                NodeList elevations = element.getElementsByTagName("ele");
                if (elevations.getLength() > 0) {
                    try {
                        elevation = new BigDecimal(elevations.item(0).getTextContent().trim());
                    } catch (NumberFormatException ignored) {
                        // optional elevation
                    }
                }
                points.add(new RoutePointDto(sequence++, latitude, longitude, elevation));
            }
            return points;
        } catch (ApiException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new ApiException("Failed to parse GPX file");
        }
    }

    private Race requireRace(UUID raceId) {
        return raceRepository.findById(raceId)
            .orElseThrow(() -> new ResourceNotFoundException("Race not found"));
    }
}
