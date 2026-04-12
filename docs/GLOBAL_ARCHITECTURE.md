# Global Multi-Region Streaming Architecture

## Overview

Livora's globally distributed architecture routes users to the nearest region for minimal latency, survives full region outages via cross-region failover, and scales each region independently.

```
                    ┌─────────────────┐
                    │   Cloudflare    │
                    │  Load Balancer  │
                    │  (GeoDNS/LB)   │
                    └────┬───────┬────┘
                         │       │
              ┌──────────┘       └──────────┐
              ▼                              ▼
    ┌─────────────────┐            ┌─────────────────┐
    │    EU Region     │            │    US Region     │
    │  (Primary)       │            │  (Secondary)     │
    ├─────────────────┤            ├─────────────────┤
    │ Backend (Spring) │            │ Backend (Spring) │
    │ Redis (regional) │            │ Redis (regional) │
    │ RabbitMQ         │            │ RabbitMQ         │
    │ PostgreSQL       │            │ PostgreSQL (R/R) │
    ├─────────────────┤            ├─────────────────┤
    │ mediasoup-eu-1   │            │ mediasoup-us-1   │
    │ mediasoup-eu-2   │            │ mediasoup-us-2   │
    │ mediasoup-eu-3   │            │                  │
    ├─────────────────┤            ├─────────────────┤
    │ coturn-eu        │            │ coturn-us         │
    └─────────────────┘            └─────────────────┘
```

## Deployment

### Single Region (default)
```bash
docker compose up -d
```

### EU Region
```bash
docker compose -f docker-compose.yml -f docker-compose.eu.yml up -d
```

### US Region
```bash
docker compose -f docker-compose.yml -f docker-compose.us.yml up -d
```

## Configuration

### Node Format

Nodes are configured via `MEDIASOUP_NODES` env var:

```
# Legacy (single region — region defaults to MEDIASOUP_LOCAL_REGION):
MEDIASOUP_NODES=node-1:http://node-1:4000,node-2:http://node-2:4001

# Multi-region (nodeId@region:url):
MEDIASOUP_NODES=eu-1@eu:http://eu-ms-1:4000,us-1@us:http://us-ms-1:4000
```

### Key Environment Variables

| Variable | Description | Default |
|----------|-------------|---------|
| `MEDIASOUP_LOCAL_REGION` | Region this backend belongs to | `eu` |
| `MEDIASOUP_NODES` | Comma-separated `nodeId[@region]:url` | empty |
| `MEDIASOUP_REGION` | Region for mediasoup node (metrics label) | `unknown` |
| `EU_PUBLIC_IP` | Public IP of EU server | required |
| `US_PUBLIC_IP` | Public IP of US server | required |

## Geo-based Routing

### How It Works

1. **Cloudflare** (recommended) adds `CF-IPCountry` header with user's country code
2. **nginx** maps country → region using `geo-routing.conf`
3. **nginx** routes to regional backend upstream
4. **Backend** passes `X-User-Region` header to inform stream routing
5. **MediasoupNodeRegistry** selects nodes in the preferred region (cross-region penalty = 100 score points)

### Region Mapping

| Countries | Region |
|-----------|--------|
| US, CA, MX, BR, AR, CO, CL, PE | `us` |
| GB, DE, FR, NL, ES, IT, PL, SE, NO, etc. | `eu` |
| TR, IL, AE, SA, ZA, EG, NG, KE | `eu` |
| JP, KR, AU, IN, SG (future) | `asia` |

### Without Cloudflare

Use GeoDNS (e.g., Route 53 latency-based routing):
```
eu.api.joinlivora.com → EU backend IP
us.api.joinlivora.com → US backend IP
api.joinlivora.com    → latency-based routing
```

## Region-Aware Stream Routing

### Node Selection Algorithm

```
score = (consumers × 2) + transports + (rooms × 3)

if node.region ≠ targetRegion:
    score += 100  (cross-region penalty)
```

- Creator starts stream → assigned to node in creator's region
- Viewer joins stream → sticky to same node (Redis lookup)
- If creator's region has no healthy nodes → falls back to any region

### Sticky Sessions

Stream→node mapping stored in Redis:
```
mediasoup:stream:node:{streamId} → nodeId   (TTL: 12h)
mediasoup:node:streams:{nodeId}  → SET of streamIds
```

## Cross-Region Failover

### Automatic Failover Flow

```
1. EU region goes down (all nodes fail health checks)
2. Backend detects via 5s heartbeat (10s threshold)
3. New streams get assigned to US nodes (cross-region penalty ignored when no local nodes)
4. Existing streams: STREAM_RESTART_REQUIRED broadcast
5. Clients auto-reconnect → routed to US
6. DNS (Cloudflare failover) reroutes new connections to US backend
```

