package com.joinlivora.backend.payout;

import com.joinlivora.backend.testutil.TestUserFactory;
import com.joinlivora.backend.user.Role;
import com.joinlivora.backend.user.User;
import com.joinlivora.backend.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@ActiveProfiles("test")
class CreatorEarningsInvoiceRepositoryTest {

    @Autowired
    private CreatorEarningsInvoiceRepository repository;

    @Autowired
    private UserRepository userRepository;

    @Test
    void testSaveAndFindCreatorEarningsInvoice() {
        User creator = TestUserFactory.createCreator("creator@example.com");
        creator = userRepository.save(creator);

        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
        CreatorEarningsInvoice invoice = CreatorEarningsInvoice.builder()
                .creator(creator)
                .invoiceNumber("INV-001")
                .periodStart(now.minus(30, ChronoUnit.DAYS))
                .periodEnd(now)
                .grossEarnings(new BigDecimal("1000.00"))
                .platformFee(new BigDecimal("200.00"))
                .netEarnings(new BigDecimal("800.00"))
                .currency("EUR")
                .status(CreatorEarningsInvoiceStatus.GENERATED)
                .build();

        CreatorEarningsInvoice saved = repository.save(invoice);
        assertNotNull(saved.getId());

        Optional<CreatorEarningsInvoice> found = repository.findById(saved.getId());
        assertTrue(found.isPresent());
        assertEquals(creator.getId(), found.get().getCreator().getId());
        assertEquals(CreatorEarningsInvoiceStatus.GENERATED, found.get().getStatus());
        
        // Use compareTo for BigDecimal comparison to avoid scale issues
        assertEquals(0, new BigDecimal("1000.00").compareTo(found.get().getGrossEarnings()));
        assertEquals(0, new BigDecimal("200.00").compareTo(found.get().getPlatformFee()));
        assertEquals(0, new BigDecimal("800.00").compareTo(found.get().getNetEarnings()));
    }

    @Test
    void testFindByCreatorId() {
        User creator = TestUserFactory.createCreator("creator2@example.com");
        creator = userRepository.save(creator);

        CreatorEarningsInvoice invoice = CreatorEarningsInvoice.builder()
                .creator(creator)
                .invoiceNumber("INV-002")
                .periodStart(Instant.now().minus(7, ChronoUnit.DAYS))
                .periodEnd(Instant.now())
                .grossEarnings(new BigDecimal("500.00"))
                .platformFee(new BigDecimal("100.00"))
                .netEarnings(new BigDecimal("400.00"))
                .currency("EUR")
                .status(CreatorEarningsInvoiceStatus.PAID)
                .build();

        repository.save(invoice);

        List<CreatorEarningsInvoice> found = repository.findByCreatorId(creator.getId());
        assertFalse(found.isEmpty());
        assertEquals(1, found.size());
        assertEquals(creator.getId(), found.get(0).getCreator().getId());
        assertEquals(CreatorEarningsInvoiceStatus.PAID, found.get(0).getStatus());
    }
}








