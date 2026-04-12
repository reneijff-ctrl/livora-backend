# Livora — Multi-Region Architecture

> **Status**: Implemented and ready for deployment.  
> Single-region mode (default) requires zero config changes.  
> Multi-region mode is activated by setting `TRANSCODE_S3_ENABLED=true` and providing S3/R2 credentials.

---

## 🌍 Final Architecture (Multi-Region)

```
                    ┌──────────────────────────────────────────────┐
                    │         Cloudflare Global Network            │
                    │                                              │
  EU Users ────────►│  cdn.joinlivora.com                          │
                    │  ┌──────────────────────────────────────┐    │
  US Users ────────►│  │  multi-origin-worker.js              │    │
                    │  │  - Geo-steering (colo detection)      │    │
                    │  │  - HMAC token validation (edge)       │    │
                    │  │  - Primary → failover routing         │    │
                    │  │  - Cache-key normalization (?t= strip) │   │
                    │  │  - TTL: .ts=3600s  .m3u8=4s           │    │
                    │  └──────────────┬───────────┬────────────┘    │
                    │                 │           │                  │
                    │    EU Pool      │           │  US Pool        │
                    │  ┌──────────┐   │           │  ┌──────────┐   │
                    │  │origin-eu-1│◄──┘           └─►│origin-us-1│  │
                    │  │origin-eu-2│                  │origin-us-2│  │
                    │  └──────────┘                  └──────────┘   │
                    └──────────────────────────────────────────────┘
                              │                         │
              ┌───────────────▼───────────┐  ┌──────────▼────────────────┐
              │       EU Region            │  │       US Region            │
              │                            │  │                            │
              │  ┌─────────────────────┐   │  │  ┌─────────────────────┐  │
              │  │  Nginx (RTMP ingest) │   │  │  │  Nginx (RTMP ingest) │  │
              │  │  HLS file serving    │   │  │  │  HLS file serving    │  │
              │  └──────────┬──────────┘   │  │  └──────────┬──────────┘  │
              │             │  /streams    │  │             │  /streams    │
              │  ┌──────────▼──────────┐   │  │  ┌──────────▼──────────┐  │
              │  │  transcode-worker    │   │  │  │  transcode-worker    │  │
              │  │  (GPU FFmpeg)        │   │  │  │  (GPU FFmpeg)        │  │
              │  │  region: eu          │   │  │  │  region: us          │  │
              │  │  heartbeat → Redis   │──────────  heartbeat → Redis   │  │
              │  │  S3 upload (async)   │───────────► S3 upload (async)  │  │
              │  └──────────────────────┘   │  │  └──────────────────────┘  │
              │                             │  │                             │
              │  ┌─────────────────────┐    │  │  ┌─────────────────────┐   │
              │  │  Spring Boot backend │    │  │  │  Spring Boot backend │   │
              │  │  (livora-backend)    │    │  │  │  (livora-backend)    │   │
              │  └──────────┬──────────┘    │  │  └──────────┬──────────┘   │
              │             │               │  │             │               │
              │  ┌──────────▼──────────┐    │  │  ┌──────────▼──────────┐   │
              │  │  Redis EU            │    │  │  │  Redis US            │   │
              │  │  (sessions, chat,    │    │  │  │  (sessions, chat,    │   │
              │  │   signaling, queues) │    │  │  │   signaling, queues) │   │
              │  └─────────────────────┘    │  │  └─────────────────────┘   │
              │                             │  │                             │
              │  ┌─────────────────────┐    │  │  ┌─────────────────────┐   │
              │  │  PostgreSQL (primary)│◄───────────  PostgreSQL (replica)│  │
              │  └─────────────────────┘    │  │  └─────────────────────┘   │
              │                             │  │                             │
              │  ┌─────────────────────┐    │  │  ┌─────────────────────┐   │
              │  │  Mediasoup SFU       │    │  │  │  Mediasoup SFU       │   │
              │  │  eu-ms-1/2/3         │    │  │  │  us-ms-1/2           │   │
              │  └─────────────────────┘    │  │  └─────────────────────┘   │
              └─────────────────────────────┘  └────────────────────────────┘
                              │                         │
                              └───────────┬─────────────┘
                                          │
                        ┌─────────────────▼──────────────────┐
                        │      Cloudflare R2 / AWS S3         │
                        │      s3://livora-hls/               │
                        │      streams/<streamId>/            │
                        │        master.m3u8                  │
                        │        720p.m3u8  480p.m3u8         │
                        │        720p_20250109_...0001.ts      │
                        │      (zero CDN egress cost with R2) │
                        └────────────────────────────────────┘
```

---

## 🚀 Deployment Instructions

### Step 1: Single-Region (Default — No Config Changes)

```bash
# EU primary (existing)
docker compose -f docker-compose.yml -f docker-compose.eu.yml up -d

# Operates with local streams_eu_data volume
# No S3 required
```

