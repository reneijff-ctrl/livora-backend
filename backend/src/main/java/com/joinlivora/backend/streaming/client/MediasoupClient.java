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

/**
 * Client for communicating with mediasoup SFU nodes.
 * Supports multi-node routing via MediasoupNodeRegistry.
 * Falls back to single-node WebClient if registry is not available.
 */
@Component
@Slf4j
public class MediasoupClient {

    private final WebClient webClient;
    private final MediasoupNodeRegistry nodeRegistry;

    public MediasoupClient(WebClient mediasoupWebClient, MediasoupNodeRegistry nodeRegistry) {
        this.webClient = mediasoupWebClient;
        this.nodeRegistry = nodeRegistry;
        log.info("MediasoupClient initialized with multi-node support");
    }

    /**
     * Returns the appropriate WebClient for a given roomId (stream).
     * Uses node registry for stream→node sticky routing.
     * Falls back to default WebClient if registry returns null.
     */
    private WebClient getClientForRoom(String roomId) {
        if (nodeRegistry != null && roomId != null) {
            WebClient nodeClient = nodeRegistry.getClientForStream(roomId);
            if (nodeClient != null) {
                return nodeClient;
            }
        }
        return webClient;
    }

    /**
     * Ensures a stream is assigned to a mediasoup node.
     * Should be called when a creator goes live.
     */
    public String assignStreamToNode(String streamId) {
        if (nodeRegistry != null) {
            return nodeRegistry.selectNodeForNewStream(streamId);
        }
        return "default";
    }

    /**
     * Removes stream→node assignment when a stream ends.
     */
    public void releaseStream(String streamId) {
        if (nodeRegistry != null) {
            nodeRegistry.removeStreamAssignment(streamId);
        }
    }

    @Async("mediasoupExecutor")
    public CompletableFuture<MediasoupRoomsResponse> getRooms() {
        try {
            // Aggregate rooms from all healthy nodes
            if (nodeRegistry != null) {
                List<MediasoupRoom> allRooms = new java.util.ArrayList<>();
                for (MediasoupNodeRegistry.NodeInfo node : nodeRegistry.getHealthyNodes()) {
                    WebClient client = nodeRegistry.getClientForNode(node.getNodeId());
                    if (client == null) continue;
                    try {
                        MediasoupRoomsResponse resp = client.get()
                                .uri("/rooms")
                                .retrieve()
                                .bodyToMono(MediasoupRoomsResponse.class)
                                .block();
                        if (resp != null && resp.getRooms() != null) {
                            allRooms.addAll(resp.getRooms());
                        }
                    } catch (Exception e) {
                        log.warn("Failed to fetch rooms from node {}: {}", node.getNodeId(), e.getMessage());
                    }
                }
                return CompletableFuture.completedFuture(new MediasoupRoomsResponse(allRooms));
            }

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
            // Aggregate stats from all healthy nodes
            if (nodeRegistry != null) {
                int totalRouters = 0, totalTransports = 0, totalProducers = 0, totalConsumers = 0;
                List<MediasoupWorkerStats> allWorkers = new java.util.ArrayList<>();

                for (MediasoupNodeRegistry.NodeInfo node : nodeRegistry.getHealthyNodes()) {
                    WebClient client = nodeRegistry.getClientForNode(node.getNodeId());
                    if (client == null) continue;
                    try {
                        MediasoupStatsResponse resp = client.get()
                                .uri("/rooms/stats")
                                .retrieve()
                                .bodyToMono(MediasoupStatsResponse.class)
                                .block();
                        if (resp != null) {
                            if (resp.getGlobal() != null) {
                                totalRouters += resp.getGlobal().getRouters();
                                totalTransports += resp.getGlobal().getTransports();
                                totalProducers += resp.getGlobal().getProducers();
                                totalConsumers += resp.getGlobal().getConsumers();
                            }
                            if (resp.getWorkers() != null) {
                                allWorkers.addAll(resp.getWorkers());
                            }
                        }
                    } catch (Exception e) {
                        log.warn("Failed to fetch stats from node {}: {}", node.getNodeId(), e.getMessage());
                    }
                }

                MediasoupGlobalStats globalStats = new MediasoupGlobalStats(totalRouters, totalTransports, totalProducers, totalConsumers);
                return CompletableFuture.completedFuture(new MediasoupStatsResponse(globalStats, allWorkers));
            }

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
            Object response = getClientForRoom(roomId).get()
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
            Object result = getClientForRoom(roomId).get()
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
            Object result = getClientForRoom(roomId).post()
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
            getClientForRoom(roomId).post()
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
            Object result = getClientForRoom(roomId).post()
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
            Object result = getClientForRoom(roomId).post()
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
            getClientForRoom(roomId).post()
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
            Object result = getClientForRoom(roomId).post()
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
        String roomIdStr = roomId.toString();
        String url = "/rooms/" + roomIdStr;
        try {
            log.info("MEDIASOUP-SFU: Attempting to close room {} at URL: {}", roomId, url);
            getClientForRoom(roomIdStr).delete()
                    .uri(url)
                    .retrieve()
                    .toBodilessEntity()
                    .block();
            log.info("MEDIASOUP-SFU: Successfully sent DELETE request for room {}", roomId);

            // Release the stream→node assignment
            releaseStream(roomIdStr);
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
            getClientForRoom(roomId).delete()
                    .uri(url)
                    .retrieve()
                    .toBodilessEntity()
                    .block();
        } catch (Exception e) {
            log.error("MEDIASOUP-SFU: Failed to close transport {} in room {}: {}", transportId, roomId, e.getMessage());
        }
        return CompletableFuture.completedFuture(null);
    }

    /**
     * Returns cluster-wide stats from the node registry.
     */
    public Map<String, Object> getClusterStats() {
        if (nodeRegistry != null) {
            return nodeRegistry.getClusterStats();
        }
        return Map.of("totalNodes", 1, "healthyNodes", 1);
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
