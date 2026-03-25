package com.joinlivora.backend.streaming.client;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;
import java.util.Collections;
import java.util.Map;
import java.util.HashMap;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Component
@Slf4j
public class MediasoupClient {

    private final WebClient webClient;

    public MediasoupClient(WebClient mediasoupWebClient) {
        this.webClient = mediasoupWebClient;
        log.info("MediasoupClient initialized with non-blocking WebClient");
    }

    @Async("mediasoupExecutor")
    public CompletableFuture<MediasoupRoomsResponse> getRooms() {
        try {
            MediasoupRoomsResponse result = webClient.get()
                    .uri("/rooms")
                    .retrieve()
                    .bodyToMono(MediasoupRoomsResponse.class)
                    .block();
            return CompletableFuture.completedFuture(result);
        } catch (Exception e) {
            log.error("LIVESTREAM-SFU: Failed to fetch rooms from Mediasoup: {}", e.getMessage());
            return CompletableFuture.completedFuture(new MediasoupRoomsResponse(Collections.emptyList()));
        }
    }

    @Async("mediasoupExecutor")
    public CompletableFuture<MediasoupStatsResponse> getStats() {
        try {
            MediasoupStatsResponse result = webClient.get()
                    .uri("/rooms/stats")
                    .retrieve()
                    .bodyToMono(MediasoupStatsResponse.class)
                    .block();
            return CompletableFuture.completedFuture(result);
        } catch (Exception e) {
            log.error("LIVESTREAM-SFU: Failed to fetch stats from Mediasoup: {}", e.getMessage());
            return CompletableFuture.completedFuture(null);
        }
    }

    @Async("mediasoupExecutor")
    public CompletableFuture<Map<String, Object>> getRouterCapabilities(String roomId) {
        String url = "/rooms/capabilities?roomId=" + roomId;

        log.info("MEDIASOUP REQUEST: getRouterCapabilities for roomId={}", roomId);

        try {
            Object response = webClient.get()
                    .uri(url)
                    .retrieve()
                    .bodyToMono(Object.class)
                    .block();

            log.info("MEDIASOUP RESPONSE: getRouterCapabilities success for roomId={}", roomId);
            return CompletableFuture.completedFuture(toMap(response));
        } catch (Exception e) {
            log.error("MEDIASOUP-SFU ERROR: getRouterCapabilities failed for roomId={}", roomId, e);
            return CompletableFuture.completedFuture(null);
        }
    }

    @Async("mediasoupExecutor")
    public CompletableFuture<Map<String, Object>> getProducers(String roomId) {
        try {
            Object result = webClient.get()
                    .uri("/rooms/" + roomId + "/producers")
                    .retrieve()
                    .bodyToMono(Object.class)
                    .block();
            return CompletableFuture.completedFuture(toMap(result));
        } catch (Exception e) {
            log.error("MEDIASOUP-SFU: Failed to fetch producers for room {}: {}", roomId, e.getMessage());
            return CompletableFuture.completedFuture(null);
        }
    }

    @Async("mediasoupExecutor")
    public CompletableFuture<Map<String, Object>> createTransport(String roomId) {
        try {
            Object result = webClient.post()
                    .uri("/transports")
                    .bodyValue(Map.of("roomId", roomId))
                    .retrieve()
                    .bodyToMono(Object.class)
                    .block();
            return CompletableFuture.completedFuture(toMap(result));
        } catch (Exception e) {
            log.error("MEDIASOUP-SFU: Failed to create transport for room {}: {}", roomId, e.getMessage());
            return CompletableFuture.completedFuture(null);
        }
    }

    @Async("mediasoupExecutor")
    public CompletableFuture<Void> connectTransport(String roomId, String transportId, Object dtlsParameters) {
        try {
            webClient.post()
                    .uri("/connect")
                    .bodyValue(Map.of("roomId", roomId, "transportId", transportId, "dtlsParameters", dtlsParameters))
                    .retrieve()
                    .bodyToMono(Object.class)
                    .block();
        } catch (Exception e) {
            log.error("MEDIASOUP-SFU: Failed to connect transport in room {}: {}", roomId, e.getMessage());
        }
        return CompletableFuture.completedFuture(null);
    }