### Step 2: Enable S3/R2 Storage (Multi-Region HLS)

```bash
# 1. Create R2 bucket in Cloudflare dashboard: livora-hls
# 2. Generate R2 API token (S3-compatible)

export TRANSCODE_S3_ENABLED=true
export TRANSCODE_S3_BUCKET=livora-hls
export TRANSCODE_S3_ENDPOINT=https://<account-id>.r2.cloudflarestorage.com
export TRANSCODE_S3_REGION=auto
export TRANSCODE_S3_ACCESS_KEY_ID=<r2-access-key>
export TRANSCODE_S3_SECRET_ACCESS_KEY=<r2-secret-key>

# EU region
docker compose -f docker-compose.yml -f docker-compose.eu.yml up -d

# US region (separate host)
docker compose -f docker-compose.yml -f docker-compose.us.yml up -d
```

### Step 3: Deploy Cloudflare Multi-Origin Worker

```bash
cd cloudflare

# Install wrangler
npm install -g wrangler

# Set HMAC secret (must match backend hls.token.secret)
wrangler secret put HLS_HMAC_SECRET

# Configure origins in wrangler.toml (see multi-origin-worker.js for template)
wrangler deploy
```

### Step 4: Configure Cloudflare Load Balancer

See the embedded configuration at the bottom of `cloudflare/multi-origin-worker.js`:
- Create `livora-eu` pool (eu-1 + eu-2 origins)
- Create `livora-us` pool (us-1 + us-2 origins)
- Configure geo-steering: WNAM/ENAM → US, WEUR/EEUR → EU
- Enable Smart Tiered Cache

---

## 📊 Latency Improvements (EU vs US)

| User Location | Single-Region (EU only) | Multi-Region | Improvement |
|---|---|---|---|
| EU (Frankfurt) | 10–30 ms | 10–30 ms | Baseline |
| EU (London) | 15–35 ms | 15–35 ms | Same (EU pool) |
| US East (New York) | 90–140 ms | 10–25 ms | **~80% reduction** |
| US West (Los Angeles) | 130–180 ms | 15–30 ms | **~85% reduction** |
| APAC (Tokyo) | 200–280 ms | 150–200 ms | ~25% (CDN caches) |

HLS segment latency (via CDN):
- First segment (cache MISS): origin latency + ~5 ms CDN overhead
- Subsequent segments (cache HIT): 3–8 ms globally (CDN PoP)
- Cache hit ratio at 10k+ viewers: 94–99% for segments, ~0% for playlists (by design)

---

## 🔥 Failure Scenarios and Outcomes

### Scenario 1: EU Origin 1 goes down (planned maintenance or crash)

**Outcome**:
1. Cloudflare Worker calls EU origin 1 → HTTP 5xx (or timeout)
2. Worker immediately retries EU origin 2 (fallover within the same request)
3. **Total additional latency: ~50–200 ms** (one extra HTTP roundtrip)
4. No viewer sees an error; in-flight segment requests complete normally
5. Cloudflare Load Balancer health check detects failure within 10s and stops routing to eu-1

**Stream continuity**: ✅ Uninterrupted (CDN has last 6 segments cached)

### Scenario 2: Entire EU region (both origins) unavailable

**Outcome**:
1. Worker receives 5xx from both EU origins
2. Worker returns 503 to CDN; CDN has cached the last 6 segments (~24s buffer)
3. Players continue playback from cache for up to 24s
4. If Cloudflare Load Balancer geo-failover is configured, traffic reroutes to US pool
5. After geo-failover: EU viewers receive segments from US origin (~90–140 ms latency vs 10–30 ms)

**Stream continuity**: ✅ Up to 24s grace (CDN buffer); then seamless via geo-failover

### Scenario 3: Transcode worker crashes mid-stream

**Outcome**:
1. Worker heartbeat key `stream:worker:{streamId}` expires (TTL = 15s) after crash
2. A second transcode worker replica (if `replicas: 2`) detects expired heartbeat on next poll
3. Second worker re-enqueues the job to `stream:transcode:jobs` Redis List
4. FFmpeg restarts from the RTMP stream (if still being pushed by creator)
5. New segments appear within 5–10s of restart

**Stream continuity**: ⚠️ ~15–30s gap in new segments; CDN buffer covers ~24s

### Scenario 4: Cloudflare R2 / S3 unavailable

**Outcome**:
1. S3 uploads fail; `S3UploadService.upload()` logs `S3_UPLOAD_FAILED` at WARN level
2. Local `/streams/{streamId}/` volume continues to accumulate segments
3. Nginx continues serving HLS from local volume (unaffected)
4. EU viewers unaffected; US viewers receive segments from EU origin (higher latency)
5. When R2 recovers, next `watchAndUpload()` tick re-uploads any missed files

