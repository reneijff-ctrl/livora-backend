# Ultra-Scale Architecture — Livora Platform
## Target: 100k–1M Concurrent Viewers Per Stream

---

## Architecture Overview

```
                        ┌─────────────────────────────────────────────────┐
                        │            Cloudflare Global Network             │
                        │                                                   │
    Viewer              │  ┌──────────────────────────────────────────┐   │
    ─────────────────►  │  │    WAF Worker (waf-rate-limit-worker.js) │   │
                        │  │  • Block scraper UAs                      │   │
                        │  │  • HLS rate limit: 200 seg/min per IP    │   │
                        │  │  • Auth rate limit: 5 req/min per IP     │   │
                        │  │  • Anti-hotlink referer check            │   │
                        │  └────────────────┬─────────────────────────┘   │
                        │                   │                              │
                        │  ┌────────────────▼─────────────────────────┐   │
                        │  │   HLS Token Worker (hls-token-worker.js) │   │
                        │  │  • HMAC-SHA256 token validation          │   │
                        │  │  • IP binding check (anti-leech)         │   │
                        │  │  • Strip ?t= from CDN cache key          │   │
                        │  │  • Segments: cache 3600s immutable       │   │
                        │  │  • Playlists: cache 4s                   │   │
                        │  └────────────────┬─────────────────────────┘   │
                        │                   │ CDN HIT (98%)                │
                        │                   │ CDN MISS (2%)                │
                        └───────────────────┼──────────────────────────────┘
                                            │
                        ┌───────────────────▼──────────────────────────────┐
                        │            Multi-Region Origins                   │
                        │                                                   │
                        │   EU Origin          US Origin                   │
                        │   ─────────          ─────────                  │
                        │   Nginx + HLS        Nginx + HLS                │
                        │   Backend ×3         Backend ×3                 │
                        │   Redis (primary)    Redis (replica)            │
                        │   GPU Worker ×3      GPU Worker ×3              │
                        │   Mediasoup ×3       Mediasoup ×3               │
                        │                                                   │
                        │   S3/R2: streams_data (shared between regions)   │
                        └──────────────────────────────────────────────────┘
```

---

## Component Catalog

### 1. Stream Sharding (`StreamShardingService`)

**Problem**: One viral stream concentrates all Redis writes on a single key, creating a hot slot.

**Solution**: 16 logical shards per stream (`stream:{id}:shard:{0..15}:viewers`).

| Metric | Value |
|---|---|
| Shards per stream | 16 (configurable via `STREAM_SHARD_COUNT`) |
| Viewer shard assignment | `userId % 16` (deterministic, O(1) add/remove) |
| Total viewer count | Cached aggregate (`stream:{id}:viewer-count:total`), refreshed every 5s |
| Redis write distribution | 16× more even per slot |
| Effective hot-key ceiling | ~6,250 writers per slot at 100k viewers |

```yaml
livora:
  streaming:
    shard-count: 16  # increase to 64 for >1M viewers
```

---

### 2. Partitioned Chat (`PartitionedChatService`)

**Problem**: RabbitMQ single-node fan-out saturates at ~50–100k msg/s. At 1M viewers each sending 1 msg/10s → 100k msg/s.

**Solution**: 16 Redis Pub/Sub partitions per stream chat channel.

| Metric | Value |
|---|---|
| Partitions per stream | 16 |
| Theoretical ceiling | 16 × 50k msg/s = 800k msg/s |
| Message routing | Regular messages → sender's partition (`senderId % 16`) |
| Broadcast messages | Creator / moderator / system → all 16 partitions |
| Fallback | If Redis fails → RabbitMQ (existing path) |
| Feature flag | `CHAT_PARTITIONED_ENABLED=false` (opt-in) |

**Viewer subscription model**:
```
Viewer 12345 subscribes to: /topic/chat.{streamId}.part.{12345 % 16}
Creator broadcasts:          all 16 partitions simultaneously
```

---

### 3. HLS CDN Pre-warming (`HlsPreWarmService`)

**Problem**: When a stream starts, the first 1,000+ viewers all cache-miss simultaneously (thundering herd). Each miss hits origin Nginx → disk.

**Solution**: Fire async HTTP requests to the CDN immediately after `StreamStartedEventV2`.

**Pre-warmed assets**:
- `master.m3u8` (1 request)
- `720p.m3u8`, `480p.m3u8`, `360p.m3u8` (3 requests)
- First 3 segments per variant (9 requests) = **13 requests total**

