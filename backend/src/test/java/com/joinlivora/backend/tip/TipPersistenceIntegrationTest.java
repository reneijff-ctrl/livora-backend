package com.joinlivora.backend.tip;

import com.joinlivora.backend.testutil.TestUserFactory;
import com.joinlivora.backend.user.Role;
import com.joinlivora.backend.user.User;
import com.joinlivora.backend.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class TipPersistenceIntegrationTest {

    @Autowired
    private CreatorTipRepository tipRepository;

    @Autowired
    private UserRepository userRepository;

    @Test
    void testTipPersistence() {
        User fromUser = TestUserFactory.createViewer("tipper@test.com");
        fromUser = userRepository.save(fromUser);

        User creator = TestUserFactory.createCreator("creator@test.com");
        creator = userRepository.save(creator);

        DirectTip tip = DirectTip.builder()
                .user(fromUser)
                .creator(creator)
                .amount(new BigDecimal("15.50"))
                .currency("USD")
                .status(TipStatus.PENDING)
                .build();

        DirectTip savedTip = tipRepository.save(tip);

        assertNotNull(savedTip.getId());
        assertNotNull(savedTip.getCreatedAt());

        Optional<DirectTip> fetchedTip = tipRepository.findById(savedTip.getId());
        assertTrue(fetchedTip.isPresent());
        assertEquals(0, new BigDecimal("15.50").compareTo(fetchedTip.get().getAmount()));
        assertEquals("USD", fetchedTip.get().getCurrency());
        assertEquals(fromUser.getId(), fetchedTip.get().getUser().getId());
        assertEquals(creator.getId(), fetchedTip.get().getCreator().getId());
    }
}