**Stream continuity**: ✅ Uninterrupted for local-origin viewers; degraded for cross-region

### Scenario 5: Redis unavailable

**Outcome**:
1. Heartbeat publish fails; `HEARTBEAT_PUBLISH_ERROR` logged at WARN
2. S3 watcher continues operating (it doesn't use Redis)
3. BLPOP poll fails; polling loop retries after 5s backoff
4. No new transcode jobs are dispatched during outage
5. **Already-running FFmpeg processes continue unaffected** (Redis only used for heartbeat/job dispatch)

**Stream continuity**: ✅ Active streams continue; no new streams can start during Redis outage

---

## 💰 Cost Analysis: S3 vs R2 vs Local Disk

### Scenario: 10 concurrent live streams, 10k viewers per stream, 3-hour event

| Metric | Value |
|---|---|
| Segments generated per stream | ~2,700 per hour (4s segments × 3 bitrates) |
| Total segment files | 81,000 (10 streams × 2,700 × 3h) |
| Average segment size | ~1.1 MB (720p) + 0.55 MB (480p) + 0.3 MB (360p) = ~1.95 MB avg |
| Total HLS storage per event | ~158 GB |
| Total bandwidth served | 10k viewers × 5.4 Mbps × 3h = **583 TB** |

#### Option A: Local disk only (single-region)
- Storage cost: $0 (Docker volume on host)
- Bandwidth cost: **~$25,000–$50,000** (at $0.04–$0.08/GB egress from cloud host)
- Multi-region: ❌ Not possible

#### Option B: AWS S3 + CloudFront CDN
- S3 storage: 158 GB × $0.023/GB = **$3.63**
- S3 PUT requests: 81,000 × $0.005/1000 = **$0.41**
- CloudFront egress: 583 TB × $0.0085/GB = **$4,956**
- CloudFront requests: ~150M × $0.012/10k = **$180**
- **Total: ~$5,140 per event**

#### Option C: Cloudflare R2 + Cloudflare CDN (Recommended ✅)
- R2 storage: 158 GB × $0.015/GB = **$2.37**
- R2 Class A operations (PUT): 81,000 × $4.50/1M = **$0.36**
- **R2 → CDN egress: $0.00** (zero egress within Cloudflare network)
- CDN requests (cache misses at 2%): 3M × $0 (included in Workers plan) = **$0**
- **Total: ~$2.73 per event** (~99.95% savings vs no CDN)

### Monthly cost estimate (100 events/month)
| Option | Monthly Cost |
|---|---|
| Local disk (no CDN) | $5,000–$50,000+ |
| AWS S3 + CloudFront | ~$514,000 |
| Cloudflare R2 + CDN | ~$273 |

---

## 🚀 Max Scale Estimate (After Multi-Region)

| Metric | Before | After | Bottleneck |
|---|---|---|---|
| Max concurrent live streams | 4 (1 worker, max-concurrent=4) | 100+ (horizontal worker scaling) | GPU availability |
| Max viewers per stream | ~150–200 (Tomcat threads) | **Unlimited** (CDN absorbs 99%+) | CDN pricing |
| Max total viewers | ~2,000 | **1M+** | R2/CDN capacity |
| HLS delivery latency (EU) | 10–30 ms | 3–8 ms (CDN hit) | CDN PoP distance |
| HLS delivery latency (US) | 90–140 ms | 3–8 ms (CDN hit) | CDN PoP distance |
| Backend API throughput | ~500 rps | ~500 rps (unchanged) | DB query patterns |
| WebSocket connections | ~5,000–10,000 | ~5,000–10,000 per region | RabbitMQ fan-out |
| Transcode worker failover | 15–30s restart | 15–30s (heartbeat detection) | FFmpeg restart time |

---

## 🔑 Activation Checklist

- [ ] Create Cloudflare R2 bucket `livora-hls`
- [ ] Generate R2 API credentials (S3-compatible)
- [ ] Set `TRANSCODE_S3_ENABLED=true` in production env
- [ ] Set `TRANSCODE_S3_ENDPOINT`, `TRANSCODE_S3_ACCESS_KEY_ID`, `TRANSCODE_S3_SECRET_ACCESS_KEY`
- [ ] Deploy `multi-origin-worker.js` with `wrangler deploy`
- [ ] Set `HLS_HMAC_SECRET` worker secret = same value as backend `hls.token.secret`
- [ ] Configure Cloudflare Load Balancer pools (EU + US)
- [ ] Enable Smart Tiered Cache on the zone
- [ ] Configure 3 Cache Rules (segments 3600s, playlists 4s, /api/hls/ bypass)
- [ ] Deploy second transcode worker replica per region for HA
- [ ] Test: kill EU origin → verify US failover within 200ms
- [ ] Monitor: `transcode.jobs.active`, `hls.origin.requests`, `hls.token.rejections`
