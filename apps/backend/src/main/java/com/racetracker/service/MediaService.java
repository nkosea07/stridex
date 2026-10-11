package com.racetracker.service;

import com.racetracker.dto.media.MediaDto;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

public interface MediaService {

    MediaDto upload(UUID raceId, MultipartFile file, UUID participantId, String caption, String actorEmail);

    List<MediaDto> listForRace(UUID raceId);

    List<MediaDto> listApproved(UUID raceId);

    MediaDto moderate(UUID raceId, UUID mediaId, boolean approve, String actorEmail);

    void delete(UUID raceId, UUID mediaId);

    MediaContent content(UUID raceId, UUID mediaId, boolean publicOnly);

    MediaContent thumbnail(UUID raceId, UUID mediaId, boolean publicOnly);

    record MediaContent(byte[] bytes, String contentType) {}
}
