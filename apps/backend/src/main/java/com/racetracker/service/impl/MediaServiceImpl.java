package com.racetracker.service.impl;

import com.racetracker.config.RaceTrackerProperties;
import com.racetracker.dto.media.MediaDto;
import com.racetracker.exception.ApiException;
import com.racetracker.exception.ResourceNotFoundException;
import com.racetracker.model.Participant;
import com.racetracker.model.Race;
import com.racetracker.model.RaceMedia;
import com.racetracker.repository.ParticipantRepository;
import com.racetracker.repository.RaceMediaRepository;
import com.racetracker.repository.RaceRepository;
import com.racetracker.repository.UserRepository;
import com.racetracker.service.MediaService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class MediaServiceImpl implements MediaService {

    private final RaceMediaRepository mediaRepository;
    private final RaceRepository raceRepository;
    private final ParticipantRepository participantRepository;
    private final UserRepository userRepository;
    private final FileStorageService fileStorageService;
    private final RaceTrackerProperties properties;

    public MediaServiceImpl(
        RaceMediaRepository mediaRepository,
        RaceRepository raceRepository,
        ParticipantRepository participantRepository,
        UserRepository userRepository,
        FileStorageService fileStorageService,
        RaceTrackerProperties properties
    ) {
        this.mediaRepository = mediaRepository;
        this.raceRepository = raceRepository;
        this.participantRepository = participantRepository;
        this.userRepository = userRepository;
        this.fileStorageService = fileStorageService;
        this.properties = properties;
    }

    @Override
    @Transactional
    public MediaDto upload(UUID raceId, MultipartFile file, UUID participantId, String caption, String actorEmail) {
        Race race = raceRepository.findById(raceId)
            .orElseThrow(() -> new ResourceNotFoundException("Race not found"));

        Participant participant = null;
        if (participantId != null) {
            participant = participantRepository.findByRaceIdAndId(raceId, participantId)
                .orElseThrow(() -> new ResourceNotFoundException("Participant not found"));
        }

        String contentType = file.getContentType() == null ? "" : file.getContentType();
        RaceMedia.MediaType mediaType = contentType.toLowerCase(java.util.Locale.ROOT).startsWith("video/")
            ? RaceMedia.MediaType.VIDEO : RaceMedia.MediaType.IMAGE;
        String key = fileStorageService.store(file, raceId.toString());

        RaceMedia media = RaceMedia.builder()
            .race(race)
            .participant(participant)
            .mediaType(mediaType)
            .storageKey(key)
            .contentType(contentType)
            .caption(caption)
            .status(RaceMedia.MediaStatus.PENDING)
            .uploadedBy(resolveUserId(actorEmail))
            .build();

        return toDto(mediaRepository.save(media));
    }

    @Override
    @Transactional(readOnly = true)
    public List<MediaDto> listForRace(UUID raceId) {
        return mediaRepository.findByRaceIdOrderByCreatedAtDesc(raceId).stream().map(this::toDto).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<MediaDto> listApproved(UUID raceId) {
        return mediaRepository.findByRaceIdAndStatusOrderByCreatedAtDesc(raceId, RaceMedia.MediaStatus.APPROVED)
            .stream().map(this::toDto).toList();
    }

    @Override
    @Transactional
    public MediaDto moderate(UUID raceId, UUID mediaId, boolean approve, String actorEmail) {
        RaceMedia media = mediaRepository.findByRaceIdAndId(raceId, mediaId)
            .orElseThrow(() -> new ResourceNotFoundException("Media not found"));
        media.setStatus(approve ? RaceMedia.MediaStatus.APPROVED : RaceMedia.MediaStatus.REJECTED);
        media.setModeratedBy(resolveUserId(actorEmail));
        media.setModeratedAt(OffsetDateTime.now());
        return toDto(mediaRepository.save(media));
    }

    @Override
    @Transactional
    public void delete(UUID raceId, UUID mediaId) {
        RaceMedia media = mediaRepository.findByRaceIdAndId(raceId, mediaId)
            .orElseThrow(() -> new ResourceNotFoundException("Media not found"));
        fileStorageService.delete(media.getStorageKey());
        mediaRepository.delete(media);
    }

    @Override
    @Transactional(readOnly = true)
    public MediaContent content(UUID raceId, UUID mediaId, boolean publicOnly) {
        RaceMedia media = mediaRepository.findByRaceIdAndId(raceId, mediaId)
            .orElseThrow(() -> new ResourceNotFoundException("Media not found"));
        if (publicOnly && media.getStatus() != RaceMedia.MediaStatus.APPROVED) {
            throw new ResourceNotFoundException("Media not found");
        }
        String contentType = media.getContentType() == null ? "application/octet-stream" : media.getContentType();
        return new MediaContent(fileStorageService.load(media.getStorageKey()), contentType);
    }

    private UUID resolveUserId(String actorEmail) {
        if (actorEmail == null) {
            return null;
        }
        return userRepository.findByEmailIgnoreCase(actorEmail).map(user -> user.getId()).orElse(null);
    }

    private MediaDto toDto(RaceMedia media) {
        MediaDto dto = new MediaDto();
        dto.setId(media.getId());
        dto.setRaceId(media.getRace() != null ? media.getRace().getId() : null);
        dto.setParticipantId(media.getParticipant() != null ? media.getParticipant().getId() : null);
        dto.setMediaType(media.getMediaType() != null ? media.getMediaType().name() : null);
        dto.setUrl(publicUrl(media));
        dto.setCaption(media.getCaption());
        dto.setStatus(media.getStatus() != null ? media.getStatus().name() : null);
        dto.setCreatedAt(media.getCreatedAt());
        return dto;
    }

    private String publicUrl(RaceMedia media) {
        String version = properties.getApi() != null ? properties.getApi().getVersion() : "1";
        return "/api/v" + version + "/public/races/" + media.getRace().getId()
            + "/media/" + media.getId() + "/content";
    }
}
