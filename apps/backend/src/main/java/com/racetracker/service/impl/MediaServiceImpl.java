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
import com.racetracker.service.MediaStorage;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class MediaServiceImpl implements MediaService {

    private final RaceMediaRepository mediaRepository;
    private final RaceRepository raceRepository;
    private final ParticipantRepository participantRepository;
    private final UserRepository userRepository;
    private final MediaStorage mediaStorage;
    private final ImageThumbnailService thumbnailService;
    private final RaceTrackerProperties properties;

    public MediaServiceImpl(
        RaceMediaRepository mediaRepository,
        RaceRepository raceRepository,
        ParticipantRepository participantRepository,
        UserRepository userRepository,
        MediaStorage mediaStorage,
        ImageThumbnailService thumbnailService,
        RaceTrackerProperties properties
    ) {
        this.mediaRepository = mediaRepository;
        this.raceRepository = raceRepository;
        this.participantRepository = participantRepository;
        this.userRepository = userRepository;
        this.mediaStorage = mediaStorage;
        this.thumbnailService = thumbnailService;
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
        String key = mediaStorage.store(file, raceId.toString());

        RaceMedia.RaceMediaBuilder builder = RaceMedia.builder()
            .race(race)
            .participant(participant)
            .mediaType(mediaType)
            .storageKey(key)
            .contentType(contentType)
            .caption(caption)
            .status(RaceMedia.MediaStatus.PENDING)
            .uploadedBy(resolveUserId(actorEmail));

        if (mediaType == RaceMedia.MediaType.IMAGE) {
            applyThumbnail(builder, file, raceId);
        }

        return toDto(mediaRepository.save(builder.build()));
    }

    private void applyThumbnail(RaceMedia.RaceMediaBuilder builder, MultipartFile file, UUID raceId) {
        try {
            thumbnailService.generate(file.getBytes()).ifPresent(thumbnail -> {
                String thumbnailKey = mediaStorage.storeBytes(thumbnail.bytes(), ".jpg", raceId.toString());
                builder.thumbnailKey(thumbnailKey);
                builder.width(thumbnail.originalWidth());
                builder.height(thumbnail.originalHeight());
            });
        } catch (IOException ex) {
            // Thumbnail generation is best-effort; keep the original upload.
        }
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
        mediaStorage.delete(media.getStorageKey());
        mediaStorage.delete(media.getThumbnailKey());
        mediaRepository.delete(media);
    }

    @Override
    @Transactional(readOnly = true)
    public MediaContent content(UUID raceId, UUID mediaId, boolean publicOnly) {
        RaceMedia media = requireVisible(raceId, mediaId, publicOnly);
        String contentType = media.getContentType() == null ? "application/octet-stream" : media.getContentType();
        return new MediaContent(mediaStorage.load(media.getStorageKey()), contentType);
    }

    @Override
    @Transactional(readOnly = true)
    public MediaContent thumbnail(UUID raceId, UUID mediaId, boolean publicOnly) {
        RaceMedia media = requireVisible(raceId, mediaId, publicOnly);
        if (media.getThumbnailKey() == null) {
            throw new ResourceNotFoundException("Thumbnail not available");
        }
        return new MediaContent(mediaStorage.load(media.getThumbnailKey()), "image/jpeg");
    }

    private RaceMedia requireVisible(UUID raceId, UUID mediaId, boolean publicOnly) {
        RaceMedia media = mediaRepository.findByRaceIdAndId(raceId, mediaId)
            .orElseThrow(() -> new ResourceNotFoundException("Media not found"));
        if (publicOnly && media.getStatus() != RaceMedia.MediaStatus.APPROVED) {
            throw new ResourceNotFoundException("Media not found");
        }
        return media;
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
        dto.setUrl(publicUrl(media, "content"));
        dto.setThumbnailUrl(media.getThumbnailKey() != null ? publicUrl(media, "thumbnail") : null);
        dto.setWidth(media.getWidth());
        dto.setHeight(media.getHeight());
        dto.setCaption(media.getCaption());
        dto.setStatus(media.getStatus() != null ? media.getStatus().name() : null);
        dto.setCreatedAt(media.getCreatedAt());
        return dto;
    }

    private String publicUrl(RaceMedia media, String variant) {
        String version = properties.getApi() != null ? properties.getApi().getVersion() : "1";
        return "/api/v" + version + "/public/races/" + media.getRace().getId()
            + "/media/" + media.getId() + "/" + variant;
    }
}