**Result**: CDN is hot before any organic viewer arrives. First 1,000 viewers all get cache hits.

```yaml
livora:
  cdn:
    prewarm-enabled: true
    prewarm-segments: 3
```

---

### 4. Viewer Load Shedding (`ViewerLoadSheddingService`)

**Graceful degradation tiers (checked in order)**:

| Tier | Threshold | Action | Redis key |
|---|---|---|---|
| 0 (soft cap) | 50,000 viewers | WARN log only | — |
| 1 (quality) | 100,000 viewers | Remove 720p from master playlist (-55% bandwidth) | `stream:shed:{id}:quality-reduced` |
| 2 (WebRTC) | 200,000 viewers | Deny new WebRTC consumers; HLS-only for new joins | `stream:shed:{id}:webrtc-denied` |
| 3 (hard cap) | 500,000 viewers | HTTP 503 for all new viewer joins | `stream:shed:{id}:hard-reject` |

All shedding flags have a 2-minute TTL — auto-clear if stream ends unexpectedly.
Recovery is automatic when viewer count drops below the threshold.

```yaml
livora:
  shedding:
    soft-cap:    50000
    quality-cap: 100000
    webrtc-cap:  200000
    hard-cap:    500000
```

---

### 5. Redis Streams Analytics (`StreamAnalyticsService`)

**Problem**: PostgreSQL cannot absorb 50k+ writes/second for viewer join/leave events.

**Solution**: Redis Streams (XADD) for all hot-path events.

| Stream key | Content | Max length |
|---|---|---|
| `analytics:viewer-events` | viewer join/leave | 100k records |
| `analytics:segment-requests` | HLS segment requests | 500k records |
| `analytics:stream-metrics:{id}` | aggregated per-stream metrics (Hash) | TTL 7 days |

**Aggregated metrics per stream**:
```
peak-viewers           → highest simultaneous viewer count
total-joins            → cumulative join events
segment-requests       → cumulative HLS segment requests
estimated-bandwidth-gbps → formula: viewers × (3×0.6 + 1.5×0.3 + 0.8×0.1) / 1000
shedding-tier          → current tier (0–3)
```

---

### 6. IP-Bound HLS Tokens (`HlsTokenService`)

**Problem**: Without IP binding, a viewer can share a valid HLS URL with others (hotlinking, stream rebroadcast via streamlink/youtube-dl).

**Token format (v2)**:
```
{base64url(streamId:expiryEpochSeconds:ipHash8)}_{hex(HMAC-SHA256)}

Example payload before encoding:
  550e8400-e29b-41d4-a716-446655440000:1700000180:a1b2c3d4

ipHash8 = first 8 chars of HMAC-SHA256(clientIp, secret)
```

| Property | Value |
|---|---|
| Token TTL | 180 seconds (3 minutes) |
| IP hash | 8-hex HMAC prefix (32-bit collision space) |
| IP source priority | CF-Connecting-IP → X-Real-IP → X-Forwarded-For |
| Legacy backward compat | Tokens without ipHash still work when `ip-binding-enabled=false` |
| Zero DB calls | All data embedded in token, validated purely via HMAC |

```yaml
hls:
  token:
    ip-binding-enabled: ${HLS_TOKEN_IP_BINDING:false}  # opt-in
    ttl-seconds: 180
```

---

### 7. Edge Rate Limiting (`waf-rate-limit-worker.js`)

Deployed as a Cloudflare Worker, protecting before any request reaches origin:

| Endpoint | Limit | Window |
|---|---|---|
| HLS segments (`.ts`) | 200 req/min | 60s |
| HLS playlists (`.m3u8`) | 60 req/min | 60s |
| API auth (`/api/auth/*`) | 5 req/min | 60s |
| WebSocket upgrade | 30 req/min | 60s |
| API general | 300 req/min | 60s |
| Unknown referer on HLS | 20 req/min | 60s (10× stricter) |

**Bot/scraper UA blocking** (edge, zero origin cost):
`python-requests`, `ffmpeg`, `streamlink`, `youtube-dl`, `yt-dlp`, `curl/`, `wget/`, `scrapy`

---

## Bottlenecks Remaining

