package com.racetracker.mapper;

import com.racetracker.dto.checkpoint.CheckpointDto;
import com.racetracker.dto.checkpoint.CheckpointRecordDto;
import com.racetracker.model.Checkpoint;
import com.racetracker.model.CheckpointRecord;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.time.Duration;

@Mapper(componentModel = "spring")
public interface CheckpointMapper {

    @Mapping(target = "raceId", expression = "java(entity.getRace() != null ? entity.getRace().getId() : null)")
    @Mapping(target = "checkpointType", expression = "java(entity.getCheckpointType() != null ? entity.getCheckpointType().name() : null)")
    @Mapping(target = "cutoffTime", expression = "java(formatDuration(entity.getCutoffTime()))")
    @Mapping(target = "recordCount", expression = "java(entity.getRecords() != null ? (long) entity.getRecords().size() : 0L)")
    CheckpointDto toDto(Checkpoint entity);

    @Mapping(target = "participantId", expression = "java(entity.getParticipant() != null ? entity.getParticipant().getId() : null)")
    @Mapping(target = "checkpointId", expression = "java(entity.getCheckpoint() != null ? entity.getCheckpoint().getId() : null)")
    @Mapping(target = "raceId", expression = "java(entity.getRace() != null ? entity.getRace().getId() : null)")
    @Mapping(target = "recordedAt", expression = "java(entity.getRecordedAt() != null ? entity.getRecordedAt().toString() : null)")
    @Mapping(target = "recordMethod", expression = "java(entity.getRecordMethod() != null ? entity.getRecordMethod().name() : null)")
    @Mapping(target = "recordedById", expression = "java(entity.getRecordedBy() != null ? entity.getRecordedBy().getId() : null)")
    @Mapping(target = "splitTime", expression = "java(formatDuration(entity.getSplitTime()))")
    @Mapping(target = "elapsedTime", expression = "java(formatDuration(entity.getElapsedTime()))")
    @Mapping(target = "adjustedRecordedAt", expression = "java(entity.getAdjustedRecordedAt() != null ? entity.getAdjustedRecordedAt().toString() : null)")
    @Mapping(target = "adjustedAt", expression = "java(entity.getAdjustedAt() != null ? entity.getAdjustedAt().toString() : null)")
    CheckpointRecordDto toRecordDto(CheckpointRecord entity);

    default String formatDuration(Duration duration) {
        if (duration == null) {
            return null;
        }
        long totalSeconds = duration.getSeconds();
        long hours = totalSeconds / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        long seconds = totalSeconds % 60;
        return String.format("%02d:%02d:%02d", hours, minutes, seconds);
    }
}
