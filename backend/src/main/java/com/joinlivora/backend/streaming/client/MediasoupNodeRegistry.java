package com.joinlivora.backend.streaming.client;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import jakarta.annotation.PostConstruct;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Registry that manages multiple mediasoup SFU nodes across multiple regions.
 * Handles health checking, region-aware load-based node selection, stream→node sticky sessions via Redis,
 * circuit breaker logic, graceful draining, cross-region failover, and per-region autoscaling.
 */
@Component
@Slf4j
public class MediasoupNodeRegistry {

    private static final String STREAM_NODE_KEY_PREFIX = "mediasoup:stream:node:";
    private static final String NODE_STATS_KEY_PREFIX = "mediasoup:node:stats:";
    private static final String NODE_STREAMS_KEY_PREFIX = "mediasoup:node:streams:";
    private static final String REGION_STREAMS_KEY_PREFIX = "mediasoup:region:streams:";
    private static final Duration STREAM_NODE_TTL = Duration.ofHours(12);

    // Cross-region relay weight penalty (higher = less preferred for cross-region)
    private static final double CROSS_REGION_PENALTY = 100.0;

    // Circuit breaker defaults
    private static final int CIRCUIT_BREAKER_FAILURE_THRESHOLD = 5;
    private static final long CIRCUIT_BREAKER_COOLDOWN_MS = 120_000; // 2 minutes

    // Autoscaling defaults
    private static final double SCALE_UP_CPU_THRESHOLD = 0.70;
    private static final double SCALE_DOWN_CPU_THRESHOLD = 0.30;
    private static final int SCALE_UP_VIEWERS_PER_NODE = 350;
    private static final int SCALE_DOWN_VIEWERS_PER_NODE = 50;
    private static final int MIN_NODES = 1;

    private final StringRedisTemplate redisTemplate;
    private final SimpMessagingTemplate messagingTemplate;
    private final MeterRegistry meterRegistry;
    private final Map<String, NodeInfo> nodes = new ConcurrentHashMap<>();
    private final Map<String, WebClient> nodeClients = new ConcurrentHashMap<>();

    // Circuit breaker state per node
    private final Map<String, Integer> consecutiveFailures = new ConcurrentHashMap<>();
    private final Map<String, Long> circuitOpenUntil = new ConcurrentHashMap<>();

    // Failure metrics
    private final AtomicLong totalNodeFailures = new AtomicLong(0);
    private final AtomicLong totalStreamRestarts = new AtomicLong(0);
    private final AtomicLong totalReconnectAttempts = new AtomicLong(0);
    private final AtomicLong totalSuccessfulRecoveries = new AtomicLong(0);

    // Autoscaling state
    private final AtomicLong scaleUpEvents = new AtomicLong(0);
    private final AtomicLong scaleDownEvents = new AtomicLong(0);

    // Cold start: queue viewers when no healthy nodes available
    private final ConcurrentLinkedQueue<PendingViewer> pendingViewerQueue = new ConcurrentLinkedQueue<>();
    private static final int MAX_PENDING_VIEWERS = 100;
    private static final long PENDING_VIEWER_TTL_MS = 30_000; // 30 seconds

    @Value("${mediasoup.nodes:}")
    private String nodesConfig;

    @Value("${mediasoup.auth-token}")
    private String authToken;

    @Value("${mediasoup.base-url:http://localhost:4000}")
    private String fallbackBaseUrl;

    @Value("${mediasoup.max-viewers-per-stream:500}")
    private int maxViewersPerStream;

    @Value("${mediasoup.heartbeat-interval:5000}")
    private long heartbeatInterval;

    @Value("${mediasoup.failure-threshold-ms:10000}")
    private long failureThresholdMs;

    @Value("${mediasoup.local-region:eu}")
    private String localRegion;

    public MediasoupNodeRegistry(StringRedisTemplate redisTemplate,
                                  @org.springframework.context.annotation.Lazy SimpMessagingTemplate messagingTemplate,
                                  MeterRegistry meterRegistry) {
        this.redisTemplate = redisTemplate;
        this.messagingTemplate = messagingTemplate;
        this.meterRegistry = meterRegistry;
    }

    @PostConstruct
    public void init() {
        if (nodesConfig != null && !nodesConfig.isBlank()) {
            parseNodesConfig(nodesConfig);
        }

        // Fallback: if no nodes configured, register a default single node
        if (nodes.isEmpty()) {
            log.warn("No mediasoup nodes configured via MEDIASOUP_NODES, using fallback: {}", fallbackBaseUrl);
            String nodeId = "default";
            nodes.put(nodeId, new NodeInfo(nodeId, fallbackBaseUrl, true, false, 0, 0, 0, 0, System.currentTimeMillis(), 0, 0.0, localRegion));
            nodeClients.put(nodeId, createWebClient(fallbackBaseUrl));
        }

        log.info("MediasoupNodeRegistry initialized with {} node(s): {}", nodes.size(), nodes.keySet());
        registerMetrics();
    }

