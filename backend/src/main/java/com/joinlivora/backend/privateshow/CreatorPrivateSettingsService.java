package com.joinlivora.backend.privateshow;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CreatorPrivateSettingsService {

    private final CreatorPrivateSettingsRepository repository;

    @Transactional
    public CreatorPrivateSettings getOrCreate(Long creatorId) {
        return repository.findById(creatorId)
                .orElseGet(() -> {
                    CreatorPrivateSettings settings = new CreatorPrivateSettings();
                    settings.setCreatorId(creatorId);
                    settings.setEnabled(false);
                    settings.setPricePerMinute(50L);
                    settings.setAllowSpyOnPrivate(false);
                    settings.setSpyPricePerMinute(25L);
                    settings.setMaxSpyViewers(5);
                    return repository.save(settings);
                });
    }

    @Transactional
    public CreatorPrivateSettings update(Long creatorId, boolean enabled, Long pricePerMinute) {
        CreatorPrivateSettings settings = getOrCreate(creatorId);
        settings.setEnabled(enabled);
        settings.setPricePerMinute(pricePerMinute);
        return repository.save(settings);
    }

    @Transactional
    public CreatorPrivateSettings updateWithSpy(Long creatorId, boolean enabled, Long pricePerMinute,
                                                 boolean allowSpyOnPrivate, Long spyPricePerMinute, Integer maxSpyViewers) {
        CreatorPrivateSettings settings = getOrCreate(creatorId);
        settings.setEnabled(enabled);
        settings.setPricePerMinute(pricePerMinute);
        settings.setAllowSpyOnPrivate(allowSpyOnPrivate);
        settings.setSpyPricePerMinute(spyPricePerMinute != null ? spyPricePerMinute : 25L);
        settings.setMaxSpyViewers(maxSpyViewers);
        return repository.save(settings);
    }
}
