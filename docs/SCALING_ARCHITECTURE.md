# Livora — Scalable Production Architecture

## Overview

This document describes the multi-node mediasoup architecture designed to support 500–2,000+ concurrent viewers across multiple simultaneous streams.

## Architecture Diagram

```
                    ┌─────────────┐
                    │   Nginx     │
                    │  (Reverse   │
                    │   Proxy)    │
                    └──────┬──────┘
                           │
              ┌────────────┼────────────┐
              │            │            │
       ┌──────┴──────┐    │     ┌──────┴──────┐
       │  Frontend   │    │     │  Frontend   │
       │  (React)    │    │     │  (React)    │
       └──────┬──────┘    │     └──────┬──────┘
              │            │            │
              └────────────┼────────────┘
                           │
                    ┌──────┴──────┐
                    │  Spring     │
                    │  Boot API   │──── RabbitMQ (STOMP)
                    │  + WS       │
                    └──────┬──────┘
                           │
              ┌────────────┼────────────┐
              │            │            │
       ┌──────┴──────┐ ┌──┴───┐ ┌──────┴──────┐
       │ mediasoup-1 │ │Redis │ │ mediasoup-3 │
       │ :4000       │ │      │ │ :4002       │
       │ UDP 40000-  │ └──────┘ │ UDP 40201-  │
       │     40100   │          │     40300   │
       └─────────────┘          └─────────────┘
              │                        │
       ┌──────┴──────┐
       │ mediasoup-2 │
       │ :4001       │       ┌─────────────┐
       │ UDP 40101-  │       │   coturn     │
       │     40200   │       │ (TURN/STUN)  │
       └─────────────┘       └─────────────┘
```

## Service Roles

| Service | Role | Scaling |
|---------|------|---------|
| **Frontend (React)** | SPA served via Nginx | Horizontal via CDN |
| **Backend API (Spring Boot)** | REST API + WebSocket/STOMP | Horizontal (stateless, RabbitMQ relay) |
| **mediasoup-1/2/3** | WebRTC SFU media servers | Horizontal (add more nodes) |
| **Redis** | State store, pub/sub, sticky sessions | Sentinel/Cluster for HA |
| **RabbitMQ** | STOMP message broker relay | Cluster for HA |
| **PostgreSQL** | Persistent data store | Read replicas for scale |
| **coturn** | TURN/STUN for NAT traversal | Horizontal |

## Multi-Node Mediasoup Architecture

### Stream Routing

When a creator goes live:
1. Backend calls `MediasoupNodeRegistry.selectNodeForNewStream(streamId)`
2. Registry selects the **least-loaded healthy node** (by consumer + transport count)
3. Stream→node mapping is stored in Redis: `mediasoup:stream:node:{streamId} → nodeId`
4. All subsequent requests for that stream (transport creation, consume, produce) are routed to the same node

### Sticky Sessions

Once a stream is assigned to a node, **all viewers of that stream connect to the same node**. This is enforced via:
- Redis key: `mediasoup:stream:node:{streamId}` with 12-hour TTL
- `MediasoupClient.getClientForRoom(roomId)` looks up the assignment and returns the correct WebClient

### Health Checking

Every 15 seconds (configurable via `MEDIASOUP_HEALTH_INTERVAL`):
1. Backend pings `/health` on each mediasoup node
2. Response includes: status, workers, rooms, producers, consumers, transports
3. Node stats are published to Redis: `mediasoup:node:stats:{nodeId}`
4. Unhealthy nodes are excluded from new stream assignments
5. Existing streams on unhealthy nodes trigger failover (reassignment to healthy node)

### Failover

If a node goes down:
- Health check marks it unhealthy
- New streams are assigned to remaining healthy nodes
- Existing streams on the dead node: viewers will experience a disconnect; on reconnect, the stream is reassigned to a healthy node
- Stream→node mapping TTL ensures stale entries expire

## Docker Compose Configuration

Three mediasoup instances with non-overlapping UDP port ranges:

| Node | HTTP Port | RTC UDP Range |
|------|-----------|---------------|
| mediasoup-1 | 4000 | 40000-40100 |
| mediasoup-2 | 4001 | 40101-40200 |
| mediasoup-3 | 4002 | 40201-40300 |

Each node gets 2 CPU cores and 1GB RAM. Adjust based on load.

## Capacity Planning

### Per mediasoup node (2 CPU cores):
- ~200-300 concurrent consumers (viewers)
- ~10-20 concurrent producers (streamers)
- ~50-100 rooms

### With 3 nodes:
- ~600-900 concurrent viewers
- ~30-60 concurrent streams
- Headroom for spikes

### Scaling beyond:
- Add more nodes to `MEDIASOUP_NODES` env var
- Assign non-overlapping RTC port ranges
- No code changes needed

## Rate Limiting

### Mediasoup Level
- **Max viewers per stream**: 500 (configurable via `MEDIASOUP_MAX_VIEWERS_PER_STREAM`)
- **API rate limit**: 100 requests/minute per IP (internal requests exempt)

### Backend Level
- Rate limits by role: anonymous (30), authenticated (200), creator (1000), admin (5000)
- WebSocket: max 10 sessions per IP, 5000 global sessions, 20 handshakes/minute

## Monitoring

### Prometheus Metrics (per node)
Each mediasoup node exposes `/metrics` in Prometheus format:
- `mediasoup_workers_total`
- `mediasoup_rooms_total`
- `mediasoup_producers_total`
- `mediasoup_consumers_total`
- `mediasoup_transports_total`
- `mediasoup_uptime_seconds`

### Cluster Stats API
Backend exposes aggregated cluster stats via `MediasoupClient.getClusterStats()`:
- Total/healthy nodes
- Aggregated consumers, producers, transports, rooms
- Per-node details

## Environment Variables

### Required
| Variable | Description | Example |
|----------|-------------|---------|
| `MEDIASOUP_AUTH_TOKEN` | Shared secret for node auth | `strong-random-token` |
| `MEDIASOUP_ANNOUNCED_IP` | Public IP for ICE candidates | `203.0.113.1` |

### Optional (with defaults)
| Variable | Default | Description |
|----------|---------|-------------|
| `MEDIASOUP_NODES` | *(empty)* | Comma-separated `nodeId:url` pairs |
| `MEDIASOUP_BASE_URL` | `http://localhost:4000` | Single-node fallback URL |
| `MEDIASOUP_HEALTH_INTERVAL` | `15000` | Health check interval (ms) |
| `MEDIASOUP_MAX_VIEWERS_PER_STREAM` | `500` | Max consumers per room |

## WebSocket Scaling Readiness

The backend WebSocket layer is already stateless:
- STOMP broker relay delegates to RabbitMQ (external)
- No in-memory WebSocket session state (sessions tracked in Redis)
- Multiple backend instances can run behind a load balancer
- RabbitMQ handles message routing across backend instances

## Next Steps

1. **Kubernetes**: Deploy with HPA (Horizontal Pod Autoscaler) for mediasoup nodes
2. **Global Edge**: CDN for frontend + geo-distributed TURN servers
3. **Recording**: Pipe mediasoup producers to recording service
4. **Auto-scaling**: Scale mediasoup nodes based on consumer count metrics
