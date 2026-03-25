package com.joinlivora.backend.creator.monetization;

import com.joinlivora.backend.creator.model.CreatorMonetization;
import com.joinlivora.backend.creator.model.CreatorProfile;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class CreatorMonetizationService {

    private final CreatorMonetizationRepository creatorMonetizationRepository;

    @Transactional
    public CreatorMonetization getOrCreateForCreator(CreatorProfile creator) {
        return creatorMonetizationRepository.findByCreator(creator)
                .orElseGet(() -> createDefaultMonetization(creator));
    }

    @Transactional
    public CreatorMonetization createDefaultMonetization(CreatorProfile creator) {
        log.info("Creating default CreatorMonetization for profile: {}", creator.getUsername());
        CreatorMonetization monetization = CreatorMonetization.builder()
                .creator(creator)
                .build();
        return creatorMonetizationRepository.save(monetization);
    }

    @Transactional
    public CreatorMonetization save(CreatorMonetization monetization) {
        return creatorMonetizationRepository.save(monetization);
    }
}