    private void registerMetrics() {
        Gauge.builder("mediasoup_cluster_nodes_total", nodes, Map::size)
                .description("Total registered mediasoup nodes").register(meterRegistry);
        Gauge.builder("mediasoup_cluster_nodes_healthy", this, r -> r.getHealthyNodeCount())
                .description("Healthy mediasoup nodes").register(meterRegistry);
        Gauge.builder("mediasoup_cluster_nodes_unhealthy", this,
                r -> r.getAllNodes().size() - r.getHealthyNodeCount())
                .description("Unhealthy mediasoup nodes").register(meterRegistry);
        Gauge.builder("mediasoup_cluster_node_failures_total", totalNodeFailures, AtomicLong::get)
                .description("Total node failure events").register(meterRegistry);
        Gauge.builder("mediasoup_cluster_stream_restarts_total", totalStreamRestarts, AtomicLong::get)
                .description("Total stream restart events").register(meterRegistry);
        Gauge.builder("mediasoup_cluster_reconnect_attempts_total", totalReconnectAttempts, AtomicLong::get)
                .description("Total reconnect attempts").register(meterRegistry);
        Gauge.builder("mediasoup_cluster_successful_recoveries_total", totalSuccessfulRecoveries, AtomicLong::get)
                .description("Total successful recoveries").register(meterRegistry);
        Gauge.builder("mediasoup_cluster_total_consumers", this,
                r -> r.getAllNodes().stream().filter(NodeInfo::isHealthy).mapToInt(NodeInfo::getConsumers).sum())
                .description("Total consumers across cluster").register(meterRegistry);
        Gauge.builder("mediasoup_cluster_total_rooms", this,
                r -> r.getAllNodes().stream().filter(NodeInfo::isHealthy).mapToInt(NodeInfo::getRooms).sum())
                .description("Total rooms across cluster").register(meterRegistry);
        Gauge.builder("mediasoup_cluster_scale_up_events_total", scaleUpEvents, AtomicLong::get)
                .description("Total scale-up recommendations").register(meterRegistry);
        Gauge.builder("mediasoup_cluster_scale_down_events_total", scaleDownEvents, AtomicLong::get)
                .description("Total scale-down recommendations").register(meterRegistry);
        Gauge.builder("mediasoup_cluster_pending_viewers", pendingViewerQueue, ConcurrentLinkedQueue::size)
                .description("Viewers waiting for a node (cold start queue)").register(meterRegistry);
    }

    private void parseNodesConfig(String config) {
        // Format: "nodeId1:url1, nodeId2:url2" (legacy) or "nodeId1@region:url1, nodeId2@region:url2" (region-aware)
        String[] entries = config.split(",");
        for (String entry : entries) {
            String trimmed = entry.trim();
            if (trimmed.isEmpty()) continue;

            int colonIdx = trimmed.indexOf(':');
            if (colonIdx <= 0) {
                log.warn("Invalid mediasoup node config entry (expected nodeId[@region]:url): {}", trimmed);
                continue;
            }

            String nodeIdentifier = trimmed.substring(0, colonIdx).trim();
            String url = trimmed.substring(colonIdx + 1).trim();

            // Parse optional region from nodeId@region format
            String nodeId;
            String region;
            int atIdx = nodeIdentifier.indexOf('@');
            if (atIdx > 0) {
                nodeId = nodeIdentifier.substring(0, atIdx).trim();
                region = nodeIdentifier.substring(atIdx + 1).trim().toLowerCase();
            } else {
                nodeId = nodeIdentifier;
                region = localRegion; // default to local region
            }

            nodes.put(nodeId, new NodeInfo(nodeId, url, false, false, 0, 0, 0, 0, 0, 0, 0.0, region));
            nodeClients.put(nodeId, createWebClient(url));
            log.info("Registered mediasoup node: {} -> {} (region: {})", nodeId, url, region);
        }
    }

    private WebClient createWebClient(String baseUrl) {
        HttpClient httpClient = HttpClient.create()
                .responseTimeout(Duration.ofSeconds(3));

        return WebClient.builder()
                .baseUrl(baseUrl)
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .defaultHeader("Authorization", "Bearer " + authToken)
                .build();
    }

    // ── Health Check (runs every 5 seconds by default) ──

    @Scheduled(fixedDelayString = "${mediasoup.heartbeat-interval:5000}")
    public void healthCheckAll() {
        long now = System.currentTimeMillis();

        for (Map.Entry<String, NodeInfo> entry : nodes.entrySet()) {
            String nodeId = entry.getKey();
            NodeInfo info = entry.getValue();
            WebClient client = nodeClients.get(nodeId);

            if (client == null) continue;

            // Circuit breaker: skip nodes in cooldown
            if (isCircuitOpen(nodeId)) {
                long remaining = circuitOpenUntil.getOrDefault(nodeId, 0L) - now;
                if (remaining > 0) {
                    log.debug("Node {} circuit breaker open, skipping health check ({} ms remaining)", nodeId, remaining);
                    continue;
                }
                // Cooldown expired — allow a probe
                log.info("Node {} circuit breaker cooldown expired, probing health", nodeId);
            }

            try {
                Map<String, Object> health = client.get()
                        .uri("/health")
                        .retrieve()
                        .bodyToMono(Map.class)
                        .block(Duration.ofSeconds(3));

                if (health != null && ("ok".equals(health.get("status")) || "draining".equals(health.get("status")))) {
                    boolean wasUnhealthy = !info.isHealthy();
                    info.setHealthy(true);
                    info.setDraining("draining".equals(health.get("status")) || Boolean.TRUE.equals(health.get("draining")));
                    info.setConsumers(toInt(health.get("consumers")));
                    info.setProducers(toInt(health.get("producers")));
                    info.setTransports(toInt(health.get("transports")));
                    info.setRooms(toInt(health.get("rooms")));
                    info.setLastHealthCheck(now);

                    // Reset circuit breaker on success
                    consecutiveFailures.put(nodeId, 0);
                    circuitOpenUntil.remove(nodeId);

                    if (wasUnhealthy) {
                        log.info("Mediasoup node {} recovered and is now HEALTHY", nodeId);
                        totalSuccessfulRecoveries.incrementAndGet();
                    }

                    // Publish stats to Redis for cross-backend-instance visibility
                    publishNodeStats(nodeId, info);
                } else {
                    handleHealthCheckFailure(nodeId, "Unexpected health response", now);
                }
            } catch (Exception e) {
                handleHealthCheckFailure(nodeId, e.getMessage(), now);
            }
        }

        // Check for nodes that missed heartbeats (stale lastHealthCheck)
        detectStaleNodes(now);
    }

