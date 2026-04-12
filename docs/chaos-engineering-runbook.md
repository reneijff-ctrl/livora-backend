# Chaos Engineering & Failure Hardening Runbook — Livora Platform

> **Version**: 1.0 — Post ultra-scale hardening  
> **Audience**: SRE / On-call engineers  
> **Scope**: 100k–1M concurrent viewers, multi-region (EU + US)

---

## 🚨 Top 10 Real Failure Scenarios

---

### 1. Redis Node Crash (CRITICAL)

**Probability**: High. Redis is currently single-instance (no Sentinel/Cluster).

**What breaks immediately**:
| Component | Failure mode |
|---|---|
| `RateLimitingFilter` | All rate limits fail-open → login brute-force unprotected |
| `WebSocketConfig.beforeHandshake()` | Handshake rate limit fail-open → WS flood possible |
| `WebRTCSignalingController` | IP/global session counters fail-open → session cap bypassed |
| `SessionRegistryService` | All session lookups return null → presence/cleanup breaks |
| `RedisChatBatchService` | Flush loop aborts early → messages accumulate in memory |
| `PresenceTrackingService.getActiveCreatorIds()` | Returns empty set → explore page shows 0 live streams |
| `StreamShardingService` | Viewer count reads return 0 → shedding thresholds never trigger |
| `LivestreamAccessService` | Access grants fail → viewers lose stream access |

**Implemented protections**:
- `RedisCircuitBreakerService`: opens after 5 consecutive failures; all callers pass a safe fallback
- `application.yml`: `timeout: 2000ms`, `connect-timeout: 1000ms` — failures detected in ≤2s not 30s
- All services use try/catch fail-open pattern on Redis errors

**What still breaks** (honestly):
- HLS segment rate limiting becomes unenforced (fail-open by design — prefer availability)
- Chat message throughput temporarily uncapped (Redis Pub/Sub down → RabbitMQ only)
- Session cleanup on disconnect is skipped → stale viewer counts until recovery

**Recovery time**: Redis TTL-based auto-expiry of stale keys within 24h (session TTL). Circuit breaker probes every 10s; closes after 2 successes.

**Runbook**:
```bash
# Check Redis status
docker inspect livora-redis | grep Status

# Restart Redis (Docker Compose)
docker compose restart livora-redis

# Watch circuit breaker recovery in logs
docker logs livora-backend | grep CIRCUIT_BREAKER
```

---

### 2. Redis High Latency Spike (500ms+)

**Cause**: Redis CPU spike, network congestion, large SMEMBERS call on `ws:sessions:active`.

**What breaks**:
- All Redis operations queue → Tomcat threads blocked → thread pool exhaustion
- `RedisChatBatchService.flushBatches()` takes >200ms → next invocation skipped → messages pile up
- `SessionRegistryService` reads become 500ms blocking calls on every WebSocket frame

**Implemented protections**:
- `application.yml`: `timeout: 2000ms` — Redis calls cap at 2s before throwing
- `DatabaseCircuitBreakerService`: 2s timeout on DB calls prevents compounding with Redis latency
- `RedisCircuitBreakerService`: 5 timeout failures open the circuit

**What still breaks**:
- Tomcat thread pool (200 threads) can still exhaust between the first latency spike and circuit opening (5 × 2s = 10s window)
- Recommended: set `server.tomcat.threads.max=400` and add Tomcat connection timeout config

---

### 3. CDN (Cloudflare) Outage

**Cause**: Cloudflare global incident (rare but real — Dec 2021 BGP incident).

**Impact without protection**: 
- 10k viewers × 1 segment/4s = 2,500 req/s hitting Spring Boot JVM
- At 200 Tomcat threads: queue saturation in <1s → 503 cascade

**Implemented protections**:
- `ViewerLoadSheddingService.activateCdnFailureMode()`: forces quality to 480p for all streams
  - Reduces segment bitrate from 5.3Mbps to 2.3Mbps per stream (~55% bandwidth reduction)
- `CdnFailureController`: `POST /api/internal/cdn-failure/activate` (ADMIN only)
- Nginx serves HLS files directly from volume (no JVM in the path for segments)
- Quality reduction + viewer hard-cap (tier 3) together limit origin to ~500 req/s sustainable

