package com.joinlivora.backend.payout;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.YearMonth;

@Component("creatorEarningsInvoiceScheduler")
@RequiredArgsConstructor
@Slf4j
public class CreatorEarningsInvoiceScheduler {

    private final CreatorEarningsInvoiceService invoiceService;

    /**
     * Runs on the 1st day of every month at 00:00 to generate invoices for the previous month.
     */
    @Scheduled(cron = "0 0 0 1 * *")
    public void scheduleMonthlyInvoices() {
        log.info("INVOICE_JOB: Starting monthly creator invoice generation");
        YearMonth previousMonth = YearMonth.now().minusMonths(1);
        invoiceService.generateMonthlyInvoices(previousMonth);
        log.info("INVOICE_JOB: Finished monthly creator invoice generation for {}", previousMonth);
    }
}