    @Async("mediasoupExecutor")
    public CompletableFuture<Map<String, Object>> produce(String roomId, Map<String, Object> produceReq) {
        try {
            Map<String, Object> req = new HashMap<>(produceReq);
            req.put("roomId", roomId);
            Object result = webClient.post()
                    .uri("/produce")
                    .bodyValue(req)
                    .retrieve()
                    .bodyToMono(Object.class)
                    .block();
            return CompletableFuture.completedFuture(toMap(result));
        } catch (Exception e) {
            log.error("MEDIASOUP-SFU: Failed to produce in room {}: {}", roomId, e.getMessage());
            return CompletableFuture.completedFuture(null);
        }
    }

    @Async("mediasoupExecutor")
    public CompletableFuture<Map<String, Object>> consume(String roomId, Map<String, Object> consumeReq) {
        try {
            Map<String, Object> req = new HashMap<>(consumeReq);
            req.put("roomId", roomId);
            Object result = webClient.post()
                    .uri("/consume")
                    .bodyValue(req)
                    .retrieve()
                    .bodyToMono(Object.class)
                    .block();
            return CompletableFuture.completedFuture(toMap(result));
        } catch (Exception e) {
            log.error("MEDIASOUP-SFU: Failed to consume in room {}: {}", roomId, e.getMessage());
            return CompletableFuture.completedFuture(null);
        }
    }

    @Async("mediasoupExecutor")
    public CompletableFuture<Void> resumeConsumer(String roomId, String consumerId) {
        try {
            webClient.post()
                    .uri("/consume/resume")
                    .bodyValue(Map.of("roomId", roomId, "consumerId", consumerId))
                    .retrieve()
                    .bodyToMono(Object.class)
                    .block();
        } catch (Exception e) {
            log.error("MEDIASOUP-SFU: Failed to resume consumer in room {}: {}", roomId, e.getMessage());
        }
        return CompletableFuture.completedFuture(null);
    }

    @Async("mediasoupExecutor")
    public CompletableFuture<Map<String, Object>> restartIce(String roomId, String transportId) {
        try {
            Object result = webClient.post()
                    .uri("/transports/restart-ice")
                    .bodyValue(Map.of("roomId", roomId, "transportId", transportId))
                    .retrieve()
                    .bodyToMono(Object.class)
                    .block();
            return CompletableFuture.completedFuture(toMap(result));
        } catch (Exception e) {
            log.error("MEDIASOUP-SFU: Failed to restart ICE for transport {} in room {}: {}", transportId, roomId, e.getMessage());
            return CompletableFuture.completedFuture(null);
        }
    }

    @Async("mediasoupExecutor")
    public CompletableFuture<Void> closeRoom(UUID roomId) {
        if (roomId == null) return CompletableFuture.completedFuture(null);
        String url = "/rooms/" + roomId;
        try {
            log.info("MEDIASOUP-SFU: Attempting to close room {} at URL: {}", roomId, url);
            webClient.delete()
                    .uri(url)
                    .retrieve()
                    .toBodilessEntity()
                    .block();
            log.info("MEDIASOUP-SFU: Successfully sent DELETE request for room {}", roomId);
        } catch (Exception e) {
            log.error("MEDIASOUP-SFU: Failed to close room {}: {}", roomId, e.getMessage());
        }
        return CompletableFuture.completedFuture(null);
    }

    @Async("mediasoupExecutor")
    public CompletableFuture<Void> closeTransport(String roomId, String transportId) {
        if (roomId == null || transportId == null) return CompletableFuture.completedFuture(null);
        String url = "/rooms/" + roomId + "/transports/" + transportId;
        try {
            log.info("MEDIASOUP-SFU: Closing transport {} in room {}", transportId, roomId);
            webClient.delete()
                    .uri(url)
                    .retrieve()
                    .toBodilessEntity()
                    .block();
        } catch (Exception e) {
            log.error("MEDIASOUP-SFU: Failed to close transport {} in room {}: {}", transportId, roomId, e.getMessage());
        }
        return CompletableFuture.completedFuture(null);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> toMap(Object obj) {
        if (obj instanceof Map) {
            return (Map<String, Object>) obj;
        }
        return null;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MediasoupRoomsResponse {
        private List<MediasoupRoom> rooms;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MediasoupStatsResponse {
        private MediasoupGlobalStats global;
        private List<MediasoupWorkerStats> workers;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MediasoupGlobalStats {
        private int routers;
        private int transports;
        private int producers;
        private int consumers;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MediasoupWorkerStats {
        private String workerId;
        private double cpuUsage;
        private double memoryUsage;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MediasoupRoom {
        private String roomId;
        private int producers;
        private int consumers;
        private int transports;
    }
}