**Activation procedure**:
```bash
# Activate CDN failure mode (admin JWT required)
curl -X POST https://api.joinlivora.com/api/internal/cdn-failure/activate \
  -H "Authorization: Bearer $ADMIN_JWT"

# Check status
curl https://api.joinlivora.com/api/internal/cdn-failure/status \
  -H "Authorization: Bearer $ADMIN_JWT"

# Deactivate after CDN recovery
curl -X POST https://api.joinlivora.com/api/internal/cdn-failure/deactivate \
  -H "Authorization: Bearer $ADMIN_JWT"
```

**Auto-trigger** (future): Configure Cloudflare notification webhook → `POST /api/internal/cdn-failure/activate`.

**What still breaks**: 
- HLS viewers experience quality degradation (480p max) during the incident
- CDN-bypassing viewers may see 503 under extreme load if not capped fast enough

---

### 4. PostgreSQL Down / Connection Pool Exhaustion

**Cause**: DB maintenance, OOM kill, HikariCP pool exhausted by slow queries.

**What breaks immediately**:
- Auth flow (`/api/auth/**`) — JWTs can't be validated → ALL endpoints return 401/500
- Stream access check in `HlsProxyController` → HLS playback fails
- WebRTC signaling access check → viewers lose WebRTC

**Key property**: `HlsTokenService.validateToken()` is **DB-free** (pure HMAC). Viewers with a valid token can continue watching HLS even when DB is down.

**Implemented protections**:
- `DatabaseCircuitBreakerService`: 
  - 2s query timeout → slow queries don't block threads for 30s each
  - Opens after 5 failures → short-circuits all DB calls
  - HikariCP `connection-timeout: 3000ms` (down from 30s) — pool exhaustion detected in 3s
  - `leak-detection-threshold: 10000ms` — warns on connections held >10s
- `HlsTokenController.validate()` — never calls DB (pure HMAC, no circuit-breaker needed)

**Cascading failure chain** (prevented):
```
DB slow (10s queries)
  → HikariCP pool exhausted (3s timeout — detected quickly)
  → DatabaseCircuitBreaker OPENS after 5 failures
  → All DB calls short-circuit with fallback (null/empty)
  → HLS auth falls back to token-only validation (no DB call)
  → Streams continue via HLS (WebRTC auth degrades gracefully)
```

**What still breaks**:
- Login (`/api/auth/login`) is DB-dependent — no fallback; users can't authenticate while DB is down
- Admin endpoints are DB-dependent
- New viewer stream-unlock (token wallet deduction) is DB-dependent → new purchases fail

---

### 5. RabbitMQ Broker Down / Message Backlog

**Cause**: AMQP connection refused, broker OOM, queue overflow.

**What breaks without protection**:
- Chat delivery stops → all messages queued in Redis `chat:batch:*` lists
- WebSocket signaling events fail silently

**Implemented protections**:
- `RedisChatBatchService` automatic failover:
  1. `SimpMessagingTemplate.convertAndSend()` throws on broker failure
  2. `brokerConsecutiveFailures` incremented; after 3 failures → `rabbitMqDown = true`
  3. All subsequent sends use `RedisPubSubChatService` exclusively
  4. Every 30s: probe RabbitMQ once; on success → `rabbitMqDown = false` (auto-recovery)
- Redis Pub/Sub is independent of RabbitMQ → chat continues with sub-5ms delivery
- Messages still buffered in Redis (peek-and-trim pattern) — no message loss even during failover

**What still breaks**:
- Admin real-time events (`AdminRealtimeEventService`) use RabbitMQ topics — admin dashboard is silent
- Non-chat WebSocket events that go through STOMP/RabbitMQ relay are delayed until broker recovers

**Monitoring**:
```bash
# Check RabbitMQ status
docker exec livora-rabbitmq rabbitmq-diagnostics status

# Watch failover in logs
docker logs livora-backend | grep RABBITMQ_FAILOVER
```

---

### 6. Transcode Worker Crash Mid-Stream

**Cause**: GPU OOM, CUDA driver fault, OOM kill, pod restart.

**What breaks without protection**:
- FFmpeg process is killed
- `runningProcesses` map cleared (in-memory)
- Redis heartbeat key expires in 15s
- HLS segments stop being written → viewers see buffering then error

**Implemented protections**:
- `TranscodeHeartbeatWatchdog` (runs every 8s in all worker instances):
  1. Compares `stream:active-jobs` set against live `stream:worker:{streamId}` heartbeat keys
  2. When heartbeat expires (worker crashed) → `handleOrphanedJob()` is called
  3. Stored job payload re-pushed to front of `stream:transcode:jobs` queue (LPUSH — priority)
  4. Next healthy worker claims the job within seconds
