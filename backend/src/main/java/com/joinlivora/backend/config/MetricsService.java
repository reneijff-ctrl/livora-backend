package com.joinlivora.backend.config;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.Getter;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Central Micrometer metrics registry for all custom application metrics.
 * Exposes Counters and Gauges that are wired into services throughout the codebase.
 */
@Component
public class MetricsService {

    // ── Redis failures ────────────────────────────────────────────────────────
    @Getter private final Counter redisFailuresTotal;

    // ── HTTP Rate Limiting ────────────────────────────────────────────────────
    @Getter private final Counter rateLimitHits;
    @Getter private final Counter rateLimitBlocked;

    // ── WebRTC sessions ───────────────────────────────────────────────────────
    private final AtomicLong webrtcSessionsGlobal = new AtomicLong(0);
    private final AtomicLong webrtcSessionsPerIp  = new AtomicLong(0);

    // ── Chat messages ─────────────────────────────────────────────────────────
    @Getter private final Counter chatMessagesSent;
    @Getter private final Counter chatMessagesRetried;
    @Getter private final Counter chatMessagesFailed;

    // ── Chat Pub/Sub ──────────────────────────────────────────────────────────
    @Getter private final Counter chatMessagesPubSubSent;
    @Getter private final Counter chatMessagesPubSubReceived;

    // ── Cache hits / misses ───────────────────────────────────────────────────
    @Getter private final Counter cacheCharoomHit;
    @Getter private final Counter cacheCharoomMiss;
    @Getter private final Counter cacheStreamHit;
    @Getter private final Counter cacheStreamMiss;

    // ── HLS / CDN delivery ────────────────────────────────────────────────────
    /** Total HLS token-validate requests that reached the origin (CDN miss + direct). */
    @Getter private final Counter hlsOriginRequests;
    /** Total HLS token validations that were rejected (expired / invalid signature). */
    @Getter private final Counter hlsTokenRejections;
    /** Total HLS segment requests served by the Spring fallback controller (non-CDN path). */
    @Getter private final Counter hlsFallbackServed;

    // ── WebSocket sessions ────────────────────────────────────────────────────
    private final AtomicLong wsSessionsActive = new AtomicLong(0);
    private final AtomicLong wsSessionsCount  = new AtomicLong(0);

    public MetricsService(MeterRegistry registry) {

        redisFailuresTotal = Counter.builder("redis.failures.total")
                .description("Total number of Redis operation failures (any service)")
                .register(registry);

        rateLimitHits = Counter.builder("rate.limit.hits")
                .description("Total HTTP rate-limit windows checked")
                .register(registry);

        rateLimitBlocked = Counter.builder("rate.limit.blocked")
                .description("Total HTTP requests blocked by rate limiting")
                .register(registry);

        Gauge.builder("webrtc.sessions.global", webrtcSessionsGlobal, AtomicLong::get)
                .description("Current number of active WebRTC signaling sessions (global)")
                .register(registry);

        Gauge.builder("webrtc.sessions.per_ip", webrtcSessionsPerIp, AtomicLong::get)
                .description("Peak per-IP WebRTC signaling session count in the last window")
                .register(registry);

        chatMessagesSent = Counter.builder("chat.messages.sent")
                .description("Total chat messages flushed successfully from Redis batch")
                .register(registry);

        chatMessagesRetried = Counter.builder("chat.messages.retried")
                .description("Total chat message batches left in Redis for retry (broker/DB failure)")
                .register(registry);

        chatMessagesFailed = Counter.builder("chat.messages.failed")
                .description("Total chat messages permanently dropped due to JSON parse failure")
                .register(registry);

        chatMessagesPubSubSent = Counter.builder("chat.messages.pubsub.sent")
                .description("Total chat messages published to Redis Pub/Sub channels")
                .register(registry);

        chatMessagesPubSubReceived = Counter.builder("chat.messages.pubsub.received")
                .description("Total chat messages received from Redis Pub/Sub and forwarded to STOMP")
                .register(registry);

        cacheCharoomHit = Counter.builder("cache.chatroom.hit")
                .description("ChatRoom Redis cache hits (subscription validation)")
                .register(registry);

        cacheCharoomMiss = Counter.builder("cache.chatroom.miss")
                .description("ChatRoom Redis cache misses (database fallback)")
                .register(registry);

        cacheStreamHit = Counter.builder("cache.stream.hit")
                .description("StreamRoom Redis ZSET cache hits (explore page)")
                .register(registry);

        cacheStreamMiss = Counter.builder("cache.stream.miss")
                .description("StreamRoom Redis ZSET cache misses (database fallback)")
                .register(registry);

        hlsOriginRequests = Counter.builder("hls.origin.requests")
                .description("Total HLS requests reaching the origin (CDN cache miss or direct hit)")
                .register(registry);

        hlsTokenRejections = Counter.builder("hls.token.rejections")
                .description("Total HLS token validation failures (expired, invalid signature, missing)")
                .register(registry);

        hlsFallbackServed = Counter.builder("hls.fallback.served")
                .description("Total HLS responses served by Spring fallback controller (non-Nginx path)")
                .register(registry);

        Gauge.builder("ws.sessions.active", wsSessionsActive, AtomicLong::get)
                .description("Current number of active WebSocket sessions tracked in Redis registry")
                .register(registry);

        Gauge.builder("ws.sessions.count", wsSessionsCount, AtomicLong::get)
                .description("Global WebSocket session count (INCR/DECR per connect/disconnect)")
                .register(registry);
    }

    // ── Mutators for Gauge-backed AtomicLongs ─────────────────────────────────

    public void setWebrtcSessionsGlobal(long value) {
        webrtcSessionsGlobal.set(Math.max(0, value));
    }

    public void incrementWebrtcSessionsGlobal() {
        webrtcSessionsGlobal.incrementAndGet();
    }

    public void decrementWebrtcSessionsGlobal() {
        webrtcSessionsGlobal.updateAndGet(v -> Math.max(0, v - 1));
    }

    public void setWebrtcSessionsPerIp(long value) {
        webrtcSessionsPerIp.set(Math.max(0, value));
    }

    public void setWsSessionsActive(long value) {
        wsSessionsActive.set(Math.max(0, value));
    }

    public void incrementWsSessionsActive() {
        wsSessionsActive.incrementAndGet();
    }

    public void decrementWsSessionsActive() {
        wsSessionsActive.updateAndGet(v -> Math.max(0, v - 1));
    }

    public void setWsSessionsCount(long value) {
        wsSessionsCount.set(Math.max(0, value));
    }

    public void incrementWsSessionsCount() {
        wsSessionsCount.incrementAndGet();
    }

    public void decrementWsSessionsCount() {
        wsSessionsCount.updateAndGet(v -> Math.max(0, v - 1));
    }
}