    private void handleHealthCheckFailure(String nodeId, String reason, long now) {
        int failures = consecutiveFailures.merge(nodeId, 1, Integer::sum);
        NodeInfo info = nodes.get(nodeId);
        if (info == null) return;

        boolean wasHealthy = info.isHealthy();
        info.setHealthy(false);

        if (wasHealthy) {
            totalNodeFailures.incrementAndGet();
            log.error("Mediasoup node {} is DOWN (failure #{}/{}): {}",
                    nodeId, failures, CIRCUIT_BREAKER_FAILURE_THRESHOLD, reason);
            // Trigger failover for all streams on this node
            handleNodeFailure(nodeId);
        }

        // Circuit breaker: if too many consecutive failures, disable node for cooldown period
        if (failures >= CIRCUIT_BREAKER_FAILURE_THRESHOLD) {
            long cooldownUntil = now + CIRCUIT_BREAKER_COOLDOWN_MS;
            circuitOpenUntil.put(nodeId, cooldownUntil);
            log.error("Circuit breaker OPEN for node {} after {} consecutive failures. Disabled for {} seconds.",
                    nodeId, failures, CIRCUIT_BREAKER_COOLDOWN_MS / 1000);
        }

        // Publish unhealthy status to Redis
        String statsKey = NODE_STATS_KEY_PREFIX + nodeId;
        redisTemplate.opsForHash().put(statsKey, "healthy", "false");
        redisTemplate.expire(statsKey, 60, TimeUnit.SECONDS);
    }

    /**
     * Detects nodes whose last successful health check is older than the failure threshold.
     * This catches nodes that silently stop responding (no exception thrown, just no response).
     */
    private void detectStaleNodes(long now) {
        for (Map.Entry<String, NodeInfo> entry : nodes.entrySet()) {
            NodeInfo info = entry.getValue();
            if (info.isHealthy() && info.getLastHealthCheck() > 0) {
                long staleness = now - info.getLastHealthCheck();
                if (staleness > failureThresholdMs) {
                    log.warn("Node {} has stale health check ({}ms old, threshold: {}ms), marking unhealthy",
                            entry.getKey(), staleness, failureThresholdMs);
                    handleHealthCheckFailure(entry.getKey(), "Stale health check (" + staleness + "ms)", now);
                }
            }
        }
    }

    private void publishNodeStats(String nodeId, NodeInfo info) {
        String statsKey = NODE_STATS_KEY_PREFIX + nodeId;
        Map<String, String> stats = new HashMap<>();
        stats.put("healthy", "true");
        stats.put("draining", String.valueOf(info.isDraining()));
        stats.put("consumers", String.valueOf(info.getConsumers()));
        stats.put("producers", String.valueOf(info.getProducers()));
        stats.put("transports", String.valueOf(info.getTransports()));
        stats.put("rooms", String.valueOf(info.getRooms()));
        stats.put("timestamp", String.valueOf(System.currentTimeMillis()));
        redisTemplate.opsForHash().putAll(statsKey, stats);
        redisTemplate.expire(statsKey, 60, TimeUnit.SECONDS);
    }

    // ── Node Failure Handling & Stream Failover ──

    /**
     * Handles a node failure: cleans up Redis state and sends STREAM_RESTART_REQUIRED
     * to all viewers/creators of affected streams so they can reconnect to a new node.
     */
    private void handleNodeFailure(String nodeId) {
        log.warn("Handling failure of node {}: cleaning up streams and notifying clients", nodeId);

        // Find all streams assigned to the failed node
        Set<String> affectedStreams = getStreamsOnNode(nodeId);

        for (String streamId : affectedStreams) {
            totalStreamRestarts.incrementAndGet();

            // Remove the stale stream→node assignment so new connections go to a healthy node
            removeStreamAssignment(streamId);

            // Send STREAM_RESTART_REQUIRED event via WebSocket to all stream viewers
            broadcastStreamRestart(streamId, nodeId);
        }

        // Clean up the node's stream set in Redis
        String nodeStreamsKey = NODE_STREAMS_KEY_PREFIX + nodeId;
        redisTemplate.delete(nodeStreamsKey);

        log.warn("Node {} failure handled: {} streams affected, restart events sent", nodeId, affectedStreams.size());
    }

    /**
     * Broadcasts a STREAM_RESTART_REQUIRED event to all viewers/creators of a stream.
     */
    private void broadcastStreamRestart(String streamId, String failedNodeId) {
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("type", "STREAM_RESTART_REQUIRED");
        event.put("streamId", streamId);
        event.put("reason", "Node " + failedNodeId + " failed");
        event.put("timestamp", System.currentTimeMillis());

        try {
            messagingTemplate.convertAndSend(
                    "/exchange/amq.topic/streams.status",
                    event
            );
            log.info("Sent STREAM_RESTART_REQUIRED for stream {} (failed node: {})", streamId, failedNodeId);
        } catch (Exception e) {
            log.error("Failed to send STREAM_RESTART_REQUIRED for stream {}: {}", streamId, e.getMessage());
        }
    }