- Job payload stored at `stream:transcode:job:{streamId}` for re-queue
- 6-hour runaway guard: jobs >6h old are not re-queued (prevents infinite replay)

**Recovery timeline**:
- T+0: Worker crashes, FFmpeg killed
- T+15s: Heartbeat key expires (TTL=15s)
- T+23s: Watchdog detects expired key (runs every 8s)
- T+24s: Job re-pushed to queue
- T+25s: Second worker picks up job (BLPOP)
- T+35s: New FFmpeg process establishes RTMP connection
- T+55s: New HLS segments available (first segment after connection)
- **Total viewer interruption: ~55s max**

**What still breaks**:
- Viewers see buffering for ~55s during recovery
- In-progress segments from the crashed worker are abandoned (partial `.ts` files)
- No automatic notification to viewers of the interruption

---

### 7. Viral Event / 10× Traffic Spike (1M viewers in 60 seconds)

**What happens**:
- CDN absorbs 95%+ of HLS traffic (cold cache miss rate 5% initially, drops to 1% within 1 min)
- Redis receives burst of new INCR operations for rate limiters and viewer counts
- `StreamShardingService` distributes viewer tracking across 16 shards — no hot-key

**Load shedding response timeline**:
```
T+0:   0 → 100k viewers — normal operation
T+10s: 100k viewers — SOFT_CAP logged, no user impact
T+20s: 200k viewers — ViewerLoadSheddingService sets quality-reduced flag
                        → Nginx serves 480p playlists from HLS pre-warm cache
T+30s: 400k viewers — WebRTC consumers denied, force HLS-only
T+60s: 600k viewers — Hard cap active, new viewers see 503 with retry-after
Existing viewers: unaffected throughout (shedding is join-time only)
```

**What still breaks**:
- New viewers during hard cap receive 503 (by design — protecting existing viewers)
- Bot attacks that spike to hard cap in <10s may cause brief shedding for real users

---

### 8. Multi-Region Failure (EU Region Down)

**What happens**:
- Cloudflare Load Balancer detects EU health check failure within 30s
- US pool receives all traffic (Geo steering falls back to US pool)
- EU Redis, RabbitMQ, sessions are unreachable from US

**Protections**:
- `RedisCircuitBreakerService`: EU-originating connections time out → circuit opens → US instance operates independently on its own Redis
- HLS content: Cloudflare serves from CDN edge — completely unaffected by EU origin
- Stream continuity: Creators using EU RTMP are affected (RTMP is single-origin); US RTMP unaffected

**What still breaks**:
- EU creators can't go live (EU Nginx unreachable) — US viewers can still watch EU creator's last HLS segment cache from CDN
- Cross-region viewer sessions from EU can't reconnect until failover completes (~30s Cloudflare health check)

---

### 9. Cascading Failure: Redis Slow → API Slow → Thread Exhaustion

**Chain**:
```
Redis responds at 500ms (not failing, just slow)
  → RateLimitingFilter: each request blocks 500ms waiting for INCR
  → 200 Tomcat threads × 500ms Redis wait = 100 req/s max throughput
  → Queue builds up → Tomcat rejects with 503
  → Users retry → amplification (thundering herd)
  → Redis gets MORE pressure from retries
  → Redis degrades to 1s+ response time
  → Circuit breaker opens (5 failures × 2s = 10s to open)
  → After circuit opens: rate limiters fail-open
  → Traffic floods in (now unprotected) → backend CPU spikes
  → Full outage
```

**Prevention in current implementation**:
- `application.yml`: `timeout: 2000ms` caps Redis waits at 2s
- `RedisCircuitBreakerService`: opens after 5 failures (max 10s cascade window)
- Tomcat 400 thread recommendation (not yet applied — see Notes)

**Mitigation** (apply immediately if this cascade starts):
```bash
# Step 1: Activate CDN failure mode (reduces HLS origin load 55%)
curl -X POST .../api/internal/cdn-failure/activate -H "Authorization: Bearer $ADMIN_JWT"

# Step 2: If Redis is the problem — restart it (data loss risk — only if fully stuck)
docker compose restart livora-redis

# Step 3: Watch logs for circuit breaker state
docker logs -f livora-backend | grep -E "CIRCUIT_BREAKER|RATE_LIMIT|REDIS"
```

