package com.joinlivora.backend.creator.service;

import com.joinlivora.backend.user.Role;
import com.joinlivora.backend.user.User;
import com.joinlivora.backend.user.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
@Slf4j
public class CreatorProfileInitializer {

    private final UserRepository userRepository;
    private final CreatorProfileService creatorProfileService;

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void initialize() {
        log.info("Starting CreatorProfile consistency check...");
        
        int page = 0;
        int size = 100;

        Page<User> creatorsPage;
        do {
            creatorsPage = userRepository.findAllByRole(Role.CREATOR, PageRequest.of(page, size));
            for (User user : creatorsPage.getContent()) {
                creatorProfileService.initializeCreatorProfile(user);
            }
            page++;
        } while (creatorsPage.hasNext());

        log.info("Consistency check finished.");
    }
}