    // ── Circuit Breaker ──

    private boolean isCircuitOpen(String nodeId) {
        Long openUntil = circuitOpenUntil.get(nodeId);
        return openUntil != null && System.currentTimeMillis() < openUntil;
    }

    // ── Graceful Draining ──

    /**
     * Marks a node as draining: it will not receive new stream assignments,
     * but existing streams continue until they end naturally.
     */
    public void drainNode(String nodeId) {
        NodeInfo info = nodes.get(nodeId);
        if (info == null) {
            log.warn("Cannot drain unknown node: {}", nodeId);
            return;
        }

        info.setDraining(true);
        log.info("Node {} marked as DRAINING — no new streams will be assigned", nodeId);

        // Publish draining status to Redis
        String statsKey = NODE_STATS_KEY_PREFIX + nodeId;
        redisTemplate.opsForHash().put(statsKey, "draining", "true");
    }

    /**
     * Removes draining flag — node accepts new streams again.
     */
    public void undrainNode(String nodeId) {
        NodeInfo info = nodes.get(nodeId);
        if (info == null) return;

        info.setDraining(false);
        log.info("Node {} is no longer draining — accepting new streams", nodeId);

        String statsKey = NODE_STATS_KEY_PREFIX + nodeId;
        redisTemplate.opsForHash().put(statsKey, "draining", "false");
    }

    /**
     * Force-drains a node: marks as draining AND sends STREAM_RESTART_REQUIRED
     * for all active streams, forcing clients to migrate to other nodes immediately.
     */
    public void forceDrainNode(String nodeId) {
        drainNode(nodeId);

        Set<String> streams = getStreamsOnNode(nodeId);
        log.info("Force-draining node {}: migrating {} active streams", nodeId, streams.size());

        for (String streamId : streams) {
            totalStreamRestarts.incrementAndGet();
            removeStreamAssignment(streamId);
            broadcastStreamRestart(streamId, nodeId);
        }

        String nodeStreamsKey = NODE_STREAMS_KEY_PREFIX + nodeId;
        redisTemplate.delete(nodeStreamsKey);
    }

    // ── Node Selection (composite-score-based healthy, non-draining node) ──

    /**
     * Computes a composite load score for a node.
     * Lower score = less loaded = preferred for new streams.
     * Score = (consumers * 2) + transports + (rooms * 3)
     * Weighting: consumers heavily (viewer impact), rooms next (resource impact), transports least.
     */
    private double computeNodeScore(NodeInfo node) {
        return (node.getConsumers() * 2.0) + node.getTransports() + (node.getRooms() * 3.0);
    }

    /**
     * Selects the best mediasoup node for a new stream.
     * Uses composite-score strategy among healthy, non-draining nodes.
     * Prefers nodes in the specified region; falls back to any region with a penalty.
     * Returns null and queues viewer if no healthy nodes available (cold start).
     */
    public String selectNodeForNewStream(String streamId) {
        return selectNodeForNewStream(streamId, null);
    }

    /**
     * Region-aware node selection. If preferredRegion is null, uses localRegion.
     * Nodes in the preferred region get lower scores; cross-region nodes get a penalty.
     */
    public String selectNodeForNewStream(String streamId, String preferredRegion) {
        // Check if stream already has a sticky assignment
        String existingNode = getNodeForStream(streamId);
        if (existingNode != null && isNodeHealthy(existingNode) && !isNodeDraining(existingNode)) {
            log.debug("Stream {} already assigned to node {}", streamId, existingNode);
            return existingNode;
        }

        String targetRegion = (preferredRegion != null && !preferredRegion.isBlank())
                ? preferredRegion.toLowerCase() : localRegion;

        // Select lowest-scored healthy, non-draining node with region preference
        String selectedNode = nodes.values().stream()
                .filter(n -> n.isHealthy() && !n.isDraining() && !isCircuitOpen(n.getNodeId()))
                .min(Comparator.comparingDouble(n -> computeRegionAwareScore(n, targetRegion)))
                .map(NodeInfo::getNodeId)
                .orElse(null);

        if (selectedNode == null) {
            log.error("No healthy non-draining mediasoup nodes available!");
            // Last resort: try any healthy node (even draining)
            selectedNode = nodes.values().stream()
                    .filter(NodeInfo::isHealthy)
                    .min(Comparator.comparingDouble(n -> computeRegionAwareScore(n, targetRegion)))
                    .map(NodeInfo::getNodeId)
                    .orElse(null);
        }

        if (selectedNode == null) {
            // Cold start: no healthy nodes — queue the viewer
            log.warn("No healthy nodes for stream {}. Queuing viewer (cold start).", streamId);
            queuePendingViewer(streamId);
            return null;
        }

        assignStreamToNode(streamId, selectedNode);
        NodeInfo selected = nodes.get(selectedNode);
        log.info("Assigned stream {} to mediasoup node {} in region {} (score: {})", streamId, selectedNode,
                selected != null ? selected.getRegion() : "unknown",
                String.format("%.1f", selected != null ? computeRegionAwareScore(selected, targetRegion) : 0));

        return selectedNode;
    }

