package com.joinlivora.backend.monetization;

import com.joinlivora.backend.testutil.TestUserFactory;
import com.joinlivora.backend.user.Role;
import com.joinlivora.backend.user.User;
import com.joinlivora.backend.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
class TipRepositoryTest {

    @Autowired
    private TipRepository tipRepository;

    @Autowired
    private UserRepository userRepository;

    private User tipper1;
    private User tipper2;
    private User creator;

    @BeforeEach
    void setUp() {
        tipper1 = userRepository.save(TestUserFactory.createViewer("tipper1@test.com"));
        tipper2 = userRepository.save(TestUserFactory.createViewer("tipper2@test.com"));
        creator = userRepository.save(TestUserFactory.createCreator("creator@test.com"));
    }

    @Test
    void aggregateTips_ShouldReturnCorrectAggregations() {
        Instant now = Instant.now();
        
        // Tipper 1 to Creator: 2 tips
        createTip(tipper1, creator, new BigDecimal("10.00"), now.minus(5, ChronoUnit.DAYS), TipStatus.COMPLETED);
        createTip(tipper1, creator, new BigDecimal("20.00"), now.minus(2, ChronoUnit.DAYS), TipStatus.COMPLETED);
        
        // Tipper 2 to Creator: 1 tip
        createTip(tipper2, creator, new BigDecimal("15.00"), now.minus(1, ChronoUnit.DAYS), TipStatus.COMPLETED);
        
        // Unpaid tip - should be ignored
        createTip(tipper1, creator, new BigDecimal("100.00"), now.minus(1, ChronoUnit.DAYS), TipStatus.PENDING);
        
        // Tip outside window - should be ignored
        createTip(tipper1, creator, new BigDecimal("50.00"), now.minus(40, ChronoUnit.DAYS), TipStatus.COMPLETED);

        Instant since = now.minus(30, ChronoUnit.DAYS);
        List<Object[]> results = tipRepository.aggregateTips(since);

        assertThat(results).hasSize(2);
        
        // Tipper 1 to Creator
        Object[] res1 = results.stream()
                .filter(r -> r[0].equals(tipper1.getId()))
                .findFirst().orElseThrow();
        assertThat(res1[1]).isEqualTo(creator.getId());
        assertThat((BigDecimal) res1[2]).isEqualByComparingTo("30.00");
        assertThat(res1[3]).isEqualTo(2L);
        
        // Tipper 2 to Creator
        Object[] res2 = results.stream()
                .filter(r -> r[0].equals(tipper2.getId()))
                .findFirst().orElseThrow();
        assertThat(res2[1]).isEqualTo(creator.getId());
        assertThat((BigDecimal) res2[2]).isEqualByComparingTo("15.00");
        assertThat(res2[3]).isEqualTo(1L);
    }

    private void createTip(User from, User to, BigDecimal amount, Instant createdAt, TipStatus status) {
        Tip tip = Tip.builder()
                .senderUserId(from)
                .creatorUserId(to)
                .amount(amount)
                .currency("EUR")
                .status(status)
                .build();
        tip.setCreatedAt(createdAt);
        tipRepository.save(tip);
    }
}









