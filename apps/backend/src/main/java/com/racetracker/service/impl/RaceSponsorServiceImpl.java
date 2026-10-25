package com.racetracker.service.impl;

import com.racetracker.dto.race.RaceSponsorDto;
import com.racetracker.dto.race.RaceSponsorRequest;
import com.racetracker.exception.ResourceNotFoundException;
import com.racetracker.model.RaceSponsor;
import com.racetracker.repository.RaceRepository;
import com.racetracker.repository.RaceSponsorRepository;
import com.racetracker.service.RaceSponsorService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class RaceSponsorServiceImpl implements RaceSponsorService {

    private final RaceRepository raceRepository;
    private final RaceSponsorRepository sponsorRepository;

    public RaceSponsorServiceImpl(RaceRepository raceRepository, RaceSponsorRepository sponsorRepository) {
        this.raceRepository = raceRepository;
        this.sponsorRepository = sponsorRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public List<RaceSponsorDto> list(UUID raceId) {
        requireRace(raceId);
        return sponsorRepository.findByRaceIdOrderByDisplayOrderAsc(raceId).stream().map(this::toDto).toList();
    }

    @Override
    @Transactional
    public RaceSponsorDto create(UUID raceId, RaceSponsorRequest request) {
        var race = requireRace(raceId);
        RaceSponsor sponsor = new RaceSponsor();
        sponsor.setRace(race);
        apply(sponsor, request);
        return toDto(sponsorRepository.save(sponsor));
    }

    @Override
    @Transactional
    public RaceSponsorDto update(UUID raceId, UUID sponsorId, RaceSponsorRequest request) {
        RaceSponsor sponsor = sponsorRepository.findByRaceIdAndId(raceId, sponsorId)
            .orElseThrow(() -> new ResourceNotFoundException("Race sponsor not found"));
        apply(sponsor, request);
        return toDto(sponsorRepository.save(sponsor));
    }

    @Override
    @Transactional
    public void delete(UUID raceId, UUID sponsorId) {
        RaceSponsor sponsor = sponsorRepository.findByRaceIdAndId(raceId, sponsorId)
            .orElseThrow(() -> new ResourceNotFoundException("Race sponsor not found"));
        sponsorRepository.delete(sponsor);
    }

    private void apply(RaceSponsor sponsor, RaceSponsorRequest request) {
        sponsor.setName(request.name().trim());
        sponsor.setLogoUrl(request.logoUrl());
        sponsor.setWebsiteUrl(request.websiteUrl());
        sponsor.setTier(request.tier());
        sponsor.setDisplayOrder(request.displayOrder() == null ? 0 : request.displayOrder());
    }

    private com.racetracker.model.Race requireRace(UUID raceId) {
        return raceRepository.findById(raceId)
            .orElseThrow(() -> new ResourceNotFoundException("Race not found"));
    }

    private RaceSponsorDto toDto(RaceSponsor sponsor) {
        return new RaceSponsorDto(sponsor.getId(), sponsor.getRace().getId(), sponsor.getName(),
            sponsor.getLogoUrl(), sponsor.getWebsiteUrl(), sponsor.getTier(), sponsor.getDisplayOrder());
    }
}
