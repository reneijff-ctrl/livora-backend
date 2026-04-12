# Capacity Planning — Mediasoup Cluster

## Node Capacity Estimates

Based on mediasoup benchmarks and typical live streaming workloads:

| Config | Viewers per Node | Notes |
|--------|-----------------|-------|
| 2 CPU cores, 1GB RAM | ~200-300 viewers | Default docker-compose config |
| 4 CPU cores, 2GB RAM | ~400-600 viewers | Recommended production |
| 8 CPU cores, 4GB RAM | ~800-1200 viewers | High-capacity node |

### Assumptions
- 1 stream = 1 video producer (720p/1080p VP8) + N consumers
- Each consumer uses ~1-3 Mbps outbound bandwidth
- mediasoup workers = CPU cores (1 worker per core)
- Each worker handles ~100-150 consumers before CPU saturation

## Scaling Formula

```
required_nodes = ceil(total_expected_viewers / viewers_per_node)
```

### Examples

| Scenario | Viewers | Node Config | Nodes Required |
|----------|---------|-------------|----------------|
| Single creator, small audience | 50 | 2 CPU | 1 |
| Multiple creators, medium | 500 | 2 CPU | 2-3 |
| Popular creator event | 1,000 | 4 CPU | 2-3 |
| Platform-wide peak | 2,000 | 4 CPU | 4-5 |
| Large scale | 5,000 | 8 CPU | 5-7 |

## Autoscaling Thresholds

Configured in `MediasoupNodeRegistry.java`:

| Parameter | Value | Description |
|-----------|-------|-------------|
| `SCALE_UP_VIEWERS_PER_NODE` | 350 | Trigger scale-up when any node exceeds this |
| `SCALE_DOWN_VIEWERS_PER_NODE` | 50 | Trigger scale-down when avg below this |
| `MIN_NODES` | 1 | Never scale below this |
| `autoscale-interval` | 30s | How often autoscale evaluation runs |

## Bandwidth Planning

```
bandwidth_per_node = viewers_per_node × bitrate_per_viewer
```

| Quality | Bitrate | 300 viewers | 500 viewers |
|---------|---------|-------------|-------------|
| 720p | 1.5 Mbps | 450 Mbps | 750 Mbps |
| 1080p | 3.0 Mbps | 900 Mbps | 1.5 Gbps |

**Ensure each node has sufficient network bandwidth.**

## TURN Server Bandwidth

- ~15-20% of users may use TURN relay
- TURN bandwidth = viewers × 0.15 × bitrate × 2 (bidirectional)
- For 1000 viewers at 1080p: ~900 Mbps TURN bandwidth

## Monitoring Thresholds

| Metric | Warning | Critical |
|--------|---------|----------|
| Worker CPU rate | > 800 ms/s (80%) | > 950 ms/s (95%) |
| Node memory RSS | > 900 MB | > 950 MB (OOM risk) |
| Active viewers/node | > 400 | > 480 |
| Outbound bitrate/node | > 500 Mbps | > 800 Mbps |
| Reconnect failure rate | > 5% | > 15% |

## Cost Optimization

- Use spot/preemptible instances for non-primary nodes
- Scale down aggressively during off-peak hours
- Monitor `cost_per_viewer` metric via `/api/admin/mediasoup/costs`
- Target: < $0.01/viewer/hour for cost efficiency

## Load Testing Recommendations

1. Use `mediasoup-demo` or custom client to simulate viewers
2. Start with 50 viewers, increase by 50 every 2 minutes
3. Monitor via Grafana dashboards:
   - Worker CPU rate should stay < 800 ms/s
   - Memory should stay < 80% of container limit
   - No ICE failures or consumer errors
4. Record the breaking point → that's your actual `viewers_per_node`
5. Set `SCALE_UP_VIEWERS_PER_NODE` to 70% of breaking point
