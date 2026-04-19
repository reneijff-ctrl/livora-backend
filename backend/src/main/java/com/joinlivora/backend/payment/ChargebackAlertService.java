package com.joinlivora.backend.payment;

import com.joinlivora.backend.chargeback.model.ChargebackCase;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service("chargebackAlertService")
@RequiredArgsConstructor
@Slf4j
public class ChargebackAlertService {

    private final MeterRegistry meterRegistry;

    public void alert(ChargebackCase chargeback, int clusterSize) {
        log.warn("SECURITY ALERT: Chargeback received. ID: {}, UserID: {}, Amount: {} {}, Cluster Size: {}",
                chargeback.getId(),
                chargeback.getUserId(),
                chargeback.getAmount(),
                chargeback.getCurrency(),
                clusterSize);

        meterRegistry.counter("chargebacks_total", 
                "type", String.valueOf(chargeback.getReason()),
                "currency", chargeback.getCurrency()
        ).increment();

        if (clusterSize > 1) {
            log.warn("SECURITY ALERT: Correlated chargeback cluster detected for creator {}. Cluster size: {}",
                    chargeback.getUserId(), clusterSize);
            
            meterRegistry.counter("correlated_chargebacks_total",
                    "cluster_size", String.valueOf(clusterSize)
            ).increment();
        }
    }
}