### Recovery

```
1. EU nodes come back online
2. Health checks pass → nodes marked healthy
3. New streams assigned to EU again
4. Existing cross-region streams stay on US until they end
```

## Multi-Region TURN

Each region has its own TURN server:
```
EU: turn-eu.joinlivora.com:3478
US: turn-us.joinlivora.com:3478
```

The nginx config passes regional TURN hints via headers:
```
X-Regional-Turn: turn:turn-eu.joinlivora.com:3478
X-Regional-Turn-Fallback: turn:turn-us.joinlivora.com:3478
```

ICE server priority: local TURN first, then fallback TURN.

## Global Redis Strategy (Option A)

Each region has its own Redis instance:
- **EU Redis**: viewer counts, presence, stream→node mappings for EU
- **US Redis**: same for US
- **No cross-region sync** (streams are region-local)

### What's shared
- PostgreSQL (primary in EU, read replica in US)
- User accounts, creator profiles, token balances → synced via DB replication

### What's regional
- WebSocket presence
- Viewer counts
- Stream→node mappings
- Rate limiting state

## Region-Aware Autoscaling

Each region scales independently:

```
EU load high  → SCALE_UP event for region "eu"
US idle       → SCALE_DOWN event for region "us"
Global        → evaluated separately as aggregate
```

Autoscale events published to Redis `mediasoup:autoscale` channel include `region` field.

## Monitoring

### Global Dashboard

`monitoring/grafana/dashboards/mediasoup-global.json`:
- Total viewers (global)
- Viewers per region (time series)
- Healthy nodes per region
- Traffic distribution pie chart
- Latency per region
- Failover timeline

### Alert Rules

`monitoring/prometheus/alerts/region_alerts.yml`:

| Alert | Severity | Condition |
|-------|----------|-----------|
| RegionNoHealthyNodes | critical | All nodes in a region down |
| RegionAllNodesDraining | warning | All nodes draining |
| RegionHighViewerLoad | warning | >300 viewers/node avg |
| RegionCriticalViewerLoad | critical | >450 viewers/node avg |
| CrossRegionTrafficSpike | critical | Viewers routing cross-region |
| RegionLatencyHigh | warning | p95 health check >1s |
| RegionTrafficImbalance | info | 5x traffic difference between regions |

### Prometheus Metrics

All mediasoup metrics include `region` label:
```
mediasoup_active_viewers{node="eu-ms-1",region="eu"} 150
mediasoup_active_viewers{node="us-ms-1",region="us"} 80
```

Query examples:
```promql
# Viewers per region
sum by (region) (mediasoup_active_viewers)

# Healthy nodes per region
count by (region) (mediasoup_draining == 0)
```

## Edge Caching

Static content (thumbnails, profile images, recordings) cached at nginx level:
```
proxy_cache_path /var/cache/nginx/static ...
```

For production: Use Cloudflare CDN with cache rules:
- `/api/uploads/*` → cache 24h
- `*.jpg, *.png, *.webp` → cache 7d
- API responses → no cache

## Latency Optimization

### ICE Configuration
- Prefer UDP over TCP
- Local TURN server first, fallback TURN second
- STUN always attempted first (TURN = relay fallback)

### WebRTC Transport
- Bitrate limits: 3 Mbps in/out
- ICE restart with jittered delay (3-6s)
- Max 3 restart attempts

## Admin API

### Region Endpoints

```
GET  /api/admin/mediasoup/regions           → per-region stats
GET  /api/admin/mediasoup/regions/{r}/nodes → nodes in region
POST /api/admin/mediasoup/register          → register node (with region)
```

### Registration with Region

```json
POST /api/admin/mediasoup/register
{
  "nodeId": "us-ms-3",
  "url": "http://us-ms-3:4002",
  "capacity": 500,
  "region": "us"
}
```

## Adding a New Region (e.g., Asia)

1. Create `docker-compose.asia.yml` (copy `docker-compose.us.yml`, change region to `asia`)
2. Add country mappings in `nginx/conf.d/geo-routing.conf`
3. Add `backend_asia` upstream in geo-routing.conf
4. Deploy with: `docker compose -f docker-compose.yml -f docker-compose.asia.yml up -d`
5. Register nodes via API or update `MEDIASOUP_NODES` env var
6. Update Cloudflare DNS/LB to include Asia backend

## Firewall Ports

| Port | Protocol | Purpose |
|------|----------|---------|
| 80 | TCP | HTTP → HTTPS redirect |
| 443 | TCP | HTTPS (API + WebSocket) |
| 3478 | TCP+UDP | TURN server |
| 40000-40300 | UDP | WebRTC media (mediasoup) |
| 49152-49200 | UDP | TURN relay |

Open these ports on **each regional server**.