    /**
     * Score with cross-region penalty. Same-region nodes get base score;
     * cross-region nodes get base score + CROSS_REGION_PENALTY.
     */
    private double computeRegionAwareScore(NodeInfo node, String targetRegion) {
        double baseScore = computeNodeScore(node);
        if (targetRegion != null && !targetRegion.equalsIgnoreCase(node.getRegion())) {
            return baseScore + CROSS_REGION_PENALTY;
        }
        return baseScore;
    }

    /**
     * Gets the node assigned to a stream (sticky session lookup).
     */
    public String getNodeForStream(String streamId) {
        String key = STREAM_NODE_KEY_PREFIX + streamId;
        return redisTemplate.opsForValue().get(key);
    }

    /**
     * Assigns a stream to a specific node (sticky session).
     * Also tracks the stream in the node's stream set for failover.
     */
    public void assignStreamToNode(String streamId, String nodeId) {
        String key = STREAM_NODE_KEY_PREFIX + streamId;
        redisTemplate.opsForValue().set(key, nodeId, STREAM_NODE_TTL);

        // Track stream in node's stream set (for fast lookup on node failure)
        String nodeStreamsKey = NODE_STREAMS_KEY_PREFIX + nodeId;
        redisTemplate.opsForSet().add(nodeStreamsKey, streamId);
        redisTemplate.expire(nodeStreamsKey, STREAM_NODE_TTL);
    }

    /**
     * Removes the stream→node mapping (when stream ends).
     */
    public void removeStreamAssignment(String streamId) {
        String key = STREAM_NODE_KEY_PREFIX + streamId;
        String nodeId = redisTemplate.opsForValue().get(key);
        redisTemplate.delete(key);

        // Also remove from the node's stream set
        if (nodeId != null) {
            String nodeStreamsKey = NODE_STREAMS_KEY_PREFIX + nodeId;
            redisTemplate.opsForSet().remove(nodeStreamsKey, streamId);
        }

        log.debug("Removed stream→node assignment for stream {}", streamId);
    }

    /**
     * Returns all stream IDs currently assigned to a given node.
     */
    public Set<String> getStreamsOnNode(String nodeId) {
        String nodeStreamsKey = NODE_STREAMS_KEY_PREFIX + nodeId;
        Set<String> streams = redisTemplate.opsForSet().members(nodeStreamsKey);
        return streams != null ? streams : Collections.emptySet();
    }

    // ── WebClient Access ──

    /**
     * Returns the WebClient for the node assigned to a given stream.
     * Falls back to any healthy node if the assigned node is down.
     */
    public WebClient getClientForStream(String streamId) {
        String nodeId = getNodeForStream(streamId);

        if (nodeId != null && isNodeHealthy(nodeId)) {
            WebClient client = nodeClients.get(nodeId);
            if (client != null) return client;
        }

        // Fallback: reassign to a healthy node
        if (nodeId != null) {
            log.warn("Assigned node {} for stream {} is unhealthy, reassigning", nodeId, streamId);
        }

        String newNode = selectNodeForNewStream(streamId);
        if (newNode != null) {
            return nodeClients.get(newNode);
        }

        // Absolute fallback
        return nodeClients.values().stream().findFirst().orElse(null);
    }

    /**
     * Returns the WebClient for a specific node by ID.
     */
    public WebClient getClientForNode(String nodeId) {
        return nodeClients.get(nodeId);
    }

    // ── Query Methods ──

    public boolean isNodeHealthy(String nodeId) {
        NodeInfo info = nodes.get(nodeId);
        return info != null && info.isHealthy();
    }

    public boolean isNodeDraining(String nodeId) {
        NodeInfo info = nodes.get(nodeId);
        return info != null && info.isDraining();
    }

    public int getMaxViewersPerStream() {
        return maxViewersPerStream;
    }

    public List<NodeInfo> getAllNodes() {
        return new ArrayList<>(nodes.values());
    }

    public List<NodeInfo> getHealthyNodes() {
        return nodes.values().stream()
                .filter(NodeInfo::isHealthy)
                .toList();
    }

    public int getHealthyNodeCount() {
        return (int) nodes.values().stream().filter(NodeInfo::isHealthy).count();
    }

    // ── Dynamic Node Registration ──

    /**
     * Registers a new mediasoup node dynamically at runtime.
     * Used for auto-scaled nodes that self-register on startup.
     */
    public synchronized void registerNode(String nodeId, String url, int capacity) {
        registerNode(nodeId, url, capacity, localRegion);
    }

    /**
     * Region-aware dynamic node registration.
     */
    public synchronized void registerNode(String nodeId, String url, int capacity, String region) {
        if (nodes.containsKey(nodeId)) {
            log.info("Node {} already registered, updating URL: {}", nodeId, url);
        }
        String nodeRegion = (region != null && !region.isBlank()) ? region.toLowerCase() : localRegion;
        NodeInfo info = new NodeInfo(nodeId, url, true, false, 0, 0, 0, 0, System.currentTimeMillis(), capacity, 0.0, nodeRegion);
        nodes.put(nodeId, info);
        nodeClients.put(nodeId, createWebClient(url));
        consecutiveFailures.put(nodeId, 0);
        circuitOpenUntil.remove(nodeId);
        log.info("Dynamically registered mediasoup node: {} at {} (region: {}, capacity: {})", nodeId, url, nodeRegion, capacity);

        // Process any pending viewers waiting for a node
        drainPendingViewerQueue();
    }

