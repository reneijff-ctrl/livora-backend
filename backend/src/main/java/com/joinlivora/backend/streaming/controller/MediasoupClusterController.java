package com.joinlivora.backend.streaming.controller;

import com.joinlivora.backend.streaming.client.MediasoupNodeRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * REST controller for mediasoup cluster management.
 * Provides dynamic node registration, cluster stats, cost metrics, and autoscaling triggers.
 * All endpoints require ADMIN role.
 */
@RestController
@RequestMapping("/api/admin/mediasoup")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
@Slf4j
public class MediasoupClusterController {

    private final MediasoupNodeRegistry nodeRegistry;

    /**
     * POST /api/admin/mediasoup/register
     * Dynamic node registration — called by auto-scaled nodes on startup.
     */
    @PostMapping("/register")
    public ResponseEntity<Map<String, Object>> registerNode(@RequestBody Map<String, Object> body) {
        String nodeId = (String) body.get("nodeId");
        String url = (String) body.get("url");
        int capacity = body.containsKey("capacity") ? ((Number) body.get("capacity")).intValue() : 0;
        String region = body.containsKey("region") ? (String) body.get("region") : null;

        if (nodeId == null || url == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "nodeId and url are required"));
        }

        nodeRegistry.registerNode(nodeId, url, capacity, region);
        log.info("Node registered via API: {} at {} (region: {}, capacity: {})", nodeId, url, region, capacity);

        return ResponseEntity.ok(Map.of(
                "status", "registered",
                "nodeId", nodeId,
                "url", url,
                "region", region != null ? region : nodeRegistry.getLocalRegion(),
                "capacity", capacity
        ));
    }

    /**
     * DELETE /api/admin/mediasoup/deregister/{nodeId}
     * Deregisters a node from the cluster.
     */
    @DeleteMapping("/deregister/{nodeId}")
    public ResponseEntity<Map<String, Object>> deregisterNode(@PathVariable String nodeId) {
        nodeRegistry.deregisterNode(nodeId);
        return ResponseEntity.ok(Map.of("status", "deregistered", "nodeId", nodeId));
    }

    /**
     * POST /api/admin/mediasoup/drain/{nodeId}
     * Marks a node as draining — no new streams assigned.
     */
    @PostMapping("/drain/{nodeId}")
    public ResponseEntity<Map<String, Object>> drainNode(@PathVariable String nodeId) {
        nodeRegistry.drainNode(nodeId);
        return ResponseEntity.ok(Map.of("status", "draining", "nodeId", nodeId));
    }

    /**
     * POST /api/admin/mediasoup/undrain/{nodeId}
     * Removes draining flag — node accepts new streams again.
     */
    @PostMapping("/undrain/{nodeId}")
    public ResponseEntity<Map<String, Object>> undrainNode(@PathVariable String nodeId) {
        nodeRegistry.undrainNode(nodeId);
        return ResponseEntity.ok(Map.of("status", "active", "nodeId", nodeId));
    }

    /**
     * POST /api/admin/mediasoup/force-drain/{nodeId}
     * Force-drains: marks as draining AND sends STREAM_RESTART_REQUIRED for all streams.
     */
    @PostMapping("/force-drain/{nodeId}")
    public ResponseEntity<Map<String, Object>> forceDrainNode(@PathVariable String nodeId) {
        nodeRegistry.forceDrainNode(nodeId);
        return ResponseEntity.ok(Map.of("status", "force-draining", "nodeId", nodeId));
    }

    /**
     * GET /api/admin/mediasoup/stats
     * Returns full cluster stats including per-node details and failure metrics.
     */
    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getClusterStats() {
        return ResponseEntity.ok(nodeRegistry.getClusterStats());
    }

    /**
     * GET /api/admin/mediasoup/costs
     * Returns cost metrics per node and cluster-wide cost/viewer ratio.
     */
    @GetMapping("/costs")
    public ResponseEntity<Map<String, Object>> getCostMetrics() {
        return ResponseEntity.ok(nodeRegistry.getCostMetrics());
    }

    /**
     * GET /api/admin/mediasoup/nodes
     * Returns list of all registered nodes with health status.
     */
    @GetMapping("/nodes")
    public ResponseEntity<?> getAllNodes() {
        return ResponseEntity.ok(nodeRegistry.getAllNodes());
    }

    /**
     * GET /api/admin/mediasoup/regions
     * Returns per-region stats for the cluster.
     */
    @GetMapping("/regions")
    public ResponseEntity<?> getRegionStats() {
        return ResponseEntity.ok(Map.of(
                "localRegion", nodeRegistry.getLocalRegion(),
                "regions", nodeRegistry.getPerRegionStats(),
                "allRegions", nodeRegistry.getAllRegions()
        ));
    }

    /**
     * GET /api/admin/mediasoup/regions/{region}/nodes
     * Returns all nodes in a specific region.
     */
    @GetMapping("/regions/{region}/nodes")
    public ResponseEntity<?> getNodesInRegion(@PathVariable String region) {
        return ResponseEntity.ok(Map.of(
                "region", region,
                "healthy", nodeRegistry.isRegionHealthy(region),
                "nodes", nodeRegistry.getNodesInRegion(region)
        ));
    }
}
