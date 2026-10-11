package com.racetracker.service;

import com.racetracker.dto.checkpoint.CheckpointDto;
import com.racetracker.model.Race;
import com.racetracker.repository.RaceRepository;
import com.racetracker.service.impl.CheckpointExportServiceImpl;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CheckpointExportServiceImplTest {

    @Test
    void csvExportContainsCheckpointRows() {
        UUID raceId = UUID.randomUUID();
        Race race = new Race();
        race.setId(raceId);
        race.setName("Demo Marathon");

        CheckpointDto checkpoint = new CheckpointDto();
        checkpoint.setName("Water Point");
        checkpoint.setCheckpointType("WATER_STATION");
        checkpoint.setDistanceFromStartKm(BigDecimal.valueOf(10));
        checkpoint.setOrderIndex(2);
        checkpoint.setRecordCount(15L);

        CheckpointService checkpointService = mock(CheckpointService.class);
        RaceRepository raceRepository = mock(RaceRepository.class);
        when(raceRepository.findById(raceId)).thenReturn(Optional.of(race));
        when(checkpointService.listByRace(raceId)).thenReturn(List.of(checkpoint));

        ExportedFile file = new CheckpointExportServiceImpl(checkpointService, raceRepository).export(raceId, "csv");

        String csv = new String(file.content(), StandardCharsets.UTF_8);
        assertThat(file.filename()).isEqualTo("demo-marathon-checkpoints.csv");
        assertThat(file.contentType()).isEqualTo("text/csv");
        assertThat(csv).contains("\"Order\",\"Name\",\"Type\"");
        assertThat(csv).contains("\"Water Point\"");
        assertThat(csv).contains("\"15\"");
    }
}