    /**
     * Deregisters a mediasoup node, cleaning up all associated state.
     * Should be preceded by drainNode() for graceful removal.
     */
    public synchronized void deregisterNode(String nodeId) {
        NodeInfo removed = nodes.remove(nodeId);
        nodeClients.remove(nodeId);
        consecutiveFailures.remove(nodeId);
        circuitOpenUntil.remove(nodeId);

        if (removed != null) {
            log.info("Deregistered mediasoup node: {} (was healthy: {}, consumers: {})",
                    nodeId, removed.isHealthy(), removed.getConsumers());
            // Clean up Redis
            redisTemplate.delete(NODE_STATS_KEY_PREFIX + nodeId);
            redisTemplate.delete(NODE_STREAMS_KEY_PREFIX + nodeId);
        }
    }

    // ── Cold Start: Pending Viewer Queue ──

    private void queuePendingViewer(String streamId) {
        // Evict expired entries
        long now = System.currentTimeMillis();
        pendingViewerQueue.removeIf(pv -> now - pv.timestamp > PENDING_VIEWER_TTL_MS);

        if (pendingViewerQueue.size() >= MAX_PENDING_VIEWERS) {
            log.warn("Pending viewer queue full ({}), dropping oldest", MAX_PENDING_VIEWERS);
            pendingViewerQueue.poll();
        }

        pendingViewerQueue.offer(new PendingViewer(streamId, now));
    }

    private void drainPendingViewerQueue() {
        long now = System.currentTimeMillis();
        PendingViewer pv;
        int processed = 0;
        while ((pv = pendingViewerQueue.poll()) != null) {
            if (now - pv.timestamp > PENDING_VIEWER_TTL_MS) continue; // expired
            String node = nodes.values().stream()
                    .filter(n -> n.isHealthy() && !n.isDraining())
                    .min(Comparator.comparingDouble(this::computeNodeScore))
                    .map(NodeInfo::getNodeId)
                    .orElse(null);
            if (node == null) break; // still no healthy nodes
            assignStreamToNode(pv.streamId, node);
            processed++;
        }
        if (processed > 0) {
            log.info("Drained {} pending viewers from cold start queue", processed);
        }
    }

    // ── Autoscaling Evaluation ──

    /**
     * Evaluates cluster load per region and returns scaling recommendations.
     * Called periodically (every 30 seconds) alongside health checks.
     */
    @Scheduled(fixedDelayString = "${mediasoup.autoscale-interval:30000}")
    public void evaluateAutoscaling() {
        List<NodeInfo> healthyNodes = getHealthyNodes();
        if (healthyNodes.isEmpty()) {
            log.warn("Autoscale: No healthy nodes — SCALE_UP recommended");
            publishScalingEvent("SCALE_UP", "No healthy nodes available", 0, 0, "global");
            scaleUpEvents.incrementAndGet();
            return;
        }

        // Evaluate per-region
        Map<String, List<NodeInfo>> nodesByRegion = new LinkedHashMap<>();
        for (NodeInfo node : healthyNodes) {
            nodesByRegion.computeIfAbsent(node.getRegion(), k -> new ArrayList<>()).add(node);
        }

        for (Map.Entry<String, List<NodeInfo>> regionEntry : nodesByRegion.entrySet()) {
            String region = regionEntry.getKey();
            List<NodeInfo> regionNodes = regionEntry.getValue();
            evaluateRegionAutoscaling(region, regionNodes);
        }

        // Also evaluate global totals
        int totalViewers = healthyNodes.stream().mapToInt(NodeInfo::getConsumers).sum();
        int nodeCount = healthyNodes.size();
        double avgViewersPerNode = (double) totalViewers / nodeCount;

        // Check scale-up condition
        boolean anyOverloaded = healthyNodes.stream()
                .anyMatch(n -> n.getConsumers() > SCALE_UP_VIEWERS_PER_NODE);

        if (anyOverloaded || avgViewersPerNode > SCALE_UP_VIEWERS_PER_NODE) {
            log.warn("Autoscale: SCALE_UP — avgViewers={}, overloaded={}", avgViewersPerNode, anyOverloaded);
            publishScalingEvent("SCALE_UP",
                    String.format("Avg viewers/node=%.0f, threshold=%d", avgViewersPerNode, SCALE_UP_VIEWERS_PER_NODE),
                    nodeCount, totalViewers, "global");
            scaleUpEvents.incrementAndGet();
            return;
        }

        // Check scale-down condition (only if more than MIN_NODES)
        if (nodeCount > MIN_NODES && avgViewersPerNode < SCALE_DOWN_VIEWERS_PER_NODE) {
            // Don't scale down if any node is draining (already shrinking)
            boolean anyDraining = healthyNodes.stream().anyMatch(NodeInfo::isDraining);
            if (!anyDraining) {
                log.info("Autoscale: SCALE_DOWN — avgViewers={}, nodes={}", avgViewersPerNode, nodeCount);
                publishScalingEvent("SCALE_DOWN",
                        String.format("Avg viewers/node=%.0f, threshold=%d", avgViewersPerNode, SCALE_DOWN_VIEWERS_PER_NODE),
                        nodeCount, totalViewers, "global");
                scaleDownEvents.incrementAndGet();
                return;
            }
        }

        log.debug("Autoscale: STABLE — {} nodes, {} total viewers, avg {}",
                nodeCount, totalViewers, String.format("%.0f", avgViewersPerNode));
    }