---

### 10. Database Slow Queries (5–10s) Leading to Auth Cascade

**Chain**:
```
SELECT * FROM users (missing index) takes 8s
  → HikariCP connection held 8s (10s leak-detection threshold warns)
  → 20-connection pool exhausted in 2.5s under moderate load
  → HikariCP throws ConnectionTimeoutException after 3s
  → DatabaseCircuitBreaker counts failure
  → After 5 failures: OPEN state
  → Auth (`/api/auth/login`) returns 500 — users can't log in
  → HLS access check returns fallback (null Stream) → playback fails
```

**Prevention**:
- `connection-timeout: 3000ms` — pool exhaustion detected in 3s (not 30s)
- `DatabaseCircuitBreaker`: 2s per-query timeout — 8s queries are cancelled after 2s
- HLS validate endpoint is DB-free (HMAC only) — unaffected

**What still breaks**:
- Login is fully DB-dependent; no cached credential path exists
- ~10s disruption window before circuit breaker opens

---

## ⚙️ System Behavior Under Failure (Summary Matrix)

| Failure | Rate Limits | Chat | HLS (existing viewers) | HLS (new join) | WebRTC | Auth |
|---|---|---|---|---|---|---|
| Redis down | fail-open ✓ | Redis PubSub only | continues (HMAC token) | new tokens fail | fail-open | continues (JWT) |
| DB down | fail-open ✓ | unaffected | continues (HMAC) | new access checks fail | degrades | broken |
| RabbitMQ down | unaffected | Redis PubSub only | unaffected | unaffected | unaffected | unaffected |
| CDN down | unaffected | unaffected | continues (480p via origin) | degrades (480p) | unaffected | unaffected |
| Worker crash | unaffected | unaffected | buffering ~55s | buffering ~55s | unaffected | unaffected |
| EU region down | unaffected (US Redis) | US only | CDN edge cache | new from US only | US nodes | US DB |

---

## 🧠 What Still Breaks (Brutally Honest)

1. **Login is DB-dependent** — no JWT cache or offline auth. DB down = no new sessions.
2. **Redis SPOF** — no Sentinel/Cluster. Single Redis failure takes down all state.
3. **Single RabbitMQ** — failover to Redis PubSub is automatic but admin events are lost.
4. **55s HLS gap on worker crash** — viewer sees buffering during transcode worker recovery.
5. **FlywayConfig runs `repair()` in production** — tampered checksums pass silently. Add `@Profile("dev")`.
6. **`findAll()` DB scans** in `AMLRulesEngine`, `CreatorSearchService`, `ChargebackService` — not circuit-breakered.
7. **Tomcat thread pool** default 200 — Redis 2s timeouts × 100 concurrent users = pool exhaustion in 4s. Increase to 400.

---

## 🔧 Runbook Quick Reference

| Scenario | First action | Second action | Log to watch |
|---|---|---|---|
| Redis down | Check `redis.circuit.opened` metric | `docker compose restart livora-redis` | `CIRCUIT_BREAKER` |
| CDN down | `POST /api/internal/cdn-failure/activate` | Monitor origin bandwidth | `CDN_FAILURE_MODE` |
| Worker crash | Check `stream:active-jobs` Redis set | Watchdog auto-requeues in ~23s | `WATCHDOG` |
| RabbitMQ down | `docker compose restart livora-rabbitmq` | Watch auto-recovery probe | `RABBITMQ_FAILOVER` |
| DB slow | Monitor `db.circuit.timeout` metric | Check slow query log | `DB_CIRCUIT` |
| Traffic spike | Watch `stream.shedding.tier` metric | Increase Mediasoup replicas | `LOAD SHEDDING TIER` |

---

## 📊 Circuit Breaker Status Endpoints

```bash
# Redis circuit state (via Micrometer)
curl https://api.joinlivora.com/actuator/prometheus | grep redis.circuit

# DB circuit state
curl https://api.joinlivora.com/actuator/prometheus | grep db.circuit

# CDN failure mode
curl https://api.joinlivora.com/api/internal/cdn-failure/status \
  -H "Authorization: Bearer $ADMIN_JWT"

# RabbitMQ failover state (via app logs — no dedicated endpoint yet)
docker logs livora-backend | grep RABBITMQ_FAILOVER | tail -5
```
