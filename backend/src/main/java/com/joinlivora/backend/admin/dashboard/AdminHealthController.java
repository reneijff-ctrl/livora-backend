package com.joinlivora.backend.admin.dashboard;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.actuate.health.CompositeHealth;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthComponent;
import org.springframework.boot.actuate.health.HealthEndpoint;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminHealthController {

    private final HealthEndpoint healthEndpoint;

    @GetMapping("/health")
    public ResponseEntity<?> health() {
        HealthComponent root = healthEndpoint.health();
        log.info("HEALTH RESPONSE: {}", root);

        Map<String, Object> response = new HashMap<>();
        response.put("status", root.getStatus().getCode());

        if (root instanceof CompositeHealth composite) {
            Map<String, Object> components = new HashMap<>();
            composite.getComponents().forEach((name, component) -> {
                Map<String, Object> componentMap = new HashMap<>();
                componentMap.put("status", component.getStatus().getCode());
                if (component instanceof Health h && !h.getDetails().isEmpty()) {
                    componentMap.put("details", h.getDetails());
                }
                components.put(name, componentMap);
            });
            response.put("components", components);
        }

        return ResponseEntity.ok(response);
    }
}