    /**
     * Evaluates autoscaling for a single region.
     */
    private void evaluateRegionAutoscaling(String region, List<NodeInfo> regionNodes) {
        int regionViewers = regionNodes.stream().mapToInt(NodeInfo::getConsumers).sum();
        int regionNodeCount = regionNodes.size();
        double avgViewers = (double) regionViewers / regionNodeCount;

        boolean anyOverloaded = regionNodes.stream()
                .anyMatch(n -> n.getConsumers() > SCALE_UP_VIEWERS_PER_NODE);

        if (anyOverloaded || avgViewers > SCALE_UP_VIEWERS_PER_NODE) {
            log.warn("Autoscale [{}]: SCALE_UP — {} nodes, {} viewers, avg={}",
                    region, regionNodeCount, regionViewers, String.format("%.0f", avgViewers));
            publishScalingEvent("SCALE_UP",
                    String.format("Region %s: avg viewers/node=%.0f, threshold=%d", region, avgViewers, SCALE_UP_VIEWERS_PER_NODE),
                    regionNodeCount, regionViewers, region);
        } else if (regionNodeCount > MIN_NODES && avgViewers < SCALE_DOWN_VIEWERS_PER_NODE) {
            boolean allLow = regionNodes.stream().allMatch(n -> n.getConsumers() < SCALE_DOWN_VIEWERS_PER_NODE);
            if (allLow) {
                log.info("Autoscale [{}]: SCALE_DOWN — {} nodes, {} viewers, avg={}",
                        region, regionNodeCount, regionViewers, String.format("%.0f", avgViewers));
                publishScalingEvent("SCALE_DOWN",
                        String.format("Region %s: avg viewers/node=%.0f, threshold=%d", region, avgViewers, SCALE_DOWN_VIEWERS_PER_NODE),
                        regionNodeCount, regionViewers, region);
            }
        } else {
            log.debug("Autoscale [{}]: STABLE — {} nodes, {} viewers", region, regionNodeCount, regionViewers);
        }
    }

    private void publishScalingEvent(String action, String reason, int currentNodes, int totalViewers, String region) {
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("type", "AUTOSCALE_" + action);
        event.put("reason", reason);
        event.put("region", region);
        event.put("currentNodes", currentNodes);
        event.put("totalViewers", totalViewers);
        event.put("timestamp", System.currentTimeMillis());

        // Publish to Redis for external autoscaler to consume
        try {
            redisTemplate.convertAndSend("mediasoup:autoscale", event.toString());
        } catch (Exception e) {
            log.error("Failed to publish autoscale event: {}", e.getMessage());
        }
    }

    // ── Cost Tracking ──

    /**
     * Returns cost metrics per node.
     * costPerHour should be configured per node (defaults to 0 if not set).
     */
    public Map<String, Object> getCostMetrics() {
        Map<String, Object> result = new LinkedHashMap<>();
        double totalCost = 0;
        int totalViewers = 0;

        List<Map<String, Object>> perNode = new ArrayList<>();
        for (NodeInfo node : nodes.values()) {
            Map<String, Object> nm = new LinkedHashMap<>();
            nm.put("nodeId", node.getNodeId());
            nm.put("costPerHour", node.getCostPerHour());
            nm.put("viewers", node.getConsumers());
            double costPerViewer = node.getConsumers() > 0
                    ? node.getCostPerHour() / node.getConsumers()
                    : node.getCostPerHour(); // full cost if idle
            nm.put("costPerViewer", costPerViewer);
            nm.put("healthy", node.isHealthy());
            perNode.add(nm);

            totalCost += node.getCostPerHour();
            totalViewers += node.getConsumers();
        }

        result.put("totalCostPerHour", totalCost);
        result.put("totalViewers", totalViewers);
        result.put("avgCostPerViewer", totalViewers > 0 ? totalCost / totalViewers : 0);
        result.put("nodes", perNode);
        return result;
    }

    // ── Cluster Stats & Failure Metrics ──

