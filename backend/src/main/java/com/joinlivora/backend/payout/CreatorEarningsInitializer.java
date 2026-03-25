package com.joinlivora.backend.payout;

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
public class CreatorEarningsInitializer {

    private final UserRepository userRepository;
    private final PayoutCreatorEarningsRepository payoutCreatorEarningsRepository;

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void initializeExistingCreators() {
        log.info("Starting CreatorEarnings initialization for existing creators...");
        
        int page = 0;
        int size = 100;
        long count = 0;

        Page<User> creatorsPage;
        do {
            creatorsPage = userRepository.findAllByRole(Role.CREATOR, PageRequest.of(page, size));
            for (User creator : creatorsPage.getContent()) {
                if (payoutCreatorEarningsRepository.findByCreator(creator).isEmpty()) {
                    CreatorEarnings earnings = CreatorEarnings.builder()
                            .creator(creator)
                            .build();
                    payoutCreatorEarningsRepository.save(earnings);
                    count++;
                }
            }
            page++;
        } while (creatorsPage.hasNext());

        if (count > 0) {
            log.info("Initialized CreatorEarnings for {} existing creators.", count);
        } else {
            log.info("All creators already have CreatorEarnings entities.");
        }
    }
}
