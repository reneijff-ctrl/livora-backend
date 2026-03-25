package com.joinlivora.backend.monetization;

import com.joinlivora.backend.testutil.TestUserFactory;
import com.joinlivora.backend.user.Role;
import com.joinlivora.backend.user.User;
import com.joinlivora.backend.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

@DataJpaTest
@ActiveProfiles("test")
class PpvPurchaseRepositoryTest {

    @Autowired
    private PpvPurchaseRepository ppvPurchaseRepository;

    @Autowired
    private PpvContentRepository ppvContentRepository;

    @Autowired
    private UserRepository userRepository;

    @Test
    void testFindTopContentByCreator() {
        User creator = TestUserFactory.createCreator("creator@example.com");
        userRepository.save(creator);

        User user = TestUserFactory.createViewer("viewer@example.com");
        userRepository.save(user);

        PpvContent content1 = PpvContent.builder()
                .creator(creator)
                .title("Content 1")
                .price(new BigDecimal("10.00"))
                .currency("EUR")
                .contentUrl("url1")
                .active(true)
                .build();
        ppvContentRepository.save(content1);

        PpvContent content2 = PpvContent.builder()
                .creator(creator)
                .title("Content 2")
                .price(new BigDecimal("20.00"))
                .currency("EUR")
                .contentUrl("url2")
                .active(true)
                .build();
        ppvContentRepository.save(content2);

        // Purchases for content 1
        ppvPurchaseRepository.save(PpvPurchase.builder()
                .ppvContent(content1)
                .user(user)
                .amount(new BigDecimal("10.00"))
                .status(PpvPurchaseStatus.PAID)
                .build());

        // Purchases for content 2
        ppvPurchaseRepository.save(PpvPurchase.builder()
                .ppvContent(content2)
                .user(user)
                .amount(new BigDecimal("20.00"))
                .status(PpvPurchaseStatus.PAID)
                .build());
        ppvPurchaseRepository.save(PpvPurchase.builder()
                .ppvContent(content2)
                .user(user)
                .amount(new BigDecimal("20.00"))
                .status(PpvPurchaseStatus.PAID)
                .build());

        List<Object[]> results = ppvPurchaseRepository.findTopContentByCreator(creator, PageRequest.of(0, 10));

        assertEquals(2, results.size());
        assertEquals("Content 2", results.get(0)[1]);
        assertEquals(0, new BigDecimal("40.00").compareTo((BigDecimal) results.get(0)[2]));
        assertEquals("Content 1", results.get(1)[1]);
        assertEquals(0, new BigDecimal("10.00").compareTo((BigDecimal) results.get(1)[2]));
    }

    @Test
    void testFindByClientRequestId() {
        User user = TestUserFactory.createViewer("request@example.com");
        userRepository.save(user);

        User creator = TestUserFactory.createCreator("creator2@example.com");
        userRepository.save(creator);

        PpvContent content = PpvContent.builder()
                .creator(creator)
                .title("Some Content")
                .price(BigDecimal.TEN)
                .currency("USD")
                .contentUrl("some-url")
                .active(true)
                .build();
        ppvContentRepository.save(content);

        String requestId = UUID.randomUUID().toString();
        PpvPurchase purchase = PpvPurchase.builder()
                .ppvContent(content)
                .user(user)
                .amount(BigDecimal.TEN)
                .status(PpvPurchaseStatus.PAID)
                .clientRequestId(requestId)
                .build();
        ppvPurchaseRepository.save(purchase);

        java.util.Optional<PpvPurchase> found = ppvPurchaseRepository.findByClientRequestId(requestId);
        org.junit.jupiter.api.Assertions.assertTrue(found.isPresent());
        assertEquals(requestId, found.get().getClientRequestId());
    }
}