    public Map<String, Object> getClusterStats() {
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("localRegion", localRegion);
        stats.put("totalNodes", nodes.size());
        stats.put("healthyNodes", getHealthyNodeCount());
        stats.put("maxViewersPerStream", maxViewersPerStream);

        int totalConsumers = 0;
        int totalProducers = 0;
        int totalTransports = 0;
        int totalRooms = 0;

        // Per-region aggregates
        Map<String, Map<String, Object>> regionStats = new LinkedHashMap<>();

        List<Map<String, Object>> nodeDetails = new ArrayList<>();
        for (NodeInfo node : nodes.values()) {
            Map<String, Object> detail = new LinkedHashMap<>();
            detail.put("nodeId", node.getNodeId());
            detail.put("url", node.getUrl());
            detail.put("region", node.getRegion());
            detail.put("healthy", node.isHealthy());
            detail.put("draining", node.isDraining());
            detail.put("consumers", node.getConsumers());
            detail.put("producers", node.getProducers());
            detail.put("transports", node.getTransports());
            detail.put("rooms", node.getRooms());
            detail.put("lastHealthCheck", node.getLastHealthCheck());
            detail.put("consecutiveFailures", consecutiveFailures.getOrDefault(node.getNodeId(), 0));
            detail.put("circuitBreakerOpen", isCircuitOpen(node.getNodeId()));
            nodeDetails.add(detail);

            if (node.isHealthy()) {
                totalConsumers += node.getConsumers();
                totalProducers += node.getProducers();
                totalTransports += node.getTransports();
                totalRooms += node.getRooms();

                // Aggregate per region
                Map<String, Object> rs = regionStats.computeIfAbsent(node.getRegion(), k -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("region", k);
                    m.put("nodes", 0);
                    m.put("healthyNodes", 0);
                    m.put("consumers", 0);
                    m.put("producers", 0);
                    m.put("rooms", 0);
                    return m;
                });
                rs.put("healthyNodes", (int) rs.get("healthyNodes") + 1);
                rs.put("consumers", (int) rs.get("consumers") + node.getConsumers());
                rs.put("producers", (int) rs.get("producers") + node.getProducers());
                rs.put("rooms", (int) rs.get("rooms") + node.getRooms());
            }

            // Count total nodes per region (including unhealthy)
            Map<String, Object> rs = regionStats.computeIfAbsent(node.getRegion(), k -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("region", k);
                m.put("nodes", 0);
                m.put("healthyNodes", 0);
                m.put("consumers", 0);
                m.put("producers", 0);
                m.put("rooms", 0);
                return m;
            });
            rs.put("nodes", (int) rs.get("nodes") + 1);
        }

        stats.put("totalConsumers", totalConsumers);
        stats.put("totalProducers", totalProducers);
        stats.put("totalTransports", totalTransports);
        stats.put("totalRooms", totalRooms);
        stats.put("nodes", nodeDetails);
        stats.put("regions", new ArrayList<>(regionStats.values()));

        // Failure metrics
        Map<String, Object> failureMetrics = new LinkedHashMap<>();
        failureMetrics.put("totalNodeFailures", totalNodeFailures.get());
        failureMetrics.put("totalStreamRestarts", totalStreamRestarts.get());
        failureMetrics.put("totalReconnectAttempts", totalReconnectAttempts.get());
        failureMetrics.put("totalSuccessfulRecoveries", totalSuccessfulRecoveries.get());
        stats.put("failureMetrics", failureMetrics);

        return stats;
    }

    /**
     * Called by the frontend/signaling layer to record a reconnect attempt.
     */
    public void recordReconnectAttempt() {
        totalReconnectAttempts.incrementAndGet();
    }

    /**
     * Called when a viewer successfully recovers after a stream restart.
     */
    public void recordSuccessfulRecovery() {
        totalSuccessfulRecoveries.incrementAndGet();
    }

    private int toInt(Object obj) {
        if (obj instanceof Number) return ((Number) obj).intValue();
        if (obj instanceof String) {
            try { return Integer.parseInt((String) obj); } catch (NumberFormatException e) { return 0; }
        }
        return 0;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class NodeInfo {
        private String nodeId;
        private String url;
        private boolean healthy;
        private boolean draining;
        private int consumers;
        private int producers;
        private int transports;
        private int rooms;
        private long lastHealthCheck;
        private int capacity; // max viewers this node can handle (0 = unlimited/default)
        private double costPerHour; // cost per hour for this node (for cost tracking)
        private String region; // geographic region (e.g., "eu", "us", "asia")
    }

    // ── Region Utilities ──

    /**
     * Returns the local region this backend instance is configured for.
     */
    public String getLocalRegion() {
        return localRegion;
    }

    /**
     * Returns all unique regions in the cluster.
     */
    public Set<String> getAllRegions() {
        Set<String> regions = new LinkedHashSet<>();
        for (NodeInfo node : nodes.values()) {
            if (node.getRegion() != null) {
                regions.add(node.getRegion());
            }
        }
        return regions;
    }

    /**
     * Returns healthy nodes in a specific region.
     */
    public List<NodeInfo> getHealthyNodesInRegion(String region) {
        return nodes.values().stream()
                .filter(n -> n.isHealthy() && region.equalsIgnoreCase(n.getRegion()))
                .toList();
    }

    /**
     * Returns all nodes in a specific region.
     */
    public List<NodeInfo> getNodesInRegion(String region) {
        return nodes.values().stream()
                .filter(n -> region.equalsIgnoreCase(n.getRegion()))
                .toList();
    }

    /**
     * Checks if a region has at least one healthy node.
     */
    public boolean isRegionHealthy(String region) {
        return nodes.values().stream()
                .anyMatch(n -> n.isHealthy() && region.equalsIgnoreCase(n.getRegion()));
    }

    /**
     * Returns per-region stats summary for monitoring.
     */
    public Map<String, Map<String, Object>> getPerRegionStats() {
        Map<String, Map<String, Object>> regionMap = new LinkedHashMap<>();
        for (NodeInfo node : nodes.values()) {
            String region = node.getRegion() != null ? node.getRegion() : "unknown";
            Map<String, Object> rs = regionMap.computeIfAbsent(region, k -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("totalNodes", 0);
                m.put("healthyNodes", 0);
                m.put("consumers", 0);
                m.put("producers", 0);
                m.put("rooms", 0);
                return m;
            });
            rs.put("totalNodes", (int) rs.get("totalNodes") + 1);
            if (node.isHealthy()) {
                rs.put("healthyNodes", (int) rs.get("healthyNodes") + 1);
                rs.put("consumers", (int) rs.get("consumers") + node.getConsumers());
                rs.put("producers", (int) rs.get("producers") + node.getProducers());
                rs.put("rooms", (int) rs.get("rooms") + node.getRooms());
            }
        }
        return regionMap;
    }

    /**
     * Represents a viewer waiting for a healthy node (cold start queue).
     */
    @Data
    @AllArgsConstructor
    public static class PendingViewer {
        private String streamId;
        private long timestamp;
    }
}