| Bottleneck | Description | Fix Required |
|---|---|---|
| **Single RabbitMQ broker** | Fan-out ceiling ~100k msg/s even with Redis Pub/Sub dual-publish | Migrate to RabbitMQ cluster or remove RabbitMQ when `CHAT_PARTITIONED_ENABLED=true` |
| **PostgreSQL write throughput** | `findAll()` calls in 8+ services still exist; index coverage is incomplete | Fix `AMLRulesEngine`, `CreatorSearchService`, `VelocityAnomalyDetector` |
| **Mediasoup CPU** | WebRTC SFU nodes handle ~200–300 consumers each | Pre-tier large streams to HLS-only (shedding tier 2 handles this automatically) |
| **Redis single-node** | All sessions, rate limits, presence, chat on one Redis | Upgrade to Redis Cluster or Upstash multi-region |
| **`SessionRegistryService` SMEMBERS** | `ws:sessions:active` returns all sessions; still used in some cleanup paths | Complete per-pod session set migration |
| **FlywayConfig repair in prod** | No `@Profile("dev")` guard | Add `@Profile("dev")` to `FlywayConfig` |

---

## Cost Estimate

### At 100,000 concurrent viewers (1 stream, 1 hour)

| Component | Cost |
|---|---|
| Cloudflare R2 storage (HLS segments, 1h × 3 bitrates) | ~$0.015 |
| Cloudflare CDN egress | **$0** (R2 → CDN zero egress) |
| Origin bandwidth (2% CDN miss rate) | ~$0.50 |
| GPU transcode worker (1 h264_nvenc instance) | ~$0.20/hr |
| Redis memory | ~$0.10/hr |
| PostgreSQL | ~$0.05/hr |
| **Total per 100k-viewer stream-hour** | **~$0.87** |

### At 1,000,000 concurrent viewers (1 viral stream, 1 hour)

| Component | Cost |
|---|---|
| CDN egress | **$0** (R2) |
| Origin bandwidth (2% miss, 1M viewers × 2.5Mbps avg) | ~$3.00 |
| Extra transcode worker instances (×10 to handle load shed) | ~$2.00 |
| Redis (3 Cluster nodes) | ~$0.50/hr |
| Load shedding (tier 2 active above 200k → HLS only) | Reduces cost ~60% |
| **Total per 1M-viewer stream-hour** | **~$5.50** |

---

## Failure Scenarios

| Scenario | Detection | Response |
|---|---|---|
| Transcode worker crash | Heartbeat TTL=15s expires | Second worker picks BLPOP job; 15s gap in HLS |
| Redis unavailable | fail-open in all services | Rate limits disabled; presence degraded; analytics paused |
| CDN origin unreachable | Cloudflare Load Balancer health check | Failover to secondary origin within 10s |
| Viral stream exceeds capacity | `evaluateShedding()` checks viewer count | Auto-tier 1→2→3 shedding; never crashes |
| PostgreSQL overloaded | Connection pool saturation (HikariCP 30s timeout) | All DB-backed paths timeout; Redis paths unaffected |
| Segment flood from one IP | WAF Worker rate limit (200/min) | 429 at CDN edge; zero origin impact |

---

## Activation Checklist

| Feature | Config key | Default |
|---|---|---|
| Stream sharding | `STREAM_SHARD_COUNT=16` | 16 |
| Partitioned chat | `CHAT_PARTITIONED_ENABLED=true` | false |
| CDN pre-warm | `CDN_PREWARM_ENABLED=true` | true |
| Load shedding | `SHEDDING_ENABLED=true` | true |
| IP-bound HLS tokens | `HLS_TOKEN_IP_BINDING=true` | false |
| Analytics | `ANALYTICS_ENABLED=true` | true |
| WAF Worker | Deploy `cloudflare/waf-rate-limit-worker.js` | Manual |
| Multi-origin | Deploy `docker-compose.eu.yml` + `docker-compose.us.yml` | Manual |
| S3/R2 HLS storage | `TRANSCODE_S3_ENABLED=true` | false |
| Low-latency HLS | `TRANSCODE_LOW_LATENCY=true` | false |

---

## Performance Estimates After Full Activation

| Metric | Single Region | Multi-Region |
|---|---|---|
| Max viewers per stream | 500,000 (hard cap) | 1,000,000+ (geo-distributed) |
| HLS latency (end-to-end) | 4–8s (standard) / 2–4s (LL-HLS) | Same |
| Chat throughput | 800k msg/s (16 partitions) | Same |
| WebRTC viewers per stream | 200,000 (shedding tier 2 kicks in) | Same |
| CDN cache hit ratio | 95–99% | 95–99% |
| Origin HLS bandwidth at 1M viewers | ~50 Gbps → ~2.5 Gbps (after CDN) | ~1.2 Gbps per region |
| System cost per 1M-viewer stream-hour | ~$5.50 | ~$8.00 (2 regions) |
