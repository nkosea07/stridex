package com.racetracker.service;

import com.racetracker.dto.route.RoutePointDto;
import com.racetracker.model.Race;
import com.racetracker.repository.RaceRepository;
import com.racetracker.repository.RaceRoutePointRepository;
import com.racetracker.service.impl.RaceRouteServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RaceRouteServiceImplTest {

    @Mock RaceRoutePointRepository routePointRepository;
    @Mock RaceRepository raceRepository;

    private RaceRouteServiceImpl service;
    private final UUID raceId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new RaceRouteServiceImpl(routePointRepository, raceRepository);
    }

    @Test
    void importGpxParsesTrackPoints() {
        Race race = new Race();
        race.setId(raceId);
        when(raceRepository.findById(raceId)).thenReturn(Optional.of(race));
        when(routePointRepository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));

        String gpx = """
            <?xml version="1.0" encoding="UTF-8"?>
            <gpx version="1.1" creator="test">
              <trk><trkseg>
                <trkpt lat="-33.90000000" lon="18.40000000"><ele>12.5</ele></trkpt>
                <trkpt lat="-33.91000000" lon="18.41000000"></trkpt>
              </trkseg></trk>
            </gpx>
            """;
        MockMultipartFile file = new MockMultipartFile("file", "route.gpx", "application/gpx+xml",
            gpx.getBytes(StandardCharsets.UTF_8));

        List<RoutePointDto> points = service.importGpx(raceId, file);

        assertThat(points).hasSize(2);
        assertThat(points.get(0).latitude()).isEqualByComparingTo("-33.90000000");
        assertThat(points.get(0).elevation()).isEqualByComparingTo("12.5");
        assertThat(points.get(1).sequence()).isEqualTo(1);
    }
}
