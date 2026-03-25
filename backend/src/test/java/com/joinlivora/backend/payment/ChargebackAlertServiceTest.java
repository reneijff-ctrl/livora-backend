package com.joinlivora.backend.payment;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ChargebackAlertServiceTest {

    @Mock
    private MeterRegistry meterRegistry;

    @Mock
    private Counter counter;

    @InjectMocks
    private ChargebackAlertService chargebackAlertService;

    private Chargeback chargeback;

    @BeforeEach
    void setUp() {
        chargeback = Chargeback.builder()
                .id(UUID.randomUUID())
                .userId(new UUID(0L, 1L))
                .amount(new BigDecimal("50.00"))
                .currency("USD")
                .reason("fraudulent")
                .build();

        when(meterRegistry.counter(anyString(), anyString(), anyString(), anyString(), anyString())).thenReturn(counter);
    }

    @Test
    void alert_ShouldIncrementTotalCounter() {
        chargebackAlertService.alert(chargeback, 1);

        verify(meterRegistry).counter("chargebacks_total", "type", "fraudulent", "currency", "USD");
        verify(counter).increment();
    }

    @Test
    void alert_WhenCorrelated_ShouldIncrementBothCounters() {
        when(meterRegistry.counter(eq("correlated_chargebacks_total"), anyString(), anyString())).thenReturn(counter);

        chargebackAlertService.alert(chargeback, 2);

        verify(meterRegistry).counter("chargebacks_total", "type", "fraudulent", "currency", "USD");
        verify(meterRegistry).counter("correlated_chargebacks_total", "cluster_size", "2");
        verify(counter, times(2)).increment();
    }
}








