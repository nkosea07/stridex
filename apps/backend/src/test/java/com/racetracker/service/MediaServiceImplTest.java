package com.racetracker.service;

import com.racetracker.config.RaceTrackerProperties;
import com.racetracker.dto.media.MediaDto;
import com.racetracker.model.Race;
import com.racetracker.model.RaceMedia;
import com.racetracker.repository.ParticipantRepository;
import com.racetracker.repository.RaceMediaRepository;
import com.racetracker.repository.RaceRepository;
import com.racetracker.repository.UserRepository;
import com.racetracker.service.impl.ImageThumbnailService;
import com.racetracker.service.impl.LocalMediaStorage;
import com.racetracker.service.impl.MediaServiceImpl;
import com.racetracker.service.impl.MediaStorageScope;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.file.Path;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MediaServiceImplTest {

    @Mock RaceMediaRepository mediaRepository;
    @Mock RaceRepository raceRepository;
    @Mock ParticipantRepository participantRepository;
    @Mock UserRepository userRepository;

    @TempDir Path tempDir;

    private MediaServiceImpl service;
    private final UUID raceId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        RaceTrackerProperties properties = new RaceTrackerProperties();
        properties.getStorage().setUploadDir(tempDir.toString());
        LocalMediaStorage storage = new LocalMediaStorage(properties);
        service = new MediaServiceImpl(mediaRepository, raceRepository, participantRepository, userRepository,
            storage, new ImageThumbnailService(), properties, new MediaStorageScope(properties));
        lenient().when(mediaRepository.save(any(RaceMedia.class))).thenAnswer(inv -> {
            RaceMedia media = inv.getArgument(0);
            if (media.getId() == null) media.setId(UUID.randomUUID());
            return media;
        });
    }

    private Race race() {
        Race race = new Race();
        race.setId(raceId);
        return race;
    }

    @Test
    void uploadStoresPendingImageAndBuildsPublicUrl() {
        when(raceRepository.findById(raceId)).thenReturn(Optional.of(race()));
        MockMultipartFile file = new MockMultipartFile("file", "photo.png", "image/png", new byte[]{1, 2, 3});

        MediaDto dto = service.upload(raceId, file, null, "Finish line", null);

        assertThat(dto.getStatus()).isEqualTo("PENDING");
        assertThat(dto.getMediaType()).isEqualTo("IMAGE");
        assertThat(dto.getUrl()).contains("/public/races/" + raceId + "/media/");
        assertThat(dto.getCaption()).isEqualTo("Finish line");
        org.mockito.ArgumentCaptor<RaceMedia> saved = org.mockito.ArgumentCaptor.forClass(RaceMedia.class);
        org.mockito.Mockito.verify(mediaRepository).save(saved.capture());
        assertThat(saved.getValue().getStorageScope()).isEqualTo("local|" + tempDir.toAbsolutePath().normalize());
    }

    @Test
    void uploadRejectsUnsupportedContentType() {
        when(raceRepository.findById(raceId)).thenReturn(Optional.of(race()));
        MockMultipartFile file = new MockMultipartFile("file", "notes.txt", "text/plain", new byte[]{1});

        assertThatThrownBy(() -> service.upload(raceId, file, null, null, null))
            .hasMessageContaining("Unsupported media type");
    }

    @Test
    void publicContentRejectsPendingMedia() {
        RaceMedia media = RaceMedia.builder().race(race()).storageKey("x.jpg").status(RaceMedia.MediaStatus.PENDING).build();
        media.setId(UUID.randomUUID());
        when(mediaRepository.findByRaceIdAndId(raceId, media.getId())).thenReturn(Optional.of(media));

        assertThatThrownBy(() -> service.content(raceId, media.getId(), true))
            .isInstanceOf(com.racetracker.exception.ResourceNotFoundException.class);
    }
}
